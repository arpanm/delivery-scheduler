package com.deliveryscheduler.scheduler.optimization;

import com.deliveryscheduler.scheduler.model.RiderSchedule;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for local search neighborhood operators.
 * Each operator generates a set of moves that modify the current solution.
 *
 * The frozenOrderIds parameter identifies orders whose stops must not be
 * relocated or rearranged because the rider is already committed to them
 * (estimated arrival within the freeze window).
 */
public interface NeighborhoodOperator {

    List<Move> generateMoves(Map<Long, RiderSchedule> schedules, Set<Long> frozenOrderIds);
}
