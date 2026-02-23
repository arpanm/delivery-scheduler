package com.deliveryscheduler.domain.repository;

import com.deliveryscheduler.domain.model.Rider;
import com.deliveryscheduler.domain.model.RiderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiderRepository extends JpaRepository<Rider, Long> {

    List<Rider> findByAssignedZoneIdAndStatusIn(Long zoneId, List<RiderStatus> statuses);

    List<Rider> findByStatusIn(List<RiderStatus> statuses);
}
