package com.freepark.local.device.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 设备心跳登记中心（进程内存）：
 * <ul>
 *   <li>每次 poll/push 都刷新内存「最近心跳」，在线/离线判定走内存值，秒级准确；</li>
 *   <li>lastPollAt 落库做节流：同一设备 60 秒内最多写一次，避免臻识老款等高频轮询
 *       （约 1s/次）反复 UPDATE 损耗数据库性能；</li>
 *   <li>进程重启后内存为空，查询侧回退到落库的 lastPollAt。</li>
 * </ul>
 * key 统一为设备 code（忽略大小写），正式设备与自动发现设备共用同一物理设备的心跳。
 */
@Component
public class DeviceHeartbeatTracker {

    /** lastPollAt 落库节流：同一设备两次写库至少间隔 60 秒。 */
    public static final Duration PERSIST_INTERVAL = Duration.ofSeconds(60);

    /** 在线判定超时：超过此时长未收到任何轮询/推送即视为离线。 */
    public static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(30);

    /** code(小写) -> 最近一次心跳时间（内存，不落库）。 */
    private final ConcurrentHashMap<String, Instant> lastSeen = new ConcurrentHashMap<>();

    /** code(小写) -> 最近一次 lastPollAt 落库时间。 */
    private final ConcurrentHashMap<String, Instant> lastPersistedAt = new ConcurrentHashMap<>();

    /**
     * 记录一次心跳：
     * 始终刷新内存最近心跳；返回 true 表示已满节流周期，需要调用方将 lastPollAt 落库。
     */
    public boolean record(String code, Instant at) {
        String key = key(code);
        lastSeen.put(key, at);
        Instant lastPersisted = lastPersistedAt.get(key);
        if (lastPersisted == null
                || Duration.between(lastPersisted, at).compareTo(PERSIST_INTERVAL) >= 0) {
            lastPersistedAt.put(key, at);
            return true;
        }
        return false;
    }

    /**
     * 设备最近心跳（查询侧使用）：内存记录优先（实时）；
     * 进程重启后尚未再次心跳的设备回退到落库值。
     */
    public Instant resolveLastPollAt(String code, Instant persisted) {
        Instant seen = lastSeen.get(key(code));
        return seen != null ? seen : persisted;
    }

    /** 根据心跳新鲜度判定在线：最近心跳在 ONLINE_TIMEOUT 内视为在线。 */
    public boolean isOnline(Instant lastPollAt) {
        return lastPollAt != null
                && Duration.between(lastPollAt, Instant.now()).compareTo(ONLINE_TIMEOUT) < 0;
    }

    /** 设备删除时清除心跳记录，避免 code 被复用后旧心跳误判在线。 */
    public void forget(String code) {
        String key = key(code);
        lastSeen.remove(key);
        lastPersistedAt.remove(key);
    }

    private static String key(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }
}
