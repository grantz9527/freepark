package com.freepark.local.device.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 黑名单拦截「LED 文字 + 语音」0x6E 单包多行 485 帧构造测试。
 * 帧格式：DA + VR + PN[2] + 0x6E + DL + SAVE_FLAG + TEXT_CONTEXT_NUMBER
 *   + 每行 LID/DM/DS/DT/DR/TC[4]/TL/TEXT(末行 00、其余 0D)
 *   + VF(0A) + VTL + VOICE(GBK) + 00 + CRC16(小端)
 */
class ZhenshiProtocolLedTextTest {

    private final ZhenshiProtocol protocol = new ZhenshiProtocol(null, JsonMapper.builder().build(), null);

    private static final Charset GBK = Charset.forName("GBK");

    @Test
    void entranceBlacklistResponseCarriesInfoNoAndTextVoiceFrame() {
        JsonNode resp = protocol.buildPushResponse(false, "此车为黑名单车辆,禁止入场", "此车为黑名单车辆\n禁止入场");
        assertEquals("no", resp.path("Response_AlarmInfoPlate").path("info").asText(), "拦截应不开闸");
        JsonNode item = serialDataItem(resp);
        byte[] frame = Base64.getDecoder().decode(item.path("data").asText());
        assertEquals(item.path("dataLen").asInt(), frame.length, "dataLen 应为帧实际字节数");
        // 帧头：DA=00 VR=64 PN=FFFF CMD=6E
        assertEquals(0x6E, frame[4] & 0xFF, "应为 0x6E 单包多行指令");
        // SAVE_FLAG=0 临时区、两行文本
        assertEquals(0, frame[6] & 0xFF);
        assertEquals(2, frame[7] & 0xFF);
        int dl = frame[5] & 0xFF;
        assertEquals(frame.length - 5 - 1 - 2, dl, "DL 应为 CMD 后、CRC 前的数据长度");
        // 逐行解析并断言文字与行终止符
        int pos = 8;
        byte[] line1 = "此车为黑名单车辆".getBytes(GBK);
        byte[] line2 = "禁止入场".getBytes(GBK);
        assertEquals(0, frame[pos]);            // LID=0
        assertEquals(0x00, frame[pos + 1] & 0xFF); // DM 立即显示
        assertEquals(line1.length, frame[pos + 9] & 0xFF); // TL
        assertArrayEquals(line1, java.util.Arrays.copyOfRange(frame, pos + 10, pos + 10 + line1.length));
        assertEquals(0x0D, frame[pos + 10 + line1.length] & 0xFF, "非末行以 0D 结束");
        pos += 11 + line1.length;
        assertEquals(1, frame[pos]);            // LID=1
        assertEquals(0x00, frame[pos + 1] & 0xFF);
        assertEquals(line2.length, frame[pos + 9] & 0xFF); // TL
        assertArrayEquals(line2, java.util.Arrays.copyOfRange(frame, pos + 10, pos + 10 + line2.length));
        assertEquals(0x00, frame[pos + 10 + line2.length] & 0xFF, "末行以 00 结束");
        pos += 11 + line2.length;
        // 语音段：VF=0A + VTL + VOICE(GBK) + 00
        byte[] voice = "此车为黑名单车辆,禁止入场".getBytes(GBK);
        assertEquals(0x0A, frame[pos] & 0xFF, "VF 固定 0A");
        assertEquals(voice.length, frame[pos + 1] & 0xFF, "VTL");
        assertArrayEquals(voice, java.util.Arrays.copyOfRange(frame, pos + 2, pos + 2 + voice.length));
        assertEquals(0x00, frame[pos + 2 + voice.length] & 0xFF, "语音以 00 结束");
        // 末尾两字节为 CRC16-Modbus（小端）
        int crc = crc16Modbus(frame, frame.length - 2);
        assertEquals(crc & 0xFF, frame[frame.length - 2] & 0xFF);
        assertEquals((crc >>> 8) & 0xFF, frame[frame.length - 1] & 0xFF);
    }

