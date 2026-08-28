package com.freepark.local.yolo26plate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.sitesettings.dto.Yolo26PlateSettings;
import com.freepark.local.sitesettings.service.SystemSettingsService;
import com.freepark.local.softwareplate.SoftwarePlateClient;
import com.freepark.local.softwareplate.SoftwarePlateProvider;

@Service
public class Yolo26PlateClient implements SoftwarePlateClient {

    public record BBox(double x1, double y1, double x2, double y2) {}

    public record DetectedPlate(
            BBox bbox,
            double detectConfidence,
            int cls,
            List<double[]> keypoints,
            String plate,
            double plateConfidence,
            String plateColorZh,
            PlateColor plateColor,
            double plateColorConfidence,
            String error,
            Double score,
            Boolean plateValid,
            Boolean suppressed) {
        public DetectedPlate(
                BBox bbox, double detectConfidence, int cls, List<double[]> keypoints,
                String plate, double plateConfidence, String plateColorZh, PlateColor plateColor,
                double plateColorConfidence, String error) {
            this(bbox, detectConfidence, cls, keypoints, plate, plateConfidence, plateColorZh, plateColor,
                    plateColorConfidence, error, null, null, null);
        }

        public DetectedPlate withRanking(double score, boolean plateValid, boolean suppressed) {
            return new DetectedPlate(bbox, detectConfidence, cls, keypoints, plate, plateConfidence, plateColorZh,
                    plateColor, plateColorConfidence, error, score, plateValid, suppressed);
        }
    }

    public record RecognitionResult(
            String imageId,
            int elapsedMs,
            int count,
            List<DetectedPlate> plates,
            DetectedPlate best,
            String device,
            String upstreamBaseUrl) {
    }

    // 中国大陆车牌省份简称 + 常见合法首位（不含「危」这种 OCR 幻觉字符）
    private static final Set<String> CN_PROVINCE_PREFIX = new HashSet<>(Arrays.asList(
            "京", "津", "沪", "渝", "冀", "豫", "云", "辽", "黑", "湘", "皖", "鲁",
            "新", "苏", "浙", "赣", "鄂", "桂", "甘", "晋", "蒙", "陕", "吉", "闽",
            "贵", "粤", "青", "藏", "川", "宁", "琼", "使", "领", "学", "警", "港", "澳"));

    // 第二位：字母（新能源 8 位车牌第二位仍可以是字母，第三位才开始有数字）
    private static final Pattern SECOND_CHAR_LETTER = Pattern.compile("^[A-Z]$");
    // 车牌整体结构：1位省份 + 1位字母 + 5~6位车牌序号（新能源 8 位；常规 7 位；港澳入境内地牌前位后结构略有差异也兼容）
    private static final Pattern PLATE_SHAPE = Pattern.compile("^[\\u4e00-\\u9fa5A-Z][A-Z][A-Z0-9]{5,6}$");

    private static final double NMS_IOU_THRESHOLD = 0.6;

    private static final Logger log = LoggerFactory.getLogger(Yolo26PlateClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient baseClient;

    public Yolo26PlateClient() {
        this.objectMapper = new ObjectMapper();
        this.baseClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public SoftwarePlateProvider provider() {
        return SoftwarePlateProvider.YOLO26_PLATE;
    }

    public RecognitionResult recognize(Yolo26PlateSettings settings, byte[] image, String originalName) {
        return recognize(settings, image, originalName, null);
    }

    @Override
    public RecognitionResult recognize(byte[] imageBytes, String originalName, String imageId, Double minConfidenceOverride) {
        // 外部接口调用时不依赖注入 settings，而是由 Router 先解析 settings 再传。此处留空不使用。
        throw new UnsupportedOperationException(
                "Yolo26PlateClient requires settings-aware call; use recognize(Yolo26PlateSettings, byte[], String, Double)");
    }

    public RecognitionResult recognize(Yolo26PlateSettings settings, byte[] image, String originalName, Double minConfidenceOverride) {
        if (!settings.enabled()) {
            throw new BusinessException(ErrorCode.YOLO26_PLATE_DISABLED);
        }
        if (image == null || image.length == 0) {
            throw new BusinessException(ErrorCode.YOLO26_PLATE_EMPTY_IMAGE);
        }
        Yolo26PlateSettings actual = settings;
        if (minConfidenceOverride != null) {
            double v = Math.min(1.0, Math.max(0.0, minConfidenceOverride));
            actual = new Yolo26PlateSettings(actual.enabled(), actual.baseUrl(), v,
                    actual.connectTimeoutMs(), actual.readTimeoutMs());
        }
        String baseUrl = actual.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_YOLO26_PLATE_CONFIG, "baseUrl");
        }

        String boundary = "----FreeparkYolo26" + UUID.randomUUID().toString().replace("-", "");
        String imageId = originalName == null || originalName.isBlank()
                ? UUID.randomUUID().toString()
                : originalName;
        double minConf = actual.minConfidence() == null ? 0.25 : actual.minConfidence();

        byte[] body = buildMultipartBody(boundary, imageId, minConf, originalName, image);

        if (log.isDebugEnabled()) {
            String head = new String(body, 0, Math.min(400, body.length), StandardCharsets.UTF_8);
            log.debug("yolo26-plate request sample: boundary={}, bodyBytes={}, url={}\n{}",
                    boundary, body.length, baseUrl + "/api/v1/plate/recognize", head);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/plate/recognize"))
                .timeout(Duration.ofMillis(actual.readTimeoutMs() == null ? 60_000 : actual.readTimeoutMs()))
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .setHeader("X-Image-Id", imageId)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpClient client = baseClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(actual.connectTimeoutMs() == null ? 5_000 : actual.connectTimeoutMs()))
                .build();

        HttpResponse<byte[]> response;
        try {
            response = client.send(request, BodyHandlers.ofByteArray());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("yolo26-plate upstream failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.YOLO26_PLATE_UPSTREAM_FAILED, baseUrl + ": " + e.getMessage());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String hint = "";
            try {
                hint = new String(response.body(), StandardCharsets.UTF_8);
                if (hint.length() > 400) {
                    hint = hint.substring(0, 400);
                }
            } catch (Exception ignored) {}
            log.warn("yolo26-plate upstream HTTP {}: {}", response.statusCode(), hint);
            throw new BusinessException(
                    ErrorCode.YOLO26_PLATE_UPSTREAM_FAILED,
                    "HTTP " + response.statusCode() + (hint.isBlank() ? "" : (" :: " + hint)));
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.YOLO26_PLATE_UPSTREAM_FAILED, "bad json: " + e.getMessage());
        }
        return parse(root, baseUrl);
    }

