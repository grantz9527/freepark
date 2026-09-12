package com.freepark.local.parkingflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.domain.LotType;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.ParkingSession;
import com.freepark.local.domain.ParkingSessionRepository;
import com.freepark.local.domain.ParkingSessionStatus;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.parkingflow.service.CloudSessionApplyService;

@SpringBootTest
@Transactional
class CloudSessionApplyServiceTest {

    @Autowired
    private CloudSessionApplyService applyService;

    @Autowired
    private ParkingLotRepository lots;

    @Autowired
    private ParkingSessionRepository sessions;

    @Test
    void cloudOpenSnapshotDoesNotReopenLocalClosedSession() {
        ParkingLot lot = lots.save(new ParkingLot(
                "Apply Test Lot", "APPLY-" + UUID.randomUUID(), LotType.PUBLIC, null, 10, true));
        Instant entry = Instant.parse("2026-09-12T01:00:00Z");
        Instant exit = Instant.parse("2026-09-12T08:00:00Z");
        ParkingSession local = sessions.save(new ParkingSession(
                lot.getId(), lot.getName(), "浙B11111", PlateColor.BLUE,
                entry, null, "入口1", null, null));
        local.closeWithExit(exit, null, "出口1", null, null);
        local.setCloudId(1001L);
        local.setCloudRevision(2L);
        sessions.save(local);

        applyService.apply(
                "node-001", 1001L, 5L, local.getId().toString(),
                lot.getCode(), lot.getName(), "云A22222", "BLUE",
                "OPEN", entry, null, "入口1", null);

        ParkingSession saved = sessions.findById(local.getId()).orElseThrow();
        assertEquals(ParkingSessionStatus.CLOSED, saved.getStatus());
        assertEquals(exit, saved.getExitTime());
        assertEquals("云A22222", saved.getPlateNumber());
        assertEquals(5L, saved.getCloudRevision());
        assertTrue(Boolean.TRUE.equals(saved.getSyncPending()));
    }

    @Test
    void cloudClosedSnapshotAppliesWhenLocalStillOpen() {
        ParkingLot lot = lots.save(new ParkingLot(
                "Apply Test Lot", "APPLY-" + UUID.randomUUID(), LotType.PUBLIC, null, 10, true));
        Instant entry = Instant.parse("2026-09-12T01:00:00Z");
        Instant exit = Instant.parse("2026-09-12T08:00:00Z");
        ParkingSession local = sessions.save(new ParkingSession(
                lot.getId(), lot.getName(), "浙B33333", PlateColor.BLUE,
                entry, null, "入口1", null, null));
        local.setCloudId(1002L);
        local.setCloudRevision(1L);
        sessions.save(local);

        applyService.apply(
                "node-001", 1002L, 2L, local.getId().toString(),
                lot.getCode(), lot.getName(), "浙B33333", "BLUE",
                "CLOSED", entry, exit, "入口1", "出口1");

        ParkingSession saved = sessions.findById(local.getId()).orElseThrow();
        assertEquals(ParkingSessionStatus.CLOSED, saved.getStatus());
        assertEquals(exit, saved.getExitTime());
        assertEquals(Boolean.FALSE, saved.getSyncPending());
    }
}
