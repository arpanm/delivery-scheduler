package com.deliveryscheduler.scheduler.cost;

import com.deliveryscheduler.config.SchedulerProperties;
import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class CostFunction {

    private final double wDistance;
    private final double wTime;
    private final double wDetour;
    private final double wIdle;

    public CostFunction(SchedulerProperties properties) {
        this.wDistance = properties.getCost().getWeightDistance();
        this.wTime = properties.getCost().getWeightTime();
        this.wDetour = properties.getCost().getWeightDetour();
        this.wIdle = properties.getCost().getWeightIdle();
    }

    /**
     * Compute the incremental cost of inserting a new order: how much worse
     * does the solution get? Lower is better.
     */
    public double insertionCost(RiderSchedule before, RiderSchedule after) {
        double distanceDelta = totalDistance(after) - totalDistance(before);
        double timeDelta = totalTime(after) - totalTime(before);
        double detourPenalty = maxDetourToExistingOrders(before, after);
        double idlePenalty = totalIdleTime(after);

        return wDistance * (distanceDelta / 1000.0)     // m -> km
                + wTime * (timeDelta / 60.0)            // s -> min
                + wDetour * (detourPenalty / 60.0)       // s -> min
                + wIdle * (idlePenalty / 60.0);          // s -> min
    }

    /**
     * Absolute cost for comparing entire solutions during local search.
     */
    public double absoluteCost(Map<Long, RiderSchedule> allSchedules) {
        return allSchedules.values().stream()
                .mapToDouble(s -> {
                    double dist = totalDistance(s) / 1000.0;
                    double time = totalTime(s) / 60.0;
                    double idle = totalIdleTime(s) / 60.0;
                    return wDistance * dist + wTime * time + wIdle * idle;
                })
                .sum();
    }

    /**
     * Total route distance in meters across all uncompleted stops.
     */
    public double totalDistance(RiderSchedule schedule) {
        List<ScheduledStop> stops = schedule.getStops();
        if (stops.isEmpty()) return 0;

        double total = 0;
        GeoLocation prev = schedule.getCurrentLocation();

        for (int i = schedule.getCurrentStopIndex(); i < stops.size(); i++) {
            GeoLocation next = stops.get(i).getLocation();
            total += prev.distanceTo(next);
            prev = next;
        }
        return total;
    }

    /**
     * Total time in seconds from now until last stop's estimated departure.
     */
    public double totalTime(RiderSchedule schedule) {
        List<ScheduledStop> stops = schedule.getStops();
        if (stops.isEmpty()) return 0;

        Instant now = Instant.now();
        ScheduledStop lastStop = stops.get(stops.size() - 1);
        Instant lastDeparture = lastStop.getEstimatedDeparture();
        if (lastDeparture == null) return 0;

        return Math.max(0, Duration.between(now, lastDeparture).getSeconds());
    }

    /**
     * Total idle time: sum of time a rider waits at stops before the
     * earliest time window opens.
     */
    public double totalIdleTime(RiderSchedule schedule) {
        double idle = 0;
        for (ScheduledStop stop : schedule.getStops()) {
            if (stop.isCompleted() || stop.getEstimatedArrival() == null) continue;
            if (stop.getTimeWindow().getEarliest() != null
                    && stop.getEstimatedArrival().isBefore(stop.getTimeWindow().getEarliest())) {
                idle += Duration.between(stop.getEstimatedArrival(),
                        stop.getTimeWindow().getEarliest()).getSeconds();
            }
        }
        return idle;
    }

    /**
     * For each order that existed in 'before', compute how much later its
     * delivery becomes in 'after'. Return the maximum detour.
     */
    private double maxDetourToExistingOrders(RiderSchedule before, RiderSchedule after) {
        double maxDetour = 0;

        for (ScheduledStop beforeStop : before.getStops()) {
            if (beforeStop.isCompleted()) continue;
            if (beforeStop.getEstimatedArrival() == null) continue;

            // Find corresponding stop in 'after'
            for (ScheduledStop afterStop : after.getStops()) {
                if (afterStop.getOrderId().equals(beforeStop.getOrderId())
                        && afterStop.getType() == beforeStop.getType()
                        && afterStop.getEstimatedArrival() != null) {
                    double detour = Duration.between(beforeStop.getEstimatedArrival(),
                            afterStop.getEstimatedArrival()).getSeconds();
                    maxDetour = Math.max(maxDetour, detour);
                    break;
                }
            }
        }
        return maxDetour;
    }
}
