package com.freepark.local.device.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.freepark.local.sitesettings.service.SystemSettingsService;

/**
 * 「一键同步主板时间」0x05 同步时间 485 帧测试。
 * 帧格式：DA + VR + PN[2] + 0x05 + DL + Y[2](小端) + M + D + W + H + N + S + CRC16(小端)。
 * W 为协议星期编码：1=星期日、2~7=星期一至六。
 */
class ZhenshiProtocolTimeSyncTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    /** 固定站点时区的桩：仅覆写 getTimezone，单测不依赖数据库。 */
    private static final class FixedZoneSettings extends SystemSettingsService {
        private final ZoneId zone;

        FixedZoneSettings(String zoneId) {
            super(null, null);
            this.zone = ZoneId.of(zoneId);
        }

        @Override
        public ZoneId getTimezone() {
            return zone;
        }
    }

    private final ZhenshiProtocol protocol =
            new ZhenshiProtocol(null, JsonMapper.builder().build(), new FixedZoneSettings("UTC"));

    @Test
    void timeSyncFrameCarriesHeaderAndWallClockInUtc() {
        ZonedDateTime before = ZonedDateTime.now(UTC);
        JsonNode resp = protocol.appendTimeSync(protocol.buildPushResponse(false));
        ZonedDateTime after = ZonedDateTime.now(UTC);

        JsonNode item = serialData(resp, 1).get(0);
        byte[] frame = Base64.getDecoder().decode(item.path("data").asText());
        assertEquals(item.path("dataLen").asInt(), frame.length, "dataLen 应为帧实际字节数");
        assertEquals(16, frame.length, "0x05 帧固定 16 字节");
        // 帧头：DA=00 VR=64 PN=FFFF CMD=05 DL=08
        assertEquals(0x05, frame[4] & 0xFF, "应为 0x05 同步时间指令");
        assertEquals(8, frame[5] & 0xFF, "DL 应为 8");
        // Y 16 位小端
        int year = (frame[6] & 0xFF) | ((frame[7] & 0xFF) << 8);
        int month = frame[8] & 0xFF;
        int day = frame[9] & 0xFF;
        int week = frame[10] & 0xFF;
        int hour = frame[11] & 0xFF;
        int minute = frame[12] & 0xFF;
        int second = frame[13] & 0xFF;
        assertEquals(year, before.getYear(), "Y 应为当前年份（小端）");

        // 帧内墙钟时间应与服务器当前时间一致（容差 1s，覆盖秒边界）
        long frameSec = LocalDateTime.of(year, month, day, hour, minute, second).atZone(UTC).toEpochSecond();
        assertTrue(frameSec >= before.toEpochSecond() - 1 && frameSec <= after.toEpochSecond() + 1,
                "帧内时间应与服务器当前时间一致");

        // 星期编码内部一致：W = java 周值 % 7 + 1（1=星期日…7=星期六）
        LocalDateTime frameLdt = LocalDateTime.of(year, month, day, hour, minute, second);
        assertEquals(frameLdt.getDayOfWeek().getValue() % 7 + 1, week,
                "W 应按协议换算（1=星期日、2~7=星期一至六）");
        assertTrue(week >= 1 && week <= 7, "W 应在 1~7");

        // 末尾两字节为 CRC16-Modbus（小端）
        int expected = crc16Modbus(frame, frame.length - 2);
        assertEquals(expected & 0xFF, frame[frame.length - 2] & 0xFF);
        assertEquals((expected >>> 8) & 0xFF, frame[frame.length - 1] & 0xFF);
    }

    @Test
    void appendTimeSyncAppendsFrameToExistingSerialData() {
        // 拦截响应已带 0x6E「LED 文字+语音」帧：时间同步帧应追加而非覆盖
        JsonNode intercepted = protocol.buildPushResponse(false, "此车为黑名单车辆,禁止入场", "此车为黑名单车辆\n禁止入场");
        JsonNode appended = protocol.appendTimeSync(intercepted);
        JsonNode items = appended.path("Response_AlarmInfoPlate").path("serialData");
        assertTrue(items.isArray() && items.size() == 2, "已有语音帧时应在同一 serialData 中追加，共 2 条");
        byte[] first = Base64.getDecoder().decode(items.get(0).path("data").asText());
        byte[] second = Base64.getDecoder().decode(items.get(1).path("data").asText());
        assertEquals(0x6E, first[4] & 0xFF, "第一条仍为 LED 文字+语音帧");
        assertEquals(0x05, second[4] & 0xFF, "第二条为 0x05 时间同步帧");
    }

    @Test
    void heartbeatResponseIsConvertedToCarryTimeFrame() {
        ObjectNode heartbeat = JsonNodeFactory.instance.objectNode();
        heartbeat.put("status", "ok");
        JsonNode result = protocol.appendTimeSync(heartbeat);
        assertFalse(result == heartbeat, "心跳应答应被改写为可携带 serialData 的响应");
        assertEquals("no", result.path("Response_AlarmInfoPlate").path("info").asText(), "时间同步不开闸");
        assertFalse(result.has("status"), "改写后不再保留 status 心跳字段");
        JsonNode item = serialData(result, 1).get(0);
        byte[] frame = Base64.getDecoder().decode(item.path("data").asText());
        assertEquals(0x05, frame[4] & 0xFF);
    }

    private static JsonNode serialData(JsonNode response, int size) {
        JsonNode serialData = response.path("Response_AlarmInfoPlate").path("serialData");
        assertTrue(serialData.isArray() && serialData.size() == size,
                "应带回 " + size + " 条 serialData，实际 " + serialData.size());
        return serialData;
    }

    private static int crc16Modbus(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= data[i] & 0xFF;
            for (int b = 0; b < 8; b++) {
                crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xA001 : crc >>> 1;
            }
        }
        return crc;
    }
}
