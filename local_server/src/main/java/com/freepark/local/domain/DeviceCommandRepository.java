package com.freepark.local.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, UUID> {

    /** 取该设备最早一条 PENDING 指令（FIFO），用于轮询出队。 */
    Optional<DeviceCommand> findFirstByDevice_IdAndStatusOrderByCreatedAtAsc(UUID deviceId, DeviceCommand.Status status);

    List<DeviceCommand> findByDevice_IdAndStatusOrderByCreatedAtDesc(UUID deviceId, DeviceCommand.Status status);

    /** 清理指定时间之前已投递的指令。 */
    long deleteByStatusAndDeliveredAtBefore(DeviceCommand.Status status, Instant before);

    long countByDevice_IdAndStatus(UUID deviceId, DeviceCommand.Status status);

    List<DeviceCommand> findByDevice_IdOrderByCreatedAtDesc(UUID deviceId, Pageable pageable);
}
