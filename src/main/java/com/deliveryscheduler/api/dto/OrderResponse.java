package com.deliveryscheduler.api.dto;

import com.deliveryscheduler.domain.model.Order;
import com.deliveryscheduler.domain.model.OrderStatus;

import java.time.Instant;

public record OrderResponse(
        Long id,
        Long restaurantId,
        OrderStatus status,
        Instant placedAt,
        Instant promisedDeliveryBy,
        Instant estimatedReadyAt,
        Long assignedRiderId,
        GeoLocationDto deliveryLocation,
        int itemCount) {

    public static OrderResponse from(Order order) {
        Long riderId = null;
        if (order.getTrip() != null) {
            riderId = order.getTrip().getRider().getId();
        }
        return new OrderResponse(
                order.getId(),
                order.getRestaurant().getId(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getPromisedDeliveryBy(),
                order.getEstimatedReadyAt(),
                riderId,
                GeoLocationDto.from(order.getDeliveryLocation()),
                order.getItemCount());
    }
}
