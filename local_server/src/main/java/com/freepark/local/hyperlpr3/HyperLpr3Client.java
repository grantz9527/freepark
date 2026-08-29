package com.freepark.local.hyperlpr3;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.softwareplate.SoftwarePlateClient;
import com.freepark.local.softwareplate.SoftwarePlateProvider;
import com.freepark.local.softwareplate.dto.HyperLpr3Settings;
import com.freepark.local.sitesettings.service.SystemSettingsService;
import com.freepark.local.yolo26plate.Yolo26PlateClient;

@Service
public class HyperLpr3Client implements SoftwarePlateClient {

    private static final int SUCCESS_CODE = 5000;
    private static final double NMS_IOU_THRESHOLD = 0.6;
    private static final Logger log = LoggerFactory.getLogger(HyperLpr3Client.class);

    private final ObjectMapper objectMapper;
    private final HttpClient baseClient;
    private final SystemSettingsService systemSettingsService;

    public HyperLpr3Client(SystemSettingsService systemSettingsService) {
        this.objectMapper = new ObjectMapper();
        this.baseClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.systemSettingsService = systemSettingsService;
    }

    /**
     * 供 Frigate 事件补全等内部链路使用：不受「软件车牌识别」enabled 开关限制，
     * 优先复用站点设置中的 HyperLPR3 地址，未配置时回退默认本机 8715。
     */
    public Yolo26PlateClient.DetectedPlate recognizeBestEffort(byte[] imageBytes, String imageId) {
        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("hyperlpr3 recognizeBestEffort skipped: empty image");
            return null;
        }
        HyperLpr3Settings s = systemSettingsService.getHyperLpr3Settings();
        String baseUrl = (s.baseUrl() == null || s.baseUrl().isBlank())
                ? SystemSettingsService.DEFAULT_HYPER_LPR3_BASE_URL
                : s.baseUrl();
        Double minConf = s.minConfidence() == null
                ? SystemSettingsService.DEFAULT_HYPER_LPR3_MIN_CONF
                : s.minConfidence();
        Integer connectMs = s.connectTimeoutMs() == null
                ? SystemSettingsService.DEFAULT_HYPER_LPR3_CONNECT_TIMEOUT_MS
                : s.connectTimeoutMs();
        Integer readMs = s.readTimeoutMs() == null
                ? SystemSettingsService.DEFAULT_HYPER_LPR3_READ_TIMEOUT_MS
                : s.readTimeoutMs();
        try {
            Yolo26PlateClient.RecognitionResult result = recognize(
                    new HyperLpr3Settings(true, baseUrl, minConf, connectMs, readMs),
                    imageBytes,
                    imageId == null || imageId.isBlank() ? "frigate-lpr" : imageId,
                    null);
            return result.best();
        } catch (Exception ex) {
            log.warn("hyperlpr3 recognizeBestEffort failed: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public SoftwarePlateProvider provider() {
        return SoftwarePlateProvider.HYPER_LPR3;
    }

    public Yolo26PlateClient.RecognitionResult recognize(HyperLpr3Settings settings, byte[] image, String originalName) {
        return recognize(settings, image, originalName, null);
    }

    public Yolo26PlateClient.RecognitionResult recognize(HyperLpr3Settings settings, byte[] image, String originalName,
            Double minConfidenceOverride) {
        if (!settings.enabled()) {
            throw new BusinessException(ErrorCode.SOFTWARE_PLATE_DISABLED);
        }
        if (image == null || image.length == 0) {
            throw new BusinessException(ErrorCode.SOFTWARE_PLATE_EMPTY_IMAGE);
        }
        HyperLpr3Settings actual = settings.withMinConfidenceOverride(minConfidenceOverride);
        String baseUrl = actual.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_HYPER_LPR3_CONFIG, "baseUrl");
        }

        String boundary = "----FreeparkHl3" + UUID.randomUUID().toString().replace("-", "");
        String imageId = originalName == null || originalName.isBlank() ? UUID.randomUUID().toString() : originalName;
        double minConf = actual.minConfidence() == null ? 0.6 : actual.minConfidence();

        byte[] body = buildMultipartBody(boundary, imageId, originalName, image);

