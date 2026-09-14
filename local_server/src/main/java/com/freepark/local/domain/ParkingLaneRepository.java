package com.freepark.local.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLaneRepository extends JpaRepository<ParkingLane, UUID> {

    Optional<ParkingLane> findByCloudId(Long cloudId);

    boolean existsByCodeIgnoreCase(String code);

    Optional<ParkingLane> findByCodeIgnoreCase(String code);

    List<ParkingLane> findAllByOrderByCreatedAtDesc();

    List<ParkingLane> findAllByLot_IdOrLinkedLot_IdOrderByCreatedAtDesc(UUID lotId, UUID linkedLotId);

    List<ParkingLane> findAllByLot_IdOrderByCreatedAtAsc(UUID lotId);
}
