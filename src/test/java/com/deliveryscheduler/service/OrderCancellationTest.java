package com.deliveryscheduler.service;

import com.deliveryscheduler.TestDataFactory;
import com.deliveryscheduler.domain.event.OrderCancelledEvent;
import com.deliveryscheduler.domain.model.*;
import com.deliveryscheduler.routing.TravelTimeEstimator;
import com.deliveryscheduler.scheduler.constraint.ConstraintEngine;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the order cancellation flow — tests the in-memory
 * schedule manipulation that underlies the SchedulerOrchestrator's
 * onOrderCancelled() handler.
 */
class OrderCancellationTest {

    private ConstraintEngine constraintEngine;

    @BeforeEach
    void setUp() {
        TravelTimeEstimator estimator = new TravelTimeEstimator(6.0, 1.4, 120, 90);
        constraintEngine = new ConstraintEngine(estimator);
    }

    @Test
    void cancelOrder_removesStopsFromSchedule() {
        Instant now = Instant.now();
        RiderSchedule schedule = new RiderSchedule(1L, new GeoLocation(12.95, 77.60));

        // Add order 100: pickup + delivery
        schedule.getStops().add(new ScheduledStop(
                100L, StopType.PICKUP, new GeoLocation(12.955, 77.605),
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        schedule.getStops().add(new ScheduledStop(
                100L, StopType.DELIVERY, new GeoLocation(12.96, 77.61),
                new TimeWindow(now, now.plusSeconds(3600)), 90));

        // Add order 200: pickup + delivery
        schedule.getStops().add(new ScheduledStop(
                200L, StopType.PICKUP, new GeoLocation(12.965, 77.615),
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        schedule.getStops().add(new ScheduledStop(
                200L, StopType.DELIVERY, new GeoLocation(12.97, 77.62),
                new TimeWindow(now, now.plusSeconds(3600)), 90));

        assertEquals(4, schedule.getStops().size(), "Should start with 4 stops");

        // Cancel order 100
        List<ScheduledStop> removed = schedule.removeOrderStops(100L);

        assertEquals(2, removed.size(), "Should remove 2 stops (pickup + delivery)");
        assertEquals(2, schedule.getStops().size(), "Schedule should have 2 stops left");

        // Remaining stops should all belong to order 200
        Set<Long> remainingOrderIds = schedule.getUncompletedOrderIds();
        assertEquals(Set.of(200L), remainingOrderIds, "Only order 200 should remain");
    }

    @Test
    void cancelOrder_propagatesArrivalTimesCorrectly() {
        Instant now = Instant.now();
        RiderSchedule schedule = new RiderSchedule(1L, new GeoLocation(12.95, 77.60));

        // Add two orders interleaved
        schedule.getStops().add(new ScheduledStop(
                100L, StopType.PICKUP, new GeoLocation(12.955, 77.605),
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        schedule.getStops().add(new ScheduledStop(
                200L, StopType.PICKUP, new GeoLocation(12.957, 77.607),
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        schedule.getStops().add(new ScheduledStop(
                100L, StopType.DELIVERY, new GeoLocation(12.96, 77.61),
                new TimeWindow(now, now.plusSeconds(3600)), 90));
        schedule.getStops().add(new ScheduledStop(
                200L, StopType.DELIVERY, new GeoLocation(12.965, 77.615),
                new TimeWindow(now, now.plusSeconds(3600)), 90));

        // Propagate initial times
        constraintEngine.propagateArrivalTimes(schedule);
        Instant order200DeliveryBefore = schedule.getStops().get(3).getEstimatedArrival();
        assertNotNull(order200DeliveryBefore);

        // Cancel order 100
        schedule.removeOrderStops(100L);
        constraintEngine.propagateArrivalTimes(schedule);

        // After removing order 100, order 200's delivery should be earlier
        assertEquals(2, schedule.getStops().size());
        Instant order200DeliveryAfter = schedule.getStops().get(1).getEstimatedArrival();
        assertNotNull(order200DeliveryAfter);
        assertTrue(order200DeliveryAfter.isBefore(order200DeliveryBefore),
                "Removing cancelled order should improve arrival times for remaining orders");
    }

    @Test
    void cancelledEvent_containsOrderAndTimestamp() {
        Zone zone = TestDataFactory.createZone(1L, "Test Zone");
        Restaurant restaurant = TestDataFactory.createRestaurant(1L, "Pizza Place",
                new GeoLocation(12.95, 77.60), zone);
        Order order = TestDataFactory.createOrder(100L, restaurant,
                new GeoLocation(12.96, 77.61), 3600);

        OrderCancelledEvent event = new OrderCancelledEvent(order);

        assertEquals(order, event.order());
        assertNotNull(event.timestamp());
        assertEquals(100L, event.order().getId());
    }

    @Test
    void cancelOrder_completedStopsArePreserved() {
        Instant now = Instant.now();
        RiderSchedule schedule = new RiderSchedule(1L, new GeoLocation(12.95, 77.60));

        // Add order 100: pickup is already completed
        ScheduledStop completedPickup = new ScheduledStop(
                100L, StopType.PICKUP, new GeoLocation(12.955, 77.605),
                new TimeWindow(now, now.plusSeconds(3600)), 120);
        completedPickup.setCompleted(true);
        schedule.getStops().add(completedPickup);

        // Delivery is not completed
        schedule.getStops().add(new ScheduledStop(
                100L, StopType.DELIVERY, new GeoLocation(12.96, 77.61),
                new TimeWindow(now, now.plusSeconds(3600)), 90));

        // Cancel: only uncompleted stops are removed
        List<ScheduledStop> removed = schedule.removeOrderStops(100L);

        // removeOrderStops only removes uncompleted stops
        assertEquals(1, removed.size(), "Only uncompleted delivery stop should be removed");
        assertEquals(1, schedule.getStops().size(), "Completed pickup should remain");
        assertTrue(schedule.getStops().get(0).isCompleted(), "Remaining stop should be completed");
    }

    @Test
    void cancelNonExistentOrder_removesNothing() {
        Instant now = Instant.now();
        RiderSchedule schedule = new RiderSchedule(1L, new GeoLocation(12.95, 77.60));
        schedule.getStops().add(new ScheduledStop(
                100L, StopType.PICKUP, new GeoLocation(12.955, 77.605),
                new TimeWindow(now, now.plusSeconds(3600)), 120));

        List<ScheduledStop> removed = schedule.removeOrderStops(999L);

        assertTrue(removed.isEmpty(), "Should not remove any stops for non-existent order");
        assertEquals(1, schedule.getStops().size(), "Schedule should be unchanged");
    }

    @Test
    void frozenStop_identifiedCorrectly() {
        Instant now = Instant.now();

        ScheduledStop imminentStop = new ScheduledStop(
                100L, StopType.PICKUP, new GeoLocation(12.955, 77.605),
                new TimeWindow(now, now.plusSeconds(3600)), 120);
        imminentStop.setEstimatedArrival(now.plusSeconds(60)); // arriving in 1 minute

        ScheduledStop futureStop = new ScheduledStop(
                200L, StopType.PICKUP, new GeoLocation(12.96, 77.61),
                new TimeWindow(now, now.plusSeconds(3600)), 120);
        futureStop.setEstimatedArrival(now.plusSeconds(600)); // arriving in 10 minutes

        ScheduledStop completedStop = new ScheduledStop(
                300L, StopType.PICKUP, new GeoLocation(12.965, 77.615),
                new TimeWindow(now, now.plusSeconds(3600)), 120);
        completedStop.setCompleted(true);
        completedStop.setEstimatedArrival(now.plusSeconds(60));

        // Freeze window = 300 seconds (5 minutes)
        assertTrue(imminentStop.isFrozen(now, 300),
                "Stop arriving in 1 min should be frozen with 5-min window");
        assertFalse(futureStop.isFrozen(now, 300),
                "Stop arriving in 10 min should NOT be frozen with 5-min window");
        assertFalse(completedStop.isFrozen(now, 300),
                "Completed stop should NOT be frozen even if imminent");
    }
}
