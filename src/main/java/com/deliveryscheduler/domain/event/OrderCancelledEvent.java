package com.deliveryscheduler.domain.event;

import com.deliveryscheduler.domain.model.Order;
import java.time.Instant;

public record OrderCancelledEvent(Order order, Instant timestamp) {
    public OrderCancelledEvent(Order order) {
        this(order, Instant.now());
    }
}
