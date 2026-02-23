package com.deliveryscheduler.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip_stops")
public class TripStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private int sequenceIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StopType stopType;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "location_latitude")),
        @AttributeOverride(name = "longitude", column = @Column(name = "location_longitude"))
    })
    private GeoLocation location;

    // For PICKUP: restaurant_id. For DELIVERY: order_id.
    @Column(nullable = false)
    private Long referenceId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "earliest", column = @Column(name = "tw_earliest")),
        @AttributeOverride(name = "latest", column = @Column(name = "tw_latest"))
    })
    private TimeWindow timeWindow;

    private Instant estimatedArrival;
    private Instant actualArrival;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(nullable = false)
    private int serviceTimeSeconds = 120;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TripStop() {}

    public TripStop(int sequenceIndex, StopType stopType, GeoLocation location,
                    Long referenceId, TimeWindow timeWindow, int serviceTimeSeconds) {
        this.sequenceIndex = sequenceIndex;
        this.stopType = stopType;
        this.location = location;
        this.referenceId = referenceId;
        this.timeWindow = timeWindow;
        this.serviceTimeSeconds = serviceTimeSeconds;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public void markCompleted() {
        this.completed = true;
        this.actualArrival = Instant.now();
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    public int getSequenceIndex() { return sequenceIndex; }
    public void setSequenceIndex(int sequenceIndex) { this.sequenceIndex = sequenceIndex; }
    public StopType getStopType() { return stopType; }
    public GeoLocation getLocation() { return location; }
    public Long getReferenceId() { return referenceId; }
    public TimeWindow getTimeWindow() { return timeWindow; }
    public Instant getEstimatedArrival() { return estimatedArrival; }
    public void setEstimatedArrival(Instant estimatedArrival) { this.estimatedArrival = estimatedArrival; }
    public Instant getActualArrival() { return actualArrival; }
    public boolean isCompleted() { return completed; }
    public int getServiceTimeSeconds() { return serviceTimeSeconds; }
}
