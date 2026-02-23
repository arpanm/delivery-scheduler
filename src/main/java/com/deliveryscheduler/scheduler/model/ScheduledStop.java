package com.deliveryscheduler.scheduler.model;

import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.StopType;
import com.deliveryscheduler.domain.model.TimeWindow;

import java.time.Instant;

/**
 * Lightweight in-memory representation of a stop within a rider's schedule.
 * Used by the scheduler for speculative evaluation — never touches JPA directly.
 */
public class ScheduledStop {

    private Long orderId;
    private StopType type;
    private GeoLocation location;
    private TimeWindow timeWindow;
    private Instant estimatedArrival;
    private int serviceTimeSeconds;
    private boolean completed;

    public ScheduledStop(Long orderId, StopType type, GeoLocation location,
                         TimeWindow timeWindow, int serviceTimeSeconds) {
        this.orderId = orderId;
        this.type = type;
        this.location = location;
        this.timeWindow = timeWindow;
        this.serviceTimeSeconds = serviceTimeSeconds;
        this.completed = false;
    }

    public ScheduledStop copy() {
        ScheduledStop copy = new ScheduledStop(orderId, type, location, timeWindow, serviceTimeSeconds);
        copy.estimatedArrival = this.estimatedArrival;
        copy.completed = this.completed;
        return copy;
    }

    public Instant getEstimatedDeparture() {
        if (estimatedArrival == null) return null;
        Instant effectiveStart = timeWindow.getEarliest() != null
                && estimatedArrival.isBefore(timeWindow.getEarliest())
                ? timeWindow.getEarliest() : estimatedArrival;
        return effectiveStart.plusSeconds(serviceTimeSeconds);
    }

    public Long getOrderId() { return orderId; }
    public StopType getType() { return type; }
    public GeoLocation getLocation() { return location; }
    public TimeWindow getTimeWindow() { return timeWindow; }
    public Instant getEstimatedArrival() { return estimatedArrival; }
    public void setEstimatedArrival(Instant estimatedArrival) { this.estimatedArrival = estimatedArrival; }
    public int getServiceTimeSeconds() { return serviceTimeSeconds; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
