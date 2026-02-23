package com.deliveryscheduler.service;

import com.deliveryscheduler.api.dto.GeoLocationDto;
import com.deliveryscheduler.api.dto.TrackingUpdate;
import com.deliveryscheduler.domain.event.RiderLocationUpdatedEvent;
import com.deliveryscheduler.domain.model.GeoLocation;
import com.deliveryscheduler.domain.model.Order;
import com.deliveryscheduler.domain.model.OrderStatus;
import com.deliveryscheduler.domain.repository.OrderRepository;
import com.deliveryscheduler.routing.TravelTimeEstimator;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class TrackingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final OrderRepository orderRepository;
    private final TravelTimeEstimator travelTimeEstimator;

    public TrackingService(SimpMessagingTemplate messagingTemplate,
                           OrderRepository orderRepository,
                           TravelTimeEstimator travelTimeEstimator) {
        this.messagingTemplate = messagingTemplate;
        this.orderRepository = orderRepository;
        this.travelTimeEstimator = travelTimeEstimator;
    }

    @Async("schedulerExecutor")
    @EventListener
    public void onRiderLocationUpdated(RiderLocationUpdatedEvent event) {
        Long riderId = event.riderId();
        GeoLocation riderLocation = event.location();

        List<Order> activeOrders = orderRepository.findByTripRiderIdAndStatusIn(
                riderId, List.of(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP));

        for (Order order : activeOrders) {
            long etaSeconds = travelTimeEstimator.estimateSeconds(
                    riderLocation, order.getDeliveryLocation());

            TrackingUpdate update = new TrackingUpdate(
                    order.getId(),
                    order.getStatus(),
                    GeoLocationDto.from(riderLocation),
                    Instant.now().plusSeconds(etaSeconds),
                    riderId);

            messagingTemplate.convertAndSend(
                    "/topic/orders/" + order.getId() + "/tracking", update);
        }
    }

    public TrackingUpdate getTrackingInfo(Long orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> o.getTrip() != null)
                .map(order -> new TrackingUpdate(
                        order.getId(),
                        order.getStatus(),
                        null, // rider location would come from Redis in real implementation
                        order.getPromisedDeliveryBy(),
                        order.getTrip().getRider().getId()))
                .orElse(null);
    }
}
