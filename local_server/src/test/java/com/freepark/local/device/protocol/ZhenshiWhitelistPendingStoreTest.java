package com.freepark.local.device.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ZhenshiWhitelistPendingStoreTest {

    private final ZhenshiWhitelistPendingStore store = new ZhenshiWhitelistPendingStore();

    @Test
    void lastWriteForSamePlateWins() {
        UUID device = UUID.randomUUID();
        store.upsert(device, "京A12345", "t1", "t2");
        store.upsert(device, "京A12345", "t3", "t4");

        ZhenshiWhitelistBatch batch = store.drain(device);
        assertEquals(ZhenshiWhitelistBatch.ADD, batch.operateType());
        assertEquals(1, batch.items().size());
        assertEquals("京A12345", batch.items().getFirst().plate());
        assertEquals("t3", batch.items().getFirst().enableTime());
        assertEquals("t4", batch.items().getFirst().overdueTime());
    }

    @Test
    void upsertThenRemoveSamePlateDrainsAsDelete() {
        UUID device = UUID.randomUUID();
        store.upsert(device, "京A12345", "t1", "t2");
        store.remove(device, "京A12345");

        ZhenshiWhitelistBatch batch = store.drain(device);
        assertEquals(ZhenshiWhitelistBatch.DELETE, batch.operateType());
        assertEquals("京A12345", batch.items().getFirst().plate());
    }

    @Test
    void snapshotDoesNotDrop() {
        UUID device = UUID.randomUUID();
        store.upsert(device, "京A12345", "t1", "t2");

        ZhenshiWhitelistBatch first = store.snapshot(device);
        ZhenshiWhitelistBatch second = store.snapshot(device);
        assertEquals("京A12345", first.items().getFirst().plate());
        assertEquals("京A12345", second.items().getFirst().plate());
        assertEquals("京A12345", store.drain(device).items().getFirst().plate());
    }

    @Test
    void replaceWithClearThenAddsDrainsClearFirst() {
        UUID device = UUID.randomUUID();
        store.upsert(device, "旧A00001", "s", "e");

        store.replaceWithClearThenAdds(device, List.of(
                new ZhenshiWhitelistBatch.Item("京A12345", "t1", "t2"),
                new ZhenshiWhitelistBatch.Item("京B67890", null, null)));

        ZhenshiWhitelistBatch clear = store.drain(device);
        assertEquals(ZhenshiWhitelistBatch.DELETE, clear.operateType());
        assertEquals(1, clear.items().size());
        assertEquals("", clear.items().getFirst().plate());

        // clear 还需再下发到 DELIVERIES_BEFORE_DONE
        for (int i = 1; i < ZhenshiWhitelistPendingStore.DELIVERIES_BEFORE_DONE; i++) {
            assertEquals(ZhenshiWhitelistBatch.DELETE, store.drain(device).operateType());
        }

        ZhenshiWhitelistBatch adds = store.drain(device);
        assertEquals(ZhenshiWhitelistBatch.ADD, adds.operateType());
        assertEquals(2, adds.items().size());
        assertEquals("京A12345", adds.items().getFirst().plate());
    }

    @Test
    void drainPrefersDeleteAndRequiresMultipleDeliveries() {
        UUID device = UUID.randomUUID();
        store.upsert(device, "京A00001", "s", "e");
        store.remove(device, "苏B00001");

        for (int i = 0; i < ZhenshiWhitelistPendingStore.DELIVERIES_BEFORE_DONE; i++) {
            ZhenshiWhitelistBatch batch = store.drain(device);
            assertEquals(ZhenshiWhitelistBatch.DELETE, batch.operateType());
            assertEquals("苏B00001", batch.items().getFirst().plate());
        }

        for (int i = 0; i < ZhenshiWhitelistPendingStore.DELIVERIES_BEFORE_DONE; i++) {
            ZhenshiWhitelistBatch batch = store.drain(device);
            assertEquals(ZhenshiWhitelistBatch.ADD, batch.operateType());
            assertEquals("京A00001", batch.items().getFirst().plate());
        }
        assertNull(store.drain(device));
    }

    @Test
    void identicalUpsertDoesNotResetDeliveryCount() {
        UUID device = UUID.randomUUID();
        store.upsert(device, "粤R888G8", "a", "b");
        store.drain(device);
        store.drain(device);
        assertTrue(store.hasIdenticalAdd(device, "粤R888G8", "a", "b"));

        store.upsert(device, "粤R888G8", "a", "b");
        store.drain(device);
        assertFalse(store.hasPending(device, "粤R888G8"));
    }

    @Test
    void hasPendingTracksPlate() {
        UUID device = UUID.randomUUID();
        store.remove(device, "粤R888G8");
        assertTrue(store.hasPending(device, "粤R888G8"));
        assertTrue(store.hasDelete(device, "粤R888G8"));
        for (int i = 0; i < ZhenshiWhitelistPendingStore.DELIVERIES_BEFORE_DONE; i++) {
            store.drain(device);
        }
        assertFalse(store.hasPending(device, "粤R888G8"));
    }
}
