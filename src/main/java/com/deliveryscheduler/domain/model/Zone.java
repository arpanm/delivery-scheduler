package com.deliveryscheduler.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zones")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // Boundary stored as WKT text; actual spatial queries use native SQL with PostGIS
    @Column(columnDefinition = "geometry(Polygon, 4326)")
    private String boundary;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "zone")
    private List<Restaurant> restaurants = new ArrayList<>();

    protected Zone() {}

    public Zone(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBoundary() { return boundary; }
    public void setBoundary(String boundary) { this.boundary = boundary; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Restaurant> getRestaurants() { return restaurants; }
}
