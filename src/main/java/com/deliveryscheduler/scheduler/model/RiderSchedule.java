package com.deliveryscheduler.scheduler.model;

import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.StopType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mutable working copy of a rider's stop sequence. The scheduler operates
 * on these copies during speculative evaluation, never touching JPA entities.
 */
public class RiderSchedule {

    private Long riderId;
    private GeoLocation currentLocation;
    private List<ScheduledStop> stops;
    private int currentStopIndex; // first uncompleted stop

    public RiderSchedule(Long riderId, GeoLocation currentLocation) {
        this.riderId = riderId;
        this.currentLocation = currentLocation;
        this.stops = new ArrayList<>();
        this.currentStopIndex = 0;
    }

    public RiderSchedule copy() {
        RiderSchedule copy = new RiderSchedule(riderId, currentLocation);
        copy.stops = stops.stream().map(ScheduledStop::copy).collect(Collectors.toCollection(ArrayList::new));
        copy.currentStopIndex = this.currentStopIndex;
        return copy;
    }

    public void insertStopAt(int index, ScheduledStop stop) {
        stops.add(index, stop);
    }

    public void removeStop(int index) {
        stops.remove(index);
    }

    public int uncompletedStopCount() {
        return (int) stops.stream().filter(s -> !s.isCompleted()).count();
    }

    /**
     * Get unique order IDs that have uncompleted stops in this schedule.
     */
    public Set<Long> getUncompletedOrderIds() {
        Set<Long> ids = new HashSet<>();
        for (ScheduledStop stop : stops) {
            if (!stop.isCompleted()) {
                ids.add(stop.getOrderId());
            }
        }
        return ids;
    }

    /**
     * Remove all stops for a given order (both pickup and delivery).
     * Returns the removed stops.
     */
    public List<ScheduledStop> removeOrderStops(Long orderId) {
        List<ScheduledStop> removed = new ArrayList<>();
        stops.removeIf(stop -> {
            if (stop.getOrderId().equals(orderId) && !stop.isCompleted()) {
                removed.add(stop);
                return true;
            }
            return false;
        });
        recalculateCurrentStopIndex();
        return removed;
    }

    private void recalculateCurrentStopIndex() {
        currentStopIndex = 0;
        for (int i = 0; i < stops.size(); i++) {
            if (!stops.get(i).isCompleted()) {
                currentStopIndex = i;
                return;
            }
        }
        currentStopIndex = stops.size();
    }

    public Long getRiderId() { return riderId; }
    public GeoLocation getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(GeoLocation currentLocation) { this.currentLocation = currentLocation; }
    public List<ScheduledStop> getStops() { return stops; }
    public int getCurrentStopIndex() { return currentStopIndex; }
    public void setCurrentStopIndex(int currentStopIndex) { this.currentStopIndex = currentStopIndex; }
}
