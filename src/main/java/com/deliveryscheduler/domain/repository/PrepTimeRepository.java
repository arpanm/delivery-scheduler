package com.deliveryscheduler.domain.repository;

import com.deliveryscheduler.domain.model.PrepTimeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrepTimeRepository extends JpaRepository<PrepTimeRecord, Long> {

    @Query("""
        SELECT p FROM PrepTimeRecord p
        WHERE p.restaurantId = :restaurantId
        AND p.actualPrepTimeSeconds IS NOT NULL
        ORDER BY p.createdAt DESC
        """)
    List<PrepTimeRecord> findRecentByRestaurantId(
            @Param("restaurantId") Long restaurantId,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT DISTINCT p.restaurantId FROM PrepTimeRecord p WHERE p.actualPrepTimeSeconds IS NOT NULL")
    List<Long> findAllActiveRestaurantIds();
}
