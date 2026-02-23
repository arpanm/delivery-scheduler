package com.deliveryscheduler.scheduler.constraint;

import com.deliveryscheduler.scheduler.model.RiderSchedule;

public interface ScheduleConstraint {
    boolean isSatisfied(RiderSchedule schedule);
}
