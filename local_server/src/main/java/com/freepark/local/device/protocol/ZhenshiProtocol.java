package com.freepark.local.device.protocol;

import java.time.Instant;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.freepark.local.device.dto.DevicePollResponse;
import com.freepark.local.domain.DeviceCommand;
import com.freepark.local.domain.ParkingBarrier;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.RecognitionRecord;

/**
 * 臻识（ZHENSHI）识别相机协议适配。
 *
 * 臻识500等相机采用 HTTP 推送模型：识别到车牌后主动 POST 到服务器，
 * 服务器在 HTTP 响应中返回开闸指令。
 *
 * 推送报文根结构为 AlarmInfoPlate，内含 serialno（设备序列号）、
 * result.PlateResult（车牌、图像、时间戳等）。
 *
 * 响应报文为 Response_AlarmInfoPlate，info="ok" 表示开闸，info="no" 表示不开闸。
 */
@Component
public class ZhenshiProtocol implements CameraProtocol {

    public static final String BRAND = "ZHENSHI";

    @Override
    public String brand() {
        return BRAND;
    }

    /**
     * 从推送报文中提取设备序列号，用于匹配 ParkingBarrier.code。
     * 路径：AlarmInfoPlate.serialno
     */
    @Override
    public String extractDeviceId(JsonNode pushData) {
        return pushData.path("AlarmInfoPlate").path("serialno").asText("").trim();
    }

    /**
     * 解析臻识 AlarmInfoPlate 推送报文为识别记录。
     * 关键字段：license（车牌）、imageFile/imageFragmentFile（抓拍图 base64）、
     * direction（行进方向）、timeStamp.Timeval.sec/usec（抓拍时间）。
     */
    @Override
    public RecognitionRecord parsePush(ParkingBarrier device, JsonNode pushData) {
        JsonNode plateResult = pushData.path("AlarmInfoPlate").path("result").path("PlateResult");
        String license = plateResult.path("license").asText("").trim();
        String direction = String.valueOf(plateResult.path("direction").asInt(0));
        PlateColor color = parseColor(plateResult);

        String imageRef = extractImage(plateResult);
        Instant capturedAt = extractTimestamp(plateResult);

        return new RecognitionRecord(device, license, color, imageRef, direction, capturedAt);
    }

    /**
     * 生成臻识推送响应：info="ok" 开闸，info="no" 不开闸。
     */
    @Override
    public JsonNode buildPushResponse(boolean openGate) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode resp = root.putObject("Response_AlarmInfoPlate");
        resp.put("info", openGate ? "ok" : "no");
        resp.put("channelNum", 0);
        resp.put("is_pay", "true");
        return root;
    }

    /**
     * 轮询返回报文（支持轮询模型的臻识老款设备）。
     */
    @Override
    public DevicePollResponse buildPollResponse(DeviceCommand command) {
        if (command == null) {
            return DevicePollResponse.empty();
        }
        return DevicePollResponse.of(command.getAction().name().toLowerCase(), command.getId(), command.getCreatedAt());
    }

    private String extractImage(JsonNode plateResult) {
        // 优先大图 imageFile，其次小图 imageFragmentFile
        JsonNode large = plateResult.path("imageFile");
        if (!large.isMissingNode() && !large.isNull() && !large.asText("").isEmpty()) {
            return large.asText();
        }
        JsonNode small = plateResult.path("imageFragmentFile");
        if (!small.isMissingNode() && !small.isNull() && !small.asText("").isEmpty()) {
            return small.asText();
        }
        return null;
    }

    private Instant extractTimestamp(JsonNode plateResult) {
        JsonNode timeval = plateResult.path("timeStamp").path("Timeval");
        if (timeval.has("sec")) {
            long sec = timeval.path("sec").asLong();
            long usec = timeval.path("usec").asLong(0);
            return Instant.ofEpochSecond(sec, usec * 1_000);
        }
        return Instant.now();
    }

    /**
     * 解析臻识 PlateResult 中的车牌颜色字段。
     * 常见字段：color（数字，0=蓝 1=黄 2=白 3=黑 4=绿 ...）、或 colorName / plateColor（字符串）。
     */
    private PlateColor parseColor(JsonNode plateResult) {
        JsonNode colorName = plateResult.path("colorName");
        if (!colorName.isMissingNode() && !colorName.asText("").isBlank()) {
            return tryColor(colorName.asText(""));
        }
        JsonNode plateColor = plateResult.path("plateColor");
        if (!plateColor.isMissingNode() && !plateColor.asText("").isBlank()) {
            return tryColor(plateColor.asText(""));
        }
        JsonNode colorNum = plateResult.path("color");
        if (!colorNum.isMissingNode() && !colorNum.asText("").isEmpty()) {
            return mapZhenshiColorIndex(colorNum.asInt(-1));
        }
        return null;
    }

    private PlateColor tryColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return PlateColor.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // 中文/别名映射
            return mapColorAlias(raw.trim());
        }
    }

    private PlateColor mapZhenshiColorIndex(int idx) {
        return switch (idx) {
            case 0 -> PlateColor.BLUE;
            case 1 -> PlateColor.YELLOW;
            case 2 -> PlateColor.WHITE;
            case 3 -> PlateColor.BLACK;
            case 4, 5 -> PlateColor.GREEN;
            case 6 -> PlateColor.YELLOW_GREEN;
            default -> null;
        };
    }

    private PlateColor mapColorAlias(String name) {
        return switch (name) {
            case "蓝", "蓝色", "蓝底" -> PlateColor.BLUE;
            case "黄", "黄色", "黄底" -> PlateColor.YELLOW;
            case "白", "白色", "白底" -> PlateColor.WHITE;
            case "黑", "黑色", "黑底" -> PlateColor.BLACK;
            case "绿", "绿色", "绿底", "渐变绿" -> PlateColor.GREEN;
            case "黄绿", "黄底黑字" -> PlateColor.YELLOW_GREEN;
            default -> null;
        };
    }
}
