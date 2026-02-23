package com.deliveryscheduler.domain.event;

import com.deliveryscheduler.domain.model.Order;
import java.time.Instant;

public record OrderPlacedEvent(Order order, Instant timestamp) {
    public OrderPlacedEvent(Order order) {
        this(order, Instant.now());
    }
}
