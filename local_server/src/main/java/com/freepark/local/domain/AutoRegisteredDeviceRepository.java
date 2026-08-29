package com.freepark.local.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoRegisteredDeviceRepository extends JpaRepository<AutoRegisteredDevice, UUID> {

    Optional<AutoRegisteredDevice> findByCodeIgnoreCase(String code);

    List<AutoRegisteredDevice> findAllByAdoptedFalseOrderByLastPollAtDesc();
}
