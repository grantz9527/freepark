package com.freepark.local.whitelist.support;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.freepark.local.domain.WhitelistVehicle;

/**
 * 同车牌多张停车卡的连续有效段合并。
 *
 * <p>相邻/重叠（含「到期日次日续上」）并入同一段；中间有断档则拆成多段。
 * 下发臻识机内白名单时只取<strong>当前有单卡覆盖</strong>的区间，且 overdue 不并入尚未开始的续期卡；
 * 未生效或已过期由定时任务再决定写入/移除。
 */
public final class WhitelistTimeContinuity {

    private WhitelistTimeContinuity() {
    }

    /** start/end 均可为 null：null start=不限开始，null end=长期有效。 */
    public record Segment(Instant start, Instant end) {
    }

    /**
     * 按开始时间排序后合并连续段（断档处切开）。
     */
    public static List<Segment> mergeContinuousSegments(List<WhitelistVehicle> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        List<WhitelistVehicle> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparing(WhitelistVehicle::getStartTime,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        List<Segment> segments = new ArrayList<>();
        Instant segmentStart = null;
        Instant segmentEnd = null;
        boolean open = false;
        for (WhitelistVehicle card : sorted) {
            Instant start = card.getStartTime();
            Instant end = card.getEndTime();
            if (!open) {
                segmentStart = start;
                segmentEnd = end;
                open = true;
                continue;
            }
            if (breaksContinuity(segmentEnd, start)) {
                segments.add(new Segment(segmentStart, segmentEnd));
                segmentStart = start;
                segmentEnd = end;
                continue;
            }
            if (segmentEnd != null && (end == null || end.isAfter(segmentEnd))) {
                segmentEnd = end;
            }
        }
        if (open) {
            segments.add(new Segment(segmentStart, segmentEnd));
        }
        return segments;
    }

    /**
     * 机内白名单下发用：仅当至少有一张停车卡本身覆盖 {@code now} 时返回区间。
     *
     * <p>合并时<strong>只纳入已开始</strong>的卡（{@code start == null || start <= now}），
     * 这样 overdue 停在当前这段的真实终点，不会把「次日才开始」的续期卡写进相机，
     * 避免第一段过期后机内名单仍显示未到期、也不触发删除。
     */
    public static Segment activeSegmentAt(List<WhitelistVehicle> cards, Instant now) {
        Instant at = now == null ? Instant.now() : now;
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        if (!anyCardCovers(cards, at)) {
            return null;
        }
        List<WhitelistVehicle> started = cards.stream()
                .filter(c -> c != null)
                .filter(c -> c.getStartTime() == null || !c.getStartTime().isAfter(at))
                .toList();
        for (Segment segment : mergeContinuousSegments(started)) {
            if (covers(segment.start(), segment.end(), at)) {
                return segment;
            }
        }
        return null;
    }

    /**
     * @deprecated 改用 {@link #activeSegmentAt}；不再预取未来段。
     */
    @Deprecated
    public static Segment segmentForCameraSync(List<WhitelistVehicle> cards, Instant now) {
        return activeSegmentAt(cards, now);
    }

    /**
     * 播报剩余有效期用：当前有单卡覆盖时，连续段可并入尚未开始的「次日续上」卡，
     * 终点取整段最后一张；无覆盖或长期有效则 null。
     */
    public static Instant continuousEffectiveEnd(List<WhitelistVehicle> cards, Instant now) {
        if (now == null) {
            now = Instant.now();
        }
        if (!anyCardCovers(cards, now)) {
            return null;
        }
        for (Segment segment : mergeContinuousSegments(cards)) {
            if (covers(segment.start(), segment.end(), now)) {
                return segment.end();
            }
        }
        return null;
    }

    static boolean anyCardCovers(List<WhitelistVehicle> cards, Instant now) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }
        for (WhitelistVehicle card : cards) {
            if (card != null && covers(card.getStartTime(), card.getEndTime(), now)) {
                return true;
            }
        }
        return false;
    }

    /** 上一段结束日与下一段开始日之间间隔超过 1 天则断档。 */
    static boolean breaksContinuity(Instant segmentEnd, Instant nextStart) {
        if (segmentEnd == null || nextStart == null) {
            return false;
        }
        return nextStart.atZone(ZoneOffset.UTC).toLocalDate()
                .isAfter(segmentEnd.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1));
    }

    static boolean covers(Instant segmentStart, Instant segmentEnd, Instant now) {
        if (segmentStart != null && now.isBefore(segmentStart)) {
            return false;
        }
        return segmentEnd == null || !now.isAfter(segmentEnd);
    }
}
