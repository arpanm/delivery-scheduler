package com.deliveryscheduler.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id", nullable = false)
    private Rider rider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.PLANNED;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceIndex ASC")
    private List<TripStop> stops = new ArrayList<>();

    @OneToMany(mappedBy = "trip")
    private List<Order> orders = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;
    private Instant completedAt;

    protected Trip() {}

    public Trip(Rider rider) {
        this.rider = rider;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public void addStop(TripStop stop) {
        stops.add(stop);
        stop.setTrip(this);
    }

    public void start() {
        this.status = TripStatus.ACTIVE;
        this.startedAt = Instant.now();
    }

    public void complete() {
        this.status = TripStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Rider getRider() { return rider; }
    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus status) { this.status = status; }
    public List<TripStop> getStops() { return stops; }
    public List<Order> getOrders() { return orders; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
