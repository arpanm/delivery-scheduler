package com.deliveryscheduler.domain.repository;

import com.deliveryscheduler.domain.model.Order;
import com.deliveryscheduler.domain.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByTripId(Long tripId);

    List<Order> findByTripRiderIdAndStatusIn(Long riderId, List<OrderStatus> statuses);
}
