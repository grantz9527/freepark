package com.freepark.local.parkingflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.freepark.local.domain.ParkingSessionRepository;
import com.freepark.local.domain.ParkingSessionStatus;
import com.freepark.local.parkingflow.service.LotOccupancyTracker;

class LotOccupancyTrackerTest {

    private ParkingSessionRepository sessions;
    private LotOccupancyTracker tracker;
    private UUID lotId;

    @BeforeEach
    void setUp() {
        sessions = mock(ParkingSessionRepository.class);
        tracker = new LotOccupancyTracker(sessions);
        lotId = UUID.randomUUID();
    }

    @Test
    void openCountLoadsOnceThenUsesCache() {
        when(sessions.countByLotIdAndStatus(lotId, ParkingSessionStatus.OPEN)).thenReturn(3L);

        assertEquals(3L, tracker.openCount(lotId));
        tracker.onChanged(lotId, null, ParkingSessionStatus.OPEN);
        assertEquals(4L, tracker.openCount(lotId));

        verify(sessions, times(1)).countByLotIdAndStatus(lotId, ParkingSessionStatus.OPEN);
    }

    @Test
    void deltaBeforeCacheLoadDoesNotPoisonCount() {
        tracker.onChanged(lotId, null, ParkingSessionStatus.OPEN);
        when(sessions.countByLotIdAndStatus(lotId, ParkingSessionStatus.OPEN)).thenReturn(5L);

        assertEquals(5L, tracker.openCount(lotId));
    }

    @Test
    void closeAfterLoadDecrements() {
        when(sessions.countByLotIdAndStatus(lotId, ParkingSessionStatus.OPEN)).thenReturn(2L);
        assertEquals(2L, tracker.openCount(lotId));

        tracker.onChanged(lotId, ParkingSessionStatus.OPEN, ParkingSessionStatus.CLOSED);
        assertEquals(1L, tracker.openCount(lotId));
    }

    @Test
    void reconcileReplacesCachedCounts() {
        when(sessions.countByLotIdAndStatus(lotId, ParkingSessionStatus.OPEN)).thenReturn(9L);
        assertEquals(9L, tracker.openCount(lotId));

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] {lotId, 4L});
        when(sessions.countGroupedByLotIdAndStatus(ParkingSessionStatus.OPEN)).thenReturn(rows);
        tracker.reconcile();

        assertEquals(4L, tracker.openCount(lotId));
        verify(sessions, times(1)).countByLotIdAndStatus(lotId, ParkingSessionStatus.OPEN);
    }

    @Test
    void openDeltaOnlyChangesWhenCrossingOpen() {
        assertEquals(1, LotOccupancyTracker.openDelta(null, ParkingSessionStatus.OPEN));
        assertEquals(-1, LotOccupancyTracker.openDelta(ParkingSessionStatus.OPEN, ParkingSessionStatus.CLOSED));
        assertEquals(-1, LotOccupancyTracker.openDelta(ParkingSessionStatus.OPEN, ParkingSessionStatus.VOIDED));
        assertEquals(0, LotOccupancyTracker.openDelta(ParkingSessionStatus.CLOSED, ParkingSessionStatus.VOIDED));
        assertEquals(0, LotOccupancyTracker.openDelta(ParkingSessionStatus.OPEN, ParkingSessionStatus.OPEN));
    }
}
