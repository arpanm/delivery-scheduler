package com.deliveryscheduler.api.dto;

import com.deliveryscheduler.domain.model.GeoLocation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GeoLocationDto(
        @NotNull @Min(-90) @Max(90) Double latitude,
        @NotNull @Min(-180) @Max(180) Double longitude) {

    public GeoLocation toModel() {
        return new GeoLocation(latitude, longitude);
    }

    public static GeoLocationDto from(GeoLocation location) {
        return new GeoLocationDto(location.getLatitude(), location.getLongitude());
    }
}
