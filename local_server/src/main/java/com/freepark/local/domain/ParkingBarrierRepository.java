package com.freepark.local.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingBarrierRepository extends JpaRepository<ParkingBarrier, UUID> {

    boolean existsByLaneIdAndCodeIgnoreCase(UUID laneId, String code);

    List<ParkingBarrier> findAllByLaneIdOrderByCreatedAtDesc(UUID laneId);
}
