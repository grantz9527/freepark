package com.freepark.local.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InternalVehicleRepository
        extends JpaRepository<InternalVehicle, UUID>, JpaSpecificationExecutor<InternalVehicle> {

    boolean existsByLotIdAndPlateNumberIgnoreCase(UUID lotId, String plateNumber);

    boolean existsByLotIdAndPlateNumberIgnoreCaseAndIdNot(UUID lotId, String plateNumber, UUID id);

    List<InternalVehicle> findAllByLotIdAndBatchId(UUID lotId, UUID batchId);
}
