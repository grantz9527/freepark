package com.freepark.local.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FrigateCameraRepository extends JpaRepository<FrigateCamera, UUID> {

    boolean existsByCameraNameIgnoreCase(String cameraName);

    boolean existsByCameraNameIgnoreCaseAndIdNot(String cameraName, UUID id);

    Optional<FrigateCamera> findByCameraNameIgnoreCase(String cameraName);

    List<FrigateCamera> findAllByOrderByCreatedAtDesc();

    List<FrigateCamera> findAllByLaneId(UUID laneId);

    List<FrigateCamera> findAllByEnabledTrue();
}
