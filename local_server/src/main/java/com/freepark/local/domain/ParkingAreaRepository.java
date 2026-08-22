package com.freepark.local.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingAreaRepository extends JpaRepository<ParkingArea, UUID> {

    List<ParkingArea> findByLocationIdOrderByNameAsc(UUID locationId);

    List<ParkingArea> findByLocationLotIdOrderByNameAsc(UUID lotId);

    boolean existsByLocationIdAndNameIgnoreCase(UUID locationId, String name);
}
