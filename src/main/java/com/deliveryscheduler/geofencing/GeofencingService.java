package com.deliveryscheduler.geofencing;

import com.deliveryscheduler.config.SchedulerProperties;
import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.Zone;
import com.deliveryscheduler.domain.repository.ZoneRepository;
import com.deliveryscheduler.infrastructure.cache.RiderLocationCache;
import com.deliveryscheduler.infrastructure.cache.RiderLocationCache.RiderWithDistance;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GeofencingService {

    private final ZoneRepository zoneRepository;
    private final RiderLocationCache riderLocationCache;
    private final SchedulerProperties schedulerProperties;

    public GeofencingService(ZoneRepository zoneRepository,
                             RiderLocationCache riderLocationCache,
                             SchedulerProperties schedulerProperties) {
        this.zoneRepository = zoneRepository;
        this.riderLocationCache = riderLocationCache;
        this.schedulerProperties = schedulerProperties;
    }

    public Optional<Zone> findZoneForLocation(GeoLocation location) {
        return zoneRepository.findZoneContaining(location.getLongitude(), location.getLatitude());
    }

    public List<Zone> findAdjacentZones(Long zoneId) {
        return zoneRepository.findAdjacentZones(zoneId);
    }

    /**
     * Find nearby available riders using Redis GEO radius search.
     * Returns riders sorted by distance (nearest first).
     */
    public List<RiderWithDistance> findNearbyRiders(GeoLocation center) {
        return riderLocationCache.findRidersNear(
                center,
                schedulerProperties.getInsertion().getMaxRadiusMeters(),
                schedulerProperties.getInsertion().getMaxCandidateRiders());
    }
}