    @Test
    void exitBlacklistResponseUsesOwnCopyAndClosedLid() {
        JsonNode resp = protocol.buildPushResponse(false, "此车为黑名单车辆,无权出场", "此车为黑名单车辆\n无权出场");
        assertEquals("no", resp.path("Response_AlarmInfoPlate").path("info").asText());
        JsonNode item = serialDataItem(resp);
        byte[] frame = Base64.getDecoder().decode(item.path("data").asText());
        assertEquals(0x6E, frame[4] & 0xFF);
        // 提取第二行文字：行1块 = 10 字节固定头 + 16 字节文本 + 1 字节行终止；第二行文本位于其后再 10 字节头后
        int row2TextStart = 8 + (10 + 16 + 1) + 10;
        byte[] line2 = "无权出场".getBytes(GBK);
        assertArrayEquals(line2, java.util.Arrays.copyOfRange(frame, row2TextStart, row2TextStart + line2.length));
        // 语音段应含「无权出场」：帧尾 = CRC(2) + 语音结束 00 + VOICE；VTL 位于 VOICE 之前
        byte[] voice = "此车为黑名单车辆,无权出场".getBytes(GBK);
        int voiceStart = frame.length - 2 - 1 - voice.length;
        assertArrayEquals(voice, java.util.Arrays.copyOfRange(frame, voiceStart, voiceStart + voice.length));
    }

    @Test
    void ledTextBlankFallsBackToPlainVoiceFrame() {
        // 仅语音（无文字）：拦截响应退化回 0x30 播放语音帧
        JsonNode resp = protocol.buildPushResponse(false, "此车为黑名单车辆,禁止入场", null);
        JsonNode item = serialDataItem(resp);
        byte[] frame = Base64.getDecoder().decode(item.path("data").asText());
        assertEquals(0x30, frame[4] & 0xFF, "无 LED 文字时应回落 0x30 语音帧");
    }

    @Test
    void defaultDisplayStoresBothLinesWithStaySecondsAndNoVoice() {
        JsonMapper json = JsonMapper.builder().build();
        JsonNode heartbeat = json.readTree("{\"status\":\"ok\"}");
        String payload = "{\"line1\":\"欢迎光临\",\"line2\":\"一车一杆\",\"staySeconds\":8}";
        JsonNode resp = protocol.appendDefaultDisplay(heartbeat, payload);
        assertEquals("no", resp.path("Response_AlarmInfoPlate").path("info").asText(), "默认屏显不能附带开闸");
        byte[] frame = Base64.getDecoder().decode(serialDataItem(resp).path("data").asText());
        assertEquals(0x6E, frame[4] & 0xFF);
        assertEquals(1, frame[6] & 0xFF, "SAVE_FLAG 应为存储区");
        assertEquals(2, frame[7] & 0xFF);
        byte[] line1 = "欢迎光临".getBytes(GBK);
        byte[] line2 = "一车一杆".getBytes(GBK);
        int pos = 8;
        assertEquals(0, frame[pos], "第一行 LID");
        assertEquals(0x00, frame[pos + 1] & 0xFF, "未指定播放方式时立即显示");
        assertEquals(8, frame[pos + 3] & 0xFF, "DT 为设定停留秒数");
        assertEquals(0, frame[pos + 4] & 0xFF, "DR=0 循环显示");
        assertArrayEquals(line1, java.util.Arrays.copyOfRange(frame, pos + 10, pos + 10 + line1.length));
        assertEquals(0x0D, frame[pos + 10 + line1.length] & 0xFF);
        pos += 11 + line1.length;
        assertEquals(1, frame[pos], "第二行 LID");
        assertEquals(0x00, frame[pos + 1] & 0xFF);
        assertEquals(8, frame[pos + 3] & 0xFF);
        assertArrayEquals(line2, java.util.Arrays.copyOfRange(frame, pos + 10, pos + 10 + line2.length));
        assertEquals(0x00, frame[pos + 10 + line2.length] & 0xFF);
        pos += 11 + line2.length;
        assertEquals(0x0A, frame[pos] & 0xFF);
        assertEquals(0, frame[pos + 1] & 0xFF, "默认屏显不播语音");
        assertEquals(0x00, frame[pos + 2] & 0xFF);
        int crc = crc16Modbus(frame, frame.length - 2);
        assertEquals(crc & 0xFF, frame[frame.length - 2] & 0xFF);
        assertEquals((crc >>> 8) & 0xFF, frame[frame.length - 1] & 0xFF);
    }

