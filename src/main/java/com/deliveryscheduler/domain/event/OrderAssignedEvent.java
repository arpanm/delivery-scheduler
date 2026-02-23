package com.deliveryscheduler.domain.event;

import com.deliveryscheduler.domain.model.Order;
import java.time.Instant;

public record OrderAssignedEvent(Order order, Long riderId, Instant timestamp) {
    public OrderAssignedEvent(Order order, Long riderId) {
        this(order, riderId, Instant.now());
    }
}
