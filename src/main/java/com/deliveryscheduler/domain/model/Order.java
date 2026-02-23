package com.deliveryscheduler.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "delivery_lat")),
        @AttributeOverride(name = "longitude", column = @Column(name = "delivery_lon"))
    })
    private GeoLocation deliveryLocation;

    @Column(nullable = false, length = 200)
    private String customerName;

    @Column(nullable = false, length = 20)
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(nullable = false)
    private Instant placedAt;

    @Column(nullable = false)
    private Instant promisedDeliveryBy;

    @Column(nullable = false)
    private int estimatedPrepTimeSeconds;

    @Column
    private Double prepTimeVarianceSeconds;

    @Column(nullable = false)
    private Instant estimatedReadyAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(columnDefinition = "TEXT")
    private String itemsSummary;

    @Column(nullable = false)
    private int itemCount = 1;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Order() {}

    public Order(Restaurant restaurant, GeoLocation deliveryLocation,
                 String customerName, String customerPhone,
                 Instant promisedDeliveryBy, int estimatedPrepTimeSeconds,
                 String itemsSummary, int itemCount) {
        this.restaurant = restaurant;
        this.deliveryLocation = deliveryLocation;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.promisedDeliveryBy = promisedDeliveryBy;
        this.estimatedPrepTimeSeconds = estimatedPrepTimeSeconds;
        this.itemsSummary = itemsSummary;
        this.itemCount = itemCount;
        this.placedAt = Instant.now();
        this.estimatedReadyAt = this.placedAt.plusSeconds(estimatedPrepTimeSeconds);
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (placedAt == null) placedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Restaurant getRestaurant() { return restaurant; }
    public GeoLocation getDeliveryLocation() { return deliveryLocation; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getPromisedDeliveryBy() { return promisedDeliveryBy; }
    public int getEstimatedPrepTimeSeconds() { return estimatedPrepTimeSeconds; }
    public Double getPrepTimeVarianceSeconds() { return prepTimeVarianceSeconds; }
    public void setPrepTimeVarianceSeconds(Double prepTimeVarianceSeconds) { this.prepTimeVarianceSeconds = prepTimeVarianceSeconds; }
    public Instant getEstimatedReadyAt() { return estimatedReadyAt; }
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    public String getItemsSummary() { return itemsSummary; }
    public int getItemCount() { return itemCount; }
    public Instant getCreatedAt() { return createdAt; }
}
