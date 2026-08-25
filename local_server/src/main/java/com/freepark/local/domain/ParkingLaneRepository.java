package com.freepark.local.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLaneRepository extends JpaRepository<ParkingLane, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<ParkingLane> findAllByOrderByCreatedAtDesc();

    List<ParkingLane> findAllByLot_IdOrLinkedLot_IdOrderByCreatedAtDesc(UUID lotId, UUID linkedLotId);
}
