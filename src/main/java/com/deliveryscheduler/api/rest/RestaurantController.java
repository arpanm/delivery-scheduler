package com.deliveryscheduler.api.rest;

import com.deliveryscheduler.api.dto.GeoLocationDto;
import com.deliveryscheduler.domain.model.Restaurant;
import com.deliveryscheduler.service.RestaurantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    public record CreateRestaurantRequest(
            @NotBlank String name,
            @NotNull @Valid GeoLocationDto location,
            @NotNull Long zoneId) {}

    @PostMapping
    public ResponseEntity<RestaurantResponse> create(@Valid @RequestBody CreateRestaurantRequest request) {
        Restaurant restaurant = restaurantService.create(
                request.name(), request.location().toModel(), request.zoneId());
        return ResponseEntity.status(HttpStatus.CREATED).body(RestaurantResponse.from(restaurant));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponse> get(@PathVariable Long id) {
        return restaurantService.findById(id)
                .map(RestaurantResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record RestaurantResponse(Long id, String name, GeoLocationDto location,
                                      Long zoneId, int avgPrepTimeSeconds, boolean active) {
        public static RestaurantResponse from(Restaurant r) {
            return new RestaurantResponse(r.getId(), r.getName(),
                    GeoLocationDto.from(r.getLocation()),
                    r.getZone() != null ? r.getZone().getId() : null,
                    r.getAvgPrepTimeSeconds(), r.isActive());
        }
    }
}
