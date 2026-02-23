package com.deliveryscheduler.scheduler.optimization;

import com.deliveryscheduler.config.SchedulerProperties;
import com.deliveryscheduler.scheduler.constraint.ConstraintEngine;
import com.deliveryscheduler.scheduler.cost.CostFunction;
import com.deliveryscheduler.scheduler.insertion.InsertionHeuristic;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Periodic re-optimization using steepest descent local search.
 * Bounded by iteration count and wall-clock time per zone.
 *
 * Respects freeze windows: stops whose estimated arrival falls within
 * the freeze window (default 5 min) are never moved, since the rider
 * is already committed to reaching them.
 */
@Component
public class LocalSearchOptimizer {

    private static final Logger log = LoggerFactory.getLogger(LocalSearchOptimizer.class);

    private final ConstraintEngine constraintEngine;
    private final CostFunction costFunction;
    private final InsertionHeuristic insertionHeuristic;
    private final List<NeighborhoodOperator> operators;
    private final int maxIterations;
    private final long maxTimeMs;
    private final int freezeWindowSeconds;

    public LocalSearchOptimizer(ConstraintEngine constraintEngine,
                                 CostFunction costFunction,
                                 InsertionHeuristic insertionHeuristic,
                                 SchedulerProperties properties) {
        this.constraintEngine = constraintEngine;
        this.costFunction = costFunction;
        this.insertionHeuristic = insertionHeuristic;
        this.operators = List.of(
                new RelocateMove(),
                new TwoOptMove(),
                new OrOptMove()
        );
        this.maxIterations = properties.getOptimization().getMaxIterations();
        this.maxTimeMs = properties.getOptimization().getMaxTimeMs();
        this.freezeWindowSeconds = properties.getOptimization().getFreezeWindowSeconds();
    }

    /**
     * Improve the current solution using local search.
     * Returns true if any improvement was found.
     *
     * Computes frozen order IDs upfront: any order with a stop arriving
     * within freezeWindowSeconds is protected from all moves.
     */
    public boolean optimize(Map<Long, RiderSchedule> schedules) {
        Instant now = Instant.now();
        Set<Long> frozenOrderIds = computeFrozenOrderIds(schedules, now);

        double currentCost = costFunction.absoluteCost(schedules);
        boolean improved = false;
        long startTime = System.currentTimeMillis();

        for (int iter = 0; iter < maxIterations; iter++) {
            if (System.currentTimeMillis() - startTime > maxTimeMs) break;

            Move bestMove = null;
            double bestDelta = 0;

            for (NeighborhoodOperator operator : operators) {
                List<Move> moves = operator.generateMoves(schedules, frozenOrderIds);

                for (Move move : moves) {
                    Map<Long, RiderSchedule> candidate = applyMoveSpeculatively(schedules, move);
                    if (candidate == null) continue;

                    // Check feasibility of affected riders
                    boolean feasible = move.getAffectedRiders().stream()
                            .allMatch(rid -> {
                                RiderSchedule rs = candidate.get(rid);
                                return rs == null || constraintEngine.checkAll(rs);
                            });

                    if (!feasible) continue;

                    double newCost = costFunction.absoluteCost(candidate);
                    double delta = newCost - currentCost;

                    // For cross-rider relocations, add reassignment penalty
                    if (move.getType() == Move.MoveType.RELOCATE
                            && move.getTargetRiderId() != null
                            && !move.getTargetRiderId().equals(move.getSourceRiderId())) {
                        delta += costFunction.reassignmentCost();
                    }

                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestMove = move;
                    }
                }
            }

            if (bestMove != null) {
                applyMoveInPlace(schedules, bestMove);
                currentCost += bestDelta;
                improved = true;
            } else {
                break; // local optimum
            }
        }

