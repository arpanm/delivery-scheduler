package com.deliveryscheduler.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "prep_time_history")
public class PrepTimeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long restaurantId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private int itemCount;

    @Column(nullable = false)
    private Instant orderTime;

    @Column(nullable = false)
    private int estimatedPrepTimeSeconds;

    private Integer actualPrepTimeSeconds;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PrepTimeRecord() {}

    public PrepTimeRecord(Long restaurantId, Long orderId, int itemCount,
                          Instant orderTime, int estimatedPrepTimeSeconds) {
        this.restaurantId = restaurantId;
        this.orderId = orderId;
        this.itemCount = itemCount;
        this.orderTime = orderTime;
        this.estimatedPrepTimeSeconds = estimatedPrepTimeSeconds;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getRestaurantId() { return restaurantId; }
    public Long getOrderId() { return orderId; }
    public int getItemCount() { return itemCount; }
    public Instant getOrderTime() { return orderTime; }
    public int getEstimatedPrepTimeSeconds() { return estimatedPrepTimeSeconds; }
    public Integer getActualPrepTimeSeconds() { return actualPrepTimeSeconds; }
    public void setActualPrepTimeSeconds(Integer actualPrepTimeSeconds) {
        this.actualPrepTimeSeconds = actualPrepTimeSeconds;
    }
    public Instant getCreatedAt() { return createdAt; }
}
