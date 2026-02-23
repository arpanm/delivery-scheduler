package com.deliveryscheduler.api.dto;

import com.deliveryscheduler.domain.model.StopType;
import com.deliveryscheduler.domain.model.TripStop;

import java.time.Instant;

public record TripStopResponse(
        Long id,
        int sequenceIndex,
        StopType stopType,
        GeoLocationDto location,
        Long referenceId,
        Instant estimatedArrival,
        boolean completed) {

    public static TripStopResponse from(TripStop stop) {
        return new TripStopResponse(
                stop.getId(),
                stop.getSequenceIndex(),
                stop.getStopType(),
                GeoLocationDto.from(stop.getLocation()),
                stop.getReferenceId(),
                stop.getEstimatedArrival(),
                stop.isCompleted());
    }
}
