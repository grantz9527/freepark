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
import com.freepark.local.storage.ImageStorageService;

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

    private final ImageStorageService imageStorage;

    public ZhenshiProtocol(ImageStorageService imageStorage) {
        this.imageStorage = imageStorage;
    }

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

        // 抓拍图 base64 落盘到系统设置指定的图片存储目录，数据库只存路径/URL
        String imageRef = null;
        String eventImage = null;
        String rawImage = extractImage(plateResult);
        if (rawImage != null) {
            String relative = imageStorage.saveBase64Image(rawImage, device.getCode());
            imageRef = relative;
            eventImage = imageStorage.toPublicUrl(relative);
        }
        Instant capturedAt = extractTimestamp(plateResult);

        RecognitionRecord record = new RecognitionRecord(device, license, color, imageRef, direction, capturedAt);
        record.setEventImage(eventImage);
        return record;
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
     *
     * 臻识协议车牌颜色字段为 colorType（数字序号）：
     * 0 未知、1 蓝色、2 黄色、3 白色、4 黑色、5 绿色（新能源渐变绿）、6 黄绿（部分固件）。
     * 新固件（MQTT 等）同一取值出现在 plates[].color；个别旧固件用 color。
     * colorName / plateColor 为个别固件或字符串扩展字段，仅作兜底。
     */
    private PlateColor parseColor(JsonNode plateResult) {
        PlateColor fromColorType = mapZhenshiColorType(plateResult.path("colorType").asInt(-1));
        if (fromColorType != null) {
            return fromColorType;
        }
        JsonNode plates = plateResult.path("plates");
        if (plates.isArray() && !plates.isEmpty()) {
            PlateColor fromPlates = mapZhenshiColorType(plates.get(0).path("color").asInt(-1));
            if (fromPlates != null) {
                return fromPlates;
            }
        }
        PlateColor fromColor = mapZhenshiColorType(plateResult.path("color").asInt(-1));
        if (fromColor != null) {
            return fromColor;
        }
        PlateColor fromName = tryColor(plateResult.path("colorName").asText(""));
        if (fromName != null) {
            return fromName;
        }
        return tryColor(plateResult.path("plateColor").asText(""));
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

    /**
     * 臻识 colorType/color 序号 → 系统 PlateColor。
     * 序号与国标车牌颜色一致：1 蓝、2 黄、3 白、4 黑、5 绿（新能源）。
     */
    private PlateColor mapZhenshiColorType(int idx) {
        return switch (idx) {
            case 1 -> PlateColor.BLUE;          // 蓝色（小型汽车）
            case 2 -> PlateColor.YELLOW;        // 黄色（大型汽车等）
            case 3 -> PlateColor.WHITE;         // 白色（警用等）
            case 4 -> PlateColor.BLACK;         // 黑色（涉外）
            case 5 -> PlateColor.GREEN;         // 绿色（新能源渐变绿）
            case 6 -> PlateColor.YELLOW_GREEN;  // 黄绿（大型新能源，部分固件）
            default -> null;                    // 0 未知 及其他
        };
    }

    private PlateColor mapColorAlias(String name) {
        return switch (name) {
            case "蓝", "蓝色", "蓝底", "蓝底白字" -> PlateColor.BLUE;
            case "黄", "黄色", "黄底", "黄底黑字" -> PlateColor.YELLOW;
            case "白", "白色", "白底", "白底黑字" -> PlateColor.WHITE;
            case "黑", "黑色", "黑底", "黑底白字" -> PlateColor.BLACK;
            case "绿", "绿色", "绿底", "绿底黑字", "渐变绿" -> PlateColor.GREEN;
            case "黄绿", "黄绿牌", "黄绿底", "黄绿底黑字", "黄绿双拼" -> PlateColor.YELLOW_GREEN;
            default -> null;
        };
    }
}
