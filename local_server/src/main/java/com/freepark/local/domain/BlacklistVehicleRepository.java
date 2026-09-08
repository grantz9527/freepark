package com.freepark.local.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BlacklistVehicleRepository
        extends JpaRepository<BlacklistVehicle, UUID>, JpaSpecificationExecutor<BlacklistVehicle> {

    Optional<BlacklistVehicle> findByCloudId(Long cloudId);

    List<BlacklistVehicle> findAllByLotId(UUID lotId);

    boolean existsByLotIdAndPlateNumberIgnoreCase(UUID lotId, String plateNumber);

    boolean existsByLotIdAndPlateNumberIgnoreCaseAndEnabledTrue(UUID lotId, String plateNumber);

    boolean existsByLotIdAndPlateNumberIgnoreCaseAndIdNot(UUID lotId, String plateNumber, UUID id);
}
