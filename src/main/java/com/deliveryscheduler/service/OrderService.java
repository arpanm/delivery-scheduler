package com.deliveryscheduler.service;

import com.deliveryscheduler.domain.event.OrderPlacedEvent;
import com.deliveryscheduler.domain.model.*;
import com.deliveryscheduler.domain.repository.OrderRepository;
import com.deliveryscheduler.domain.repository.RestaurantRepository;
import com.deliveryscheduler.infrastructure.messaging.EventPublisher;
import com.deliveryscheduler.prediction.PrepTimePredictionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final PrepTimePredictionService prepTimePredictionService;
    private final EventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository,
                        RestaurantRepository restaurantRepository,
                        PrepTimePredictionService prepTimePredictionService,
                        EventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.restaurantRepository = restaurantRepository;
        this.prepTimePredictionService = prepTimePredictionService;
        this.eventPublisher = eventPublisher;
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    public List<Order> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    @Transactional
    public Order placeOrder(Long restaurantId, GeoLocation deliveryLocation,
                            String customerName, String customerPhone,
                            String itemsSummary, int itemCount,
                            Instant promisedDeliveryBy) {

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));

        int estimatedPrepTime = prepTimePredictionService.predictPrepTimeSeconds(
                restaurantId, itemCount, Instant.now());

        Order order = new Order(restaurant, deliveryLocation, customerName, customerPhone,
                promisedDeliveryBy, estimatedPrepTime, itemsSummary, itemCount);
        order = orderRepository.save(order);

        eventPublisher.publish(new OrderPlacedEvent(order));

        return order;
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }
}
