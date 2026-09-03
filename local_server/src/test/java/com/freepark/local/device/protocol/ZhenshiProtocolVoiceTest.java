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
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.ParkingBarrier;

/**
 * 播报语音 → 相机 serialData 485 帧的构造测试。
 * 帧格式：DA + VR + PN[2] + 0x30 + DL + OTP + TEXT(GBK) + CRC16(小端)。
 * 「欢迎光临」为厂商文档已知向量（00 64 FF FF 30 09 01 BB B6 D3 AD B9 E2 C1 D9 32 58）。
 */
class ZhenshiProtocolVoiceTest {

    private final ZhenshiProtocol protocol = new ZhenshiProtocol(null, JsonMapper.builder().build(), null);

    private static final Charset GBK = Charset.forName("GBK");

    @Test
    void welcomeVoiceBecomesKnownSerialFrame() {
        JsonNode item = voiceItem(protocol.buildPushResponse(true, "欢迎光临"));
        byte[] frame = Base64.getDecoder().decode(item.path("data").asText());
        assertEquals(item.path("dataLen").asInt(), frame.length, "dataLen 应为帧实际字节数");
        assertEquals("0064FFFF300901BBB6D3ADB9E2C1D93258", hex(frame),
                "帧应与厂商文档「立即播放欢迎光临」示例字节一致");
    }

    @Test
    void exitVoiceFrameCarriesGbkTextAndValidCrc() {
        JsonNode item = voiceItem(protocol.buildPushResponse(true, "一路顺风"));
        byte[] frame = Base64.getDecoder().decode(item.path("data").asText());
        // 帧头：DA=00 VR=64 PN=FFFF CMD=30 DL=09(1+8) OTP=01
        assertArrayEquals(
                new byte[] {0x00, 0x64, (byte) 0xFF, (byte) 0xFF, 0x30, 0x09, 0x01},
                java.util.Arrays.copyOfRange(frame, 0, 7));
        // TEXT = GBK 编码文本
        byte[] text = "一路顺风".getBytes(GBK);
        assertArrayEquals(text, java.util.Arrays.copyOfRange(frame, 7, 7 + text.length));
        // 末尾两字节为 DA..TEXT 的 CRC16-Modbus（小端）
        int expected = crc16Modbus(frame, frame.length - 2);
        assertEquals((expected & 0xFF), frame[frame.length - 2] & 0xFF);
        assertEquals(((expected >>> 8) & 0xFF), frame[frame.length - 1] & 0xFF);
    }

    @Test
    void noVoiceWhenTextBlankOrNull() {
        assertFalse(protocol.buildPushResponse(true, null).path("Response_AlarmInfoPlate").has("serialData"));
        assertFalse(protocol.buildPushResponse(false, "  ").path("Response_AlarmInfoPlate").has("serialData"));
    }

    @Test
    void pollOpenWithVoiceCarriesWelcomeFrame() {
        // 指令（手动）开闸经轮询下发：OPEN 应附语音 serialData，帧与推送已知向量一致
        JsonNode item = voiceItem(protocol.buildPollResponse(openCommand(), "欢迎光临"));
        assertEquals("0064FFFF300901BBB6D3ADB9E2C1D93258", hex(Base64.getDecoder().decode(item.path("data").asText())));
    }

    @Test
    void pollOpenWithoutVoiceHasNoSerialData() {
        assertFalse(protocol.buildPollResponse(openCommand(), null).path("Response_AlarmInfoPlate").has("serialData"));
    }

    private static DeviceCommand openCommand() {
        return new DeviceCommand(new ParkingBarrier(null, "test-barrier", "TEST001", true),
                DeviceCommand.Action.OPEN, "test");
    }

    private static JsonNode voiceItem(JsonNode response) {
        JsonNode serialData = response.path("Response_AlarmInfoPlate").path("serialData");
        assertTrue(serialData.isArray() && serialData.size() == 1, "应带回一条 serialData");
        return serialData.get(0);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
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
