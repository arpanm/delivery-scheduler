package com.deliveryscheduler.scheduler.optimization;

import com.deliveryscheduler.scheduler.model.RiderSchedule;

import java.util.List;
import java.util.Map;

/**
 * Interface for local search neighborhood operators.
 * Each operator generates a set of moves that modify the current solution.
 */
public interface NeighborhoodOperator {

    List<Move> generateMoves(Map<Long, RiderSchedule> schedules);
}
