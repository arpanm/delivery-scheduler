package com.deliveryscheduler.scheduler.insertion;

import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.Order;
import com.deliveryscheduler.domain.model.StopType;
import com.deliveryscheduler.domain.model.TimeWindow;
import com.deliveryscheduler.routing.TravelTimeProvider;
import com.deliveryscheduler.scheduler.constraint.ConstraintEngine;
import com.deliveryscheduler.scheduler.cost.CostFunction;
import com.deliveryscheduler.scheduler.model.InsertionCandidate;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Real-time insertion heuristic: when a new order arrives, finds the best
 * rider and position to insert the order's pickup + delivery stops.
 *
 * Must complete in <100ms. Evaluates O(R * N^2) candidates where R is the
 * number of candidate riders and N is the max stops per rider.
 */
@Component
public class InsertionHeuristic {

    private static final int MAX_STOPS_PER_RIDER = 8; // 4 orders * 2 stops

    private final ConstraintEngine constraintEngine;
    private final CostFunction costFunction;
    private final TravelTimeProvider travelTimeEstimator;

    public InsertionHeuristic(ConstraintEngine constraintEngine,
                              CostFunction costFunction,
                              TravelTimeProvider travelTimeEstimator) {
        this.constraintEngine = constraintEngine;
        this.costFunction = costFunction;
        this.travelTimeEstimator = travelTimeEstimator;
    }

    /**
     * Find the best feasible insertion of the given order into any of the
     * candidate rider schedules. Returns null if no feasible insertion exists.
     */
    public InsertionCandidate findBestInsertion(Order order, List<RiderSchedule> candidateSchedules) {
        InsertionCandidate best = null;
        double bestCost = Double.MAX_VALUE;

        ScheduledStop pickupStop = buildPickupStop(order);
        ScheduledStop deliveryStop = buildDeliveryStop(order);

        for (RiderSchedule schedule : candidateSchedules) {
            // Skip riders at capacity
            if (schedule.uncompletedStopCount() >= MAX_STOPS_PER_RIDER) {
                continue;
            }

            // Propagate times on the original schedule for cost comparison
            constraintEngine.propagateArrivalTimes(schedule);

            int startIdx = schedule.getCurrentStopIndex();
            int n = schedule.getStops().size();

            // Try every valid (pickupIndex, deliveryIndex) pair
            for (int pi = startIdx; pi <= n; pi++) {
                // Early termination: check if we can reach pickup in time
                if (cannotReachPickupInTime(schedule, pi, pickupStop)) {
                    break;
                }

                for (int di = pi; di <= n; di++) {
                    // Build candidate schedule with both stops inserted
                    RiderSchedule candidate = schedule.copy();
                    candidate.insertStopAt(pi, pickupStop.copy());
                    candidate.insertStopAt(di + 1, deliveryStop.copy());

                    // Check feasibility (propagates arrival times internally)
                    boolean feasible = constraintEngine.checkAll(candidate);

                    if (!feasible) {
                        // If delivery time window is violated, later delivery
                        // positions will only be worse
                        if (constraintEngine.isDeliveryTooLate(candidate, di + 1)) {
                            break;
                        }
                        continue;
                    }

                    double cost = costFunction.insertionCost(schedule, candidate);
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = new InsertionCandidate(
                                schedule.getRiderId(), pi, di + 1,
                                cost, true, candidate);
                    }
                }
            }
        }

        return best;
    }

    private ScheduledStop buildPickupStop(Order order) {
        GeoLocation pickupLocation = order.getRestaurant().getLocation();
        // Pickup time window: food ready time to ready time + 10 min max wait
        Instant readyAt = order.getEstimatedReadyAt();
        TimeWindow pickupWindow = new TimeWindow(readyAt, readyAt.plusSeconds(600));
        return new ScheduledStop(
                order.getId(), StopType.PICKUP, pickupLocation,
                pickupWindow, travelTimeEstimator.getPickupServiceTimeSeconds());
    }

    private ScheduledStop buildDeliveryStop(Order order) {
        GeoLocation deliveryLocation = order.getDeliveryLocation();
        // Delivery time window: now to promised delivery time (hard constraint)
        TimeWindow deliveryWindow = new TimeWindow(Instant.now(), order.getPromisedDeliveryBy());
        return new ScheduledStop(
                order.getId(), StopType.DELIVERY, deliveryLocation,
                deliveryWindow, travelTimeEstimator.getDeliveryServiceTimeSeconds());
    }

    /**
     * Quick check: can the rider reach the pickup location before
     * the pickup time window closes?
     */
    private boolean cannotReachPickupInTime(RiderSchedule schedule, int insertionIndex,
                                             ScheduledStop pickupStop) {
        GeoLocation prevLocation;
        Instant prevDeparture;

        if (insertionIndex == 0 || schedule.getStops().isEmpty()) {
            prevLocation = schedule.getCurrentLocation();
            prevDeparture = Instant.now();
        } else {
            int prevIdx = Math.min(insertionIndex - 1, schedule.getStops().size() - 1);
            ScheduledStop prevStop = schedule.getStops().get(prevIdx);
            prevLocation = prevStop.getLocation();
            prevDeparture = prevStop.getEstimatedDeparture();
            if (prevDeparture == null) {
                prevDeparture = Instant.now();
            }
        }

        long travelSeconds = travelTimeEstimator.estimateSeconds(prevLocation, pickupStop.getLocation());
        Instant arrival = prevDeparture.plusSeconds(travelSeconds);

        return pickupStop.getTimeWindow().isViolatedBy(arrival);
    }
}
