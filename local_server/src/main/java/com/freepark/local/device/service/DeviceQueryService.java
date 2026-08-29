package com.freepark.local.device.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.device.dto.DeviceCommandView;
import com.freepark.local.device.dto.DeviceStatusView;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.domain.RecognitionRecordRepository;

/**
 * admin 侧设备查询：在线状态由 lastPollAt 推导，并列出排队指令与识别记录。
 */
@Service
public class DeviceQueryService {

    /** 心跳超时阈值：超过此时长未轮询即视为离线。 */
    static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(30);

    private final ParkingBarrierRepository barriers;
    private final DeviceCommandService commandService;
    private final RecognitionRecordRepository records;

    public DeviceQueryService(
            ParkingBarrierRepository barriers,
            DeviceCommandService commandService,
            RecognitionRecordRepository records) {
        this.barriers = barriers;
        this.commandService = commandService;
        this.records = records;
    }

    @Transactional(readOnly = true)
    public List<DeviceStatusView> listStatuses() {
        return barriers.findAll().stream()
                .map(this::toStatusView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceStatusView> listStatusesByLane(UUID laneId) {
        return barriers.findAllByLaneIdOrderByCreatedAtDesc(laneId).stream()
                .map(this::toStatusView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceCommandView> listCommands(UUID deviceId, int limit) {
        return commandService.listRecent(deviceId, limit);
    }

    @Transactional(readOnly = true)
    public List<RecognitionRecord> listRecords(UUID deviceId, int limit) {
        return records.findByDevice_IdOrderByCapturedAtDesc(
                deviceId, PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "capturedAt")));
    }

    @Transactional(readOnly = true)
    public List<RecognitionRecord> listRecordsByPlate(String plate, int limit) {
        return records.findByPlateOrderByCapturedAtDesc(
                plate, PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "capturedAt")));
    }

    private DeviceStatusView toStatusView(ParkingBarrier barrier) {
        long pending = commandService.countPending(barrier.getId());
        return DeviceStatusView.from(barrier, isOnline(barrier), pending);
    }

    private boolean isOnline(ParkingBarrier barrier) {
        Instant last = barrier.getLastPollAt();
        return last != null && last.plus(ONLINE_TIMEOUT).isAfter(Instant.now());
    }
}
