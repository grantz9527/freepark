package com.freepark.local.edge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.freepark.local.domain.PlateColor;

/**
 * 欠费拦截后的「等待缴费开闸」登记：道闸口提示缴费后，车辆停在闸前，
 * 云端缴费成功会经 MQTT 下发开闸；本登记把车牌对上刚才拦截的那台设备。
 *
 * <p>内存结构，进程重启后回落到识别记录（{@code abnormalReason=fee_pending}）查找。</p>
 */
@Component
public class PendingGateOpenService {

    /** 与 AccessDecisionService 欠费拦截 remark 一致。 */
    public static final String FEE_PENDING = "fee_pending";

    /** 闸前缴费等待窗口：超时后 MQTT 开闸不再匹配该次拦截。 */
    public static final Duration TTL = Duration.ofMinutes(15);

    private final ConcurrentMap<UUID, Pending> byDevice = new ConcurrentHashMap<>();

    public void remember(UUID deviceId, String lotCode, String plate, PlateColor plateColor) {
        if (deviceId == null) {
            return;
        }
        String normalizedPlate = normalizePlate(plate);
        if (normalizedPlate == null) {
            return;
        }
        byDevice.put(deviceId, new Pending(
                deviceId,
                normalizeLot(lotCode),
                normalizedPlate,
                plateColor,
                Instant.now()));
    }

    /** 该设备出现新识别：清空旧等待，避免误开后车。 */
    public void clear(UUID deviceId) {
        if (deviceId != null) {
            byDevice.remove(deviceId);
        }
    }

    /**
     * 取出仍在有效期内、与指令车牌（及可选颜色/车场）匹配的设备，并从登记中移除。
     */
    public List<UUID> takeMatching(String plate, String plateColor, String lotCode) {
        expireStale();
        String wantedPlate = normalizePlate(plate);
        if (wantedPlate == null) {
            return List.of();
        }
        String wantedLot = normalizeLot(lotCode);
        String wantedColor = normalizeColor(plateColor);
        List<UUID> matched = new ArrayList<>();
        Iterator<Pending> it = byDevice.values().iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            if (!wantedPlate.equals(pending.plate())) {
                continue;
            }
            if (wantedLot != null && pending.lotCode() != null && !wantedLot.equals(pending.lotCode())) {
                continue;
            }
            if (wantedColor != null && pending.plateColor() != null
                    && !wantedColor.equals(pending.plateColor().name())) {
                continue;
            }
            matched.add(pending.deviceId());
            it.remove();
        }
        return matched;
    }

    private void expireStale() {
        Instant cutoff = Instant.now().minus(TTL);
        byDevice.values().removeIf(pending -> pending.interceptedAt().isBefore(cutoff));
    }

    static String normalizePlate(String plate) {
        if (plate == null) {
            return null;
        }
        String value = plate.trim().toUpperCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    private static String normalizeLot(String lotCode) {
        if (lotCode == null) {
            return null;
        }
        String value = lotCode.trim();
        return value.isEmpty() ? null : value;
    }

    private static String normalizeColor(String plateColor) {
        if (plateColor == null) {
            return null;
        }
        String value = plateColor.trim().toUpperCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    record Pending(UUID deviceId, String lotCode, String plate, PlateColor plateColor, Instant interceptedAt) {
    }
}
