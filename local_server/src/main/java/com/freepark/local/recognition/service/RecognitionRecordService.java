package com.freepark.local.recognition.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.FrigateCamera;
import com.freepark.local.domain.LaneType;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingLane;
import com.freepark.local.domain.ParkingLaneRepository;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.RecognitionEventType;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.domain.RecognitionRecordRepository;
import com.freepark.local.parkingflow.service.ParkingSessionService;
import com.freepark.local.recognition.dto.CreateRecognitionRecordRequest;
import com.freepark.local.recognition.dto.ParkingFlowResult;
import com.freepark.local.recognition.dto.RecognitionRecordView;

import jakarta.persistence.criteria.Predicate;

/**
 * 识别记录：列表查询、手工/模拟录入、异常标记、设备/相机事件入库。
 * 设备/相机事件与手工录入统一走 {@link #applyFlowAndMarkAbnormal} 联动停车流水。
 */
@Service
public class RecognitionRecordService {

    private static final int MAX_QUERY_LIMIT = 500;

    private final RecognitionRecordRepository records;
    private final ParkingLaneRepository lanes;
    private final ParkingSessionService parkingSessions;

    public RecognitionRecordService(
            RecognitionRecordRepository records,
            ParkingLaneRepository lanes,
            ParkingSessionService parkingSessions) {
        this.records = records;
        this.lanes = lanes;
        this.parkingSessions = parkingSessions;
    }

    @Transactional(readOnly = true)
    public List<RecognitionRecordView> listRecords(
            UUID lotId, UUID laneId, String keyword, RecognitionEventType eventType, Boolean abnormalOnly) {
        Specification<RecognitionRecord> spec = buildSpec(lotId, laneId, keyword, eventType, abnormalOnly);
        return records.findAll(spec).stream()
                .sorted((a, b) -> b.getCapturedAt().compareTo(a.getCapturedAt()))
                .limit(MAX_QUERY_LIMIT)
                .map(RecognitionRecordView::from)
                .toList();
    }

    /**
     * 手工/模拟录入：保存识别记录并联动停车流水。
     * 预标记异常（拦截类事件）不联动；出场未匹配时自动标记异常。
     */
    @Transactional
    public ParkingFlowResult createManualRecord(CreateRecognitionRecordRequest req) {
        Instant capturedAt = req.eventTime() != null ? req.eventTime() : Instant.now();
        boolean preAbnormal = req.abnormal() != null && req.abnormal();
        RecognitionRecord record = new RecognitionRecord(
                req.plateNumber(),
                req.plateColor(),
                req.eventImage(),
                req.eventType() == null ? RecognitionEventType.DEVICE : req.eventType(),
                req.direction(),
                capturedAt,
                req.lotId(),
                trimToNull(req.lotName()),
                req.laneId(),
                trimToNull(req.laneName()),
                req.sourceSimEventId(),
                preAbnormal,
                req.abnormalReason());
        record = records.save(record);
        if (preAbnormal) {
            return ParkingFlowResult.skipped();
        }
        return applyFlowAndMarkAbnormal(record);
    }

    @Transactional
    public RecognitionRecordView markAbnormal(UUID recordId, String reason) {
        RecognitionRecord record = records.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        record.setAbnormal(true);
        record.setAbnormalReason(reason);
        return RecognitionRecordView.from(records.save(record));
    }

    @Transactional
    public RecognitionRecordView markVoided(UUID recordId) {
        RecognitionRecord record = records.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        record.setVoided(true);
        return RecognitionRecordView.from(records.save(record));
    }

    /**
     * 设备直连事件入库（如臻识推送）：填充车场/通道快照 + 联动流水。
     */
    @Transactional
    public RecognitionRecord saveDeviceRecord(RecognitionRecord record) {
        fillSnapshot(record);
        inferDirectionIfMissing(record);
        record = records.save(record);
        applyFlowAndMarkAbnormal(record);
        return record;
    }

