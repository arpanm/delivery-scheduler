package com.deliveryscheduler.api.rest;

import com.deliveryscheduler.api.dto.TrackingUpdate;
import com.deliveryscheduler.service.TrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/{orderId}/tracking")
    public ResponseEntity<TrackingUpdate> getTracking(@PathVariable Long orderId) {
        TrackingUpdate update = trackingService.getTrackingInfo(orderId);
        if (update == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(update);
    }
}
