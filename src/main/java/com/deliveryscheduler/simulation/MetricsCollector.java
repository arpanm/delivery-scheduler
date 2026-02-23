package com.deliveryscheduler.simulation;

import com.deliveryscheduler.domain.model.Order;
import com.deliveryscheduler.domain.model.StopType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and computes KPIs during a simulation run.
 */
public class MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);

    private final AtomicInteger totalOrdersPlaced = new AtomicInteger(0);
    private final AtomicInteger totalOrdersAssigned = new AtomicInteger(0);
    private final AtomicInteger totalOrdersDelivered = new AtomicInteger(0);
    private final AtomicInteger totalOrdersOnTime = new AtomicInteger(0);
    private final AtomicInteger totalPickupsCompleted = new AtomicInteger(0);
    private final AtomicInteger totalDeliveriesCompleted = new AtomicInteger(0);
    private final AtomicInteger unassignableOrders = new AtomicInteger(0);

    // Delivery time tracking: orderId -> (placedAt, promisedBy, deliveredAt)
    private final ConcurrentHashMap<Long, OrderTiming> orderTimings = new ConcurrentHashMap<>();

    // Insertion latencies in milliseconds
    private final List<Long> insertionLatencies = Collections.synchronizedList(new ArrayList<>());

    // Optimization improvement percentages
    private final List<Double> optimizationImprovements = Collections.synchronizedList(new ArrayList<>());

    public record OrderTiming(Instant placedAt, Instant promisedBy, Instant deliveredAt) {}

    public void recordOrderPlaced(Order order) {
        totalOrdersPlaced.incrementAndGet();
        orderTimings.put(order.getId(), new OrderTiming(
                order.getPlacedAt(), order.getPromisedDeliveryBy(), null));
    }

    public void recordOrderAssigned(Long orderId) {
        totalOrdersAssigned.incrementAndGet();
    }

    public void recordStopCompletion(StopType type, Instant time) {
        if (type == StopType.PICKUP) totalPickupsCompleted.incrementAndGet();
        else totalDeliveriesCompleted.incrementAndGet();
    }

    public void recordDelivery(Long orderId, Instant deliveredAt) {
        totalOrdersDelivered.incrementAndGet();
        OrderTiming timing = orderTimings.get(orderId);
        if (timing != null) {
            orderTimings.put(orderId, new OrderTiming(timing.placedAt, timing.promisedBy, deliveredAt));
            if (!deliveredAt.isAfter(timing.promisedBy)) {
                totalOrdersOnTime.incrementAndGet();
            }
        }
    }

    public void recordInsertionLatency(long latencyMs) {
        insertionLatencies.add(latencyMs);
    }

    public void recordOptimizationImprovement(double percentImprovement) {
        optimizationImprovements.add(percentImprovement);
    }

    public void recordUnassignable() {
        unassignableOrders.incrementAndGet();
    }

    public SimulationReport generateReport() {
        List<Long> deliveryTimes = new ArrayList<>();
        for (OrderTiming timing : orderTimings.values()) {
            if (timing.deliveredAt != null) {
                long seconds = Duration.between(timing.placedAt, timing.deliveredAt).getSeconds();
                deliveryTimes.add(seconds);
            }
        }
        Collections.sort(deliveryTimes);

        double slaAdherence = totalOrdersDelivered.get() > 0
                ? (double) totalOrdersOnTime.get() / totalOrdersDelivered.get() * 100
                : 0;

        double avgDeliveryTimeMin = deliveryTimes.stream()
                .mapToLong(Long::longValue).average().orElse(0) / 60.0;
        double p50DeliveryTimeMin = percentile(deliveryTimes, 50) / 60.0;
        double p95DeliveryTimeMin = percentile(deliveryTimes, 95) / 60.0;

        List<Long> sortedLatencies = new ArrayList<>(insertionLatencies);
        Collections.sort(sortedLatencies);
        double p50InsertionMs = percentile(sortedLatencies, 50);
        double p95InsertionMs = percentile(sortedLatencies, 95);
        double p99InsertionMs = percentile(sortedLatencies, 99);

        return new SimulationReport(
                totalOrdersPlaced.get(),
                totalOrdersAssigned.get(),
                totalOrdersDelivered.get(),
                slaAdherence,
                avgDeliveryTimeMin,
                p50DeliveryTimeMin,
                p95DeliveryTimeMin,
                p50InsertionMs,
                p95InsertionMs,
                p99InsertionMs,
                unassignableOrders.get(),
                totalPickupsCompleted.get(),
                totalDeliveriesCompleted.get());
    }

    private double percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
