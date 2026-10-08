package com.freepark.local.whitelist.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.freepark.driver.api.model.VehicleType;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.device.protocol.ZhenshiProtocol;
import com.freepark.local.device.protocol.ZhenshiWhitelistBatch;
import com.freepark.local.device.protocol.ZhenshiWhitelistPendingStore;
import com.freepark.local.domain.CameraWhitelistSyncStatus;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.ParkingBarrierRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.WhitelistVehicle;
import com.freepark.local.domain.WhitelistVehicleRepository;
import com.freepark.local.sitesettings.service.SystemSettingsService;
import com.freepark.local.whitelist.dto.CameraWhitelistFullResyncView;
import com.freepark.local.whitelist.support.WhitelistTimeContinuity;

/**
 * 按系统设置把业主/月租白名单下发到臻识识别一体机。
 *
 * <p>每条白名单卡维护本地五态：已下发 / 待下发 / 过期已移除 / 过期待移除 / 未生效。
 * 定时任务周期性刷新该状态并按状态入队增删；云端协同只改业务字段，状态由本地重算。
 *
 * <p>并发策略：
 * <ul>
 *   <li>同车牌（lotId:plate）条带锁串行 {@code refreshPlateNow}</li>
 *   <li>状态落库用 CAS，避免「已下发」盖掉「过期待移除」</li>
 *   <li>全量重同步持写锁，与单车牌对账（读锁）互斥</li>
 * </ul>
 */
@Service
public class CameraWhitelistSyncService {

    private static final Logger log = LoggerFactory.getLogger(CameraWhitelistSyncService.class);
    private static final List<VehicleType> OWNER_MONTHLY =
            List.of(VehicleType.OWNER, VehicleType.MONTHLY);
    private static final int PLATE_LOCK_STRIPES = 64;

    private final SystemSettingsService systemSettings;
    private final ParkingBarrierRepository barriers;
    private final ParkingLotRepository lots;
    private final WhitelistVehicleRepository vehicles;
    private final ZhenshiWhitelistPendingStore pending;
    private final TransactionTemplate tx;
    /** lotId:plate → 曾观察到待下发队列，用于清空后推进「已下发/过期已移除」。 */
    private final ConcurrentHashMap<String, Boolean> sawCameraPending = new ConcurrentHashMap<>();
    /** 全量重同步 vs 单车牌对账。 */
    private final ReentrantReadWriteLock cameraOpsLock = new ReentrantReadWriteLock();
    private final Object[] plateLocks = new Object[PLATE_LOCK_STRIPES];

    public CameraWhitelistSyncService(
            SystemSettingsService systemSettings,
            ParkingBarrierRepository barriers,
            ParkingLotRepository lots,
            WhitelistVehicleRepository vehicles,
            ZhenshiWhitelistPendingStore pending,
            PlatformTransactionManager transactionManager) {
        this.systemSettings = systemSettings;
        this.barriers = barriers;
        this.lots = lots;
        this.vehicles = vehicles;
        this.pending = pending;
        this.tx = new TransactionTemplate(transactionManager);
        for (int i = 0; i < PLATE_LOCK_STRIPES; i++) {
            plateLocks[i] = new Object();
        }
    }

    public void onSaved(ParkingLot lot, WhitelistVehicle vehicle, VehicleType previousType) {
        onSaved(lot, vehicle, previousType, null);
    }

    public void onSaved(ParkingLot lot, WhitelistVehicle vehicle, VehicleType previousType, String previousPlate) {
        afterCommit(() -> {
            refreshPlateNow(lot, vehicle.getPlateNumber(), null);
            if (previousPlate != null
                    && !previousPlate.isBlank()
                    && !previousPlate.equalsIgnoreCase(vehicle.getPlateNumber())) {
                refreshPlateNow(lot, previousPlate, null);
            }
        });
    }

    public void onDeleted(ParkingLot lot, WhitelistVehicle vehicle) {
        UUID excludeId = vehicle.getId();
        String plate = vehicle.getPlateNumber();
        afterCommit(() -> refreshPlateNow(lot, plate, excludeId));
    }

    public void resyncOwnerAndMonthly() {
        afterCommit(this::resyncOwnerAndMonthlyNow);
    }

    public void refreshPlate(ParkingLot lot, String plate) {
        afterCommit(() -> refreshPlateNow(lot, plate, null));
    }

