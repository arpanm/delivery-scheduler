package com.deliveryscheduler.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public record OrderRequest(
        @NotNull Long restaurantId,
        @NotNull @Valid GeoLocationDto deliveryLocation,
        @NotBlank String customerName,
        @NotBlank String customerPhone,
        @NotEmpty List<OrderItemDto> items,
        Instant requestedDeliveryBy) {

    public record OrderItemDto(
            @NotBlank String name,
            @Min(1) int quantity) {}

    public String itemsSummary() {
        return items.stream()
                .map(i -> i.quantity() + "x " + i.name())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    public int totalItemCount() {
        return items.stream().mapToInt(OrderItemDto::quantity).sum();
    }
}
