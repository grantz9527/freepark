package com.freepark.local.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.freepark.driver.api.model.VehicleType;

class WhitelistVehicleCameraSyncStatusTest {

    @Test
    void deliveredBecomesExpiredPendingRemoveWhenPastEndTime() {
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        Instant end = Instant.parse("2026-10-01T00:00:00Z");
        Instant activeAt = Instant.parse("2026-09-15T00:00:00Z");
        Instant expiredAt = Instant.parse("2026-10-03T04:00:00Z");

        WhitelistVehicle card = card(VehicleType.MONTHLY, start, end, true);
        card.refreshCameraWhitelistSyncStatus(activeAt);
        assertEquals(CameraWhitelistSyncStatus.PENDING_DELIVER, card.getCameraWhitelistSyncStatus());
        assertTrue(card.markCameraDelivered());
        assertEquals(CameraWhitelistSyncStatus.DELIVERED, card.getCameraWhitelistSyncStatus());

        assertTrue(card.refreshCameraWhitelistSyncStatus(expiredAt));
        assertEquals(CameraWhitelistSyncStatus.EXPIRED_PENDING_REMOVE, card.getCameraWhitelistSyncStatus());
    }

    @Test
    void expiredPendingRemoveBecomesExpiredRemovedAfterMark() {
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        Instant end = Instant.parse("2026-10-01T00:00:00Z");
        Instant expiredAt = Instant.parse("2026-10-03T04:00:00Z");

        WhitelistVehicle card = card(VehicleType.OWNER, start, end, true);
        card.refreshCameraWhitelistSyncStatus(expiredAt);
        assertEquals(CameraWhitelistSyncStatus.EXPIRED_PENDING_REMOVE, card.getCameraWhitelistSyncStatus());

        assertTrue(card.markCameraExpiredRemoved());
        assertEquals(CameraWhitelistSyncStatus.EXPIRED_REMOVED, card.getCameraWhitelistSyncStatus());
        assertFalse(card.refreshCameraWhitelistSyncStatus(expiredAt));
        assertEquals(CameraWhitelistSyncStatus.EXPIRED_REMOVED, card.getCameraWhitelistSyncStatus());
    }

    @Test
    void pendingDeliverBecomesDeliveredOnlyWhileActive() {
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        Instant end = Instant.parse("2026-10-01T00:00:00Z");
        Instant activeAt = Instant.parse("2026-09-15T00:00:00Z");

        WhitelistVehicle card = card(VehicleType.MONTHLY, start, end, true);
        card.refreshCameraWhitelistSyncStatus(activeAt);
        assertEquals(CameraWhitelistSyncStatus.PENDING_DELIVER, card.getCameraWhitelistSyncStatus());
        assertTrue(card.markCameraDelivered());
        assertEquals(CameraWhitelistSyncStatus.DELIVERED, card.getCameraWhitelistSyncStatus());
    }

    @Test
    void computeNextKeepsDeliveredWhileStillActive() {
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        Instant end = Instant.parse("2026-10-01T00:00:00Z");
        Instant activeAt = Instant.parse("2026-09-15T00:00:00Z");

        WhitelistVehicle card = card(VehicleType.MONTHLY, start, end, true);
        card.refreshCameraWhitelistSyncStatus(activeAt);
        card.markCameraDelivered();
        assertEquals(CameraWhitelistSyncStatus.DELIVERED, card.computeNextSyncStatus(activeAt));
        assertEquals(
                CameraWhitelistSyncStatus.EXPIRED_PENDING_REMOVE,
                card.computeNextSyncStatus(Instant.parse("2026-10-03T00:00:00Z")));
    }

    private static WhitelistVehicle card(VehicleType type, Instant start, Instant end, boolean enabled) {
        ParkingLot lot = new ParkingLot("Lot", "L1", LotType.INTERNAL, null, 10, true);
        return new WhitelistVehicle(
                lot, "粤B12345", PlateColor.BLUE, "owner", type, null, null, null, start, end, enabled);
    }
}
