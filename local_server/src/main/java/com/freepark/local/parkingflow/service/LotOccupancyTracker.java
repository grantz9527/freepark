package com.freepark.local.parkingflow.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.freepark.local.domain.ParkingSessionRepository;
import com.freepark.local.domain.ParkingSessionStatus;

import jakarta.annotation.PreDestroy;

/**
 * 车场在场数内存缓存：通行判定热路径只读 AtomicLong，避免每次满位拦截都 COUNT。
 *
 * <p>更新策略：
 * <ul>
 *   <li>流水入场/出场/作废/云端覆盖时按状态差量加减；缓存尚未加载的车场不改，
 *       下次读取时一次 COUNT 回填（避免未加载时 +1 把真实在场数冲成 1）。</li>
 *   <li>定时 GROUP BY 对账，修正漏钩或并发窗口造成的漂移。</li>
 * </ul>
 * 纯定时缓存会让满位边界滞后一整轮，两辆车可能同时挤进最后一个车位，因此不以定时为主。
 */
@Component
public class LotOccupancyTracker {

    private static final Logger log = LoggerFactory.getLogger(LotOccupancyTracker.class);

    static final long RECONCILE_INTERVAL_SECONDS = 30;

    private final ParkingSessionRepository sessions;
    private final ConcurrentHashMap<UUID, AtomicLong> openCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lot-occupancy");
        t.setDaemon(true);
        return t;
    });

    public LotOccupancyTracker(ParkingSessionRepository sessions) {
        this.sessions = sessions;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::reconcileSafely, 0, RECONCILE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("车场在场数缓存已启动（流水状态差量更新，每 {} 秒对账）", RECONCILE_INTERVAL_SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    /** 当前在场流水数；缓存未命中时查一次库并写入。 */
    public long openCount(UUID lotId) {
        if (lotId == null) {
            return 0L;
        }
        AtomicLong cached = openCounts.get(lotId);
        if (cached != null) {
            return Math.max(0L, cached.get());
        }
        long counted = sessions.countByLotIdAndStatus(lotId, ParkingSessionStatus.OPEN);
        AtomicLong created = new AtomicLong(counted);
        AtomicLong raced = openCounts.putIfAbsent(lotId, created);
        return Math.max(0L, raced == null ? counted : raced.get());
    }

    /**
     * 流水状态变化后调用。{@code from == null} 表示新建；{@code to == null} 表示该车场不再持有此流水。
     */
    public void onChanged(UUID lotId, ParkingSessionStatus from, ParkingSessionStatus to) {
        if (lotId == null) {
            return;
        }
        int delta = openDelta(from, to);
        if (delta == 0) {
            return;
        }
        AtomicLong cached = openCounts.get(lotId);
        if (cached == null) {
            return;
        }
        long next = cached.addAndGet(delta);
        if (next < 0) {
            cached.set(0);
        }
    }

    public static int openDelta(ParkingSessionStatus from, ParkingSessionStatus to) {
        boolean wasOpen = from == ParkingSessionStatus.OPEN;
        boolean isOpen = to == ParkingSessionStatus.OPEN;
        if (wasOpen == isOpen) {
            return 0;
        }
        return isOpen ? 1 : -1;
    }

    void reconcileSafely() {
        try {
            reconcile();
        } catch (RuntimeException ex) {
            log.warn("车场在场数对账失败：{}", ex.getMessage());
        }
    }

    public void reconcile() {
        List<Object[]> rows = sessions.countGroupedByLotIdAndStatus(ParkingSessionStatus.OPEN);
        Set<UUID> seen = new HashSet<>();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row == null || row.length < 2 || !(row[0] instanceof UUID lotId)) {
                    continue;
                }
                long count = toLong(row[1]);
                seen.add(lotId);
                openCounts.compute(lotId, (id, prev) -> {
                    if (prev == null) {
                        return new AtomicLong(count);
                    }
                    prev.set(count);
                    return prev;
                });
            }
        }
        for (var entry : openCounts.entrySet()) {
            if (!seen.contains(entry.getKey())) {
                entry.getValue().set(0);
            }
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
