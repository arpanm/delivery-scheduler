package com.deliveryscheduler.service;

import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.Restaurant;
import com.deliveryscheduler.domain.model.Zone;
import com.deliveryscheduler.domain.repository.RestaurantRepository;
import com.deliveryscheduler.domain.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final ZoneRepository zoneRepository;

    public RestaurantService(RestaurantRepository restaurantRepository,
                             ZoneRepository zoneRepository) {
        this.restaurantRepository = restaurantRepository;
        this.zoneRepository = zoneRepository;
    }

    public Optional<Restaurant> findById(Long id) {
        return restaurantRepository.findById(id);
    }

    public List<Restaurant> findActiveByZone(Long zoneId) {
        return restaurantRepository.findByZoneIdAndActiveTrue(zoneId);
    }

    @Transactional
    public Restaurant create(String name, GeoLocation location, Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + zoneId));
        Restaurant restaurant = new Restaurant(name, location, zone);
        return restaurantRepository.save(restaurant);
    }
}
