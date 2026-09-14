package com.freepark.local.frigate.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.accessdecision.dto.AccessDecisionRequest;
import com.freepark.local.accessdecision.dto.AccessDecisionView;
import com.freepark.local.accessdecision.dto.AccessDirection;
import com.freepark.local.accessdecision.service.AccessDecisionService;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.device.service.DeviceCommandService;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.FrigateBindDirection;
import com.freepark.local.domain.FrigateCamera;
import com.freepark.local.domain.FrigateCameraRepository;
import com.freepark.local.domain.FrigateLinkStatus;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.ParkingLane;
import com.freepark.local.domain.ParkingLaneRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.edge.service.PendingGateOpenService;
import com.freepark.local.nodeconfig.service.FeeQuoteClient;
import com.freepark.local.parkingflow.service.ParkingSessionService;
import com.freepark.local.recognition.service.RecognitionRecordService;

@Service
public class FrigateEventHandler {

    private static final Logger log = LoggerFactory.getLogger(FrigateEventHandler.class);

    private final FrigateCameraRepository cameras;
    private final ParkingBarrierRepository barriers;
    private final ParkingLaneRepository lanes;
    private final RecognitionRecordService recognitionRecordService;
    private final DeviceCommandService deviceCommands;
    private final AccessDecisionService accessDecisions;
    private final ParkingSessionService parkingSessions;
    private final FeeQuoteClient feeQuoteClient;
    private final PendingGateOpenService pendingGateOpens;

    public FrigateEventHandler(
            FrigateCameraRepository cameras,
            ParkingBarrierRepository barriers,
            ParkingLaneRepository lanes,
            RecognitionRecordService recognitionRecordService,
            DeviceCommandService deviceCommands,
            AccessDecisionService accessDecisions,
            ParkingSessionService parkingSessions,
            FeeQuoteClient feeQuoteClient,
            PendingGateOpenService pendingGateOpens) {
        this.cameras = cameras;
        this.barriers = barriers;
        this.lanes = lanes;
        this.recognitionRecordService = recognitionRecordService;
        this.deviceCommands = deviceCommands;
        this.accessDecisions = accessDecisions;
        this.parkingSessions = parkingSessions;
        this.feeQuoteClient = feeQuoteClient;
        this.pendingGateOpens = pendingGateOpens;
    }

    @Transactional
    public void onPlateRecognized(String cameraName, String plate, PlateColor plateColor) {
        onPlateRecognized(cameraName, plate, plateColor, null, null);
    }