    /**
     * Frigate 相机事件入库：填充车场/通道快照 + 联动流水（未绑定通道时不联动）。
     * imageRef/eventImage：识别抓拍图（imageRef 为相对存储路径，eventImage 为可访问 URL，可为 null）。
     */
    @Transactional
    public RecognitionRecord saveCameraRecord(
            FrigateCamera camera,
            String plate,
            PlateColor plateColor,
            String direction,
            Instant capturedAt,
            String imageRef,
            String eventImage) {
        RecognitionRecord record = new RecognitionRecord(camera, plate, plateColor, imageRef, direction, capturedAt);
        record.setEventImage(eventImage);
        fillSnapshot(record);
        inferDirectionIfMissing(record);
        record = records.save(record);
        if (record.getLotId() != null) {
            applyFlowAndMarkAbnormal(record);
        }
        return record;
    }

    /** 联动流水；出场未匹配时标记记录异常。 */
    private ParkingFlowResult applyFlowAndMarkAbnormal(RecognitionRecord record) {
        ParkingFlowResult flow = parkingSessions.applyRecognition(record);
        if ("exit_unmatched".equals(flow.kind())) {
            record.setAbnormal(true);
            record.setAbnormalReason("exit_unmatched");
            records.save(record);
        }
        return flow;
    }

    /** 当方向缺失或无法判定（如臻识推送无 direction 字段）时，按绑定车道的类型兜底推断。 */
    private void inferDirectionIfMissing(RecognitionRecord record) {
        if (resolvableDirection(record.getDirection())) {
            return;
        }
        LaneType laneType = null;
        if (record.getDevice() != null && record.getDevice().getLane() != null) {
            laneType = record.getDevice().getLane().getLaneType();
        } else if (record.getFrigateCamera() != null && record.getFrigateCamera().getLaneId() != null) {
            laneType = lanes.findById(record.getFrigateCamera().getLaneId())
                    .map(ParkingLane::getLaneType)
                    .orElse(null);
        }
        if (laneType == LaneType.ENTRANCE) {
            record.setDirection("ENTRANCE");
        } else if (laneType == LaneType.EXIT) {
            record.setDirection("EXIT");
        }
        // BIDIRECTIONAL 或未知：保持原值，流水层会跳过，不误联动
    }

    private boolean resolvableDirection(String direction) {
        if (direction == null) {
            return false;
        }
        String upper = direction.trim().toUpperCase();
        return "IN".equals(upper) || "OUT".equals(upper)
                || "ENTRANCE".equals(upper) || "EXIT".equals(upper)
                || "1".equals(upper) || "2".equals(upper);
    }

    /** 解析车场/通道快照。设备来源优先走 device→lane→lot，相机来源按 laneId 查通道。 */
    private void fillSnapshot(RecognitionRecord record) {
        if (record.getDevice() != null) {
            ParkingBarrier device = record.getDevice();
            if (device.getLane() != null) {
                ParkingLane lane = device.getLane();
                record.setLaneId(lane.getId());
                record.setLaneName(lane.getName());
                if (lane.getLot() != null) {
                    record.setLotId(lane.getLot().getId());
                    record.setLotName(lane.getLot().getName());
                }
            }
            return;
        }
        if (record.getFrigateCamera() != null && record.getFrigateCamera().getLaneId() != null) {
            UUID laneId = record.getFrigateCamera().getLaneId();
            lanes.findById(laneId).ifPresent(lane -> {
                record.setLaneId(lane.getId());
                record.setLaneName(lane.getName());
                if (lane.getLot() != null) {
                    record.setLotId(lane.getLot().getId());
                    record.setLotName(lane.getLot().getName());
                }
            });
        }
    }

    private Specification<RecognitionRecord> buildSpec(
            UUID lotId, UUID laneId, String keyword, RecognitionEventType eventType, Boolean abnormalOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (lotId != null) {
                predicates.add(cb.equal(root.get("lotId"), lotId));
            }
            if (laneId != null) {
                predicates.add(cb.equal(root.get("laneId"), laneId));
            }
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (abnormalOnly != null && abnormalOnly) {
                predicates.add(cb.isTrue(root.get("abnormal")));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("plate")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("lotName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("laneName"), "")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
