package com.freepark.local.device.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.freepark.driver.api.model.VehicleType;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.WhitelistVehicle;
import com.freepark.local.whitelist.support.WhitelistTimeContinuity;

/**
 * 白名单/停车卡"连续有效段"合并纯逻辑测试：
 * 同车牌多张时间接续的卡应叠加计算剩余有效期，断档则截断；
 * 机内同步不得跨断档拼成一条长区间。
 */
class DeviceGatewayWhitelistContinuityTest {

    private static WhitelistVehicle card(String start, String end) {
        return new WhitelistVehicle(null, "京A10001", PlateColor.BLUE, "车主", VehicleType.MONTHLY,
                null, null, null,
                start == null ? null : Instant.parse(start),
                end == null ? null : Instant.parse(end),
                true);
    }

    private static Instant at(String s) {
        return Instant.parse(s);
    }

    @Test
    void consecutiveCardsExtendToLastEnd() {
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z"),
                card("2027-01-01T00:00:00Z", "2027-06-30T23:59:59Z"));
        assertEquals(at("2027-06-30T23:59:59Z"),
                WhitelistTimeContinuity.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void gapBreaksContinuityAtCurrentCardEnd() {
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", "2026-08-31T23:59:59Z"),
                card("2026-10-01T00:00:00Z", "2026-12-31T23:59:59Z"));
        assertEquals(at("2026-08-31T23:59:59Z"),
                WhitelistTimeContinuity.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void overlappingRenewalIsContinuous() {
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z"),
                card("2026-06-01T00:00:00Z", "2027-06-30T23:59:59Z"));
        assertEquals(at("2027-06-30T23:59:59Z"),
                WhitelistTimeContinuity.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void perpetualCardYieldsNullEnd() {
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", null));
        assertNull(WhitelistTimeContinuity.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void laterBrokenCardDoesNotAffectCurrentSegment() {
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z"),
                card("2027-03-01T00:00:00Z", "2027-05-31T23:59:59Z"));
        assertEquals(at("2026-12-31T23:59:59Z"),
                WhitelistTimeContinuity.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void unsortedInputStillMergesCorrectly() {
        List<WhitelistVehicle> cards = List.of(
                card("2027-01-01T00:00:00Z", "2027-06-30T23:59:59Z"),
                card("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z"));
        assertEquals(at("2027-06-30T23:59:59Z"),
                WhitelistTimeContinuity.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void expiredPrecedingCardsChainIntoCurrent() {
        List<WhitelistVehicle> cards = List.of(
                card("2025-01-01T00:00:00Z", "2025-12-31T23:59:59Z"),
                card("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z"),
                card("2027-01-01T00:00:00Z", "2027-03-31T23:59:59Z"));
        assertEquals(at("2027-03-31T23:59:59Z"),
                WhitelistTimeContinuity.continuousEffectiveEnd(cards, at("2026-06-15T00:00:00Z")));
    }

    @Test
    void emptyCardsYieldNull() {
        assertNull(WhitelistTimeContinuity.continuousEffectiveEnd(List.of(), at("2026-08-01T12:00:00Z")));
    }

    @Test
    void cameraSyncOnlyUsesActiveSegment() {
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", "2026-08-31T23:59:59Z"),
                card("2026-10-01T00:00:00Z", "2026-12-31T23:59:59Z"));
        WhitelistTimeContinuity.Segment duringFirst =
                WhitelistTimeContinuity.activeSegmentAt(cards, at("2026-08-01T12:00:00Z"));
        assertEquals(at("2026-01-01T00:00:00Z"), duringFirst.start());
        assertEquals(at("2026-08-31T23:59:59Z"), duringFirst.end());

        assertNull(WhitelistTimeContinuity.activeSegmentAt(cards, at("2026-09-15T12:00:00Z")));

        WhitelistTimeContinuity.Segment duringSecond =
                WhitelistTimeContinuity.activeSegmentAt(cards, at("2026-11-01T12:00:00Z"));
        assertEquals(at("2026-10-01T00:00:00Z"), duringSecond.start());
        assertEquals(at("2026-12-31T23:59:59Z"), duringSecond.end());
    }

    /**
     * 「次日续上」两张卡合并播报可算一大段，但机内同步：
     * <ul>
     *   <li>第一段进行中：overdue 只写到第一段终点（不并入未开始的续期卡）</li>
     *   <li>第一段已过、第二段未开始：视为未生效，应删除</li>
     *   <li>进入第二段后：再写入第二段</li>
     * </ul>
     */
    @Test
    void cameraSyncRemovesDuringGapInsideNextDayContinuity() {
        List<WhitelistVehicle> cards = List.of(
                card("2026-10-01T00:00:00Z", "2026-10-02T02:00:00Z"),
                card("2026-10-03T00:00:00Z", "2026-10-31T00:00:00Z"));
        // 合并规则：10-02 结束与 10-03 开始算续上，播报剩余天数仍可拼成一大段
        assertEquals(1, WhitelistTimeContinuity.mergeContinuousSegments(cards).size());
        assertEquals(at("2026-10-31T00:00:00Z"),
                WhitelistTimeContinuity.continuousEffectiveEnd(cards, at("2026-10-01T12:00:00Z")));

        // 第一段已过期、第二段未开始：机内必须未生效 → 删除
        assertNull(WhitelistTimeContinuity.activeSegmentAt(cards, at("2026-10-02T12:00:00Z")));

        // 仍在第一段内：生效，但 overdue 不得提前写到续期卡终点
        WhitelistTimeContinuity.Segment duringFirst =
                WhitelistTimeContinuity.activeSegmentAt(cards, at("2026-10-01T12:00:00Z"));
        assertEquals(at("2026-10-01T00:00:00Z"), duringFirst.start());
        assertEquals(at("2026-10-02T02:00:00Z"), duringFirst.end());

        // 已进入第二段：重新生效，可并入已开始的连续段
        WhitelistTimeContinuity.Segment duringSecond =
                WhitelistTimeContinuity.activeSegmentAt(cards, at("2026-10-03T12:00:00Z"));
        assertEquals(at("2026-10-01T00:00:00Z"), duringSecond.start());
        assertEquals(at("2026-10-31T00:00:00Z"), duringSecond.end());
    }
}
