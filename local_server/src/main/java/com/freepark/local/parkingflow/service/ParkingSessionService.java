package com.freepark.local.parkingflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.ParkingSession;
import com.freepark.local.domain.ParkingSessionRepository;
import com.freepark.local.domain.ParkingSessionStatus;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.domain.RecognitionRecordRepository;
import com.freepark.local.parkingflow.dto.ParkingSessionView;
import com.freepark.local.recognition.dto.ParkingFlowResult;

import jakarta.persistence.criteria.Predicate;

/**
 * 停车流水：入场识别自动生成在场流水，出场识别匹配并关闭流水，未匹配标记异常。
 */
@Service
public class ParkingSessionService {

    private static final int MAX_QUERY_LIMIT = 500;

    private final ParkingSessionRepository sessions;
    private final RecognitionRecordRepository recognitionRecords;

    public ParkingSessionService(
            ParkingSessionRepository sessions,
            RecognitionRecordRepository recognitionRecords) {
        this.sessions = sessions;
        this.recognitionRecords = recognitionRecords;
    }

    @Transactional(readOnly = true)
    public List<ParkingSessionView> listSessions(UUID lotId, String keyword, ParkingSessionStatus status) {
        Specification<ParkingSession> spec = buildSpec(lotId, keyword, status);
        return sessions.findAll(spec).stream()
                .sorted((a, b) -> b.getEntryTime().compareTo(a.getEntryTime()))
                .limit(MAX_QUERY_LIMIT)
                .map(ParkingSessionView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasOpenSession(UUID lotId, String plateNumber) {
        if (lotId == null || plateNumber == null || plateNumber.trim().isEmpty()) {
            return false;
        }
        return sessions.existsByLotIdAndPlateNumberIgnoreCaseAndStatus(
                lotId, plateNumber.trim().toUpperCase(), ParkingSessionStatus.OPEN);
    }

    /**
     * 核心联动：将识别记录应用到停车流水。
     * - ENTRANCE → 生成在场流水
     * - EXIT → 匹配同车场同车牌、入场时间在出场之前的最近在场流水并关闭
     * - 其余 → skipped
     */
    @Transactional
    public ParkingFlowResult applyRecognition(RecognitionRecord record) {
        if (record == null || record.getLotId() == null || record.getCapturedAt() == null) {
            return ParkingFlowResult.skipped();
        }
        if (isEntrance(record.getDirection())) {
            ParkingSession session = new ParkingSession(
                    record.getLotId(),
                    record.getLotName(),
                    record.getPlate(),
                    record.getPlateColor(),
                    record.getCapturedAt(),
                    record.getLaneId(),
                    record.getLaneName(),
                    record.getId(),
                    record.getEventImage());
            return ParkingFlowResult.entry(ParkingSessionView.from(sessions.save(session)));
        }
        if (isExit(record.getDirection())) {
            Optional<ParkingSession> best = sessions
                    .findFirstByLotIdAndPlateNumberIgnoreCaseAndStatusOrderByEntryTimeDesc(
                            record.getLotId(),
                            record.getPlate(),
                            ParkingSessionStatus.OPEN);
            if (best.isPresent()) {
                ParkingSession open = best.get();
                if (open.getEntryTime().isBefore(record.getCapturedAt())) {
                    open.closeWithExit(
                            record.getCapturedAt(),
                            record.getLaneId(),
                            record.getLaneName(),
                            record.getId(),
                            record.getEventImage());
                    return ParkingFlowResult.exitMatched(ParkingSessionView.from(sessions.save(open)));
                }
            }
            return ParkingFlowResult.exitUnmatched();
        }
        return ParkingFlowResult.skipped();
    }

    /**
     * 作废流水（OPEN 或 CLOSED）。联动将关联的入场/出场识别记录置为 voided。
     */
    @Transactional
    public ParkingSessionView voidSession(UUID sessionId) {
        ParkingSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (session.getStatus() != ParkingSessionStatus.VOIDED) {
            session.markVoided();
            sessions.save(session);
            markRecognitionVoided(session.getEntryRecognitionId());
            markRecognitionVoided(session.getExitRecognitionId());
        }
        return ParkingSessionView.from(session);
    }

    private void markRecognitionVoided(UUID recordId) {
        if (recordId == null) {
            return;
        }
        recognitionRecords.findById(recordId).ifPresent(record -> {
            record.setVoided(true);
            recognitionRecords.save(record);
        });
    }

    private Specification<ParkingSession> buildSpec(UUID lotId, String keyword, ParkingSessionStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (lotId != null) {
                predicates.add(cb.equal(root.get("lotId"), lotId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("plateNumber")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("lotName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("entryLaneName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("exitLaneName"), "")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean isEntrance(String direction) {
        if (direction == null) {
            return false;
        }
        String upper = direction.trim().toUpperCase();
        return "IN".equals(upper) || "ENTRANCE".equals(upper) || "1".equals(upper);
    }

    private boolean isExit(String direction) {
        if (direction == null) {
            return false;
        }
        String upper = direction.trim().toUpperCase();
        return "OUT".equals(upper) || "EXIT".equals(upper) || "2".equals(upper);
    }
}
