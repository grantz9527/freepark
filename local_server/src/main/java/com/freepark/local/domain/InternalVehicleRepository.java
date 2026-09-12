package com.freepark.local.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InternalVehicleRepository
        extends JpaRepository<InternalVehicle, UUID>, JpaSpecificationExecutor<InternalVehicle> {

    Optional<InternalVehicle> findByCloudId(Long cloudId);

    List<InternalVehicle> findAllByLotId(UUID lotId);

    boolean existsByLotIdAndPlateNumberIgnoreCase(UUID lotId, String plateNumber);

    boolean existsByLotIdAndPlateNumberIgnoreCaseAndEnabledTrue(UUID lotId, String plateNumber);

    boolean existsByLotIdAndPlateNumberIgnoreCaseAndPlateColorAndEnabledTrue(
            UUID lotId, String plateNumber, PlateColor plateColor);

    boolean existsByLotIdAndPlateNumberIgnoreCaseAndIdNot(UUID lotId, String plateNumber, UUID id);

    Optional<InternalVehicle> findByLotIdAndPlateNumberIgnoreCase(UUID lotId, String plateNumber);

    List<InternalVehicle> findAllByLotIdAndBatchId(UUID lotId, UUID batchId);
}
