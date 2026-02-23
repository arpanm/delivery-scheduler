package com.deliveryscheduler.simulation;

import com.deliveryscheduler.domain.model.*;
import com.deliveryscheduler.domain.repository.TripRepository;
import com.deliveryscheduler.infrastructure.cache.RiderLocationCache;
import com.deliveryscheduler.routing.TravelTimeEstimator;
import com.deliveryscheduler.service.RiderService;
import com.deliveryscheduler.service.TripService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Simulates rider movement along their assigned routes.
 * Moves riders toward their next stop and triggers stop completions.
 */
public class RiderSimulator {

    private static final Logger log = LoggerFactory.getLogger(RiderSimulator.class);

    private final RiderService riderService;
    private final TripService tripService;
    private final TripRepository tripRepository;
    private final RiderLocationCache riderLocationCache;
    private final TravelTimeEstimator travelTimeEstimator;
    private final List<Rider> riders;
    private final MetricsCollector metrics;

    public RiderSimulator(RiderService riderService, TripService tripService,
                          TripRepository tripRepository,
                          RiderLocationCache riderLocationCache,
                          TravelTimeEstimator travelTimeEstimator,
                          List<Rider> riders, MetricsCollector metrics) {
        this.riderService = riderService;
        this.tripService = tripService;
        this.tripRepository = tripRepository;
        this.riderLocationCache = riderLocationCache;
        this.travelTimeEstimator = travelTimeEstimator;
        this.riders = riders;
        this.metrics = metrics;
    }

    /**
     * Simulate one tick of rider movement. For each rider with an active trip,
     * move them toward the next uncompleted stop. If they've arrived, complete it.
     */
    public void simulateTick(Instant simulatedTime, double tickDurationSeconds) {
        for (Rider rider : riders) {
            tripService.getCurrentTrip(rider.getId()).ifPresent(trip -> {
                TripStop nextStop = findNextUncompletedStop(trip);
                if (nextStop == null) return;

                GeoLocation riderLoc = riderLocationCache.getRiderLocation(rider.getId());
                if (riderLoc == null) {
                    riderLoc = rider.getLastKnownLocation();
                    if (riderLoc == null) return;
                }

                GeoLocation stopLoc = nextStop.getLocation();
                double distance = riderLoc.distanceTo(stopLoc);

                // Move rider toward the stop
                double moveDistance = 6.0 * tickDurationSeconds; // 6 m/s ≈ 22 km/h

                if (distance <= moveDistance) {
                    // Arrived at stop — complete it
                    try {
                        tripService.completeStop(rider.getId(), nextStop.getId());
                        metrics.recordStopCompletion(nextStop.getStopType(), simulatedTime);

                        if (nextStop.getStopType() == StopType.DELIVERY) {
                            metrics.recordDelivery(nextStop.getReferenceId(), simulatedTime);
                        }
                    } catch (Exception e) {
                        log.debug("Could not complete stop for rider {}: {}",
                                rider.getId(), e.getMessage());
                    }

                    // Update rider location to stop
                    riderService.updateLocation(rider.getId(), stopLoc);
                } else {
                    // Interpolate position toward stop
                    double fraction = moveDistance / distance;
                    double newLat = riderLoc.getLatitude()
                            + fraction * (stopLoc.getLatitude() - riderLoc.getLatitude());
                    double newLon = riderLoc.getLongitude()
                            + fraction * (stopLoc.getLongitude() - riderLoc.getLongitude());

                    GeoLocation newLoc = new GeoLocation(newLat, newLon);
                    riderService.updateLocation(rider.getId(), newLoc);
                }
            });
        }
    }

    private TripStop findNextUncompletedStop(Trip trip) {
        return trip.getStops().stream()
                .filter(s -> !s.isCompleted())
                .findFirst()
                .orElse(null);
    }
}
