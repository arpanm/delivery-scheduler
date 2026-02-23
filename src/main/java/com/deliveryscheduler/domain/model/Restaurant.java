package com.deliveryscheduler.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "restaurants")
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Embedded
    private GeoLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(nullable = false)
    private int avgPrepTimeSeconds = 600;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Restaurant() {}

    public Restaurant(String name, GeoLocation location, Zone zone) {
        this.name = name;
        this.location = location;
        this.zone = zone;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public GeoLocation getLocation() { return location; }
    public void setLocation(GeoLocation location) { this.location = location; }
    public Zone getZone() { return zone; }
    public void setZone(Zone zone) { this.zone = zone; }
    public int getAvgPrepTimeSeconds() { return avgPrepTimeSeconds; }
    public void setAvgPrepTimeSeconds(int avgPrepTimeSeconds) { this.avgPrepTimeSeconds = avgPrepTimeSeconds; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
