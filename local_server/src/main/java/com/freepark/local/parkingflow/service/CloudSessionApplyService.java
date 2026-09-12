package com.freepark.local.parkingflow.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.ParkingSession;
import com.freepark.local.domain.ParkingSessionRepository;
import com.freepark.local.domain.ParkingSessionStatus;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.RecognitionRecordRepository;

/**
 * 应用云端下发的停车流水快照 {@code edge.parking.session/1}（origin=CLOUD）：
 * 按边缘 UUID 或云端主键定位本地行。若本地已经出场/作废而云端仍是在场，保留本地关场
 * 并保持待同步，避免冲回 OPEN；其余情况覆盖后清除待同步标记。
 */
@Service
public class CloudSessionApplyService {

    private static final Logger log = LoggerFactory.getLogger(CloudSessionApplyService.class);

    public static final String SCHEMA = "edge.parking.session/1";
    public static final String ORIGIN_CLOUD = "CLOUD";

    private final ParkingSessionRepository sessions;
    private final ParkingLotRepository lots;
    private final RecognitionRecordRepository recognitionRecords;

    public CloudSessionApplyService(
            ParkingSessionRepository sessions,
            ParkingLotRepository lots,
            RecognitionRecordRepository recognitionRecords) {
        this.sessions = sessions;
        this.lots = lots;
        this.recognitionRecords = recognitionRecords;
    }

    @Transactional
    public void apply(String nodeCode, Long cloudId, Long cloudRevision, String sessionId,
                      String lotCode, String lotName, String plate, String plateColor,
                      String statusText, Instant entryTime, Instant exitTime,
                      String entryLaneName, String exitLaneName) {
        if (plate == null || plate.isBlank() || statusText == null || entryTime == null) {
            log.warn("云端流水快照缺少车牌/状态/入场时间，忽略");
            return;
        }
        ParkingSessionStatus status;
        try {
            status = ParkingSessionStatus.valueOf(statusText);
        } catch (IllegalArgumentException ex) {
            log.warn("云端流水快照状态非法 status={}，忽略", statusText);
            return;
        }
        ParkingLot lot = lotCode == null || lotCode.isBlank()
                ? null
                : lots.findByCode(lotCode.trim()).orElse(null);
        if (lot == null) {
            log.warn("云端流水快照车场不存在 lotCode={}，忽略", lotCode);
            return;
        }
        ParkingSession session = locate(sessionId, cloudId);
        long incoming = cloudRevision == null ? 0L : cloudRevision;
        if (session != null
                && session.getCloudRevision() != null
                && session.getCloudRevision() > incoming) {
            log.info("忽略过期云端流水快照 localId={} cloudRevision={} incoming={}",
                    session.getId(), session.getCloudRevision(), incoming);
            return;
        }
        boolean created = false;
        if (session == null) {
            session = new ParkingSession(
                    lot.getId(),
                    firstNonBlank(lotName, lot.getName()),
                    plate,
                    parseColor(plateColor),
                    entryTime,
                    null,
                    entryLaneName,
                    null,
                    null);
            created = true;
        }
        ParkingSessionStatus previous = session.getStatus();
        boolean keepLocalLifecycle = !created
                && lifecycleRank(previous) > lifecycleRank(status);
        session.setLotId(lot.getId());
        session.setLotName(firstNonBlank(lotName, lot.getName()));
        session.setPlateNumber(plate);
        PlateColor color = parseColor(plateColor);
        if (color != null) {
            session.setPlateColor(color);
        }
        session.setEntryTime(entryTime);
        if (entryLaneName != null) {
            session.setEntryLaneName(entryLaneName);
        }
        if (keepLocalLifecycle) {
            // 本地已经出场/作废，云端快照仍是在场：保留本地关场，只收下云端改过的车牌/入场，
            // 并保持待同步，用新的 cloudRevision 把出场补报上去。
            if (cloudId != null) {
                session.setCloudId(cloudId);
            }
            session.setCloudRevision(incoming);
            session.setSyncPending(Boolean.TRUE);
            ParkingSession saved = sessions.save(session);
            log.info("已合并云端停车流水（保留本地 {}） localId={} cloudId={} plate={} node={}",
                    previous, saved.getId(), cloudId, saved.getPlateNumber(), nodeCode);
            return;
        }
        session.setExitTime(exitTime);
        if (exitLaneName != null) {
            session.setExitLaneName(exitLaneName);
        }
        session.setStatus(status);
        if (cloudId != null) {
            session.setCloudId(cloudId);
        }
        session.setCloudRevision(incoming);
        session.setSyncPending(Boolean.FALSE);
        ParkingSession saved = sessions.save(session);
        if (status == ParkingSessionStatus.VOIDED && previous != ParkingSessionStatus.VOIDED) {
            markRecognitionVoided(saved.getEntryRecognitionId());
            markRecognitionVoided(saved.getExitRecognitionId());
        }
        log.info("已应用云端停车流水 localId={} cloudId={} status={} plate={} created={} node={}",
                saved.getId(), cloudId, status, saved.getPlateNumber(), created, nodeCode);
    }

    private ParkingSession locate(String sessionId, Long cloudId) {
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                UUID id = UUID.fromString(sessionId.trim());
                ParkingSession byId = sessions.findById(id).orElse(null);
                if (byId != null) {
                    return byId;
                }
            } catch (IllegalArgumentException ignored) {
                // 非 UUID 时走云端主键
            }
        }
        if (cloudId != null) {
            return sessions.findByCloudId(cloudId).orElse(null);
        }
        return null;
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

    private static PlateColor parseColor(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return PlateColor.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** OPEN < CLOSED < VOIDED：云端在场快照不得把本地已出场/作废冲回去。 */
    private static int lifecycleRank(ParkingSessionStatus status) {
        if (status == ParkingSessionStatus.VOIDED) {
            return 2;
        }
        if (status == ParkingSessionStatus.CLOSED) {
            return 1;
        }
        return 0;
    }

    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return fallback;
    }
}