    @Transactional
    public void onPlateRecognized(String cameraName, String plate, PlateColor plateColor, String imageRef, String eventImage) {
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

        // 1) 识别记录入库（关联 Frigate 相机）。是否联动停车流水由下方放行/拦截判定决定：
        //    - 拦截车辆仅保留记录并标记拦截原因，不产生停车流水（拦截车辆不进入停车流水，不会上报云端）；
        //    - 放行车辆照常入库并联动停车流水。
        String direction = toDirection(camera.getBindDirection());
        boolean linked = camera.getLaneId() != null && camera.isLinkageEnabled();

        // 2) 仅当绑定通道 + 开启联动（相机直接驱动道闸）时才做通行判定；
        //    未联动相机仅观察/记录，维持原有“识别记录 + 具备车场时联动流水”语义。
        if (!linked) {
            RecognitionRecord record = recognitionRecordService.saveCameraRecord(
                    camera, plate, plateColor, direction, now, imageRef, eventImage);
            log.info(
                    "Frigate event camera={} plate={} color={} recognition record saved id={} (recorded only, no linkage lane={})",
                    camera.getCameraName(),
                    plate,
                    plateColor == null ? "unknown" : plateColor.name(),
                    record.getId(),
                    camera.getLaneId());
            return;
        }

        ParkingLane lane = lanes.findById(camera.getLaneId()).orElse(null);
        // 3) 空车牌：无法按车牌判定，不联动开闸、不生成流水，仅保留识别记录（与设备直连链路一致）。
        if (plate == null || plate.isBlank()) {
            log.info(
                    "Frigate event camera={} plate empty: recorded only, no open (no session flow)",
                    camera.getCameraName());
            recognitionRecordService.saveCameraRecordOnly(
                    camera, plate, plateColor, direction, now, imageRef, eventImage, null);
            return;
        }

        // 4) 联动通道：先按车场通行判定规则判定放行（入口/出口配置的拦截规则、方向、车道缺失均可判定时）
        AccessDirection accessDirection = toAccessDirection(direction);
        if (accessDirection != null && lane != null && lane.getLot() != null) {
            clearLanePending(lane);
            AccessDecisionView decision = decideForLane(lane, accessDirection, plate, plateColor);
            if (decision.result() == AccessDecisionView.Result.INTERCEPTED) {
                log.info(
                        "Frigate event camera={} plate={} color={} lane={} intercepted remark={}: record only, no open (no session flow)",
                        camera.getCameraName(),
                        plate,
                        plateColor == null ? "unknown" : plateColor.name(),
                        camera.getLaneId(),
                        decision.remark());
                recognitionRecordService.saveCameraRecordOnly(
                        camera, plate, plateColor, direction, now, imageRef, eventImage, decision.remark());
                if (PendingGateOpenService.FEE_PENDING.equals(decision.remark())) {
                    rememberFeePendingBarriers(lane, plate, plateColor);
                }
                return;
            }
        }

        // 5) 放行（或方向/车道信息不足无法判定时维持原联动语义）：识别记录入库并联动停车流水，再联动道闸。
        RecognitionRecord record = recognitionRecordService.saveCameraRecord(
                camera, plate, plateColor, direction, now, imageRef, eventImage);
        log.info(
                "Frigate event camera={} plate={} color={} recognition record saved id={}",
                camera.getCameraName(),
                plate,
                plateColor == null ? "unknown" : plateColor.name(),
                record.getId());

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
            // 只下发开闸指令，不再额外写「道闸来源」识别记录，避免一次事件产生两条重复记录。
            // 开闸入队与识别记录解耦：独立事务执行，入队失败仅告警，不影响已写入的识别记录。
            try {
                deviceCommands.enqueueSystemDetached(
                        barrier.getId(), DeviceCommand.Action.OPEN, "frigate:" + camera.getCameraName());
            } catch (Exception ex) {
                log.warn(
                        "Frigate event camera={} plate={} open enqueue failed for barrier={}: {}",
                        camera.getCameraName(),
                        plate,
                        barrier.getId(),
                        ex.getMessage());
            }
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
     * 联动通道通行判定：与设备直连链路同一套车场规则（白名单/黑名单/模式白名单/内部车辆/欠费拦截等）。
     * 欠费拦截在云端离线时跳过，不阻塞本机放行。
     */
    private AccessDecisionView decideForLane(ParkingLane lane, AccessDirection direction, String plate, PlateColor plateColor) {
        ParkingLot lot = lane.getLot();
        UUID lotId = lot.getId();
        boolean interceptArrears = direction == AccessDirection.ENTRANCE
                ? lot.isEntryInterceptArrears()
                : lot.isExitInterceptArrears();
        BigDecimal dueAmount = interceptArrears
                ? feeQuoteClient.quoteForAccess(
                        lot.getCode(), plate, plateColor == null ? null : plateColor.name(), lane.getCode()).orElse(null)
                : null;
        AccessDecisionView decision = accessDecisions.decide(lotId, new AccessDecisionRequest(
                lane.getId(),
                plate,
                plateColor,
                direction,
                null, // 通道未配置拦截色，由调用方具备时提供
                direction == AccessDirection.EXIT ? parkingSessions.hasOpenSession(lotId, plate) : null,
                dueAmount));
        log.info("Frigate linkage decision lane={} plate={} direction={} lot={} due={} result={} remark={}",
                lane.getId(), plate, direction, lotId, dueAmount, decision.result(), decision.remark());
        return decision;
    }

    private void rememberFeePendingBarriers(ParkingLane lane, String plate, PlateColor plateColor) {
        if (lane == null || lane.getLot() == null) {
            return;
        }
        String lotCode = lane.getLot().getCode();
        for (ParkingBarrier barrier : barriers.findAllByLaneIdOrderByCreatedAtDesc(lane.getId())) {
            if (barrier.isEnabled()) {
                pendingGateOpens.remember(barrier.getId(), lotCode, plate, plateColor);
            }
        }
    }

    /** 该通道出现新识别：清掉闸前旧等待，避免缴费后误开后车。 */
    private void clearLanePending(ParkingLane lane) {
        if (lane == null || lane.getId() == null) {
            return;
        }
        for (ParkingBarrier barrier : barriers.findAllByLaneIdOrderByCreatedAtDesc(lane.getId())) {
            pendingGateOpens.clear(barrier.getId());
        }
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

    /** 对齐判定层的方向解释：IN/ENTRANCE/1 → 入场，OUT/EXIT/2 → 出场，其余不可判定。 */
    private AccessDirection toAccessDirection(String direction) {
        if (direction == null) {
            return null;
        }
        String upper = direction.trim().toUpperCase();
        if ("IN".equals(upper) || "ENTRANCE".equals(upper) || "1".equals(upper)) {
            return AccessDirection.ENTRANCE;
        }
        if ("OUT".equals(upper) || "EXIT".equals(upper) || "2".equals(upper)) {
            return AccessDirection.EXIT;
        }
        return null;
    }
}