    @Test
    void defaultDisplayUsesPerLinePlayMode() {
        JsonMapper json = JsonMapper.builder().build();
        JsonNode heartbeat = json.readTree("{\"status\":\"ok\"}");
        String payload = "{\"line1\":\"欢迎光临\",\"line2\":\"一车一杆\",\"staySeconds\":8,\"playMode1\":21,\"playMode2\":1}";
        JsonNode resp = protocol.appendDefaultDisplay(heartbeat, payload);
        byte[] frame = Base64.getDecoder().decode(serialDataItem(resp).path("data").asText());
        int pos = 8;
        assertEquals(0x15, frame[pos + 1] & 0xFF, "第一行连续左移");
        pos += 11 + "欢迎光临".getBytes(GBK).length;
        assertEquals(0x01, frame[pos + 1] & 0xFF, "第二行从右向左移动");
        JsonNode unknown = protocol.appendDefaultDisplay(
                heartbeat, "{\"line1\":\"欢迎光临\",\"line2\":\"\",\"staySeconds\":8,\"playMode1\":99}");
        byte[] fallback = Base64.getDecoder().decode(serialDataItem(unknown).path("data").asText());
        assertEquals(0x00, fallback[9] & 0xFF, "未知播放方式按立即显示");
    }

    @Test
    void defaultDisplayKeepsSecondLineIdWhenFirstIsBlank() {
        JsonMapper json = JsonMapper.builder().build();
        JsonNode heartbeat = json.readTree("{\"status\":\"ok\"}");
        JsonNode resp = protocol.appendDefaultDisplay(heartbeat, "{\"line1\":\"\",\"line2\":\"一车一杆\",\"staySeconds\":300}");
        byte[] frame = Base64.getDecoder().decode(serialDataItem(resp).path("data").asText());
        assertEquals(1, frame[7] & 0xFF);
        assertEquals(1, frame[8] & 0xFF, "只有第二行时仍用 LID=1");
        assertEquals(255, frame[11] & 0xFF, "停留秒数限制在 255");
    }

    @Test
    void defaultDisplaySkipsFrameWhenBothLinesBlank() {
        JsonMapper json = JsonMapper.builder().build();
        JsonNode heartbeat = json.readTree("{\"status\":\"ok\"}");
        JsonNode resp = protocol.appendDefaultDisplay(heartbeat, "{\"line1\":\"  \",\"line2\":\"\",\"staySeconds\":10}");
        assertFalse(resp.has("Response_AlarmInfoPlate"));
        assertEquals("ok", resp.path("status").asText());
    }

    @Test
    void noSerialDataWhenAllTextBlank() {
        JsonNode resp = protocol.buildPushResponse(false, null, null);
        assertFalse(resp.path("Response_AlarmInfoPlate").has("serialData"));
        JsonNode blankVoice = protocol.buildPushResponse(false, " ", "此车为黑名单车辆\n禁止入场");
        assertFalse(blankVoice.path("Response_AlarmInfoPlate").has("serialData"), "缺语音文本时放弃 0x6E 下发");
    }

    private static JsonNode serialDataItem(JsonNode response) {
        JsonNode serialData = response.path("Response_AlarmInfoPlate").path("serialData");
        assertTrue(serialData.isArray() && serialData.size() == 1, "应带回一条 serialData");
        return serialData.get(0);
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
