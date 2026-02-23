package com.deliveryscheduler.scheduler.optimization;

import com.deliveryscheduler.scheduler.model.RiderSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Relocate: Take an order (pickup + delivery pair) from one rider
 * and insert it into another rider's schedule.
 *
 * Handles the case where initial greedy assignment was suboptimal
 * and a different rider can serve the order more efficiently.
 */
public class RelocateMove implements NeighborhoodOperator {

    private static final int MAX_STOPS = 8;

    @Override
    public List<Move> generateMoves(Map<Long, RiderSchedule> schedules) {
        List<Move> moves = new ArrayList<>();

        for (Map.Entry<Long, RiderSchedule> source : schedules.entrySet()) {
            Long sourceRiderId = source.getKey();
            RiderSchedule sourceSchedule = source.getValue();

            for (Long orderId : sourceSchedule.getUncompletedOrderIds()) {
                for (Map.Entry<Long, RiderSchedule> target : schedules.entrySet()) {
                    if (target.getKey().equals(sourceRiderId)) continue;

                    RiderSchedule targetSchedule = target.getValue();
                    if (targetSchedule.uncompletedStopCount() >= MAX_STOPS) continue;

                    moves.add(new Move(sourceRiderId, target.getKey(), orderId));
                }
            }
        }

        return moves;
    }
}
