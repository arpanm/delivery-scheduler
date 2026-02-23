package com.deliveryscheduler.simulation;

import com.deliveryscheduler.domain.model.Order;
import com.deliveryscheduler.domain.model.Restaurant;
import com.deliveryscheduler.domain.model.Rider;
import com.deliveryscheduler.domain.repository.RestaurantRepository;
import com.deliveryscheduler.domain.repository.RiderRepository;
import com.deliveryscheduler.domain.repository.TripRepository;
import com.deliveryscheduler.domain.repository.ZoneRepository;
import com.deliveryscheduler.infrastructure.cache.RiderLocationCache;
import com.deliveryscheduler.routing.TravelTimeProvider;
import com.deliveryscheduler.service.OrderService;
import com.deliveryscheduler.service.RiderService;
import com.deliveryscheduler.service.TripService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates a complete simulation run. Advances a simulated clock
 * in discrete time steps, generating orders and moving riders.
 */
@Service
public class SimulationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    private final OrderService orderService;
    private final RiderService riderService;
    private final TripService tripService;
    private final TripRepository tripRepository;
    private final ZoneRepository zoneRepository;
    private final RestaurantRepository restaurantRepository;
    private final RiderRepository riderRepository;
    private final RiderLocationCache riderLocationCache;
    private final TravelTimeProvider travelTimeEstimator;

    public SimulationRunner(OrderService orderService,
                             RiderService riderService,
                             TripService tripService,
                             TripRepository tripRepository,
                             ZoneRepository zoneRepository,
                             RestaurantRepository restaurantRepository,
                             RiderRepository riderRepository,
                             RiderLocationCache riderLocationCache,
                             TravelTimeProvider travelTimeEstimator) {
        this.orderService = orderService;
        this.riderService = riderService;
        this.tripService = tripService;
        this.tripRepository = tripRepository;
        this.zoneRepository = zoneRepository;
        this.restaurantRepository = restaurantRepository;
        this.riderRepository = riderRepository;
        this.riderLocationCache = riderLocationCache;
        this.travelTimeEstimator = travelTimeEstimator;
    }

    public SimulationReport run(SimulationConfig config) {
        log.info("Starting simulation: {} zones, {} restaurants, {} riders, {} min duration",
                config.getNumberOfZones(), config.getNumberOfRestaurants(),
                config.getNumberOfRiders(), config.getSimulationDurationMinutes());

        // Generate city
        CityGenerator cityGen = new CityGenerator(
                zoneRepository, restaurantRepository, riderRepository, riderLocationCache);
        CityGenerator.GeneratedCity city = cityGen.generate(config);

        MetricsCollector metrics = new MetricsCollector();

        // Set up order generator and rider simulator
        OrderGenerator orderGen = new OrderGenerator(
                orderService, city.restaurants(), config);
        RiderSimulator riderSim = new RiderSimulator(
                riderService, tripService, tripRepository,
                riderLocationCache, travelTimeEstimator,
                city.riders(), metrics);

        // Simulation loop
        double tickDurationSeconds = 10.0; // simulate in 10-second ticks
        double tickDurationMinutes = tickDurationSeconds / 60.0;
        int totalTicks = (int) (config.getSimulationDurationMinutes() * 60 / tickDurationSeconds);
        long realTickSleepMs = (long) (tickDurationSeconds * 1000 / config.getTimeAccelerationFactor());

        Instant simulatedTime = Instant.now();

        log.info("Simulation: {} ticks, {} ms per tick ({}x acceleration)",
                totalTicks, realTickSleepMs, config.getTimeAccelerationFactor());

        for (int tick = 0; tick < totalTicks; tick++) {
            // Generate new orders
            List<Order> newOrders = orderGen.generateOrdersForTick(simulatedTime, tickDurationMinutes);
            for (Order order : newOrders) {
                metrics.recordOrderPlaced(order);
            }

            // Simulate rider movement
            riderSim.simulateTick(simulatedTime, tickDurationSeconds);

            // Advance simulated clock
            simulatedTime = simulatedTime.plusSeconds((long) tickDurationSeconds);

            // Progress logging every 100 ticks
            if (tick % 100 == 0 && tick > 0) {
                double progressPct = (tick * 100.0) / totalTicks;
                log.info("Simulation progress: {:.1f}% ({} orders placed, {} delivered)",
                        progressPct, metrics.generateReport().totalOrdersPlaced(),
                        metrics.generateReport().totalOrdersDelivered());
            }

            // Sleep to maintain acceleration factor
            if (realTickSleepMs > 0) {
                try {
                    Thread.sleep(realTickSleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        SimulationReport report = metrics.generateReport();
        log.info("\n{}", report.toFormattedString());
        return report;
    }
}
