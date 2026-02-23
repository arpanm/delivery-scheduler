package com.deliveryscheduler.domain.event;

import com.deliveryscheduler.domain.model.GeoLocation;
import java.time.Instant;

public record RiderLocationUpdatedEvent(Long riderId, GeoLocation location, Instant timestamp) {
    public RiderLocationUpdatedEvent(Long riderId, GeoLocation location) {
        this(riderId, location, Instant.now());
    }
}
