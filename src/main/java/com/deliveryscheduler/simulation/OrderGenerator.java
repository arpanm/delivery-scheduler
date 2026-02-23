package com.deliveryscheduler.simulation;

import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.Order;
import com.deliveryscheduler.domain.model.Restaurant;
import com.deliveryscheduler.service.OrderService;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * Generates synthetic orders using a Poisson arrival process
 * with peak-hour multipliers.
 */
public class OrderGenerator {

    private final OrderService orderService;
    private final List<Restaurant> restaurants;
    private final SimulationConfig config;
    private final Random random = new Random(123);

    public OrderGenerator(OrderService orderService,
                          List<Restaurant> restaurants,
                          SimulationConfig config) {
        this.orderService = orderService;
        this.restaurants = restaurants;
        this.config = config;
    }

    /**
     * Generate orders for the current simulation tick.
     * Uses Poisson process: probability of an order in a small time window
     * follows the configured rate with peak adjustments.
     */
    public List<Order> generateOrdersForTick(Instant simulatedTime, double tickDurationMinutes) {
        double rate = config.getOrdersPerMinute();

        // Peak hour adjustment
        int hour = simulatedTime.atZone(java.time.ZoneId.systemDefault()).getHour();
        if ((hour >= 12 && hour < 13) || (hour >= 19 && hour < 21)) {
            rate *= config.getPeakMultiplier();
        }

        // Poisson: expected number of orders in this tick
        double expectedOrders = rate * tickDurationMinutes;
        int numOrders = poissonSample(expectedOrders);

        List<Order> orders = new java.util.ArrayList<>();
        for (int i = 0; i < numOrders; i++) {
            Order order = generateSingleOrder(simulatedTime);
            if (order != null) orders.add(order);
        }
        return orders;
    }

    private Order generateSingleOrder(Instant simulatedTime) {
        // Random restaurant
        Restaurant restaurant = restaurants.get(random.nextInt(restaurants.size()));

        // Random delivery location near the restaurant (within ~3km)
        double deliveryLat = restaurant.getLocation().getLatitude()
                + (random.nextGaussian() * 0.02);
        double deliveryLon = restaurant.getLocation().getLongitude()
                + (random.nextGaussian() * 0.02);

        deliveryLat = Math.max(config.getCityMinLat(), Math.min(config.getCityMaxLat(), deliveryLat));
        deliveryLon = Math.max(config.getCityMinLon(), Math.min(config.getCityMaxLon(), deliveryLon));

        GeoLocation deliveryLocation = new GeoLocation(deliveryLat, deliveryLon);

        // Random item count 1-5
        int itemCount = 1 + random.nextInt(5);

        // Delivery promise: 30-60 minutes from now
        int promiseMinutes = config.getMinDeliveryPromiseMinutes()
                + random.nextInt(config.getMaxDeliveryPromiseMinutes() - config.getMinDeliveryPromiseMinutes());
        Instant promisedBy = simulatedTime.plusSeconds(promiseMinutes * 60L);

        String items = generateItemsSummary(itemCount);

        return orderService.placeOrder(
                restaurant.getId(), deliveryLocation,
                "Customer-" + random.nextInt(10000),
                "555-" + String.format("%04d", random.nextInt(10000)),
                items, itemCount, promisedBy);
    }

    private String generateItemsSummary(int itemCount) {
        String[] foodItems = {"Burger", "Pizza", "Pasta", "Salad", "Biryani",
                "Noodles", "Rice Bowl", "Sandwich", "Wrap", "Soup"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(itemCount, 3); i++) {
            if (i > 0) sb.append(", ");
            sb.append("1x ").append(foodItems[random.nextInt(foodItems.length)]);
        }
        return sb.toString();
    }

    private int poissonSample(double lambda) {
        double L = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > L);
        return k - 1;
    }
}
