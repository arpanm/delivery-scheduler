package com.deliveryscheduler.scheduler;

import com.deliveryscheduler.config.SchedulerProperties;
import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.StopType;
import com.deliveryscheduler.domain.model.TimeWindow;
import com.deliveryscheduler.routing.TravelTimeEstimator;
import com.deliveryscheduler.scheduler.constraint.ConstraintEngine;
import com.deliveryscheduler.scheduler.cost.CostFunction;
import com.deliveryscheduler.scheduler.insertion.InsertionHeuristic;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import com.deliveryscheduler.scheduler.optimization.LocalSearchOptimizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalSearchOptimizerTest {

    private TravelTimeEstimator estimator;
    private ConstraintEngine constraintEngine;
    private SchedulerProperties props;
    private CostFunction costFunction;
    private InsertionHeuristic insertionHeuristic;
    private LocalSearchOptimizer optimizer;

    @BeforeEach
    void setUp() {
        estimator = new TravelTimeEstimator(6.0, 1.4, 120, 90);
        constraintEngine = new ConstraintEngine(estimator);
        props = new SchedulerProperties();
        costFunction = new CostFunction(props);
        insertionHeuristic = new InsertionHeuristic(constraintEngine, costFunction, estimator);
        optimizer = new LocalSearchOptimizer(constraintEngine, costFunction, insertionHeuristic, props);
    }

    @Test
    void frozenStops_areNotMoved() {
        Instant now = Instant.now();
        GeoLocation riderLoc = new GeoLocation(12.9500, 77.6000);

        // Rider 1: has 2 orders (4 stops). First order's stops will be frozen (near-future arrivals).
        RiderSchedule rider1 = new RiderSchedule(1L, riderLoc);

        // Order 1 -- pickup and delivery with estimated arrivals inside the freeze window.
        // Default freeze window is 300 seconds. We set arrivals at now + 60s (well within freeze).
        GeoLocation pickup1Loc = new GeoLocation(12.9510, 77.6010);
        GeoLocation delivery1Loc = new GeoLocation(12.9520, 77.6020);
        ScheduledStop pickup1 = new ScheduledStop(
                1L, StopType.PICKUP, pickup1Loc,
                new TimeWindow(now, now.plusSeconds(3600)), 120);
        pickup1.setEstimatedArrival(now.plusSeconds(60));
        ScheduledStop delivery1 = new ScheduledStop(
                1L, StopType.DELIVERY, delivery1Loc,
                new TimeWindow(now, now.plusSeconds(3600)), 90);
        delivery1.setEstimatedArrival(now.plusSeconds(240));

        // Order 2 -- stops with arrivals well outside the freeze window (not frozen).
        GeoLocation pickup2Loc = new GeoLocation(12.9600, 77.6100);
        GeoLocation delivery2Loc = new GeoLocation(12.9700, 77.6200);
        ScheduledStop pickup2 = new ScheduledStop(
                2L, StopType.PICKUP, pickup2Loc,
                new TimeWindow(now, now.plusSeconds(3600)), 120);
        pickup2.setEstimatedArrival(now.plusSeconds(600));
        ScheduledStop delivery2 = new ScheduledStop(
                2L, StopType.DELIVERY, delivery2Loc,
                new TimeWindow(now, now.plusSeconds(3600)), 90);
        delivery2.setEstimatedArrival(now.plusSeconds(900));

        rider1.getStops().add(pickup1);
        rider1.getStops().add(delivery1);
        rider1.getStops().add(pickup2);
        rider1.getStops().add(delivery2);

        // Rider 2: empty schedule -- attractive relocation target
        RiderSchedule rider2 = new RiderSchedule(2L, new GeoLocation(12.9650, 77.6150));

        Map<Long, RiderSchedule> schedules = new HashMap<>();
        schedules.put(1L, rider1);
        schedules.put(2L, rider2);

        // Record the original positions of the frozen stops (order 1)
        Long frozenOrderId = 1L;
        List<ScheduledStop> stopsBeforeOptimization = rider1.getStops();
        int originalPickup1Index = stopsBeforeOptimization.indexOf(pickup1);
        int originalDelivery1Index = stopsBeforeOptimization.indexOf(delivery1);

        optimizer.optimize(schedules);

        // After optimization, order 1 (frozen) should still be on rider 1 at its original positions.
        RiderSchedule rider1After = schedules.get(1L);
        List<ScheduledStop> rider1Stops = rider1After.getStops();

        // Verify frozen order stops are still present on rider 1
        boolean frozenPickupPresent = rider1Stops.stream()
                .anyMatch(s -> s.getOrderId().equals(frozenOrderId) && s.getType() == StopType.PICKUP);
        boolean frozenDeliveryPresent = rider1Stops.stream()
                .anyMatch(s -> s.getOrderId().equals(frozenOrderId) && s.getType() == StopType.DELIVERY);

        assertTrue(frozenPickupPresent,
                "Frozen order's pickup should remain on rider 1 after optimization");
        assertTrue(frozenDeliveryPresent,
                "Frozen order's delivery should remain on rider 1 after optimization");

        // Verify frozen stops are still at their original positions (indices 0 and 1)
        assertEquals(frozenOrderId, rider1Stops.get(originalPickup1Index).getOrderId(),
                "Frozen pickup should remain at its original index");
        assertEquals(StopType.PICKUP, rider1Stops.get(originalPickup1Index).getType(),
                "Stop at original pickup index should still be a PICKUP");
        assertEquals(frozenOrderId, rider1Stops.get(originalDelivery1Index).getOrderId(),
                "Frozen delivery should remain at its original index");
        assertEquals(StopType.DELIVERY, rider1Stops.get(originalDelivery1Index).getType(),
                "Stop at original delivery index should still be a DELIVERY");
    }

    @Test
    void optimize_respectsTimeBudget() {
        Instant now = Instant.now();
        GeoLocation baseLoc = new GeoLocation(12.9500, 77.6000);

        // Create 3 riders, each with 3 orders (6 stops) to generate many possible moves
        Map<Long, RiderSchedule> schedules = new HashMap<>();
        for (long riderId = 1; riderId <= 3; riderId++) {
            GeoLocation riderLoc = new GeoLocation(
                    baseLoc.getLatitude() + riderId * 0.005,
                    baseLoc.getLongitude() + riderId * 0.005);
            RiderSchedule schedule = new RiderSchedule(riderId, riderLoc);

            for (long orderId = riderId * 100; orderId < riderId * 100 + 3; orderId++) {
                double offset = (orderId - riderId * 100) * 0.002;
                GeoLocation pickupLoc = new GeoLocation(
                        riderLoc.getLatitude() + 0.001 + offset,
                        riderLoc.getLongitude() + 0.001 + offset);
                GeoLocation deliveryLoc = new GeoLocation(
                        riderLoc.getLatitude() + 0.005 + offset,
                        riderLoc.getLongitude() + 0.005 + offset);

                ScheduledStop pickup = new ScheduledStop(
                        orderId, StopType.PICKUP, pickupLoc,
                        new TimeWindow(now, now.plusSeconds(3600)), 120);
                ScheduledStop delivery = new ScheduledStop(
                        orderId, StopType.DELIVERY, deliveryLoc,
                        new TimeWindow(now, now.plusSeconds(3600)), 90);

                schedule.getStops().add(pickup);
                schedule.getStops().add(delivery);
            }

            constraintEngine.propagateArrivalTimes(schedule);
            schedules.put(riderId, schedule);
        }

        long maxTimeMs = props.getOptimization().getMaxTimeMs();
        long startTime = System.currentTimeMillis();

        optimizer.optimize(schedules);

        long elapsed = System.currentTimeMillis() - startTime;

        // Allow generous margin: the optimizer should finish within 2x the configured time budget.
        // The maxTimeMs check in the optimizer is per-iteration, so some overrun is expected.
        assertTrue(elapsed < maxTimeMs * 2,
                "Optimizer should complete within 2x the time budget (" + maxTimeMs + "ms), " +
                        "but took " + elapsed + "ms");
    }

    @Test
    void relocate_findsImprovement() {
        Instant now = Instant.now();

        // Rider 1 is far from order 2's locations but has both orders assigned.
        GeoLocation rider1Loc = new GeoLocation(12.9500, 77.6000);
        RiderSchedule rider1 = new RiderSchedule(1L, rider1Loc);

        // Order 1: near rider 1 -- should stay with rider 1
        GeoLocation pickup1Loc = new GeoLocation(12.9510, 77.6010);
        GeoLocation delivery1Loc = new GeoLocation(12.9520, 77.6020);
        rider1.getStops().add(new ScheduledStop(
                1L, StopType.PICKUP, pickup1Loc,
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        rider1.getStops().add(new ScheduledStop(
                1L, StopType.DELIVERY, delivery1Loc,
                new TimeWindow(now, now.plusSeconds(3600)), 90));

        // Order 2: far from rider 1, near rider 2 -- should be relocated to rider 2
        GeoLocation pickup2Loc = new GeoLocation(13.0000, 77.6500);
        GeoLocation delivery2Loc = new GeoLocation(13.0050, 77.6550);
        rider1.getStops().add(new ScheduledStop(
                2L, StopType.PICKUP, pickup2Loc,
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        rider1.getStops().add(new ScheduledStop(
                2L, StopType.DELIVERY, delivery2Loc,
                new TimeWindow(now, now.plusSeconds(3600)), 90));

        constraintEngine.propagateArrivalTimes(rider1);

        // Rider 2 is near order 2's locations and has no orders -- empty schedule.
        GeoLocation rider2Loc = new GeoLocation(12.9990, 77.6490);
        RiderSchedule rider2 = new RiderSchedule(2L, rider2Loc);

        Map<Long, RiderSchedule> schedules = new HashMap<>();
        schedules.put(1L, rider1);
        schedules.put(2L, rider2);

        boolean improved = optimizer.optimize(schedules);

        // Relocating order 2 from distant rider 1 to nearby rider 2 is clearly cheaper,
        // so the optimizer should find this improvement.
        assertTrue(improved, "Optimizer should find an improvement by relocating " +
                "order 2 from distant rider 1 to nearby rider 2");
    }
}
