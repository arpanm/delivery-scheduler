package com.deliveryscheduler.scheduler;

import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.StopType;
import com.deliveryscheduler.domain.model.TimeWindow;
import com.deliveryscheduler.routing.TravelTimeEstimator;
import com.deliveryscheduler.scheduler.constraint.ConstraintEngine;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ConstraintEngineTest {

    private ConstraintEngine constraintEngine;
    private TravelTimeEstimator travelTimeEstimator;

    @BeforeEach
    void setUp() {
        travelTimeEstimator = new TravelTimeEstimator(6.0, 1.4, 120, 90);
        constraintEngine = new ConstraintEngine(travelTimeEstimator);
    }

    @Test
    void feasibleSchedule_shouldPass() {
        GeoLocation riderLoc = new GeoLocation(12.95, 77.60);
        GeoLocation pickupLoc = new GeoLocation(12.955, 77.605);
        GeoLocation deliveryLoc = new GeoLocation(12.96, 77.61);

        Instant now = Instant.now();
        RiderSchedule schedule = new RiderSchedule(1L, riderLoc);

        // Pickup with generous time window
        schedule.getStops().add(new ScheduledStop(
                100L, StopType.PICKUP, pickupLoc,
                new TimeWindow(now, now.plusSeconds(3600)),
                120));

        // Delivery with generous time window
        schedule.getStops().add(new ScheduledStop(
                100L, StopType.DELIVERY, deliveryLoc,
                new TimeWindow(now, now.plusSeconds(3600)),
                90));

        assertTrue(constraintEngine.checkAll(schedule));
    }

    @Test
    void violatedTimeWindow_shouldFail() {
        GeoLocation riderLoc = new GeoLocation(12.95, 77.60);
        GeoLocation pickupLoc = new GeoLocation(12.955, 77.605);
        GeoLocation deliveryLoc = new GeoLocation(13.05, 77.70); // far away

        Instant now = Instant.now();
        RiderSchedule schedule = new RiderSchedule(1L, riderLoc);

        schedule.getStops().add(new ScheduledStop(
                100L, StopType.PICKUP, pickupLoc,
                new TimeWindow(now, now.plusSeconds(3600)),
                120));

        // Very tight delivery window that will be violated by the distance
        schedule.getStops().add(new ScheduledStop(
                100L, StopType.DELIVERY, deliveryLoc,
                new TimeWindow(now, now.plusSeconds(60)), // only 1 minute!
                90));

        assertFalse(constraintEngine.checkAll(schedule));
    }

    @Test
    void violatedPrecedence_shouldFail() {
        GeoLocation riderLoc = new GeoLocation(12.95, 77.60);
        GeoLocation pickupLoc = new GeoLocation(12.955, 77.605);
        GeoLocation deliveryLoc = new GeoLocation(12.96, 77.61);

        Instant now = Instant.now();
        RiderSchedule schedule = new RiderSchedule(1L, riderLoc);

        // Delivery BEFORE pickup — violates precedence
        schedule.getStops().add(new ScheduledStop(
                100L, StopType.DELIVERY, deliveryLoc,
                new TimeWindow(now, now.plusSeconds(3600)),
                90));

        schedule.getStops().add(new ScheduledStop(
                100L, StopType.PICKUP, pickupLoc,
                new TimeWindow(now, now.plusSeconds(3600)),
                120));

        assertFalse(constraintEngine.checkAll(schedule));
    }

    @Test
    void arrivalTimePropagation_shouldBeCorrect() {
        GeoLocation riderLoc = new GeoLocation(12.9500, 77.6000);
        GeoLocation stop1Loc = new GeoLocation(12.9510, 77.6010);
        GeoLocation stop2Loc = new GeoLocation(12.9520, 77.6020);

        Instant now = Instant.now();
        RiderSchedule schedule = new RiderSchedule(1L, riderLoc);

        ScheduledStop stop1 = new ScheduledStop(
                1L, StopType.PICKUP, stop1Loc,
                new TimeWindow(now, now.plusSeconds(3600)), 120);
        ScheduledStop stop2 = new ScheduledStop(
                1L, StopType.DELIVERY, stop2Loc,
                new TimeWindow(now, now.plusSeconds(3600)), 90);

        schedule.getStops().add(stop1);
        schedule.getStops().add(stop2);

        constraintEngine.propagateArrivalTimes(schedule);

        // Stop 1 should arrive after traveling from rider to stop1
        assertNotNull(stop1.getEstimatedArrival());
        assertTrue(stop1.getEstimatedArrival().isAfter(now));

        // Stop 2 should arrive after stop 1 departure + travel time
        assertNotNull(stop2.getEstimatedArrival());
        assertTrue(stop2.getEstimatedArrival().isAfter(stop1.getEstimatedArrival()));
    }
}
