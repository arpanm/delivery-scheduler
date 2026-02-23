package com.deliveryscheduler.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralized scheduler metrics for production observability.
 * All metrics are prefixed with "scheduler." and exposed via /actuator/prometheus.
 */
@Component
public class SchedulerMetrics {

    private final Timer assignmentLatency;
    private final Timer optimizationDuration;
    private final Counter ordersAssigned;
    private final Counter ordersUnassignable;
    private final Counter ordersCancelled;
    private final Counter ordersLate;
    private final DistributionSummary batchSize;
    private final DistributionSummary routeSlack;

    public SchedulerMetrics(MeterRegistry registry) {
        this.assignmentLatency = Timer.builder("scheduler.assignment.latency")
                .description("Time taken to find and apply an insertion for a new order")
                .publishPercentileHistogram()
                .register(registry);

        this.optimizationDuration = Timer.builder("scheduler.optimization.duration")
                .description("Time spent on periodic local search optimization per zone")
                .publishPercentileHistogram()
                .register(registry);

        this.ordersAssigned = Counter.builder("scheduler.orders.assigned")
                .description("Total orders successfully assigned to riders")
                .register(registry);

        this.ordersUnassignable = Counter.builder("scheduler.orders.unassignable")
                .description("Total orders that could not be assigned after all retries")
                .register(registry);

        this.ordersCancelled = Counter.builder("scheduler.orders.cancelled")
                .description("Total orders cancelled")
                .register(registry);

        this.ordersLate = Counter.builder("scheduler.orders.late")
                .description("Total orders delivered after the promised delivery time")
                .register(registry);

        this.batchSize = DistributionSummary.builder("scheduler.batch.size")
                .description("Number of active orders per rider at time of assignment")
                .register(registry);

        this.routeSlack = DistributionSummary.builder("scheduler.route.slack")
                .description("Slack time in seconds between estimated arrival and deadline")
                .register(registry);
    }

    public void recordAssignment(long durationMs) {
        assignmentLatency.record(durationMs, TimeUnit.MILLISECONDS);
        ordersAssigned.increment();
    }

    public void recordUnassignable() {
        ordersUnassignable.increment();
    }

    public void recordOptimization(long durationMs) {
        optimizationDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordCancellation() {
        ordersCancelled.increment();
    }

    public void recordLateDelivery() {
        ordersLate.increment();
    }

    public void recordBatchSize(int size) {
        batchSize.record(size);
    }

    public void recordRouteSlack(double slackSeconds) {
        routeSlack.record(slackSeconds);
    }
}
