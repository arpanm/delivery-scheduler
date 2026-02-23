package com.deliveryscheduler.domain.event;

import java.time.Instant;

public record TripUpdatedEvent(Long zoneId, Instant timestamp) {
    public TripUpdatedEvent(Long zoneId) {
        this(zoneId, Instant.now());
    }
}
