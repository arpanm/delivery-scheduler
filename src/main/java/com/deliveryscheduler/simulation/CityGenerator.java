package com.deliveryscheduler.simulation;

import com.deliveryscheduler.domain.model.*;
import com.deliveryscheduler.domain.repository.RestaurantRepository;
import com.deliveryscheduler.domain.repository.RiderRepository;
import com.deliveryscheduler.domain.repository.ZoneRepository;
import com.deliveryscheduler.infrastructure.cache.RiderLocationCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a synthetic city with zones, restaurants, and riders for simulation.
 * Zones are laid out as a grid, restaurants are clustered near zone centers,
 * and riders are distributed across zones.
 */
public class CityGenerator {

    private static final Logger log = LoggerFactory.getLogger(CityGenerator.class);

    private final ZoneRepository zoneRepository;
    private final RestaurantRepository restaurantRepository;
    private final RiderRepository riderRepository;
    private final RiderLocationCache riderLocationCache;
    private final Random random = new Random(42);

    public CityGenerator(ZoneRepository zoneRepository,
                          RestaurantRepository restaurantRepository,
                          RiderRepository riderRepository,
                          RiderLocationCache riderLocationCache) {
        this.zoneRepository = zoneRepository;
        this.restaurantRepository = restaurantRepository;
        this.riderRepository = riderRepository;
        this.riderLocationCache = riderLocationCache;
    }

    public record GeneratedCity(List<Zone> zones, List<Restaurant> restaurants, List<Rider> riders) {}

    public GeneratedCity generate(SimulationConfig config) {
        List<Zone> zones = generateZones(config);
        List<Restaurant> restaurants = generateRestaurants(config, zones);
        List<Rider> riders = generateRiders(config, zones);

        log.info("Generated city: {} zones, {} restaurants, {} riders",
                zones.size(), restaurants.size(), riders.size());

        return new GeneratedCity(zones, restaurants, riders);
    }

    private List<Zone> generateZones(SimulationConfig config) {
        List<Zone> zones = new ArrayList<>();
        int gridSize = (int) Math.ceil(Math.sqrt(config.getNumberOfZones()));

        double latStep = (config.getCityMaxLat() - config.getCityMinLat()) / gridSize;
        double lonStep = (config.getCityMaxLon() - config.getCityMinLon()) / gridSize;

        int zoneCount = 0;
        for (int row = 0; row < gridSize && zoneCount < config.getNumberOfZones(); row++) {
            for (int col = 0; col < gridSize && zoneCount < config.getNumberOfZones(); col++) {
                Zone zone = new Zone("Zone-" + (row * gridSize + col + 1));
                zone = zoneRepository.save(zone);
                zones.add(zone);
                zoneCount++;
            }
        }

        return zones;
    }

    private List<Restaurant> generateRestaurants(SimulationConfig config, List<Zone> zones) {
        List<Restaurant> restaurants = new ArrayList<>();
        int gridSize = (int) Math.ceil(Math.sqrt(config.getNumberOfZones()));
        double latStep = (config.getCityMaxLat() - config.getCityMinLat()) / gridSize;
        double lonStep = (config.getCityMaxLon() - config.getCityMinLon()) / gridSize;

        for (int i = 0; i < config.getNumberOfRestaurants(); i++) {
            int zoneIdx = i % zones.size();
            Zone zone = zones.get(zoneIdx);

            int row = zoneIdx / gridSize;
            int col = zoneIdx % gridSize;

            // Place restaurant near zone center with some jitter
            double centerLat = config.getCityMinLat() + (row + 0.5) * latStep;
            double centerLon = config.getCityMinLon() + (col + 0.5) * lonStep;
            double lat = centerLat + (random.nextGaussian() * latStep * 0.2);
            double lon = centerLon + (random.nextGaussian() * lonStep * 0.2);

            GeoLocation location = new GeoLocation(
                    clamp(lat, config.getCityMinLat(), config.getCityMaxLat()),
                    clamp(lon, config.getCityMinLon(), config.getCityMaxLon()));

            Restaurant restaurant = new Restaurant("Restaurant-" + (i + 1), location, zone);
            restaurant.setAvgPrepTimeSeconds(300 + random.nextInt(600)); // 5-15 min
            restaurant = restaurantRepository.save(restaurant);
            restaurants.add(restaurant);
        }

        return restaurants;
    }

    private List<Rider> generateRiders(SimulationConfig config, List<Zone> zones) {
        List<Rider> riders = new ArrayList<>();
        int gridSize = (int) Math.ceil(Math.sqrt(config.getNumberOfZones()));
        double latStep = (config.getCityMaxLat() - config.getCityMinLat()) / gridSize;
        double lonStep = (config.getCityMaxLon() - config.getCityMinLon()) / gridSize;

        for (int i = 0; i < config.getNumberOfRiders(); i++) {
            int zoneIdx = i % zones.size();
            Zone zone = zones.get(zoneIdx);

            int row = zoneIdx / gridSize;
            int col = zoneIdx % gridSize;

            double centerLat = config.getCityMinLat() + (row + 0.5) * latStep;
            double centerLon = config.getCityMinLon() + (col + 0.5) * lonStep;
            double lat = centerLat + (random.nextGaussian() * latStep * 0.3);
            double lon = centerLon + (random.nextGaussian() * lonStep * 0.3);

            GeoLocation location = new GeoLocation(
                    clamp(lat, config.getCityMinLat(), config.getCityMaxLat()),
                    clamp(lon, config.getCityMinLon(), config.getCityMaxLon()));

            Rider rider = new Rider("Rider-" + (i + 1), "555-" + String.format("%04d", i), zone);
            rider.setStatus(RiderStatus.AVAILABLE);
            rider.setLastKnownLocation(location);
            rider = riderRepository.save(rider);

            // Register in Redis
            riderLocationCache.updateRiderLocation(rider.getId(), location);
            riderLocationCache.updateRiderStatus(rider.getId(), RiderStatus.AVAILABLE.name());

            riders.add(rider);
        }

        return riders;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