    private RecognitionResult parse(JsonNode root, String baseUrl) {
        String imageId = root.path("imageId").asText("");
        int elapsedMs = root.path("elapsedMs").asInt(0);
        int count = root.path("count").asInt(0);
        String device = root.path("device").asText("");

        // 上游已经有 best 首选，先记下它的 plate 内容做「模型推荐锚点」
        String upstreamBestPlate = null;
        JsonNode bestNode = root.path("best");
        if (!bestNode.isNull() && !bestNode.isMissingNode()) {
            upstreamBestPlate = bestNode.path("plate").asText("");
            if (upstreamBestPlate.isBlank()) upstreamBestPlate = null;
        }

        List<DetectedPlate> raw = new ArrayList<>();
        JsonNode platesNode = root.path("plates");
        if (platesNode.isArray()) {
            for (JsonNode p : platesNode) {
                raw.add(parsePlate(p));
            }
        } else if (upstreamBestPlate != null) {
            raw.add(parsePlate(bestNode));
        }

        // 排序：综合分 detectConfidence × plateConfidence，非法车牌额外降权；最后再按综合分降序
        List<ScoredCandidate> scored = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            DetectedPlate p = raw.get(i);
            boolean valid = isValidCnPlate(p.plate());
            double s = scoreCandidate(p, valid, upstreamBestPlate);
            scored.add(new ScoredCandidate(i, p, s, valid));
        }
        scored.sort(Comparator.comparingDouble((ScoredCandidate c) -> c.score).reversed());

        // NMS 去重：高 IoU 的 bbox 只保留综合分最高的一个（标记其余为 suppressed）
        BitSet suppressed = new BitSet(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            if (suppressed.get(i)) continue;
            ScoredCandidate a = scored.get(i);
            for (int j = i + 1; j < scored.size(); j++) {
                if (suppressed.get(j)) continue;
                ScoredCandidate b = scored.get(j);
                if (iou(a.plate.bbox(), b.plate.bbox()) >= NMS_IOU_THRESHOLD) {
                    suppressed.set(j);
                }
            }
        }

        List<DetectedPlate> resultPlates = new ArrayList<>(scored.size());
        DetectedPlate best = null;
        for (int i = 0; i < scored.size(); i++) {
            ScoredCandidate c = scored.get(i);
            boolean isSuppressed = suppressed.get(i);
            DetectedPlate enriched = c.plate.withRanking(round4(c.score), c.valid, isSuppressed);
            resultPlates.add(enriched);
            // best：第一个「未被抑制」的候选；如果全部被抑制（理论上不会）就回退第一个有效候选
            if (best == null && !isSuppressed) {
                best = enriched;
            }
        }
        if (best == null && !resultPlates.isEmpty()) {
            best = resultPlates.get(0);
        }

