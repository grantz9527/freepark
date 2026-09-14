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
import com.freepark.local.domain.LaneType;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.ParkingLane;
import com.freepark.local.domain.ParkingLaneRepository;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.domain.RecognitionRecordRepository;
import com.freepark.local.parkingflow.service.ParkingSessionService;

/**
 * 执行云端缴费开闸：匹配通道最新一次欠费拦截识别（15 分钟内、车牌一致、未被新识别覆盖），
 * 再向识别一体机主动 HTTP 下发开闸并播报；无驱动/地址时入队 OPEN 供轮询设备取走。
 */
@Service
public class CloudGateCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CloudGateCommandHandler.class);

    private final PendingGateOpenService pending;
    private final RecognitionRecordRepository records;
    private final ParkingBarrierRepository barriers;
    private final ParkingLaneRepository lanes;
    private final AioDriverService aioDrivers;
    private final DeviceCommandService commands;
    private final ParkingSessionService parkingSessions;

    public CloudGateCommandHandler(
            PendingGateOpenService pending,
            RecognitionRecordRepository records,
            ParkingBarrierRepository barriers,
            ParkingLaneRepository lanes,
            AioDriverService aioDrivers,
            DeviceCommandService commands,
            ParkingSessionService parkingSessions) {
        this.pending = pending;
        this.records = records;
        this.barriers = barriers;
        this.lanes = lanes;
        this.aioDrivers = aioDrivers;
        this.commands = commands;
        this.parkingSessions = parkingSessions;
    }

    @Transactional
    public void openAfterPayment(String plate, String plateColor, String lotCode, String laneCode, String commandId) {
        String normalizedPlate = PendingGateOpenService.normalizePlate(plate);
        if (normalizedPlate == null) {
            log.warn("缴费开闸忽略：车牌为空 commandId={}", commandId);
            return;
        }
        Instant since = Instant.now().minus(PendingGateOpenService.TTL);
        Set<UUID> deviceIds = new LinkedHashSet<>();
        if (laneCode != null && !laneCode.isBlank()) {
            deviceIds.addAll(devicesOnPayableLane(laneCode.trim(), normalizedPlate, plateColor, since));
        }
        if (deviceIds.isEmpty()) {
            for (UUID deviceId : pending.takeMatching(normalizedPlate, plateColor, lotCode)) {
                if (deviceStillPayable(deviceId, normalizedPlate, plateColor, since)) {
                    deviceIds.add(deviceId);
                }
            }
        }
        if (deviceIds.isEmpty()) {
            deviceIds.addAll(devicesFromLatestIntercept(normalizedPlate, plateColor, since));
        }
        if (deviceIds.isEmpty()) {
            log.info("缴费开闸未找到闸前拦截记录 plate={} lot={} lane={} commandId={}",
                    normalizedPlate, lotCode, laneCode, commandId);
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
            String voiceText = passVoice(normalizedPlate, device);
            String ledText = passLed(normalizedPlate, device);
            boolean pushed = aioDrivers.openGateSystem(device, source, voiceText, ledText);
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
        log.info("缴费开闸完成 plate={} lot={} lane={} devices={} opened={} commandId={}",
                normalizedPlate, lotCode, laneCode, deviceIds.size(), opened, commandId);
        if (opened > 0) {
            closeOpenSessions(deviceIds, normalizedPlate);
        }
    }

    private List<UUID> devicesOnPayableLane(String laneCode, String plate, String plateColor, Instant since) {
        ParkingLane lane = lanes.findByCodeIgnoreCase(laneCode).orElse(null);
        if (lane == null || !lane.isEnabled()) {
            return List.of();
        }
        if (!latestOnLaneIsPayable(lane.getId(), plate, plateColor, since)) {
            log.info("缴费开闸跳过通道：最新识别已覆盖或不在时效内 lane={} plate={}", laneCode, plate);
            return List.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (ParkingBarrier barrier : barriers.findAllByLaneIdOrderByCreatedAtDesc(lane.getId())) {
            if (barrier.isEnabled()) {
                ids.add(barrier.getId());
            }
        }
        return List.copyOf(ids);
    }

    private List<UUID> devicesFromLatestIntercept(String plate, String plateColor, Instant since) {
        List<RecognitionRecord> hits = records
                .findByPlateIgnoreCaseAndAbnormalReasonAndCapturedAtGreaterThanEqualOrderByCapturedAtDesc(
                        plate, PendingGateOpenService.FEE_PENDING, since, PageRequest.of(0, 8));
        Set<UUID> ids = new LinkedHashSet<>();
        for (RecognitionRecord record : hits) {
            if (!colorMatches(record, plateColor)) {
                continue;
            }
            UUID laneId = record.getLaneId();
            if (laneId != null && !latestOnLaneIsPayable(laneId, plate, plateColor, since)) {
                continue;
            }
            if (laneId == null && record.getDevice() != null
                    && !latestOnDeviceIsPayable(record.getDevice().getId(), plate, plateColor, since)) {
                continue;
            }
            if (record.getDevice() != null) {
                ids.add(record.getDevice().getId());
            } else if (laneId != null) {
                for (ParkingBarrier barrier : barriers.findAllByLaneIdOrderByCreatedAtDesc(laneId)) {
                    if (barrier.isEnabled()) {
                        ids.add(barrier.getId());
                    }
                }
            }
        }
        return List.copyOf(ids);
    }

    private boolean deviceStillPayable(UUID deviceId, String plate, String plateColor, Instant since) {
        ParkingBarrier device = barriers.findById(deviceId).orElse(null);
        if (device == null) {
            return false;
        }
        if (device.getLane() != null) {
            return latestOnLaneIsPayable(device.getLane().getId(), plate, plateColor, since);
        }
        return latestOnDeviceIsPayable(deviceId, plate, plateColor, since);
    }

    private boolean latestOnLaneIsPayable(UUID laneId, String plate, String plateColor, Instant since) {
        List<RecognitionRecord> latest = records.findByLaneIdOrderByCapturedAtDesc(laneId, PageRequest.of(0, 1));
        return isPayableLatest(latest, plate, plateColor, since);
    }

    private boolean latestOnDeviceIsPayable(UUID deviceId, String plate, String plateColor, Instant since) {
        List<RecognitionRecord> latest = records.findByDevice_IdOrderByCapturedAtDesc(deviceId, PageRequest.of(0, 1));
        return isPayableLatest(latest, plate, plateColor, since);
    }

    private static boolean isPayableLatest(List<RecognitionRecord> latest, String plate, String plateColor, Instant since) {
        if (latest == null || latest.isEmpty()) {
            return false;
        }
        RecognitionRecord record = latest.get(0);
        if (record.getCapturedAt() == null || record.getCapturedAt().isBefore(since)) {
            return false;
        }
        if (record.getPlate() == null || !plate.equalsIgnoreCase(record.getPlate().trim())) {
            return false;
        }
        if (!colorMatches(record, plateColor)) {
            return false;
        }
        return PendingGateOpenService.FEE_PENDING.equals(record.getAbnormalReason());
    }

    private static boolean colorMatches(RecognitionRecord record, String plateColor) {
        String wantedColor = plateColor == null || plateColor.isBlank()
                ? null
                : plateColor.trim().toUpperCase();
        return wantedColor == null || record.getPlateColor() == null
                || wantedColor.equals(record.getPlateColor().name());
    }

    private String passVoice(String plate, ParkingBarrier device) {
        String farewell = farewellFor(device);
        return farewell == null ? plate : plate + "," + farewell;
    }

    private String passLed(String plate, ParkingBarrier device) {
        String farewell = farewellFor(device);
        return farewell == null ? plate : plate + "\n" + farewell;
    }

    private String farewellFor(ParkingBarrier device) {
        LaneType laneType = barriers.findBoundLaneType(device.getId()).orElse(null);
        if (laneType == LaneType.ENTRANCE) {
            return "欢迎光临";
        }
        if (laneType == LaneType.EXIT) {
            return "一路顺风";
        }
        return "一路顺风";
    }

    private void closeOpenSessions(Set<UUID> deviceIds, String plate) {
        Set<UUID> closedLanes = new LinkedHashSet<>();
        for (UUID deviceId : deviceIds) {
            ParkingBarrier device = barriers.findById(deviceId).orElse(null);
            if (device == null || device.getLane() == null) {
                continue;
            }
            ParkingLane lane = device.getLane();
            if (!closedLanes.add(lane.getId())) {
                continue;
            }
            RecognitionRecord intercept = null;
            if (lane.getId() != null) {
                List<RecognitionRecord> latest = records.findByLaneIdOrderByCapturedAtDesc(
                        lane.getId(), PageRequest.of(0, 1));
                if (!latest.isEmpty()) {
                    intercept = latest.get(0);
                }
            }
            parkingSessions.closeOpenOnPaidExit(lane, plate, intercept);
        }
    }
}