    /**
     * 定时对账两阶段：
     * <ol>
     *   <li>按车牌加锁，事务内刷新并<strong>CAS 落库</strong>五态</li>
     *   <li>再按落库后的状态入队增删；队列清空后推进为已下发 / 过期已移除</li>
     * </ol>
     */
    public void reconcileScheduled() {
        resyncOwnerAndMonthlyNow();
    }

    @Transactional(readOnly = true)
    public CameraWhitelistFullResyncView fullResyncForLot(UUID lotId) {
        if (lotId == null || lots.findById(lotId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        List<ParkingBarrier> cameras = zhensiCamerasOf(lotId);
        if (cameras.isEmpty()) {
            log.info("Whitelist camera full-resync lot={}: no zhenshi cameras", lotId);
            return new CameraWhitelistFullResyncView(0, 0);
        }
        List<ZhenshiWhitelistBatch.Item> adds = buildSyncItemsForLot(lotId);
        cameraOpsLock.writeLock().lock();
        try {
            int plateOps = 0;
            for (ParkingBarrier camera : cameras) {
                pending.replaceWithClearThenAdds(camera.getId(), adds);
                plateOps += adds.size();
                log.info("Whitelist camera full-resync queued device={} lot={} plates={}",
                        camera.getCode(), lotId, adds.size());
            }
            log.info("Whitelist camera full-resync lot={} cameras={} plateOps={}",
                    lotId, cameras.size(), plateOps);
            return new CameraWhitelistFullResyncView(cameras.size(), plateOps);
        } finally {
            cameraOpsLock.writeLock().unlock();
        }
    }

    @Transactional(readOnly = true)
    public CameraWhitelistFullResyncView fullResyncForDevice(UUID barrierId) {
        ParkingBarrier camera = barriers.findByIdWithLaneLot(barrierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        if (!camera.isEnabled()) {
            throw new BusinessException(ErrorCode.DEVICE_DISABLED);
        }
        if (!isZhenshi(camera)) {
            log.info("Whitelist camera full-resync device={}: not zhenshi, skip", camera.getCode());
            return new CameraWhitelistFullResyncView(0, 0);
        }
        if (camera.getLane() == null || camera.getLane().getLot() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        UUID lotId = camera.getLane().getLot().getId();
        List<ZhenshiWhitelistBatch.Item> adds = buildSyncItemsForLot(lotId);
        cameraOpsLock.writeLock().lock();
        try {
            pending.replaceWithClearThenAdds(camera.getId(), adds);
            log.info("Whitelist camera full-resync single device={} lot={} plates={}",
                    camera.getCode(), lotId, adds.size());
            return new CameraWhitelistFullResyncView(1, adds.size());
        } finally {
            cameraOpsLock.writeLock().unlock();
        }
    }

    private List<ZhenshiWhitelistBatch.Item> buildSyncItemsForLot(UUID lotId) {
        LinkedHashMap<String, ZhenshiWhitelistBatch.Item> byPlate = new LinkedHashMap<>();
        Instant now = Instant.now();
        for (WhitelistVehicle vehicle : vehicles.findAllByLotId(lotId)) {
            if (vehicle == null || !shouldSync(vehicle.getType())) {
                continue;
            }
            vehicle.refreshCameraWhitelistSyncStatus(now);
            if (!vehicle.getCameraWhitelistSyncStatus().desiresCameraPresence()) {
                continue;
            }
            String plate = vehicle.getPlateNumber();
            if (plate == null || plate.isBlank()) {
                continue;
            }
            String key = plate.trim().toLowerCase(Locale.ROOT);
            if (byPlate.containsKey(key)) {
                continue;
            }
            List<WhitelistVehicle> keepers = keepersForPlate(lotId, plate.trim(), null);
            WhitelistTimeContinuity.Segment segment = WhitelistTimeContinuity.activeSegmentAt(keepers, now);
            if (segment == null) {
                continue;
            }
            String enableTime = segment.start() == null ? null : systemSettings.formatInstant(segment.start());
            String overdueTime = segment.end() == null ? null : systemSettings.formatInstant(segment.end());
            byPlate.put(key, new ZhenshiWhitelistBatch.Item(plate.trim(), enableTime, overdueTime));
        }
        return new ArrayList<>(byPlate.values());
    }

    private void resyncOwnerAndMonthlyNow() {
        Instant now = Instant.now();
        int statusFlipped = persistAllSyncStatuses(now);

        Set<String> seen = new HashSet<>();
        int upserts = 0;
        int removes = 0;
        int skipped = 0;
        int errors = 0;
        List<WhitelistVehicle> rows;
        try {
            rows = vehicles.findAllOwnerMonthlyWithLot(OWNER_MONTHLY);
        } catch (Exception ex) {
            log.warn("Whitelist camera reconcile load failed: {}", ex.toString(), ex);
            return;
        }
        log.info("Whitelist camera scheduled reconcile start at={} loaded={} statusFlipped={}",
                now, rows.size(), statusFlipped);
        for (WhitelistVehicle vehicle : rows) {
            ParkingLot lot = vehicle.getLot();
            if (lot == null || vehicle.getPlateNumber() == null || vehicle.getPlateNumber().isBlank()) {
                continue;
            }
            String key = lot.getId() + ":" + vehicle.getPlateNumber().trim().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            try {
                ApplyResult result = refreshPlateNow(lot, vehicle.getPlateNumber(), null);
                switch (result) {
                    case UPSERT -> upserts++;
                    case REMOVE -> removes++;
                    case SKIPPED -> skipped++;
                }
            } catch (Exception ex) {
                errors++;
                log.warn("Whitelist camera reconcile plate failed lot={} plate={}: {}",
                        lot.getId(), vehicle.getPlateNumber(), ex.toString());
            }
        }
        log.info("Whitelist camera scheduled reconcile done plates={} upserts={} removes={} skipped={} errors={}",
                seen.size(), upserts, removes, skipped, errors);
    }

    /** 按车牌加锁后 CAS 推进五态（过期的已下发/待下发 → 过期待移除）。 */
    private int persistAllSyncStatuses(Instant now) {
        List<WhitelistVehicle> rows = vehicles.findAllOwnerMonthlyWithLot(OWNER_MONTHLY);
        LinkedHashMap<String, List<WhitelistVehicle>> byPlate = new LinkedHashMap<>();
        for (WhitelistVehicle vehicle : rows) {
            if (vehicle == null || vehicle.getLot() == null || vehicle.getPlateNumber() == null) {
                continue;
            }
            String key = trackKey(vehicle.getLot().getId(), vehicle.getPlateNumber());
            byPlate.computeIfAbsent(key, ignored -> new ArrayList<>()).add(vehicle);
        }
        int flipped = 0;
        for (var entry : byPlate.entrySet()) {
            WhitelistVehicle sample = entry.getValue().getFirst();
            UUID lotId = sample.getLot().getId();
            String plate = sample.getPlateNumber();
            Object lock = plateLock(lotId, plate);
            synchronized (lock) {
                for (WhitelistVehicle vehicle : entry.getValue()) {
                    if (persistSyncStatusIfChanged(vehicle, now)) {
                        flipped++;
                    }
                }
            }
        }
        return flipped;
    }

    /**
     * CAS 落库状态；成功后回写内存实体。期望状态已被别人改掉时最多再读一次重试。
     */
    private boolean persistSyncStatusIfChanged(WhitelistVehicle vehicle, Instant now) {
        if (vehicle == null || vehicle.getId() == null) {
            return false;
        }
        CameraWhitelistSyncStatus previous = vehicle.getCameraWhitelistSyncStatus();
        CameraWhitelistSyncStatus next = vehicle.computeNextSyncStatus(now);
        if (next == previous) {
            return false;
        }
        if (casStatus(vehicle.getId(), previous, next)) {
            vehicle.applyCameraWhitelistSyncStatus(next);
            logStatusFlip(vehicle, previous, next);
            return true;
        }
        WhitelistVehicle fresh = vehicles.findById(vehicle.getId()).orElse(null);
        if (fresh == null) {
            return false;
        }
        previous = fresh.getCameraWhitelistSyncStatus();
        next = fresh.computeNextSyncStatus(now);
        vehicle.applyCameraWhitelistSyncStatus(previous);
        if (next == previous) {
            return false;
        }
        if (casStatus(fresh.getId(), previous, next)) {
            vehicle.applyCameraWhitelistSyncStatus(next);
            logStatusFlip(vehicle, previous, next);
            return true;
        }
        vehicles.findById(vehicle.getId())
                .ifPresent(latest -> vehicle.applyCameraWhitelistSyncStatus(latest.getCameraWhitelistSyncStatus()));
        return false;
    }

    private boolean casStatus(UUID id, CameraWhitelistSyncStatus expected, CameraWhitelistSyncStatus next) {
        Integer updated = tx.execute(status -> vehicles.casCameraSyncStatus(id, expected, next));
        return updated != null && updated > 0;
    }

    private static void logStatusFlip(
            WhitelistVehicle vehicle, CameraWhitelistSyncStatus previous, CameraWhitelistSyncStatus next) {
        log.info(
                "Whitelist camera status flipped plate={} type={} {} -> {} start={} end={}",
                vehicle.getPlateNumber(),
                vehicle.getType(),
                previous,
                next,
                vehicle.getStartTime(),
                vehicle.getEndTime());
    }

    private ApplyResult refreshPlateNow(ParkingLot lot, String plate, UUID excludeId) {
        if (lot == null || plate == null || plate.isBlank()) {
            return ApplyResult.SKIPPED;
        }
        String normalized = plate.trim();
        cameraOpsLock.readLock().lock();
        try {
            synchronized (plateLock(lot.getId(), normalized)) {
                return refreshPlateNowLocked(lot, normalized, excludeId);
            }
        } finally {
            cameraOpsLock.readLock().unlock();
        }
    }

    private ApplyResult refreshPlateNowLocked(ParkingLot lot, String normalized, UUID excludeId) {
        Instant now = Instant.now();
        String trackKey = trackKey(lot.getId(), normalized);
        // 持锁后重新加载，避免与定时/云端并发时用到过期快照
        List<WhitelistVehicle> allCards = vehicles.findAllByLotIdAndPlateNumberIgnoreCase(lot.getId(), normalized)
                .stream()
                .filter(v -> excludeId == null || !excludeId.equals(v.getId()))
                .toList();
        for (WhitelistVehicle card : allCards) {
            persistSyncStatusIfChanged(card, now);
        }

        List<ParkingBarrier> cameras = zhensiCamerasOf(lot.getId());
        if (cameras.isEmpty()) {
            markExpiredRemovedIfPending(allCards, now);
            return ApplyResult.SKIPPED;
        }

        List<WhitelistVehicle> presentKeepers = allCards.stream()
                .filter(v -> v.getCameraWhitelistSyncStatus().desiresCameraPresence() && shouldSync(v.getType()))
                .toList();
        boolean anyPending = anyCameraPending(cameras, normalized);
        if (anyPending) {
            sawCameraPending.put(trackKey, Boolean.TRUE);
        } else if (Boolean.TRUE.equals(sawCameraPending.remove(trackKey))) {
            if (presentKeepers.isEmpty()) {
                markExpiredRemovedIfPending(allCards, now);
            } else {
                markDelivered(presentKeepers, now);
            }
        }

        if (presentKeepers.isEmpty()) {
            boolean needsCameraOp = allCards.stream().anyMatch(v -> {
                CameraWhitelistSyncStatus s = v.getCameraWhitelistSyncStatus();
                return s.needsExpireRemove()
                        || s == CameraWhitelistSyncStatus.DELIVERED
                        || s == CameraWhitelistSyncStatus.PENDING_DELIVER;
            });
            if (!needsCameraOp) {
                return ApplyResult.SKIPPED;
            }
            return queueRemove(lot.getId(), normalized, cameras, trackKey, "no-present-keepers");
        }

        WhitelistTimeContinuity.Segment segment =
                WhitelistTimeContinuity.activeSegmentAt(presentKeepers, now);
        if (segment == null) {
            return queueRemove(lot.getId(), normalized, cameras, trackKey, "present-but-not-active");
        }

        boolean needsDeliver = presentKeepers.stream()
                .anyMatch(v -> v.getCameraWhitelistSyncStatus().needsDeliver());
        if (!needsDeliver) {
            return ApplyResult.SKIPPED;
        }

        String enableTime = segment.start() == null ? null : systemSettings.formatInstant(segment.start());
        String overdueTime = segment.end() == null ? null : systemSettings.formatInstant(segment.end());
        if (cameras.stream().allMatch(c -> pending.hasIdenticalAdd(c.getId(), normalized, enableTime, overdueTime))) {
            return ApplyResult.SKIPPED;
        }
        log.info("Whitelist camera sync upsert lot={} plate={} cameras={} enable={} overdue={} status=PENDING_DELIVER",
                lot.getId(), normalized, cameras.size(), enableTime, overdueTime);
        for (ParkingBarrier camera : cameras) {
            pending.upsert(camera.getId(), normalized, enableTime, overdueTime);
        }
        sawCameraPending.put(trackKey, Boolean.TRUE);
        return ApplyResult.UPSERT;
    }

    private ApplyResult queueRemove(
            UUID lotId, String plate, List<ParkingBarrier> cameras, String trackKey, String reason) {
        boolean anyMissing = false;
        for (ParkingBarrier camera : cameras) {
            if (!pending.hasDelete(camera.getId(), plate)) {
                anyMissing = true;
                break;
            }
        }
        if (!anyMissing) {
            sawCameraPending.put(trackKey, Boolean.TRUE);
            return ApplyResult.SKIPPED;
        }
        log.info("Whitelist camera sync remove lot={} plate={} cameras={} reason={}",
                lotId, plate, cameras.size(), reason);
        for (ParkingBarrier camera : cameras) {
            pending.remove(camera.getId(), plate);
        }
        sawCameraPending.put(trackKey, Boolean.TRUE);
        return ApplyResult.REMOVE;
    }

    private void markExpiredRemovedIfPending(List<WhitelistVehicle> cards, Instant now) {
        for (WhitelistVehicle card : cards) {
            persistSyncStatusIfChanged(card, now);
            if (card.getCameraWhitelistSyncStatus() != CameraWhitelistSyncStatus.EXPIRED_PENDING_REMOVE) {
                continue;
            }
            if (casStatus(card.getId(), CameraWhitelistSyncStatus.EXPIRED_PENDING_REMOVE,
                    CameraWhitelistSyncStatus.EXPIRED_REMOVED)) {
                card.applyCameraWhitelistSyncStatus(CameraWhitelistSyncStatus.EXPIRED_REMOVED);
                log.info("Whitelist camera status -> EXPIRED_REMOVED plate={}", card.getPlateNumber());
            }
        }
    }

    private void markDelivered(List<WhitelistVehicle> keepers, Instant now) {
        for (WhitelistVehicle card : keepers) {
            // 收口前再按时间窗推进：已过期则不会写成已下发
            persistSyncStatusIfChanged(card, now);
            if (card.getCameraWhitelistSyncStatus() != CameraWhitelistSyncStatus.PENDING_DELIVER) {
                continue;
            }
            if (casStatus(card.getId(), CameraWhitelistSyncStatus.PENDING_DELIVER,
                    CameraWhitelistSyncStatus.DELIVERED)) {
                card.applyCameraWhitelistSyncStatus(CameraWhitelistSyncStatus.DELIVERED);
                log.info("Whitelist camera status -> DELIVERED plate={}", card.getPlateNumber());
            }
        }
    }

    private boolean anyCameraPending(List<ParkingBarrier> cameras, String plate) {
        for (ParkingBarrier camera : cameras) {
            if (pending.hasPending(camera.getId(), plate)) {
                return true;
            }
        }
        return false;
    }

    private List<WhitelistVehicle> keepersForPlate(UUID lotId, String plate, UUID excludeId) {
        Instant now = Instant.now();
        return vehicles.findAllByLotIdAndPlateNumberIgnoreCase(lotId, plate).stream()
                .filter(v -> excludeId == null || !excludeId.equals(v.getId()))
                .peek(v -> v.refreshCameraWhitelistSyncStatus(now))
                .filter(v -> v.getCameraWhitelistSyncStatus().desiresCameraPresence() && shouldSync(v.getType()))
                .toList();
    }

    private boolean shouldSync(VehicleType type) {
        return systemSettings.isCameraWhitelistSyncEnabled(type);
    }

    private List<ParkingBarrier> zhensiCamerasOf(UUID lotId) {
        return barriers.findAllByLaneLotIdAndEnabledTrue(lotId).stream()
                .filter(this::isZhenshi)
                .toList();
    }

    private boolean isZhenshi(ParkingBarrier camera) {
        String brand = camera.getBrand();
        return brand == null || brand.isBlank() || ZhenshiProtocol.BRAND.equalsIgnoreCase(brand.trim());
    }

    private Object plateLock(UUID lotId, String plate) {
        int h = Objects.hash(lotId, plate == null ? "" : plate.trim().toLowerCase(Locale.ROOT));
        return plateLocks[(h & 0x7fffffff) % PLATE_LOCK_STRIPES];
    }

    private static String trackKey(UUID lotId, String plate) {
        return lotId + ":" + plate.trim().toLowerCase(Locale.ROOT);
    }

    private enum ApplyResult {
        UPSERT,
        REMOVE,
        SKIPPED
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
