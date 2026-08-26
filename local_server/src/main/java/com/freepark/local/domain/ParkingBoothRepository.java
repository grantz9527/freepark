package com.freepark.local.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ParkingBoothRepository
        extends JpaRepository<ParkingBooth, UUID>, JpaSpecificationExecutor<ParkingBooth> {

    boolean existsByLotIdAndNameIgnoreCase(UUID lotId, String name);

    boolean existsByLotIdAndNameIgnoreCaseAndIdNot(UUID lotId, String name, UUID id);

    boolean existsByLotIdAndCodeIgnoreCase(UUID lotId, String code);

    boolean existsByLotIdAndCodeIgnoreCaseAndIdNot(UUID lotId, String code, UUID id);
}
