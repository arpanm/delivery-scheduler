package com.deliveryscheduler.routing;

import com.deliveryscheduler.domain.model.GeoLocation;

/**
 * Interface for travel time and distance estimation between locations.
 * Implementations may use Haversine approximation, OSRM, Google Directions, etc.
 */
public interface TravelTimeProvider {

    /**
     * Estimate travel time in seconds between two locations.
     */
    long estimateSeconds(GeoLocation from, GeoLocation to);

    /**
     * Estimate travel distance in meters between two locations.
     */
    double estimateDistanceMeters(GeoLocation from, GeoLocation to);

    /**
     * Service time at pickup stops (time spent at restaurant).
     */
    int getPickupServiceTimeSeconds();

    /**
     * Service time at delivery stops (time spent at customer).
     */
    int getDeliveryServiceTimeSeconds();
}
