package com.freepark.local.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ParkingSpaceRepository
        extends JpaRepository<ParkingSpace, UUID>, JpaSpecificationExecutor<ParkingSpace> {

    Optional<ParkingSpace> findByCloudId(Long cloudId);

    List<ParkingSpace> findAllByLotId(UUID lotId);

    boolean existsByLotIdAndCodeIgnoreCase(UUID lotId, String code);

    boolean existsByLotIdAndCodeIgnoreCaseAndIdNot(UUID lotId, String code, UUID id);
}
