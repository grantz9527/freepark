package com.freepark.local.edge.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.aiodriver.service.AioDriverService;
import com.freepark.local.device.service.DeviceCommandService;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.domain.RecognitionRecordRepository;

/**
 * 执行云端缴费开闸：先匹配闸前欠费拦截登记，没有则回落到近期 {@code fee_pending} 识别记录，
 * 再向识别一体机主动 HTTP 下发开闸；无驱动/地址时入队 OPEN 供轮询设备取走。
 */
@Service
public class CloudGateCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CloudGateCommandHandler.class);

    private final PendingGateOpenService pending;
    private final RecognitionRecordRepository records;
    private final ParkingBarrierRepository barriers;
    private final AioDriverService aioDrivers;
    private final DeviceCommandService commands;

    public CloudGateCommandHandler(
            PendingGateOpenService pending,
            RecognitionRecordRepository records,
            ParkingBarrierRepository barriers,
            AioDriverService aioDrivers,
            DeviceCommandService commands) {
        this.pending = pending;
        this.records = records;
        this.barriers = barriers;
        this.aioDrivers = aioDrivers;
        this.commands = commands;
    }

    @Transactional
    public void openAfterPayment(String plate, String plateColor, String lotCode, String commandId) {
        String normalizedPlate = PendingGateOpenService.normalizePlate(plate);
        if (normalizedPlate == null) {
            log.warn("缴费开闸忽略：车牌为空 commandId={}", commandId);
            return;
        }
        Set<UUID> deviceIds = new LinkedHashSet<>(pending.takeMatching(normalizedPlate, plateColor, lotCode));
        if (deviceIds.isEmpty()) {
            deviceIds.addAll(devicesFromRecentIntercept(normalizedPlate, plateColor));
        }
        if (deviceIds.isEmpty()) {
            log.info("缴费开闸未找到闸前拦截记录 plate={} lot={} commandId={}",
                    normalizedPlate, lotCode, commandId);
            return;
        }
        String source = "cloud-pay:" + (commandId == null || commandId.isBlank() ? normalizedPlate : commandId);
        int opened = 0;
        for (UUID deviceId : deviceIds) {
            ParkingBarrier device = barriers.findById(deviceId).orElse(null);
            if (device == null || !device.isEnabled()) {
                log.warn("缴费开闸跳过：设备不存在或已停用 deviceId={} plate={}", deviceId, normalizedPlate);
                continue;
            }
            boolean pushed = aioDrivers.openGateSystem(device, source);
            if (!pushed) {
                try {
                    commands.enqueueSystemDetached(device.getId(), DeviceCommand.Action.OPEN, source);
                    log.info("缴费开闸已入队（等待设备轮询） device={} plate={} commandId={}",
                            device.getCode(), normalizedPlate, commandId);
                } catch (Exception ex) {
                    log.warn("缴费开闸入队失败 device={} plate={}：{}",
                            device.getCode(), normalizedPlate, ex.getMessage());
                    continue;
                }
            }
            opened++;
        }
        log.info("缴费开闸完成 plate={} lot={} devices={} opened={} commandId={}",
                normalizedPlate, lotCode, deviceIds.size(), opened, commandId);
    }

    private List<UUID> devicesFromRecentIntercept(String plate, String plateColor) {
        Instant after = Instant.now().minus(PendingGateOpenService.TTL);
        List<RecognitionRecord> hits = records
                .findByPlateIgnoreCaseAndAbnormalReasonAndCapturedAtGreaterThanEqualOrderByCapturedAtDesc(
                        plate, PendingGateOpenService.FEE_PENDING, after, PageRequest.of(0, 8));
        Set<UUID> ids = new LinkedHashSet<>();
        String wantedColor = plateColor == null || plateColor.isBlank()
                ? null
                : plateColor.trim().toUpperCase();
        for (RecognitionRecord record : hits) {
            if (wantedColor != null && record.getPlateColor() != null
                    && !wantedColor.equals(record.getPlateColor().name())) {
                continue;
            }
            if (record.getDevice() != null) {
                ids.add(record.getDevice().getId());
            } else if (record.getLaneId() != null) {
                for (ParkingBarrier barrier : barriers.findAllByLaneIdOrderByCreatedAtDesc(record.getLaneId())) {
                    if (barrier.isEnabled()) {
                        ids.add(barrier.getId());
                    }
                }
            }
        }
        return List.copyOf(ids);
    }
}
