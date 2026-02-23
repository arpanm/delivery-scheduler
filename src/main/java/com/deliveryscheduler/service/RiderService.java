package com.deliveryscheduler.service;

import com.deliveryscheduler.domain.event.RiderLocationUpdatedEvent;
import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.Rider;
import com.deliveryscheduler.domain.model.RiderStatus;
import com.deliveryscheduler.domain.repository.RiderRepository;
import com.deliveryscheduler.infrastructure.cache.RiderLocationCache;
import com.deliveryscheduler.infrastructure.messaging.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RiderService {

    private final RiderRepository riderRepository;
    private final RiderLocationCache riderLocationCache;
    private final EventPublisher eventPublisher;

    public RiderService(RiderRepository riderRepository,
                        RiderLocationCache riderLocationCache,
                        EventPublisher eventPublisher) {
        this.riderRepository = riderRepository;
        this.riderLocationCache = riderLocationCache;
        this.eventPublisher = eventPublisher;
    }

    public Optional<Rider> findById(Long id) {
        return riderRepository.findById(id);
    }

    @Transactional
    public void updateLocation(Long riderId, GeoLocation location) {
        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider not found: " + riderId));

        rider.setLastKnownLocation(location);
        riderRepository.save(rider);

        // Update real-time location in Redis
        riderLocationCache.updateRiderLocation(riderId, location);

        eventPublisher.publish(new RiderLocationUpdatedEvent(riderId, location));
    }

    @Transactional
    public void updateStatus(Long riderId, RiderStatus status) {
        Rider rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider not found: " + riderId));

        rider.setStatus(status);
        riderRepository.save(rider);

        riderLocationCache.updateRiderStatus(riderId, status.name());

        if (status == RiderStatus.OFFLINE) {
            riderLocationCache.removeRider(riderId);
        }
    }
}
