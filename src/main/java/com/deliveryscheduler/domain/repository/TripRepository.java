package com.deliveryscheduler.domain.repository;

import com.deliveryscheduler.domain.model.Trip;
import com.deliveryscheduler.domain.model.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findByRiderIdAndStatusIn(Long riderId, List<TripStatus> statuses);

    List<Trip> findByStatus(TripStatus status);

    List<Trip> findByStatusIn(List<TripStatus> statuses);

    List<Trip> findByRiderId(Long riderId);
}
