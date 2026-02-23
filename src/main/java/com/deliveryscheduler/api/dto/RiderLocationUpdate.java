package com.deliveryscheduler.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RiderLocationUpdate(@NotNull @Valid GeoLocationDto location) {
}