        if (log.isDebugEnabled()) {
            String head = new String(body, 0, Math.min(400, body.length), StandardCharsets.UTF_8);
            log.debug("hyperlpr3 request sample: boundary={}, bodyBytes={}, url={}\n{}",
                    boundary, body.length, baseUrl + "/api/v1/rec", head);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/rec"))
                .timeout(Duration.ofMillis(actual.readTimeoutMs() == null ? 60_000 : actual.readTimeoutMs()))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .setHeader("X-Image-Id", imageId)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpClient client = baseClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(actual.connectTimeoutMs() == null ? 5_000 : actual.connectTimeoutMs()))
                .build();

        HttpResponse<byte[]> response;
        try {
            response = client.send(request, BodyHandlers.ofByteArray());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("hyperlpr3 upstream failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.YOLO26_PLATE_UPSTREAM_FAILED, baseUrl + ": " + e.getMessage());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String hint = "";
            try { hint = new String(response.body(), 0, Math.min(400, response.body().length), StandardCharsets.UTF_8); }
            catch (Exception ignore) {}
            log.warn("hyperlpr3 upstream status={} body={}", response.statusCode(), hint);
            throw new BusinessException(ErrorCode.YOLO26_PLATE_UPSTREAM_FAILED, "HTTP " + response.statusCode());
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.warn("hyperlpr3 response parse failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.YOLO26_PLATE_UPSTREAM_FAILED, "bad JSON");
        }

        int code = root.path("code").asInt(-1);
        String msg = root.path("msg").asText("");
        if (code != SUCCESS_CODE) {
            log.warn("hyperlpr3 business error code={} msg={}", code, msg);
            throw new BusinessException(ErrorCode.YOLO26_PLATE_UPSTREAM_FAILED, msg);
        }

        JsonNode plateList = root.path("result").path("plate_list");
        long start = System.currentTimeMillis();
        List<Yolo26PlateClient.DetectedPlate> raw = new ArrayList<>();
        if (plateList.isArray()) {
            for (JsonNode node : plateList) {
                raw.add(parsePlate(node));
            }
        }

        // 过滤 min confidence
        double finalMinConf = minConf;
        List<Yolo26PlateClient.DetectedPlate> filtered = raw.stream()
                .filter(p -> p.plateConfidence() >= finalMinConf)
                .toList();

        // 合法性 + NMS + 综合分
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            Yolo26PlateClient.DetectedPlate p = filtered.get(i);
            boolean valid = Yolo26PlateClient.isValidPlateShapePublic(p.plate());
            double s = (p.plateConfidence() * 0.9) + (valid ? 0.1 : -0.2);
            scored.add(new Scored(i, p, Math.max(0.0, Math.min(1.0, s)), valid));
        }
        scored.sort(Comparator.comparingDouble((Scored c) -> c.score).reversed());

        BitSet suppressed = new BitSet(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            if (suppressed.get(i)) continue;
            Yolo26PlateClient.DetectedPlate a = scored.get(i).plate();
            for (int j = i + 1; j < scored.size(); j++) {
                if (suppressed.get(j)) continue;
                Yolo26PlateClient.DetectedPlate b = scored.get(j).plate();
                if (iou(a.bbox(), b.bbox()) >= NMS_IOU_THRESHOLD) suppressed.set(j);
            }
        }

        List<Yolo26PlateClient.DetectedPlate> resultPlates = new ArrayList<>(scored.size());
        Yolo26PlateClient.DetectedPlate best = null;
        for (int i = 0; i < scored.size(); i++) {
            Scored c = scored.get(i);
            boolean isSuppressed = suppressed.get(i);
            Yolo26PlateClient.DetectedPlate enriched = c.plate().withRanking(round4(c.score), c.valid, isSuppressed);
            resultPlates.add(enriched);
            if (best == null && !isSuppressed) best = enriched;
        }
        if (best == null && !resultPlates.isEmpty()) best = resultPlates.get(0);

        int elapsedMs = (int) Math.max(0, System.currentTimeMillis() - start);
        return new Yolo26PlateClient.RecognitionResult(imageId, elapsedMs, resultPlates.size(), resultPlates, best,
                "cpu", baseUrl);
    }

    @Override
    public Yolo26PlateClient.RecognitionResult recognize(byte[] imageBytes, String originalName, String imageId,
            Double minConfidenceOverride) {
        throw new UnsupportedOperationException(
                "HyperLpr3Client requires settings-aware call; use recognize(HyperLpr3Settings, byte[], String, Double)");
    }

