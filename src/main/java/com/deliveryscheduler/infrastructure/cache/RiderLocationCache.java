package com.deliveryscheduler.infrastructure.cache;

import com.deliveryscheduler.domain.model.GeoLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis-backed cache for rider locations and status.
 *
 * All Redis operations are wrapped in try-catch to prevent Redis failures
 * from crashing the scheduling pipeline (GAP 7). Status keys use a 5-minute
 * TTL so stale entries auto-expire on restart (GAP 8).
 */
@Component
public class RiderLocationCache {

    private static final Logger log = LoggerFactory.getLogger(RiderLocationCache.class);
    private static final String RIDER_GEO_KEY = "rider:locations";
    private static final String RIDER_STATUS_PREFIX = "rider:status:";
    private static final Duration STATUS_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public RiderLocationCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateRiderLocation(Long riderId, GeoLocation location) {
        try {
            redisTemplate.opsForGeo().add(RIDER_GEO_KEY,
                    new Point(location.getLongitude(), location.getLatitude()),
                    riderId.toString());
        } catch (Exception e) {
            log.warn("Redis: failed to update location for rider {}: {}", riderId, e.getMessage());
        }
    }

    public void updateRiderStatus(Long riderId, String status) {
        try {
            redisTemplate.opsForValue().set(RIDER_STATUS_PREFIX + riderId, status, STATUS_TTL);
        } catch (Exception e) {
            log.warn("Redis: failed to update status for rider {}: {}", riderId, e.getMessage());
        }
    }

    public String getRiderStatus(Long riderId) {
        try {
            return redisTemplate.opsForValue().get(RIDER_STATUS_PREFIX + riderId);
        } catch (Exception e) {
            log.warn("Redis: failed to get status for rider {}: {}", riderId, e.getMessage());
            return null;
        }
    }

    public GeoLocation getRiderLocation(Long riderId) {
        try {
            List<Point> positions = redisTemplate.opsForGeo()
                    .position(RIDER_GEO_KEY, riderId.toString());
            if (positions == null || positions.isEmpty() || positions.get(0) == null) {
                return null;
            }
            Point point = positions.get(0);
            return new GeoLocation(point.getY(), point.getX()); // lat=Y, lon=X
        } catch (Exception e) {
            log.warn("Redis: failed to get location for rider {}: {}", riderId, e.getMessage());
            return null;
        }
    }

    /**
     * Find riders near a point using Redis GEORADIUS.
     * Returns rider IDs sorted by distance (nearest first).
     * Returns empty list on Redis failure to allow graceful degradation.
     */
    public List<RiderWithDistance> findRidersNear(GeoLocation center, double radiusMeters, int limit) {
        try {
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
        } catch (Exception e) {
            log.warn("Redis: failed to find riders near ({}, {}): {}",
                    center.getLatitude(), center.getLongitude(), e.getMessage());
            return Collections.emptyList();
        }
    }

    public void removeRider(Long riderId) {
        try {
            redisTemplate.opsForGeo().remove(RIDER_GEO_KEY, riderId.toString());
            redisTemplate.delete(RIDER_STATUS_PREFIX + riderId);
        } catch (Exception e) {
            log.warn("Redis: failed to remove rider {}: {}", riderId, e.getMessage());
        }
    }

    public record RiderWithDistance(Long riderId, GeoLocation location, double distanceMeters) {}
}
