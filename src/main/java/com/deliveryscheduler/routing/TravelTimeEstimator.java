package com.deliveryscheduler.routing;

import com.deliveryscheduler.domain.model.GeoLocation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Estimates travel time between two locations using Haversine distance
 * with a detour factor to approximate real road routing in urban grids.
 *
 * The detour factor of ~1.4 approximates Manhattan distance on average
 * (sqrt(2) * straight-line ≈ 1.41). For production, swap this with OSRM
 * or Google Directions API behind a cache.
 */
@Component
public class TravelTimeEstimator {

    private final double citySpeedMps;
    private final double detourFactor;
    private final int pickupServiceTimeSeconds;
    private final int deliveryServiceTimeSeconds;

    public TravelTimeEstimator(
            @Value("${routing.city-speed-mps:6.0}") double citySpeedMps,
            @Value("${routing.detour-factor:1.4}") double detourFactor,
            @Value("${routing.pickup-service-time-seconds:120}") int pickupServiceTimeSeconds,
            @Value("${routing.delivery-service-time-seconds:90}") int deliveryServiceTimeSeconds) {
        this.citySpeedMps = citySpeedMps;
        this.detourFactor = detourFactor;
        this.pickupServiceTimeSeconds = pickupServiceTimeSeconds;
        this.deliveryServiceTimeSeconds = deliveryServiceTimeSeconds;
    }

    public long estimateSeconds(GeoLocation from, GeoLocation to) {
        double distanceMeters = from.distanceTo(to);
        double adjustedDistance = distanceMeters * detourFactor;
        return (long) Math.ceil(adjustedDistance / citySpeedMps);
    }

    public double estimateDistanceMeters(GeoLocation from, GeoLocation to) {
        return from.distanceTo(to) * detourFactor;
    }

    public int getPickupServiceTimeSeconds() {
        return pickupServiceTimeSeconds;
    }

    public int getDeliveryServiceTimeSeconds() {
        return deliveryServiceTimeSeconds;
    }
}
