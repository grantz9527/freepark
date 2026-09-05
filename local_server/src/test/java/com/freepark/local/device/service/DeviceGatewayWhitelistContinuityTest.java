package com.freepark.local.device.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.freepark.driver.api.model.VehicleType;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.WhitelistVehicle;

/**
 * 白名单/停车卡"连续有效段"合并纯逻辑测试：
 * 同车牌多张时间接续的卡应叠加计算剩余有效期，断档则截断。
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
        // 上一张 23:59:59 到期、下一张次日 00:00:00 生效 → 视为接续，叠加到连续段最后一张的结束时间
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z"),
                card("2027-01-01T00:00:00Z", "2027-06-30T23:59:59Z"));
        assertEquals(at("2027-06-30T23:59:59Z"),
                DeviceGatewayService.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void gapBreaksContinuityAtCurrentCardEnd() {
        // 中间断档（9 月无卡），当前卡到期后即截断，不统计断档之后的卡
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", "2026-08-31T23:59:59Z"),
                card("2026-10-01T00:00:00Z", "2026-12-31T23:59:59Z"));
        assertEquals(at("2026-08-31T23:59:59Z"),
                DeviceGatewayService.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void overlappingRenewalIsContinuous() {
        // 提前续期导致重叠：第二张开始不晚于第一张到期日次日 → 并入，终点取较晚者
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z"),
                card("2026-06-01T00:00:00Z", "2027-06-30T23:59:59Z"));
        assertEquals(at("2027-06-30T23:59:59Z"),
                DeviceGatewayService.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void perpetualCardYieldsNullEnd() {
        // 长期有效（无 endTime）卡覆盖当前时刻 → 连续段无终点（长期）
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", null));
        assertNull(DeviceGatewayService.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void laterBrokenCardDoesNotAffectCurrentSegment() {
        // 当前段之后存在断档的另一段卡：当前段终点仍为段末，不受其影响
        List<WhitelistVehicle> cards = List.of(
                card("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z"),
                card("2027-03-01T00:00:00Z", "2027-05-31T23:59:59Z"));
        assertEquals(at("2026-12-31T23:59:59Z"),
                DeviceGatewayService.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void unsortedInputStillMergesCorrectly() {
        // 输入乱序时函数内部按开始时间排序，结果一致
        List<WhitelistVehicle> cards = List.of(
                card("2027-01-01T00:00:00Z", "2027-06-30T23:59:59Z"),
                card("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z"));
        assertEquals(at("2027-06-30T23:59:59Z"),
                DeviceGatewayService.continuousEffectiveEnd(cards, at("2026-08-01T12:00:00Z")));
    }

    @Test
    void expiredPrecedingCardsChainIntoCurrent() {
        // 历史连续卡 + 当前卡 + 后续接续卡全部并入（过去的历史不影响终点，终点由最新接续卡决定）
        List<WhitelistVehicle> cards = List.of(
                card("2025-01-01T00:00:00Z", "2025-12-31T23:59:59Z"),
                card("2026-01-01T00:00:00Z", "2026-12-31T23:59:59Z"),
                card("2027-01-01T00:00:00Z", "2027-03-31T23:59:59Z"));
        assertEquals(at("2027-03-31T23:59:59Z"),
                DeviceGatewayService.continuousEffectiveEnd(cards, at("2026-06-15T00:00:00Z")));
    }

    @Test
    void emptyCardsYieldNull() {
        assertNull(DeviceGatewayService.continuousEffectiveEnd(List.of(), at("2026-08-01T12:00:00Z")));
    }
}
