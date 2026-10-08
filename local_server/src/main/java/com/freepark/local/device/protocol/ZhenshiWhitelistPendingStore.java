package com.freepark.local.device.protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 臻识机内白名单待下发队列。
 *
 * <p>HTTP 推送协议 3.7.5 只有增删捎带；多数机型不回调 {@code white_list_replay_operate}。
 * 因此同一操作在心跳/识别应答中连续捎带 {@link #DELIVERIES_BEFORE_DONE} 次后再清掉，
 * 避免「下发一次即丢弃」时相机偶发忽略导致名单永不变化。
 *
 * <p>全量重同步：先清空再增加；drain 优先发删除。
 */
@Component
public class ZhenshiWhitelistPendingStore {

    /** 协议约定：删除且车牌为空 = 清空机内全部白名单。 */
    public static final String CLEAR_ALL_KEY = "";

    /** 同一操作连续下发次数达到该值后视为完成（无回执机型）。 */
    public static final int DELIVERIES_BEFORE_DONE = 3;

    private final AtomicLong msgIds = new AtomicLong();
    private final ConcurrentHashMap<UUID, LinkedHashMap<String, PendingOp>> byDevice = new ConcurrentHashMap<>();

    public void upsert(UUID deviceId, String plate, String enableTime, String overdueTime) {
        put(deviceId, new PendingOp(ZhenshiWhitelistBatch.ADD, plate, enableTime, overdueTime, new AtomicInteger()));
    }

    public void remove(UUID deviceId, String plate) {
        put(deviceId, new PendingOp(ZhenshiWhitelistBatch.DELETE, plate, null, null, new AtomicInteger()));
    }

    /** 该设备是否已有该车牌的待下发操作。 */
    public boolean hasPending(UUID deviceId, String plate) {
        if (deviceId == null) {
            return false;
        }
        LinkedHashMap<String, PendingOp> map = byDevice.get(deviceId);
        if (map == null || map.isEmpty()) {
            return false;
        }
        return map.containsKey(plateKey(plate));
    }

    /**
     * 是否已有与目标完全一致的待下发增加（用于对账去重，避免无意义刷队列）。
     */
    public boolean hasIdenticalAdd(UUID deviceId, String plate, String enableTime, String overdueTime) {
        if (deviceId == null || plate == null || plate.isBlank()) {
            return false;
        }
        LinkedHashMap<String, PendingOp> map = byDevice.get(deviceId);
        if (map == null) {
            return false;
        }
        PendingOp op = map.get(plateKey(plate));
        return op != null
                && op.operateType() == ZhenshiWhitelistBatch.ADD
                && Objects.equals(op.enableTime(), enableTime)
                && Objects.equals(op.overdueTime(), overdueTime);
    }

    /** 是否已有该车牌的删除待下发。 */
    public boolean hasDelete(UUID deviceId, String plate) {
        if (deviceId == null || plate == null) {
            return false;
        }
        LinkedHashMap<String, PendingOp> map = byDevice.get(deviceId);
        if (map == null) {
            return false;
        }
        PendingOp op = map.get(plateKey(plate));
        return op != null && op.operateType() == ZhenshiWhitelistBatch.DELETE;
    }

    /**
     * 替换该设备队列：先清空机内全部名单，再按序写入给定车牌（一键全量重同步）。
     */
    public void replaceWithClearThenAdds(UUID deviceId, List<ZhenshiWhitelistBatch.Item> adds) {
        if (deviceId == null) {
            return;
        }
        LinkedHashMap<String, PendingOp> next = new LinkedHashMap<>();
        next.put(CLEAR_ALL_KEY, new PendingOp(
                ZhenshiWhitelistBatch.DELETE, CLEAR_ALL_KEY, null, null, new AtomicInteger()));
        if (adds != null) {
            for (ZhenshiWhitelistBatch.Item item : adds) {
                if (item == null || item.plate() == null || item.plate().isBlank()) {
                    continue;
                }
                String key = item.plate().trim().toLowerCase(Locale.ROOT);
                next.put(key, new PendingOp(
                        ZhenshiWhitelistBatch.ADD,
                        item.plate().trim(),
                        item.enableTime(),
                        item.overdueTime(),
                        new AtomicInteger()));
            }
        }
        byDevice.put(deviceId, next);
    }

    /**
     * 窥视同一操作类型最多 {@link ZhenshiWhitelistBatch#MAX_ITEMS} 条，不增加下发计数、不删除。
     */
    public ZhenshiWhitelistBatch snapshot(UUID deviceId) {
        return take(deviceId, false);
    }

    /**
     * 取出同一操作类型最多 {@link ZhenshiWhitelistBatch#MAX_ITEMS} 条并计入一次下发；
     * 达到 {@link #DELIVERIES_BEFORE_DONE} 次后从队列移除。删除优先。
     */
    public ZhenshiWhitelistBatch drain(UUID deviceId) {
        return take(deviceId, true);
    }

    private ZhenshiWhitelistBatch take(UUID deviceId, boolean countDelivery) {
        if (deviceId == null) {
            return null;
        }
        List<PendingOp> taken = new ArrayList<>(ZhenshiWhitelistBatch.MAX_ITEMS);
        byDevice.compute(deviceId, (id, map) -> {
            if (map == null || map.isEmpty()) {
                return map;
            }
            LinkedHashMap<String, PendingOp> next = new LinkedHashMap<>(map);
            int type = preferredOperateType(map);
            for (PendingOp op : map.values()) {
                if (taken.size() >= ZhenshiWhitelistBatch.MAX_ITEMS) {
                    break;
                }
                if (op.operateType() != type) {
                    continue;
                }
                taken.add(op);
                if (countDelivery) {
                    int delivered = op.deliveries().incrementAndGet();
                    if (delivered >= DELIVERIES_BEFORE_DONE) {
                        next.remove(plateKey(op.plate()));
                    }
                }
            }
            if (!countDelivery) {
                return map;
            }
            return next.isEmpty() ? null : next;
        });
        if (taken.isEmpty()) {
            return null;
        }
        List<ZhenshiWhitelistBatch.Item> items = taken.stream()
                .map(op -> new ZhenshiWhitelistBatch.Item(op.plate(), op.enableTime(), op.overdueTime()))
                .toList();
        return new ZhenshiWhitelistBatch(
                taken.getFirst().operateType(),
                msgIds.incrementAndGet(),
                null,
                items);
    }

    private static int preferredOperateType(LinkedHashMap<String, PendingOp> map) {
        for (PendingOp op : map.values()) {
            if (op.operateType() == ZhenshiWhitelistBatch.DELETE) {
                return ZhenshiWhitelistBatch.DELETE;
            }
        }
        return ZhenshiWhitelistBatch.ADD;
    }

    private void put(UUID deviceId, PendingOp op) {
        if (deviceId == null || op.plate() == null) {
            return;
        }
        if (op.plate().isBlank() && op.operateType() != ZhenshiWhitelistBatch.DELETE) {
            return;
        }
        if (op.plate().isBlank() && op.operateType() == ZhenshiWhitelistBatch.DELETE) {
            byDevice.compute(deviceId, (id, map) -> {
                LinkedHashMap<String, PendingOp> next = map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
                next.put(CLEAR_ALL_KEY, new PendingOp(
                        ZhenshiWhitelistBatch.DELETE, CLEAR_ALL_KEY, null, null, new AtomicInteger()));
                return next;
            });
            return;
        }
        if (op.plate().isBlank()) {
            return;
        }
        String key = plateKey(op.plate());
        byDevice.compute(deviceId, (id, map) -> {
            LinkedHashMap<String, PendingOp> next = map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
            PendingOp existing = next.get(key);
            // 内容相同且仍在重试中：保留已有下发计数，避免对账把计数清零导致永远发不完
            if (existing != null
                    && existing.operateType() == op.operateType()
                    && Objects.equals(existing.enableTime(), op.enableTime())
                    && Objects.equals(existing.overdueTime(), op.overdueTime())) {
                return next;
            }
            next.put(key, op);
            return next;
        });
    }

    private static String plateKey(String plate) {
        if (plate == null || plate.isBlank()) {
            return CLEAR_ALL_KEY;
        }
        return plate.trim().toLowerCase(Locale.ROOT);
    }

    private record PendingOp(
            int operateType,
            String plate,
            String enableTime,
            String overdueTime,
            AtomicInteger deliveries) {
    }
}
