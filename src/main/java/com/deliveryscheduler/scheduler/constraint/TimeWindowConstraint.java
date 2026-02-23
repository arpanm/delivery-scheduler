package com.deliveryscheduler.scheduler.constraint;

import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;

public class TimeWindowConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(RiderSchedule schedule) {
        for (ScheduledStop stop : schedule.getStops()) {
            if (stop.isCompleted()) continue;
            if (stop.getEstimatedArrival() != null
                    && stop.getTimeWindow().isViolatedBy(stop.getEstimatedArrival())) {
                return false;
            }
        }
        return true;
    }
}
