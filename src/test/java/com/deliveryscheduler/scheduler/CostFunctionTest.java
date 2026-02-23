package com.deliveryscheduler.scheduler;

import com.deliveryscheduler.config.SchedulerProperties;
import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.StopType;
import com.deliveryscheduler.domain.model.TimeWindow;
import com.deliveryscheduler.routing.TravelTimeEstimator;
import com.deliveryscheduler.scheduler.constraint.ConstraintEngine;
import com.deliveryscheduler.scheduler.cost.CostFunction;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CostFunctionTest {

    private TravelTimeEstimator estimator;
    private ConstraintEngine constraintEngine;
    private SchedulerProperties props;
    private CostFunction costFunction;

    @BeforeEach
    void setUp() {
        estimator = new TravelTimeEstimator(6.0, 1.4, 120, 90);
        constraintEngine = new ConstraintEngine(estimator);
        props = new SchedulerProperties();
        costFunction = new CostFunction(props);
    }

    @Test
    void slaRiskPenalty_increasesAsDeadlineApproaches() {
        Instant now = Instant.now();
        GeoLocation riderLoc = new GeoLocation(12.9500, 77.6000);
        GeoLocation pickupLoc = new GeoLocation(12.9510, 77.6010);

        // --- Tight deadline scenario: delivery window latest is very close to estimated arrival ---
        GeoLocation deliveryLocTight = new GeoLocation(12.9520, 77.6020);
        RiderSchedule beforeTight = new RiderSchedule(1L, riderLoc);

        RiderSchedule afterTight = new RiderSchedule(1L, riderLoc);
        afterTight.getStops().add(new ScheduledStop(
                100L, StopType.PICKUP, pickupLoc,
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        // Set a delivery deadline that is very close to when the rider would actually arrive.
        // The rider needs travel time from riderLoc -> pickupLoc + 120s service + travel pickupLoc -> deliveryLoc.
        // We set latest to now + 400s which leaves very little slack after travel + service.
        afterTight.getStops().add(new ScheduledStop(
                100L, StopType.DELIVERY, deliveryLocTight,
                new TimeWindow(now, now.plusSeconds(400)), 90));
        constraintEngine.propagateArrivalTimes(afterTight);

        double costTight = costFunction.insertionCost(beforeTight, afterTight);

        // --- Relaxed deadline scenario: delivery window latest has plenty of slack ---
        GeoLocation deliveryLocRelaxed = new GeoLocation(12.9520, 77.6020);
        RiderSchedule beforeRelaxed = new RiderSchedule(2L, riderLoc);

        RiderSchedule afterRelaxed = new RiderSchedule(2L, riderLoc);
        afterRelaxed.getStops().add(new ScheduledStop(
                200L, StopType.PICKUP, pickupLoc,
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        // Generous deadline: 1 hour from now, giving more than enough slack
        afterRelaxed.getStops().add(new ScheduledStop(
                200L, StopType.DELIVERY, deliveryLocRelaxed,
                new TimeWindow(now, now.plusSeconds(3600)), 90));
        constraintEngine.propagateArrivalTimes(afterRelaxed);

        double costRelaxed = costFunction.insertionCost(beforeRelaxed, afterRelaxed);

        // The tight-deadline schedule should incur a higher SLA risk penalty,
        // making its total insertion cost higher than the relaxed-deadline schedule.
        assertTrue(costTight > costRelaxed,
                "Insertion cost with tight deadline (" + costTight +
                        ") should be higher than with relaxed deadline (" + costRelaxed + ")");
    }

    @Test
    void batchingBonus_appliedForNearbyPickups() {
        Instant now = Instant.now();
        GeoLocation riderLoc = new GeoLocation(12.9500, 77.6000);
        GeoLocation existingPickupLoc = new GeoLocation(12.9550, 77.6050);

        // 'before' schedule has one existing PICKUP stop
        RiderSchedule beforeNearby = new RiderSchedule(1L, riderLoc);
        beforeNearby.getStops().add(new ScheduledStop(
                50L, StopType.PICKUP, existingPickupLoc,
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        constraintEngine.propagateArrivalTimes(beforeNearby);

        // 'after' with a new PICKUP within 200m of the existing one (batching bonus applies)
        // ~100m offset: 0.001 degrees latitude ~ 111m
        GeoLocation nearbyPickupLoc = new GeoLocation(12.9555, 77.6055);
        GeoLocation nearbyDeliveryLoc = new GeoLocation(12.9600, 77.6100);
        RiderSchedule afterNearby = beforeNearby.copy();
        afterNearby.getStops().add(new ScheduledStop(
                101L, StopType.PICKUP, nearbyPickupLoc,
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        afterNearby.getStops().add(new ScheduledStop(
                101L, StopType.DELIVERY, nearbyDeliveryLoc,
                new TimeWindow(now, now.plusSeconds(3600)), 90));
        constraintEngine.propagateArrivalTimes(afterNearby);

        double costNearby = costFunction.insertionCost(beforeNearby, afterNearby);

        // 'before' schedule for the far scenario (same setup)
        RiderSchedule beforeFar = new RiderSchedule(2L, riderLoc);
        beforeFar.getStops().add(new ScheduledStop(
                50L, StopType.PICKUP, existingPickupLoc,
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        constraintEngine.propagateArrivalTimes(beforeFar);

        // 'after' with a new PICKUP far from the existing one (no batching bonus)
        // ~2km away: 0.02 degrees latitude ~ 2.2km
        GeoLocation farPickupLoc = new GeoLocation(12.9750, 77.6250);
        GeoLocation farDeliveryLoc = new GeoLocation(12.9800, 77.6300);
        RiderSchedule afterFar = beforeFar.copy();
        afterFar.getStops().add(new ScheduledStop(
                102L, StopType.PICKUP, farPickupLoc,
                new TimeWindow(now, now.plusSeconds(3600)), 120));
        afterFar.getStops().add(new ScheduledStop(
                102L, StopType.DELIVERY, farDeliveryLoc,
                new TimeWindow(now, now.plusSeconds(3600)), 90));
        constraintEngine.propagateArrivalTimes(afterFar);

        double costFar = costFunction.insertionCost(beforeFar, afterFar);

        // Nearby pickup should get a batching bonus (subtracted from cost),
        // so its insertion cost should be lower than the far pickup.
        assertTrue(costNearby < costFar,
                "Insertion cost with nearby pickup (" + costNearby +
                        ") should be lower than with far pickup (" + costFar +
                        ") due to batching bonus");
    }

    @Test
    void reassignmentCost_returnsConfiguredWeight() {
        // The default weightReassignmentPenalty in SchedulerProperties.Cost is 0.8
        double expected = 0.8;
        double actual = costFunction.reassignmentCost();

        assertEquals(expected, actual, 0.001,
                "reassignmentCost() should return the configured weight (default 0.8)");
    }
}