        return new RecognitionResult(imageId, elapsedMs, count, resultPlates, best, device, baseUrl);
    }

    private static final class ScoredCandidate {
        final int index;
        final DetectedPlate plate;
        final double score;
        final boolean valid;
        ScoredCandidate(int index, DetectedPlate plate, double score, boolean valid) {
            this.index = index; this.plate = plate; this.score = score; this.valid = valid;
        }
    }

    private static double scoreCandidate(DetectedPlate p, boolean valid, String upstreamBestPlate) {
        double base = p.detectConfidence() * 0.55 + p.plateConfidence() * 0.45;
        if (!valid) {
            base *= 0.15; // 非法车牌强降权
        } else {
            // 合法且长度更「标准」的（7 位蓝黄牌 / 8 位新能源）略加分
            int len = codePointLen(p.plate());
            if (len == 7 || len == 8) base += 0.02;
        }
        // 和上游 best 的 plate 文本（归一化后）完全一致，再加一点锚点分，但不超过非法降权的影响
        if (upstreamBestPlate != null && upstreamBestPlate.equalsIgnoreCase(p.plate())) {
            base += 0.01;
        }
        return Math.min(1.0, Math.max(0.0, base));
    }

    public static boolean isValidPlateShapePublic(String plate) { return isValidCnPlate(plate); }

    private static int codePointLen(String s) {
        if (s == null) return 0;
        return s.codePointCount(0, s.length());
    }

    private static boolean isValidCnPlate(String plate) {
        if (plate == null || plate.isBlank()) return false;
        String t = plate.trim().toUpperCase();
        int len = codePointLen(t);
        if (len < 7 || len > 8) return false;
        String first = new String(Character.toChars(t.codePointAt(0)));
        if (!CN_PROVINCE_PREFIX.contains(first)) return false;
        int secondIdx = Character.offsetByCodePoints(t, 0, 1);
        String second = t.substring(secondIdx, Character.offsetByCodePoints(t, secondIdx, 1));
        if (!SECOND_CHAR_LETTER.matcher(second).matches()) return false;
        return PLATE_SHAPE.matcher(t).matches();
    }

    private static double iou(BBox a, BBox b) {
        double x1 = Math.max(a.x1(), b.x1());
        double y1 = Math.max(a.y1(), b.y1());
        double x2 = Math.min(a.x2(), b.x2());
        double y2 = Math.min(a.y2(), b.y2());
        double interW = Math.max(0, x2 - x1);
        double interH = Math.max(0, y2 - y1);
        double inter = interW * interH;
        if (inter <= 0) return 0;
        double areaA = Math.max(0, (a.x2() - a.x1())) * Math.max(0, (a.y2() - a.y1()));
        double areaB = Math.max(0, (b.x2() - b.x1())) * Math.max(0, (b.y2() - b.y1()));
        double union = areaA + areaB - inter;
        return union <= 0 ? 0 : (inter / union);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private DetectedPlate parsePlate(JsonNode p) {
        JsonNode bboxNode = p.path("bbox");
        BBox bbox = new BBox(
                bboxNode.path(0).asDouble(0.0),
                bboxNode.path(1).asDouble(0.0),
                bboxNode.path(2).asDouble(0.0),
                bboxNode.path(3).asDouble(0.0));
        List<double[]> kpts = new ArrayList<>();
        JsonNode kptNode = p.path("keypoints");
        if (kptNode.isArray()) {
            for (JsonNode k : kptNode) {
                kpts.add(new double[] { k.path(0).asDouble(0.0), k.path(1).asDouble(0.0) });
            }
        }
        String colorRaw = p.path("plateColor").asText("");
        PlateColor color = parsePlateColor(colorRaw);
        String err = p.path("error").asText("");
        return new DetectedPlate(
                bbox,
                p.path("detectConfidence").asDouble(0.0),
                p.path("cls").asInt(0),
                kpts,
                p.path("plate").asText(""),
                p.path("plateConfidence").asDouble(0.0),
                p.path("plateColorZh").asText(""),
                color,
                p.path("plateColorConfidence").asDouble(0.0),
                err.isBlank() ? null : err);
    }

    private static PlateColor parsePlateColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // 1) 上游会返回英文（如 BLUE / YELLOW / YELLOW_GREEN），优先直接匹配
        try {
            return PlateColor.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignore) {}
        // 2) 中文兼容（COLOR_CN_TO_EN 之外的：黄绿色等）
        String t = raw.trim();
        switch (t) {
            case "蓝色": return PlateColor.BLUE;
            case "黄色": return PlateColor.YELLOW;
            case "绿色": return PlateColor.GREEN;
            case "黄绿色":
            case "黄绿":
            case "黄色绿色":
                return PlateColor.YELLOW_GREEN;
            case "白色": return PlateColor.WHITE;
            case "黑色": return PlateColor.BLACK;
            default: return PlateColor.OTHER;
        }
    }

    private byte[] buildMultipartBody(
            String boundary,
            String imageId,
            double minConf,
            String originalName,
            byte[] image) {
        String safeName = originalName == null || originalName.isBlank() ? "image.jpg" : originalName;
        StringBuilder sb = new StringBuilder();
        addField(sb, boundary, "image_id", imageId);
        addField(sb, boundary, "min_conf", Double.toString(minConf));
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"image\"; filename=\"").append(safeName).append("\"\r\n");
        sb.append("Content-Type: application/octet-stream\r\n\r\n");
        byte[] head = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[head.length + image.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(image, 0, out, head.length, image.length);
        System.arraycopy(tail, 0, out, head.length + image.length, tail.length);
        return out;
    }

    private static void addField(StringBuilder sb, String boundary, String name, String value) {
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        sb.append(value).append("\r\n");
    }
}
