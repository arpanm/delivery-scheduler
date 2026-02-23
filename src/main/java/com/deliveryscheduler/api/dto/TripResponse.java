package com.deliveryscheduler.api.dto;

import com.deliveryscheduler.domain.model.Trip;
import com.deliveryscheduler.domain.model.TripStatus;

import java.time.Instant;
import java.util.List;

public record TripResponse(
        Long id,
        Long riderId,
        TripStatus status,
        List<TripStopResponse> stops,
        int completedStops,
        int totalStops) {

    public static TripResponse from(Trip trip) {
        List<TripStopResponse> stops = trip.getStops().stream()
                .map(TripStopResponse::from)
                .toList();
        int completed = (int) trip.getStops().stream().filter(s -> s.isCompleted()).count();
        return new TripResponse(
                trip.getId(),
                trip.getRider().getId(),
                trip.getStatus(),
                stops,
                completed,
                trip.getStops().size());
    }
}
