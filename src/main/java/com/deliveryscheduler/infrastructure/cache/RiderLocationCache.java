package com.deliveryscheduler.infrastructure.cache;

import com.deliveryscheduler.domain.model.GeoLocation;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RiderLocationCache {

    private static final String RIDER_GEO_KEY = "rider:locations";
    private static final String RIDER_STATUS_PREFIX = "rider:status:";

    private final StringRedisTemplate redisTemplate;

    public RiderLocationCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateRiderLocation(Long riderId, GeoLocation location) {
        redisTemplate.opsForGeo().add(RIDER_GEO_KEY,
                new Point(location.getLongitude(), location.getLatitude()),
                riderId.toString());
    }

    public void updateRiderStatus(Long riderId, String status) {
        redisTemplate.opsForValue().set(RIDER_STATUS_PREFIX + riderId, status);
    }

    public String getRiderStatus(Long riderId) {
        return redisTemplate.opsForValue().get(RIDER_STATUS_PREFIX + riderId);
    }

    public GeoLocation getRiderLocation(Long riderId) {
        List<Point> positions = redisTemplate.opsForGeo()
                .position(RIDER_GEO_KEY, riderId.toString());
        if (positions == null || positions.isEmpty() || positions.get(0) == null) {
            return null;
        }
        Point point = positions.get(0);
        return new GeoLocation(point.getY(), point.getX()); // lat=Y, lon=X
    }

    /**
     * Find riders near a point using Redis GEORADIUS.
     * Returns rider IDs sorted by distance (nearest first).
     */
    public List<RiderWithDistance> findRidersNear(GeoLocation center, double radiusMeters, int limit) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                .radius(RIDER_GEO_KEY,
                        new Circle(
                                new Point(center.getLongitude(), center.getLatitude()),
                                new Distance(radiusMeters / 1000.0, Metrics.KILOMETERS)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeCoordinates()
                                .includeDistance()
                                .sortAscending()
                                .limit(limit));

        if (results == null) return Collections.emptyList();

        return results.getContent().stream()
                .map(result -> {
                    RedisGeoCommands.GeoLocation<String> content = result.getContent();
                    Point point = content.getPoint();
                    return new RiderWithDistance(
                            Long.parseLong(content.getName()),
                            new GeoLocation(point.getY(), point.getX()),
                            result.getDistance().getValue());
                })
                .collect(Collectors.toList());
    }

    public void removeRider(Long riderId) {
        redisTemplate.opsForGeo().remove(RIDER_GEO_KEY, riderId.toString());
        redisTemplate.delete(RIDER_STATUS_PREFIX + riderId);
    }

    public record RiderWithDistance(Long riderId, GeoLocation location, double distanceMeters) {}
}
