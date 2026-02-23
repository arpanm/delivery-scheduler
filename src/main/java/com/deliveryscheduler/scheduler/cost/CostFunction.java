package com.deliveryscheduler.scheduler.cost;

import com.deliveryscheduler.config.SchedulerProperties;
import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.StopType;
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
    private final double wSlaRisk;
    private final double wBatchingBonus;
    private final double wReassignmentPenalty;
    private final int slaRiskThresholdSeconds;

    public CostFunction(SchedulerProperties properties) {
        this.wDistance = properties.getCost().getWeightDistance();
        this.wTime = properties.getCost().getWeightTime();
        this.wDetour = properties.getCost().getWeightDetour();
        this.wIdle = properties.getCost().getWeightIdle();
        this.wSlaRisk = properties.getCost().getWeightSlaRisk();
        this.wBatchingBonus = properties.getCost().getWeightBatchingBonus();
        this.wReassignmentPenalty = properties.getCost().getWeightReassignmentPenalty();
        this.slaRiskThresholdSeconds = properties.getCost().getSlaRiskThresholdSeconds();
    }

    /**
     * Compute the incremental cost of inserting a new order: how much worse
     * does the solution get? Lower is better.
     *
     * Includes SLA risk penalty (exponential as delivery approaches deadline)
     * and batching bonus (discount for pickups near existing pickups).
     */
    public double insertionCost(RiderSchedule before, RiderSchedule after) {
        double distanceDelta = totalDistance(after) - totalDistance(before);
        double timeDelta = totalTime(after) - totalTime(before);
        double detourPenalty = maxDetourToExistingOrders(before, after);
        double idlePenalty = totalIdleTime(after);
        double slaRisk = computeSlaRisk(after);
        double batchingBonus = computeBatchingBonus(before, after);

        return wDistance * (distanceDelta / 1000.0)     // m -> km
                + wTime * (timeDelta / 60.0)            // s -> min
                + wDetour * (detourPenalty / 60.0)       // s -> min
                + wIdle * (idlePenalty / 60.0)           // s -> min
                + wSlaRisk * slaRisk                     // exponential penalty
                - wBatchingBonus * batchingBonus;        // reward (negative cost)
    }

    /**
     * Absolute cost for comparing entire solutions during local search.
     * Includes SLA risk penalty to guide optimization toward safer schedules.
     */
    public double absoluteCost(Map<Long, RiderSchedule> allSchedules) {
        return allSchedules.values().stream()
                .mapToDouble(s -> {
                    double dist = totalDistance(s) / 1000.0;
                    double time = totalTime(s) / 60.0;
                    double idle = totalIdleTime(s) / 60.0;
                    double slaRisk = computeSlaRisk(s);
                    return wDistance * dist + wTime * time + wIdle * idle + wSlaRisk * slaRisk;
                })
                .sum();
    }

    /**
     * Flat cost penalty for reassigning an order from one rider to another.
     * Used by the optimizer when evaluating cross-rider relocate moves.
     */
    public double reassignmentCost() {
        return wReassignmentPenalty;
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
     * SLA risk penalty: exponential penalty for each uncompleted delivery stop
     * that is approaching its time window deadline.
     *
     * risk(stop) = exp(-slack / 180) where slack = deadline - estimatedArrival
     * Only applies when slack < slaRiskThresholdSeconds.
     */
    private double computeSlaRisk(RiderSchedule schedule) {
        double totalRisk = 0;
        for (ScheduledStop stop : schedule.getStops()) {
            if (stop.isCompleted()) continue;
            if (stop.getType() != StopType.DELIVERY) continue;
            if (stop.getEstimatedArrival() == null) continue;
            if (stop.getTimeWindow().getLatest() == null) continue;

            long slackSeconds = Duration.between(stop.getEstimatedArrival(),
                    stop.getTimeWindow().getLatest()).getSeconds();

            if (slackSeconds < slaRiskThresholdSeconds) {
                // Exponential penalty: increases sharply as deadline approaches
                // At slack=0 → risk=1.0, at slack=180s → risk≈0.37, at slack=300s → risk≈0.19
                totalRisk += Math.exp(-slackSeconds / 180.0);
            }
        }
        return totalRisk;
    }

    /**
     * Batching bonus: reward for inserting a new order whose pickup is near
     * an existing uncompleted pickup in the 'after' schedule.
     * Proximity threshold: 200 meters (same restaurant cluster).
     */
    private double computeBatchingBonus(RiderSchedule before, RiderSchedule after) {
        // Identify the new pickup stop (present in 'after' but not in 'before')
        ScheduledStop newPickup = null;
        for (ScheduledStop afterStop : after.getStops()) {
            if (afterStop.getType() != StopType.PICKUP || afterStop.isCompleted()) continue;
            boolean isNew = true;
            for (ScheduledStop beforeStop : before.getStops()) {
                if (beforeStop.getOrderId().equals(afterStop.getOrderId())) {
                    isNew = false;
                    break;
                }
            }
            if (isNew) {
                newPickup = afterStop;
                break;
            }
        }

        if (newPickup == null) return 0;

        // Check if any existing pickup is within 200m
        for (ScheduledStop stop : after.getStops()) {
            if (stop == newPickup) continue;
            if (stop.getType() != StopType.PICKUP || stop.isCompleted()) continue;
            if (newPickup.getLocation().distanceTo(stop.getLocation()) < 200) {
                return 1.0; // binary bonus: nearby pickup found
            }
        }
        return 0;
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
