package com.freepark.local.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BlacklistVehicleRepository
        extends JpaRepository<BlacklistVehicle, UUID>, JpaSpecificationExecutor<BlacklistVehicle> {

    boolean existsByLotIdAndPlateNumberIgnoreCase(UUID lotId, String plateNumber);

    boolean existsByLotIdAndPlateNumberIgnoreCaseAndEnabledTrue(UUID lotId, String plateNumber);

    boolean existsByLotIdAndPlateNumberIgnoreCaseAndIdNot(UUID lotId, String plateNumber, UUID id);
}
