package com.freepark.local.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLotRepository extends JpaRepository<ParkingLot, UUID> {

    boolean existsByCode(String code);

    List<ParkingLot> findAllByOrderByCreatedAtDesc();
}
