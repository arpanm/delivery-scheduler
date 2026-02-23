package com.deliveryscheduler.domain.event;

import com.deliveryscheduler.domain.model.StopType;
import java.time.Instant;

public record StopCompletedEvent(Long riderId, Long tripId, Long stopId,
                                  StopType stopType, Long referenceId,
                                  Instant timestamp) {
    public StopCompletedEvent(Long riderId, Long tripId, Long stopId,
                              StopType stopType, Long referenceId) {
        this(riderId, tripId, stopId, stopType, referenceId, Instant.now());
    }
}
