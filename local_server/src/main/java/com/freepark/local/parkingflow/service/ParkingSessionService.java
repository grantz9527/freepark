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
     * - ENTRANCE → 若该车仍有多余的在场流水则先作废，再生成新的在场流水；
     * - EXIT → 匹配同车场同车牌、入场时间在出场之前的最近在场流水并关闭；
     *   无在场流水时兜底：将离场数据回写到该车最近一条未作废流水（不限状态）
     * - 其余 → skipped
     */
    @Transactional
    public ParkingFlowResult applyRecognition(RecognitionRecord record) {
        if (record == null || record.getLotId() == null || record.getCapturedAt() == null) {
            return ParkingFlowResult.skipped();
        }
        if (isEntrance(record.getDirection())) {
            voidStaleOpenSessions(record.getLotId(), record.getPlate());
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
            Optional<ParkingSession> open = sessions
                    .findFirstByLotIdAndPlateNumberIgnoreCaseAndStatusOrderByEntryTimeDesc(
                            record.getLotId(),
                            record.getPlate(),
                            ParkingSessionStatus.OPEN);
            if (open.isPresent() && open.get().getEntryTime().isBefore(record.getCapturedAt())) {
                return exitMatched(open.get(), record);
            }
            // 匹配不到入场数据（无在场流水）：回写该车最近一条未作废流水的离场数据
            if (open.isEmpty()) {
                Optional<ParkingSession> latest = sessions
                        .findFirstByLotIdAndPlateNumberIgnoreCaseAndStatusNotOrderByEntryTimeDesc(
                                record.getLotId(),
                                record.getPlate(),
                                ParkingSessionStatus.VOIDED);
                if (latest.isPresent() && latest.get().getEntryTime().isBefore(record.getCapturedAt())) {
                    return exitMatched(latest.get(), record);
                }
            }
            return ParkingFlowResult.exitUnmatched();
        }
        return ParkingFlowResult.skipped();
    }

    /** 将离场数据写入流水并关闭，返回匹配结果。 */
    private ParkingFlowResult exitMatched(ParkingSession session, RecognitionRecord record) {
        session.closeWithExit(
                record.getCapturedAt(),
                record.getLaneId(),
                record.getLaneName(),
                record.getId(),
                record.getEventImage());
        return ParkingFlowResult.exitMatched(ParkingSessionView.from(sessions.save(session)));
    }

    /**
     * 入场时若该车（同车场同车牌）仍有在场流水，说明是上次出场未识别或重复入场，
     * 先将其作废（含关联识别记录），保证同一辆车只保留一条新的在场流水。
     */
    private void voidStaleOpenSessions(UUID lotId, String plate) {
        if (lotId == null || plate == null || plate.trim().isEmpty()) {
            return;
        }
        List<ParkingSession> staleOpens = sessions
                .findAllByLotIdAndPlateNumberIgnoreCaseAndStatus(
                        lotId, plate.trim(), ParkingSessionStatus.OPEN);
        for (ParkingSession stale : staleOpens) {
            stale.markVoided();
            sessions.save(stale);
            markRecognitionVoided(stale.getEntryRecognitionId());
            markRecognitionVoided(stale.getExitRecognitionId());
        }
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
