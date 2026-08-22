package com.freepark.local.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLocationRepository extends JpaRepository<ParkingLocation, UUID> {

    List<ParkingLocation> findByLotIdOrderByNameAsc(UUID lotId);

    boolean existsByLotIdAndNameIgnoreCase(UUID lotId, String name);
}
