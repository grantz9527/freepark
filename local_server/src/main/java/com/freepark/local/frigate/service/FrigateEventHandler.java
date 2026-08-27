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
    public void onPlateRecognized(String cameraName, String plate) {
        FrigateCamera camera = cameras.findByCameraNameIgnoreCase(cameraName).orElse(null);
        if (camera == null || !camera.isEnabled()) {
            log.debug("Ignore Frigate plate for unknown/disabled camera {}", cameraName);
            return;
        }
        Instant now = Instant.now();
        camera.setLastPlate(plate);
        camera.setLastEventAt(now);
        camera.setLinkStatus(FrigateLinkStatus.CONNECTED);
        cameras.save(camera);

        if (camera.getLaneId() == null || !camera.isLinkageEnabled()) {
            return;
        }

        List<ParkingBarrier> laneBarriers = barriers.findAllByLaneIdOrderByCreatedAtDesc(camera.getLaneId())
                .stream()
                .filter(ParkingBarrier::isEnabled)
                .toList();
        if (laneBarriers.isEmpty()) {
            return;
        }

        String direction = toDirection(camera.getBindDirection());
        for (ParkingBarrier barrier : laneBarriers) {
            recognitionRecords.save(new RecognitionRecord(barrier, plate, null, direction, now));
            deviceCommands.enqueueSystem(barrier.getId(), DeviceCommand.Action.OPEN, "frigate:" + camera.getCameraName());
        }
        log.info(
                "Frigate event camera={} plate={} lane={} opened {} barrier(s)",
                camera.getCameraName(),
                plate,
                camera.getLaneId(),
                laneBarriers.size());
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
