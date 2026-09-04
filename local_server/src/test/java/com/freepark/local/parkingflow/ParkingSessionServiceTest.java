package com.freepark.local.parkingflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.domain.ParkingSession;
import com.freepark.local.domain.ParkingSessionRepository;
import com.freepark.local.domain.ParkingSessionStatus;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.RecognitionEventType;
import com.freepark.local.domain.RecognitionRecord;
import com.freepark.local.parkingflow.service.ParkingSessionService;
import com.freepark.local.recognition.dto.ParkingFlowResult;

/**
 * 停车流水联动：离场匹配不到入场时，兜底回写该车最近一条未作废流水的离场数据。
 */
@SpringBootTest
@Transactional
class ParkingSessionServiceTest {

    @Autowired
    private ParkingSessionService parkingSessionService;

    @Autowired
    private ParkingSessionRepository sessions;

    @Test
    void exitWithoutOpenSessionFallsBackToMostRecentRecord() {
        UUID lotId = UUID.randomUUID();
        String plate = "SU88888";
        Instant exitTime = Instant.parse("2026-08-03T18:30:00Z");

        // 该车只有一条已关闭的历史流水，无在场流水
        ParkingSession closed = saveClosedSession(lotId, plate,
                Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T09:00:00Z"));

        // 离场车牌大小写不一致，仍应兜底命中
        ParkingFlowResult result = parkingSessionService.applyRecognition(
                exitRecord(lotId, "su88888", exitTime));

        assertEquals("exit_matched", result.kind());
        assertNotNull(result.session());
        assertEquals(ParkingSessionStatus.CLOSED, result.session().status());
        assertEquals(exitTime, result.session().exitTime());
        assertEquals(closed.getId(), result.session().id());
    }

    @Test
    void exitWhenOnlyVoidedRecordExistsStaysUnmatched() {
        UUID lotId = UUID.randomUUID();
        String plate = "SU99999";

        ParkingSession voided = sessions.save(entrySession(lotId, plate,
                Instant.parse("2026-08-01T08:00:00Z")));
        voided.markVoided();
        sessions.save(voided);

        ParkingFlowResult result = parkingSessionService.applyRecognition(
                exitRecord(lotId, plate, Instant.parse("2026-08-03T18:30:00Z")));

        assertEquals("exit_unmatched", result.kind());
        assertEquals(ParkingSessionStatus.VOIDED, voided.getStatus());
    }

    @Test
    void openSessionIsStillMatchedBeforeFallingBack() {
        UUID lotId = UUID.randomUUID();
        String plate = "SU77777";
        Instant openEntry = Instant.parse("2026-08-02T10:00:00Z");
        Instant exitTime = Instant.parse("2026-08-03T18:30:00Z");

        ParkingSession olderClosed = saveClosedSession(lotId, plate,
                Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T09:00:00Z"));
        ParkingSession open = sessions.save(entrySession(lotId, plate, openEntry));

        ParkingFlowResult result = parkingSessionService.applyRecognition(
                exitRecord(lotId, plate, exitTime));

        assertEquals("exit_matched", result.kind());
        assertEquals(open.getId(), result.session().id());
        assertEquals(exitTime, result.session().exitTime());
        // 历史流水不受影响
        assertEquals(Instant.parse("2026-08-01T09:00:00Z"), olderClosed.getExitTime());
    }

    @Test
    void secondExitWithoutOpenUpdatesFirstFlowEntry() {
        // S1 第一次入场 → 生成在场流水 F1；L1 离场 → F1 关闭；L2 离场(无在场) → 兜底更新 F1 离场数据
        UUID lotId = UUID.randomUUID();
        String plate = "SU66666";
        Instant s1 = Instant.parse("2026-08-03T08:00:00Z");
        Instant l1 = Instant.parse("2026-08-03T10:00:00Z");
        Instant l2 = Instant.parse("2026-08-03T18:00:00Z");

        ParkingFlowResult entry = parkingSessionService.applyRecognition(entranceRecord(lotId, plate, s1));
        assertEquals("entry", entry.kind());
        UUID flowId = entry.session().id();
        assertEquals(ParkingSessionStatus.OPEN, entry.session().status());

        ParkingFlowResult firstExit = parkingSessionService.applyRecognition(exitRecord(lotId, plate, l1));
        assertEquals("exit_matched", firstExit.kind());
        assertEquals(l1, firstExit.session().exitTime());
        assertEquals(ParkingSessionStatus.CLOSED, firstExit.session().status());

        // 第二次离场：无在场流水 → 应把离场数据更新到 F1（最近一条流水）上
        ParkingFlowResult secondExit = parkingSessionService.applyRecognition(exitRecord(lotId, plate, l2));
        assertEquals("exit_matched", secondExit.kind());
        assertEquals(flowId, secondExit.session().id());
        assertEquals(ParkingSessionStatus.CLOSED, secondExit.session().status());
        assertEquals(l2, secondExit.session().exitTime());
    }

    @Test
    void entryVoidsPreviousOpenSessions() {
        // 该车已有一次入场(F1 在场) + 一条已关闭的历史流水；再次入场时 F1 应被作废，只保留新的在场流水
        UUID lotId = UUID.randomUUID();
        String plate = "SU55555";
        Instant s1 = Instant.parse("2026-08-03T08:00:00Z");
        Instant s2 = Instant.parse("2026-08-03T20:00:00Z");

        ParkingSession olderClosed = saveClosedSession(lotId, plate,
                Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T09:00:00Z"));

        ParkingFlowResult firstEntry = parkingSessionService.applyRecognition(entranceRecord(lotId, plate, s1));
        assertEquals("entry", firstEntry.kind());
        UUID staleFlowId = firstEntry.session().id();
        assertEquals(ParkingSessionStatus.OPEN, firstEntry.session().status());

        // 再次入场：旧的在场流水应被作废，新生成一条在场流水
        ParkingFlowResult secondEntry = parkingSessionService.applyRecognition(entranceRecord(lotId, plate, s2));
        assertEquals("entry", secondEntry.kind());
        assertNotNull(secondEntry.session());
        assertEquals(ParkingSessionStatus.OPEN, secondEntry.session().status());
        assertNotEquals(staleFlowId, secondEntry.session().id());

        ParkingSession stale = sessions.findById(staleFlowId).orElseThrow();
        assertEquals(ParkingSessionStatus.VOIDED, stale.getStatus());
        // 已关闭的历史流水不受影响
        assertEquals(ParkingSessionStatus.CLOSED,
                sessions.findById(olderClosed.getId()).orElseThrow().getStatus());
    }

    private ParkingSession saveClosedSession(UUID lotId, String plate, Instant entry, Instant exit) {
        ParkingSession session = sessions.save(entrySession(lotId, plate, entry));
        session.closeWithExit(exit, null, null, null, null);
        return sessions.save(session);
    }

    private ParkingSession entrySession(UUID lotId, String plate, Instant entryTime) {
        return new ParkingSession(lotId, "Flow Test Lot", plate, PlateColor.BLUE,
                entryTime, null, null, null, null);
    }

    private RecognitionRecord entranceRecord(UUID lotId, String plate, Instant capturedAt) {
        return new RecognitionRecord(plate, PlateColor.BLUE, null,
                RecognitionEventType.DEVICE, "ENTRANCE", capturedAt,
                lotId, "Flow Test Lot", null, null, null, false, null);
    }

    private RecognitionRecord exitRecord(UUID lotId, String plate, Instant capturedAt) {
        return new RecognitionRecord(plate, PlateColor.BLUE, null,
                RecognitionEventType.DEVICE, "EXIT", capturedAt,
                lotId, "Flow Test Lot", null, null, null, false, null);
    }
}
