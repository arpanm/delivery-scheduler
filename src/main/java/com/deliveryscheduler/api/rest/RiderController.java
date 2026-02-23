package com.deliveryscheduler.api.rest;

import com.deliveryscheduler.api.dto.RiderLocationUpdate;
import com.deliveryscheduler.api.dto.TripResponse;
import com.deliveryscheduler.domain.model.RiderStatus;
import com.deliveryscheduler.service.RiderService;
import com.deliveryscheduler.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/riders")
public class RiderController {

    private final RiderService riderService;
    private final TripService tripService;

    public RiderController(RiderService riderService, TripService tripService) {
        this.riderService = riderService;
        this.tripService = tripService;
    }

    @PostMapping("/{riderId}/location")
    public ResponseEntity<Void> updateLocation(
            @PathVariable Long riderId,
            @Valid @RequestBody RiderLocationUpdate update) {
        riderService.updateLocation(riderId, update.location().toModel());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{riderId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long riderId,
            @RequestParam RiderStatus status) {
        riderService.updateStatus(riderId, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{riderId}/current-trip")
    public ResponseEntity<TripResponse> getCurrentTrip(@PathVariable Long riderId) {
        return tripService.getCurrentTrip(riderId)
                .map(TripResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{riderId}/stops/{stopId}/complete")
    public ResponseEntity<Void> completeStop(
            @PathVariable Long riderId,
            @PathVariable Long stopId) {
        tripService.completeStop(riderId, stopId);
        return ResponseEntity.ok().build();
    }
}
