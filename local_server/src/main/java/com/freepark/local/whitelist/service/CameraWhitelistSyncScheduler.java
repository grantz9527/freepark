package com.freepark.local.whitelist.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * 定时对账臻识机内白名单：到点写入、过期移除；未生效的车牌不在创建时预下发。
 *
 * <p>与车场在场数等组件一致，使用 {@link ScheduledExecutorService}，不依赖 Spring {@code @Scheduled}。
 */
@Component
public class CameraWhitelistSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CameraWhitelistSyncScheduler.class);

    private final CameraWhitelistSyncService sync;
    private final long initialDelayMs;
    private final long intervalMs;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "camera-whitelist-sync");
        t.setDaemon(true);
        return t;
    });

    public CameraWhitelistSyncScheduler(
            CameraWhitelistSyncService sync,
            @Value("${freepark.camera-whitelist-sync.initial-delay-ms:5000}") long initialDelayMs,
            @Value("${freepark.camera-whitelist-sync.interval-ms:30000}") long intervalMs) {
        this.sync = sync;
        this.initialDelayMs = Math.max(0L, initialDelayMs);
        this.intervalMs = Math.max(5_000L, intervalMs);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::reconcileSafely, initialDelayMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("臻识机内白名单定时对账已启动（{}ms 后首次，之后每 {}ms；与手动删除同一入队路径）",
                initialDelayMs, intervalMs);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void reconcileSafely() {
        try {
            sync.reconcileScheduled();
        } catch (Exception ex) {
            log.warn("Whitelist camera scheduled reconcile failed: {}", ex.toString(), ex);
        }
    }
}
