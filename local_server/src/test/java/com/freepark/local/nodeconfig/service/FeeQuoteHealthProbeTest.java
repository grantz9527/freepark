package com.freepark.local.nodeconfig.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.freepark.local.domain.LotType;
import com.freepark.local.domain.NodeSettingsRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.ParkingSession;
import com.freepark.local.domain.ParkingSessionRepository;
import com.freepark.local.domain.ParkingSessionStatus;
import com.freepark.local.domain.PlateColor;

class FeeQuoteHealthProbeTest {

    private ParkingSessionRepository sessions;
    private ParkingLotRepository lots;
    private FeeQuoteHealthProbe probe;

    @BeforeEach
    void setUp() {
        sessions = mock(ParkingSessionRepository.class);
        lots = mock(ParkingLotRepository.class);
        probe = new FeeQuoteHealthProbe(
                mock(FeeQuoteClient.class),
                mock(NodeSettingsRepository.class),
                sessions,
                lots);
    }

    @Test
    void pickTargetUsesParkedVehicleWhenPresent() {
        UUID lotId = UUID.randomUUID();
        ParkingLot lot = new ParkingLot("北门", "LOT-N", LotType.PUBLIC, null, 10, true);
        ParkingSession session = new ParkingSession(
                lotId, "北门", "粤A12345", PlateColor.GREEN, Instant.parse("2026-09-14T01:00:00Z"),
                null, null, null, null);
        when(sessions.findTop50ByStatusOrderByEntryTimeDesc(ParkingSessionStatus.OPEN))
                .thenReturn(List.of(session));
        when(lots.findById(lotId)).thenReturn(Optional.of(lot));

        FeeQuoteHealthProbe.ProbeTarget target = probe.pickTarget();

        assertTrue(target.fromParkedSession());
        assertEquals("LOT-N", target.lotCode());
        assertEquals("粤A12345", target.plateNumber());
        assertEquals("GREEN", target.plateColor());
    }

    @Test
    void pickTargetFallsBackToRandomPlateWhenNoParkedVehicle() {
        ParkingLot lot = new ParkingLot("北门", "LOT-N", LotType.PUBLIC, null, 10, true);
        when(sessions.findTop50ByStatusOrderByEntryTimeDesc(ParkingSessionStatus.OPEN)).thenReturn(List.of());
        when(lots.findAll()).thenReturn(List.of(lot));

        FeeQuoteHealthProbe.ProbeTarget target = probe.pickTarget();

        assertFalse(target.fromParkedSession());
        assertEquals("LOT-N", target.lotCode());
        assertEquals(7, target.plateNumber().length());
        assertEquals("BLUE", target.plateColor());
    }

    @Test
    void randomPlateLooksLikeAMainlandPlate() {
        String plate = FeeQuoteHealthProbe.randomPlate();
        assertEquals(7, plate.length());
        assertTrue("京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼".indexOf(plate.charAt(0)) >= 0);
        assertTrue(Character.isLetter(plate.charAt(1)));
    }
}