        return improved;
    }

    /**
     * Identify all order IDs that have at least one frozen stop
     * (estimated arrival within freeze window from now).
     */
    private Set<Long> computeFrozenOrderIds(Map<Long, RiderSchedule> schedules, Instant now) {
        Set<Long> frozenIds = new HashSet<>();
        for (RiderSchedule schedule : schedules.values()) {
            for (ScheduledStop stop : schedule.getStops()) {
                if (stop.isFrozen(now, freezeWindowSeconds)) {
                    frozenIds.add(stop.getOrderId());
                }
            }
        }
        if (!frozenIds.isEmpty()) {
            log.debug("Frozen {} orders within {}s window", frozenIds.size(), freezeWindowSeconds);
        }
        return frozenIds;
    }

    /**
     * Apply a move to a copy of the schedules for speculative evaluation.
     */
    private Map<Long, RiderSchedule> applyMoveSpeculatively(
            Map<Long, RiderSchedule> schedules, Move move) {

        Map<Long, RiderSchedule> copy = new HashMap<>();
        for (Map.Entry<Long, RiderSchedule> entry : schedules.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return applyMove(copy, move) ? copy : null;
    }

    private void applyMoveInPlace(Map<Long, RiderSchedule> schedules, Move move) {
        applyMove(schedules, move);
    }

    /**
     * Apply a move to the given schedules. Returns false if the move
     * cannot be applied (e.g., source order not found).
     */
    private boolean applyMove(Map<Long, RiderSchedule> schedules, Move move) {
        switch (move.getType()) {
            case RELOCATE:
                return applyRelocate(schedules, move);
            case TWO_OPT:
                return applyTwoOpt(schedules, move);
            case OR_OPT:
                return applyOrOpt(schedules, move);
            default:
                return false;
        }
    }

    private boolean applyRelocate(Map<Long, RiderSchedule> schedules, Move move) {
        RiderSchedule source = schedules.get(move.getSourceRiderId());
        RiderSchedule target = schedules.get(move.getTargetRiderId());
        if (source == null || target == null) return false;

        // Remove order from source
        List<ScheduledStop> removed = source.removeOrderStops(move.getOrderId());
        if (removed.isEmpty()) return false;

        // Find pickup and delivery stops
        ScheduledStop pickup = null, delivery = null;
        for (ScheduledStop stop : removed) {
            if (stop.getType() == com.deliveryscheduler.domain.model.StopType.PICKUP) pickup = stop;
            else delivery = stop;
        }
        if (pickup == null || delivery == null) return false;

        // Find best insertion position in target (greedy)
        int targetStart = target.getCurrentStopIndex();
        int n = target.getStops().size();
        double bestCost = Double.MAX_VALUE;
        int bestPi = -1, bestDi = -1;

        for (int pi = targetStart; pi <= n; pi++) {
            for (int di = pi; di <= n; di++) {
                RiderSchedule trial = target.copy();
                trial.insertStopAt(pi, pickup.copy());
                trial.insertStopAt(di + 1, delivery.copy());
                constraintEngine.propagateArrivalTimes(trial);
                double cost = costFunction.totalDistance(trial);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestPi = pi;
                    bestDi = di + 1;
                }
            }
        }

        if (bestPi < 0) return false;

        target.insertStopAt(bestPi, pickup);
        target.insertStopAt(bestDi, delivery);
        return true;
    }

    private boolean applyTwoOpt(Map<Long, RiderSchedule> schedules, Move move) {
        RiderSchedule schedule = schedules.get(move.getSourceRiderId());
        if (schedule == null) return false;

        List<ScheduledStop> stops = schedule.getStops();
        int i = move.getSegmentStart();
        int j = move.getSegmentEnd();
        if (i < 0 || j >= stops.size()) return false;

        // Reverse the segment [i..j]
        while (i < j) {
            ScheduledStop temp = stops.get(i);
            stops.set(i, stops.get(j));
            stops.set(j, temp);
            i++;
            j--;
        }
        return true;
    }

    private boolean applyOrOpt(Map<Long, RiderSchedule> schedules, Move move) {
        RiderSchedule schedule = schedules.get(move.getSourceRiderId());
        if (schedule == null) return false;

        List<ScheduledStop> stops = schedule.getStops();
        int start = move.getSegmentStart();
        int end = move.getSegmentEnd();
        int target = move.getTargetIndex();

        if (start < 0 || end >= stops.size()) return false;

        // Extract chain
        List<ScheduledStop> chain = new ArrayList<>();
        for (int k = start; k <= end; k++) {
            chain.add(stops.get(k));
        }

        // Remove chain from current position
        for (int k = end; k >= start; k--) {
            stops.remove(k);
        }

        // Adjust target index if it was after the removed chain
        int adjustedTarget = target;
        if (target > start) {
            adjustedTarget -= chain.size();
        }
        adjustedTarget = Math.min(adjustedTarget, stops.size());

        // Insert chain at target position
        stops.addAll(adjustedTarget, chain);
        return true;
    }
}
