package com.deliveryscheduler.domain.model;

public enum OrderStatus {
    PLACED,
    ASSIGNED,
    PREPARING,
    READY,
    PICKED_UP,
    DELIVERED,
    UNASSIGNABLE,
    DELIVERED_LATE,
    CANCELLED
}
