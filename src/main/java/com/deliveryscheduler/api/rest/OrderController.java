package com.deliveryscheduler.api.rest;

import com.deliveryscheduler.api.dto.OrderRequest;
import com.deliveryscheduler.api.dto.OrderResponse;
import com.deliveryscheduler.domain.model.Order;
import com.deliveryscheduler.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        Instant promisedBy = request.requestedDeliveryBy() != null
                ? request.requestedDeliveryBy()
                : Instant.now().plusSeconds(3600); // default 1 hour

        Order order = orderService.placeOrder(
                request.restaurantId(),
                request.deliveryLocation().toModel(),
                request.customerName(),
                request.customerPhone(),
                request.itemsSummary(),
                request.totalItemCount(),
                promisedBy);

        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        return orderService.findById(orderId)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.noContent().build();
    }
}
