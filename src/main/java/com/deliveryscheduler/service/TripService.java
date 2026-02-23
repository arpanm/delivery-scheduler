package com.deliveryscheduler.service;

import com.deliveryscheduler.domain.event.StopCompletedEvent;
import com.deliveryscheduler.domain.model.*;
import com.deliveryscheduler.domain.repository.OrderRepository;
import com.deliveryscheduler.domain.repository.TripRepository;
import com.deliveryscheduler.infrastructure.messaging.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;

    public TripService(TripRepository tripRepository,
                       OrderRepository orderRepository,
                       EventPublisher eventPublisher) {
        this.tripRepository = tripRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
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
            updateOrderForDelivery(stop.getReferenceId());
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

    private void updateOrderForDelivery(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            order.setStatus(OrderStatus.DELIVERED);
            orderRepository.save(order);
        }
    }
}
