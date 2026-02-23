package com.deliveryscheduler;

import com.deliveryscheduler.domain.model.*;

import java.lang.reflect.Field;
import java.time.Instant;

/**
 * Test utility to create domain objects without needing a database.
 * Uses reflection to set ID fields that are normally auto-generated.
 */
public class TestDataFactory {

    public static Zone createZone(Long id, String name) {
        Zone zone = new Zone(name);
        setId(zone, id);
        return zone;
    }

    public static Restaurant createRestaurant(Long id, String name, GeoLocation location, Zone zone) {
        Restaurant restaurant = new Restaurant(name, location, zone);
        setId(restaurant, id);
        return restaurant;
    }

    public static Order createOrder(Long id, Restaurant restaurant, GeoLocation deliveryLocation,
                                     int promiseSeconds) {
        Order order = new Order(
                restaurant, deliveryLocation,
                "Test Customer", "555-0001",
                Instant.now().plusSeconds(promiseSeconds),
                restaurant.getAvgPrepTimeSeconds(),
                "1x Test Item", 2);
        setId(order, id);
        return order;
    }

    public static Rider createRider(Long id, String name, Zone zone, GeoLocation location) {
        Rider rider = new Rider(name, "555-0001", zone);
        rider.setStatus(RiderStatus.AVAILABLE);
        rider.setLastKnownLocation(location);
        setId(rider, id);
        return rider;
    }

    private static void setId(Object entity, Long id) {
        try {
            Field idField = findIdField(entity.getClass());
            if (idField != null) {
                idField.setAccessible(true);
                idField.set(entity, id);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID on " + entity.getClass().getSimpleName(), e);
        }
    }

    private static Field findIdField(Class<?> clazz) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
