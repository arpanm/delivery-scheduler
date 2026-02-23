package com.deliveryscheduler.scheduler.optimization;

import com.deliveryscheduler.domain.model.StopType;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;

import java.util.*;

/**
 * 2-opt: Reverse a segment of stops within a single rider's route.
 * Classic TSP improvement heuristic adapted for VRPTW.
 *
 * Only generates moves where the reversed segment doesn't violate
 * precedence constraints (pickup before delivery for same order)
 * and doesn't contain any frozen stops.
 */
public class TwoOptMove implements NeighborhoodOperator {

    @Override
    public List<Move> generateMoves(Map<Long, RiderSchedule> schedules, Set<Long> frozenOrderIds) {
        List<Move> moves = new ArrayList<>();

        for (Map.Entry<Long, RiderSchedule> entry : schedules.entrySet()) {
            Long riderId = entry.getKey();
            RiderSchedule schedule = entry.getValue();
            int start = schedule.getCurrentStopIndex();
            int n = schedule.getStops().size();

            for (int i = start; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    // Skip if any stop in the segment [i..j] belongs to a frozen order
                    if (segmentContainsFrozen(schedule, i, j, frozenOrderIds)) continue;

                    if (precedenceSafeToReverse(schedule, i, j)) {
                        moves.add(new Move(Move.MoveType.TWO_OPT, riderId, i, j));
                    }
                }
            }
        }

        return moves;
    }

    /**
     * Check if any stop in the segment [i..j] belongs to a frozen order.
     */
    private boolean segmentContainsFrozen(RiderSchedule schedule, int i, int j,
                                           Set<Long> frozenOrderIds) {
        List<ScheduledStop> stops = schedule.getStops();
        for (int k = i; k <= j; k++) {
            if (frozenOrderIds.contains(stops.get(k).getOrderId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if reversing the segment [i..j] would violate the pickup-before-delivery
     * constraint for any order.
     */
    private boolean precedenceSafeToReverse(RiderSchedule schedule, int i, int j) {
        List<ScheduledStop> stops = schedule.getStops();

        // Collect order IDs that have both a pickup and delivery in the segment
        Map<Long, List<Integer>> orderPositions = new HashMap<>();
        for (int k = i; k <= j; k++) {
            ScheduledStop stop = stops.get(k);
            orderPositions.computeIfAbsent(stop.getOrderId(), id -> new ArrayList<>()).add(k);
        }

        // For each order in the segment, check if reversal would put delivery before pickup
        for (Map.Entry<Long, List<Integer>> orderEntry : orderPositions.entrySet()) {
            List<Integer> positions = orderEntry.getValue();
            if (positions.size() < 2) continue; // only one stop in segment, safe

            // Find pickup and delivery positions
            int pickupPos = -1, deliveryPos = -1;
            for (int pos : positions) {
                if (stops.get(pos).getType() == StopType.PICKUP) pickupPos = pos;
                else deliveryPos = pos;
            }

            if (pickupPos >= 0 && deliveryPos >= 0) {
                // After reversal: new position = i + j - original_position
                int newPickupPos = i + j - pickupPos;
                int newDeliveryPos = i + j - deliveryPos;
                if (newPickupPos >= newDeliveryPos) {
                    return false; // would violate precedence
                }
            }
        }

        return true;
    }
}
