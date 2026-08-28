package com.freepark.local.frigate.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.device.service.DeviceCommandService;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.FrigateBindDirection;
import com.freepark.local.domain.FrigateCamera;
import com.freepark.local.domain.FrigateCameraRepository;
import com.freepark.local.domain.FrigateLinkStatus;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.domain.RecognitionRecordRepository;

@Service
public class FrigateEventHandler {

    private static final Logger log = LoggerFactory.getLogger(FrigateEventHandler.class);

    private final FrigateCameraRepository cameras;
    private final ParkingBarrierRepository barriers;
    private final RecognitionRecordRepository recognitionRecords;
    private final DeviceCommandService deviceCommands;

    public FrigateEventHandler(
            FrigateCameraRepository cameras,
            ParkingBarrierRepository barriers,
            RecognitionRecordRepository recognitionRecords,
            DeviceCommandService deviceCommands) {
        this.cameras = cameras;
        this.barriers = barriers;
        this.recognitionRecords = recognitionRecords;
        this.deviceCommands = deviceCommands;
    }

    @Transactional
    public void onPlateRecognized(String cameraName, String plate, PlateColor plateColor) {
        FrigateCamera camera = cameras.findByCameraNameIgnoreCase(cameraName).orElse(null);
        if (camera == null || !camera.isEnabled()) {
            log.debug("Ignore Frigate plate for unknown/disabled camera {}", cameraName);
            return;
        }
        Instant now = Instant.now();
        camera.setLastPlate(plate);
        camera.setLastPlateColor(plateColor);
        camera.setLastEventAt(now);
        camera.setLinkStatus(FrigateLinkStatus.CONNECTED);
        cameras.save(camera);

        // 1) 无论是否绑定通道/开启联动/有无道闸，必须写一条识别记录（关联 Frigate 相机）。
        String direction = toDirection(camera.getBindDirection());
        recognitionRecords.save(new RecognitionRecord(camera, plate, plateColor, null, direction, now));

        // 2) 仅当绑定通道 + 开启联动时，才尝试联动道闸。
        if (camera.getLaneId() == null || !camera.isLinkageEnabled()) {
            log.info(
                    "Frigate event camera={} plate={} color={} (recorded only, no linkage lane={})",
                    camera.getCameraName(),
                    plate,
                    plateColor == null ? "unknown" : plateColor.name(),
                    camera.getLaneId());
            return;
        }

        List<ParkingBarrier> laneBarriers = barriers.findAllByLaneIdOrderByCreatedAtDesc(camera.getLaneId())
                .stream()
                .filter(ParkingBarrier::isEnabled)
                .toList();
        if (laneBarriers.isEmpty()) {
            log.info(
                    "Frigate event camera={} plate={} color={} lane={} recorded, but no enabled barrier to open",
                    camera.getCameraName(),
                    plate,
                    plateColor == null ? "unknown" : plateColor.name(),
                    camera.getLaneId());
            return;
        }

        for (ParkingBarrier barrier : laneBarriers) {
            // 对于联动道闸，额外以「道闸来源」视角再写一条，保持设备侧查询可见。
            recognitionRecords.save(new RecognitionRecord(barrier, plate, plateColor, null, direction, now));
            deviceCommands.enqueueSystem(barrier.getId(), DeviceCommand.Action.OPEN, "frigate:" + camera.getCameraName());
        }
        log.info(
                "Frigate event camera={} plate={} color={} lane={} opened {} barrier(s)",
                camera.getCameraName(),
                plate,
                plateColor == null ? "unknown" : plateColor.name(),
                camera.getLaneId(),
                laneBarriers.size());
    }

    /**
     * 兼容调用：颜色为 null。
     */
    public void onPlateRecognized(String cameraName, String plate) {
        onPlateRecognized(cameraName, plate, null);
    }

    private String toDirection(FrigateBindDirection bindDirection) {
        if (bindDirection == FrigateBindDirection.EXIT) {
            return "OUT";
        }
        if (bindDirection == FrigateBindDirection.ENTRANCE) {
            return "IN";
        }
        return null;
    }
}
