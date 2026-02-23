package com.deliveryscheduler.domain.repository;

import com.deliveryscheduler.domain.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    @Query(value = """
        SELECT z.* FROM zones z
        WHERE ST_Contains(z.boundary, ST_SetSRID(ST_Point(:lon, :lat), 4326))
        """, nativeQuery = true)
    Optional<Zone> findZoneContaining(@Param("lon") double longitude, @Param("lat") double latitude);

    @Query(value = """
        SELECT z.* FROM zones z
        WHERE ST_Touches(z.boundary,
            (SELECT z2.boundary FROM zones z2 WHERE z2.id = :zoneId))
        AND z.id != :zoneId
        """, nativeQuery = true)
    List<Zone> findAdjacentZones(@Param("zoneId") Long zoneId);
}
