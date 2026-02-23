package com.deliveryscheduler.api.dto;

import com.deliveryscheduler.domain.model.OrderStatus;

import java.time.Instant;

public record TrackingUpdate(
        Long orderId,
        OrderStatus status,
        GeoLocationDto riderLocation,
        Instant estimatedDeliveryAt,
        Long riderId) {
}
