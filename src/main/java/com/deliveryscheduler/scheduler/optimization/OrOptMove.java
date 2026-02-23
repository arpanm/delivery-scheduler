package com.deliveryscheduler.scheduler.optimization;

import com.deliveryscheduler.scheduler.model.RiderSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Or-opt: Move a chain of 1-3 consecutive stops to a different position
 * within the same rider's route. Effective for fine-tuning stop order
 * without the full segment reversal of 2-opt.
 */
public class OrOptMove implements NeighborhoodOperator {

    private static final int MAX_CHAIN_LENGTH = 3;

    @Override
    public List<Move> generateMoves(Map<Long, RiderSchedule> schedules) {
        List<Move> moves = new ArrayList<>();

        for (Map.Entry<Long, RiderSchedule> entry : schedules.entrySet()) {
            Long riderId = entry.getKey();
            RiderSchedule schedule = entry.getValue();
            int start = schedule.getCurrentStopIndex();
            int n = schedule.getStops().size();

            for (int chainLen = 1; chainLen <= MAX_CHAIN_LENGTH; chainLen++) {
                for (int i = start; i + chainLen <= n; i++) {
                    // Skip if any stop in chain is completed
                    boolean hasCompleted = false;
                    for (int k = i; k < i + chainLen; k++) {
                        if (schedule.getStops().get(k).isCompleted()) {
                            hasCompleted = true;
                            break;
                        }
                    }
                    if (hasCompleted) continue;

                    // Try inserting the chain at every other valid position
                    for (int targetIdx = start; targetIdx <= n - chainLen; targetIdx++) {
                        // Skip if target overlaps with the chain's current position
                        if (targetIdx >= i && targetIdx <= i + chainLen) continue;
                        if (targetIdx == i) continue; // no-op

                        moves.add(new Move(riderId, i, i + chainLen - 1, targetIdx));
                    }
                }
            }
        }

        return moves;
    }
}
