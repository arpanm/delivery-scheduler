package com.deliveryscheduler.service;

import com.deliveryscheduler.domain.event.StopCompletedEvent;
import com.deliveryscheduler.domain.model.*;
import com.deliveryscheduler.domain.repository.OrderRepository;
import com.deliveryscheduler.domain.repository.TripRepository;
import com.deliveryscheduler.infrastructure.messaging.EventPublisher;
import com.deliveryscheduler.infrastructure.metrics.SchedulerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final SchedulerMetrics metrics;

    public TripService(TripRepository tripRepository,
                       OrderRepository orderRepository,
                       EventPublisher eventPublisher,
                       SchedulerMetrics metrics) {
        this.tripRepository = tripRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    public Optional<Trip> findById(Long id) {
        return tripRepository.findById(id);
    }

    public Optional<Trip> getCurrentTrip(Long riderId) {
        return tripRepository.findByRiderIdAndStatusIn(riderId,
                List.of(TripStatus.PLANNED, TripStatus.ACTIVE));
    }

    @Transactional
    public Trip createTrip(Rider rider) {
        Trip trip = new Trip(rider);
        return tripRepository.save(trip);
    }

    @Transactional
    public void completeStop(Long riderId, Long stopId) {
        Trip trip = getCurrentTrip(riderId)
                .orElseThrow(() -> new IllegalStateException("No active trip for rider: " + riderId));

        TripStop stop = trip.getStops().stream()
                .filter(s -> s.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stop not found: " + stopId));

        stop.markCompleted();

        // If it's the first stop being completed, activate the trip
        if (trip.getStatus() == TripStatus.PLANNED) {
            trip.start();
        }

        // Update order status based on stop type
        if (stop.getStopType() == StopType.PICKUP) {
            updateOrdersForPickup(stop.getReferenceId(), trip);
        } else {
            updateOrderForDelivery(stop);
        }

        // Check if all stops are completed
        boolean allCompleted = trip.getStops().stream().allMatch(TripStop::isCompleted);
        if (allCompleted) {
            trip.complete();
        }

        tripRepository.save(trip);

        eventPublisher.publish(new StopCompletedEvent(
                riderId, trip.getId(), stopId, stop.getStopType(), stop.getReferenceId()));
    }

    private void updateOrdersForPickup(Long restaurantId, Trip trip) {
        List<Order> orders = orderRepository.findByTripId(trip.getId());
        for (Order order : orders) {
            if (order.getRestaurant().getId().equals(restaurantId)
                    && order.getStatus() == OrderStatus.ASSIGNED) {
                order.setStatus(OrderStatus.PICKED_UP);
                orderRepository.save(order);
            }
        }
    }

    /**
     * Update order status on delivery completion (GAP 9: SLA tracking).
     * Compares actual arrival time against promised delivery time to detect
     * late deliveries. Late deliveries are marked DELIVERED_LATE and tracked
     * via metrics for operational visibility.
     */
    private void updateOrderForDelivery(TripStop stop) {
        Order order = orderRepository.findById(stop.getReferenceId()).orElse(null);
        if (order == null) return;

        Instant actualArrival = stop.getActualArrival();
        Instant promisedBy = order.getPromisedDeliveryBy();

        if (actualArrival != null && promisedBy != null && actualArrival.isAfter(promisedBy)) {
            order.setStatus(OrderStatus.DELIVERED_LATE);
            metrics.recordLateDelivery();
            log.warn("Order {} delivered late: actual={}, promised={}",
                    order.getId(), actualArrival, promisedBy);
        } else {
            order.setStatus(OrderStatus.DELIVERED);
        }
        orderRepository.save(order);
    }
}
