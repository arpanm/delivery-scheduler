package com.deliveryscheduler.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "riders")
public class Rider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiderStatus status = RiderStatus.OFFLINE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone assignedZone;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "last_known_lat")),
        @AttributeOverride(name = "longitude", column = @Column(name = "last_known_lon"))
    })
    private GeoLocation lastKnownLocation;

    private Instant lastLocationUpdateAt;

    @Column(nullable = false)
    private int maxConcurrentOrders = 3;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Rider() {}

    public Rider(String name, String phone, Zone assignedZone) {
        this.name = name;
        this.phone = phone;
        this.assignedZone = assignedZone;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public RiderStatus getStatus() { return status; }
    public void setStatus(RiderStatus status) { this.status = status; }
    public Zone getAssignedZone() { return assignedZone; }
    public void setAssignedZone(Zone assignedZone) { this.assignedZone = assignedZone; }
    public GeoLocation getLastKnownLocation() { return lastKnownLocation; }
    public void setLastKnownLocation(GeoLocation lastKnownLocation) {
        this.lastKnownLocation = lastKnownLocation;
        this.lastLocationUpdateAt = Instant.now();
    }
    public Instant getLastLocationUpdateAt() { return lastLocationUpdateAt; }
    public int getMaxConcurrentOrders() { return maxConcurrentOrders; }
    public void setMaxConcurrentOrders(int maxConcurrentOrders) { this.maxConcurrentOrders = maxConcurrentOrders; }
    public Instant getCreatedAt() { return createdAt; }
}
