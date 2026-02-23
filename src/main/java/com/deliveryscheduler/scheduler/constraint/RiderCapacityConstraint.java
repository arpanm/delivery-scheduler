package com.deliveryscheduler.scheduler.constraint;

import com.deliveryscheduler.scheduler.model.RiderSchedule;

/**
 * Limits the number of uncompleted stops per rider.
 * With maxConcurrentOrders = 3, a rider can have at most 6 uncompleted stops
 * (3 pickups + 3 deliveries).
 */
public class RiderCapacityConstraint implements ScheduleConstraint {

    private final int maxStops;

    public RiderCapacityConstraint(int maxConcurrentOrders) {
        this.maxStops = maxConcurrentOrders * 2;
    }

    @Override
    public boolean isSatisfied(RiderSchedule schedule) {
        return schedule.uncompletedStopCount() <= maxStops;
    }
}
