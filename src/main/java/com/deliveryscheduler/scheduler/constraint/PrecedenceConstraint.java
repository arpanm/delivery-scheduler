package com.deliveryscheduler.scheduler.constraint;

import com.deliveryscheduler.domain.model.StopType;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ensures that for each order, the PICKUP stop appears before the DELIVERY stop.
 */
public class PrecedenceConstraint implements ScheduleConstraint {

    @Override
    public boolean isSatisfied(RiderSchedule schedule) {
        Map<Long, Integer> pickupIndex = new HashMap<>();
        Map<Long, Integer> deliveryIndex = new HashMap<>();
        List<ScheduledStop> stops = schedule.getStops();

        for (int i = 0; i < stops.size(); i++) {
            ScheduledStop stop = stops.get(i);
            if (stop.getType() == StopType.PICKUP) {
                pickupIndex.put(stop.getOrderId(), i);
            } else if (stop.getType() == StopType.DELIVERY) {
                deliveryIndex.put(stop.getOrderId(), i);
            }
        }

        for (Map.Entry<Long, Integer> entry : deliveryIndex.entrySet()) {
            Long orderId = entry.getKey();
            Integer pi = pickupIndex.get(orderId);
            if (pi == null || pi >= entry.getValue()) {
                return false;
            }
        }
        return true;
    }
}
