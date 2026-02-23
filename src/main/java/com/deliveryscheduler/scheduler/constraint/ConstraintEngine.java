package com.deliveryscheduler.scheduler.constraint;

import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.routing.TravelTimeProvider;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Propagates estimated arrival times through a schedule and checks all constraints.
 */
@Component
public class ConstraintEngine {

    private final TravelTimeProvider travelTimeEstimator;
    private final List<ScheduleConstraint> constraints;

    public ConstraintEngine(TravelTimeProvider travelTimeEstimator) {
        this.travelTimeEstimator = travelTimeEstimator;
        this.constraints = List.of(
                new TimeWindowConstraint(),
                new PrecedenceConstraint(),
                new RiderCapacityConstraint(3) // default max 3 concurrent orders
        );
    }

    /**
     * Forward-propagate arrival times, then check all constraints.
     */
    public boolean checkAll(RiderSchedule schedule) {
        propagateArrivalTimes(schedule);
        return constraints.stream().allMatch(c -> c.isSatisfied(schedule));
    }

    /**
     * Check only time window constraints (used for early termination in insertion).
     */
    public boolean isDeliveryTooLate(RiderSchedule schedule, int deliveryIndex) {
        if (deliveryIndex >= schedule.getStops().size()) return true;
        ScheduledStop stop = schedule.getStops().get(deliveryIndex);
        return stop.getEstimatedArrival() != null
                && stop.getTimeWindow().isViolatedBy(stop.getEstimatedArrival());
    }

    /**
     * Forward-propagate estimated arrival times through the schedule.
     *
     * For each stop i:
     *   arrival[i] = departure[i-1] + travelTime(stop[i-1], stop[i])
     *   departure[i] = max(arrival[i], timeWindow.earliest) + serviceTime
     */
    public void propagateArrivalTimes(RiderSchedule schedule) {
        List<ScheduledStop> stops = schedule.getStops();
        GeoLocation prevLocation = schedule.getCurrentLocation();
        Instant prevDeparture = Instant.now();

        for (int i = 0; i < stops.size(); i++) {
            ScheduledStop stop = stops.get(i);

            // Skip completed stops — their times are fixed
            if (stop.isCompleted()) {
                if (stop.getEstimatedArrival() != null) {
                    prevDeparture = stop.getEstimatedArrival().plusSeconds(stop.getServiceTimeSeconds());
                }
                prevLocation = stop.getLocation();
                continue;
            }

            long travelSeconds = travelTimeEstimator.estimateSeconds(prevLocation, stop.getLocation());
            Instant arrival = prevDeparture.plusSeconds(travelSeconds);
            stop.setEstimatedArrival(arrival);

            // If arrival is before earliest, rider waits
            Instant effectiveStart = (stop.getTimeWindow().getEarliest() != null
                    && arrival.isBefore(stop.getTimeWindow().getEarliest()))
                    ? stop.getTimeWindow().getEarliest() : arrival;
            Instant departure = effectiveStart.plusSeconds(stop.getServiceTimeSeconds());

            prevLocation = stop.getLocation();
            prevDeparture = departure;
        }
    }
}
