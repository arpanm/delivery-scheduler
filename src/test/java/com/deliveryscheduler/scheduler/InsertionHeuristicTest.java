package com.deliveryscheduler.scheduler;

import com.deliveryscheduler.TestDataFactory;
import com.deliveryscheduler.config.SchedulerProperties;
import com.deliveryscheduler.domain.model.*;
import com.deliveryscheduler.routing.TravelTimeEstimator;
import com.deliveryscheduler.scheduler.constraint.ConstraintEngine;
import com.deliveryscheduler.scheduler.cost.CostFunction;
import com.deliveryscheduler.scheduler.insertion.InsertionHeuristic;
import com.deliveryscheduler.scheduler.model.InsertionCandidate;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InsertionHeuristicTest {

    private InsertionHeuristic insertionHeuristic;

    @BeforeEach
    void setUp() {
        TravelTimeEstimator estimator = new TravelTimeEstimator(6.0, 1.4, 120, 90);
        ConstraintEngine constraintEngine = new ConstraintEngine(estimator);
        SchedulerProperties props = new SchedulerProperties();
        CostFunction costFunction = new CostFunction(props);
        insertionHeuristic = new InsertionHeuristic(constraintEngine, costFunction, estimator);
    }

    @Test
    void emptySchedule_shouldFindInsertion() {
        RiderSchedule schedule = new RiderSchedule(1L, new GeoLocation(12.95, 77.60));

        Order order = createTestOrder(
                new GeoLocation(12.955, 77.605),
                new GeoLocation(12.96, 77.61),
                3600);

        InsertionCandidate result = insertionHeuristic.findBestInsertion(order, List.of(schedule));

        assertNotNull(result, "Should find an insertion for empty schedule");
        assertTrue(result.isFeasible());
        assertEquals(1L, result.getRiderId());
        assertEquals(0, result.getPickupInsertionIndex());
        assertEquals(1, result.getDeliveryInsertionIndex());
    }

    @Test
    void multipleRiders_shouldSelectCloserRider() {
        RiderSchedule far = new RiderSchedule(1L, new GeoLocation(12.90, 77.55));
        RiderSchedule close = new RiderSchedule(2L, new GeoLocation(12.955, 77.604));

        Order order = createTestOrder(
                new GeoLocation(12.955, 77.605),
                new GeoLocation(12.96, 77.61),
                3600);

        InsertionCandidate result = insertionHeuristic.findBestInsertion(order, List.of(far, close));

        assertNotNull(result);
        assertTrue(result.isFeasible());
        assertEquals(2L, result.getRiderId(), "Should select closer rider");
    }

    @Test
    void tightTimeWindow_shouldReturnNullOrInfeasible() {
        RiderSchedule schedule = new RiderSchedule(1L, new GeoLocation(12.90, 77.55));

        Order order = createTestOrder(
                new GeoLocation(13.05, 77.70),
                new GeoLocation(13.10, 77.75),
                120); // only 2 minutes

        InsertionCandidate result = insertionHeuristic.findBestInsertion(order, List.of(schedule));

        assertTrue(result == null || !result.isFeasible());
    }

    @Test
    void batchingWithExistingOrder_shouldFindFeasibleInsertion() {
        RiderSchedule schedule = new RiderSchedule(1L, new GeoLocation(12.95, 77.60));
        Instant now = Instant.now();

        schedule.getStops().add(new ScheduledStop(
                50L, StopType.PICKUP, new GeoLocation(12.955, 77.605),
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        schedule.getStops().add(new ScheduledStop(
                50L, StopType.DELIVERY, new GeoLocation(12.96, 77.61),
                new TimeWindow(now, now.plusSeconds(3600)), 90));

        Order newOrder = createTestOrder(
                new GeoLocation(12.956, 77.606),
                new GeoLocation(12.961, 77.611),
                3600);

        InsertionCandidate result = insertionHeuristic.findBestInsertion(newOrder, List.of(schedule));

        assertNotNull(result, "Should batch new order with existing one");
        assertTrue(result.isFeasible());
        assertEquals(4, result.getResultingSchedule().getStops().size(),
                "Schedule should have 4 stops (2 existing + 2 new)");
    }

    private Order createTestOrder(GeoLocation restaurantLoc, GeoLocation deliveryLoc,
                                   int promiseSeconds) {
        Zone zone = TestDataFactory.createZone(1L, "Test Zone");
        Restaurant restaurant = TestDataFactory.createRestaurant(1L, "Test Restaurant", restaurantLoc, zone);
        return TestDataFactory.createOrder(100L, restaurant, deliveryLoc, promiseSeconds);
    }
}