    // ---------- 内部 ----------

    private record Scored(int i, Yolo26PlateClient.DetectedPlate plate, double score, boolean valid) {}

    private Yolo26PlateClient.DetectedPlate parsePlate(JsonNode node) {
        String code = node.path("code").asText("");
        double conf = node.path("conf").asDouble(0.0);
        String type = node.path("plate_type").asText("");
        List<double[]> box = new ArrayList<>();
        JsonNode boxNode = node.path("box");
        double x1 = 0, y1 = 0, x2 = 0, y2 = 0;
        if (boxNode.isArray() && boxNode.size() >= 4) {
            x1 = boxNode.get(0).asDouble(); y1 = boxNode.get(1).asDouble();
            x2 = boxNode.get(2).asDouble(); y2 = boxNode.get(3).asDouble();
        }
        // HyperLPR3 box 输出一般是 [x1,y1,x2,y2] int 数组，直接用
        // keypoints：HyperLPR3 不给出角点，用 bbox 四角近似补（便于前端/调试统一展示）
        List<double[]> keypoints = List.of(
                new double[] { x1, y1 }, new double[] { x2, y1 },
                new double[] { x2, y2 }, new double[] { x1, y2 });
        return new Yolo26PlateClient.DetectedPlate(
                new Yolo26PlateClient.BBox(x1, y1, x2, y2),
                Math.max(0.0, Math.min(1.0, conf)), 0, keypoints,
                code, Math.max(0.0, Math.min(1.0, conf)),
                type, mapPlateTypeToColor(type), Math.max(0.0, Math.min(1.0, conf)), null);
    }

    static PlateColor mapPlateTypeToColor(String plateType) {
        if (plateType == null || plateType.isBlank()) return null;
        String t = plateType.trim();
        return switch (t) {
            case "蓝牌", "蓝", "蓝色", "小型汽车号牌" -> PlateColor.BLUE;
            case "黄牌", "黄", "黄色", "大型汽车号牌", "挂车号牌", "教", "教练" -> PlateColor.YELLOW;
            case "绿牌", "绿", "绿色", "新能源" -> PlateColor.GREEN;
            case "黄绿色", "黄绿", "黄色绿色" -> PlateColor.YELLOW_GREEN;
            case "白牌", "白", "白色", "警", "使", "领" -> PlateColor.WHITE;
            case "黑牌", "黑", "黑色", "港澳" -> PlateColor.BLACK;
            default -> PlateColor.OTHER;
        };
    }

    private static byte[] buildMultipartBody(String boundary, String imageId, String originalName, byte[] image) {
        String raw = (originalName != null && !originalName.isBlank()) ? originalName
                : (imageId != null && !imageId.isBlank()) ? imageId : "image.png";
        String lower = raw.toLowerCase();
        // HyperLPR3 强制校验文件名后缀（png/jpg/jpe），Frigate 事件ID 无后缀会被拒(5007)，这里兜底补 .jpg
        String filename = (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpe"))
                ? raw
                : raw + ".jpg";
        String mime = lower.endsWith(".png") ? "image/png" : "image/jpeg";
        String CRLF = "\r\n";
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append(CRLF);
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"").append(CRLF);
        sb.append("Content-Type: ").append(mime).append(CRLF).append(CRLF);
        byte[] head = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tail = (CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + image.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(image, 0, body, head.length, image.length);
        System.arraycopy(tail, 0, body, head.length + image.length, tail.length);
        return body;
    }

    private static double iou(Yolo26PlateClient.BBox a, Yolo26PlateClient.BBox b) {
        double x1 = Math.max(a.x1(), b.x1());
        double y1 = Math.max(a.y1(), b.y1());
        double x2 = Math.min(a.x2(), b.x2());
        double y2 = Math.min(a.y2(), b.y2());
        double interW = Math.max(0, x2 - x1), interH = Math.max(0, y2 - y1);
        double inter = interW * interH;
        if (inter <= 0) return 0;
        double areaA = Math.max(0, a.x2() - a.x1()) * Math.max(0, a.y2() - a.y1());
        double areaB = Math.max(0, b.x2() - b.x1()) * Math.max(0, b.y2() - b.y1());
        double union = areaA + areaB - inter;
        return union <= 0 ? 0 : inter / union;
    }

    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
