package com.freepark.local.frigate.service;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.freepark.local.domain.FrigateLinkStatus;
import com.freepark.local.domain.FrigateSettings;
import com.freepark.local.domain.FrigateSettingsRepository;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.hyperlpr3.HyperLpr3Client;
import com.freepark.local.storage.ImageStorageService;
import com.freepark.local.yolo26plate.Yolo26PlateClient;

import jakarta.annotation.PreDestroy;

@Component
public class FrigateMqttSubscriber {

    private static final Logger log = LoggerFactory.getLogger(FrigateMqttSubscriber.class);

    /** LPR 补全去重窗口：同一 Frigate 事件（new/update/end 多次推送）只补全一次。 */
    private static final long LPR_DEDUP_WINDOW_MS = 120_000;
    private static final int LPR_DEDUP_MAX_SIZE = 500;

    /** Track 投票窗口：track 停止更新该时长后提交投票结果；track 活跃时最长等待该时长（超上限强制提交）。 */
    private static final long TRACK_VOTE_DELAY_MS = 1_000;
    private static final long TRACK_MAX_AGE_MS = 12_000;
    private static final int PENDING_MAX_SIZE = 500;

    /** 车牌级去重窗口：同一相机识别出同一合法车牌后，该窗口内不再重复入库（可配置，默认 30 秒）。 */
    @Value("${freepark.frigate.plate-dedup-window-ms:30000}")
    private long plateDedupWindowMs = 30_000;
    private static final int PLATE_DEDUP_MAX_SIZE = 2000;

    private final FrigateSettingsRepository settingsRepository;
    private final FrigateEventHandler eventHandler;
    private final HyperLpr3Client hyperLpr3Client;
    private final ImageStorageService imageStorage;
    private final JsonMapper jsonMapper;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    private final HttpClient snapshotClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Map<String, Long> lprSeenEventIds = new ConcurrentHashMap<>();
    /** trackId -> 待投票识别结果：同一 track 的多帧候选在此聚合并投票，稳定后才提交。 */
    private final Map<String, PendingTrack> pendingTracks = new ConcurrentHashMap<>();
    /** camera|plate -> 最近提交时间：同一相机同一合法车牌窗口内去重。 */
    private final Map<String, Long> submittedPlates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService voteScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "frigate-lpr-vote");
        t.setDaemon(true);
        return t;
    });

    public FrigateMqttSubscriber(
            FrigateSettingsRepository settingsRepository,
            FrigateEventHandler eventHandler,
            HyperLpr3Client hyperLpr3Client,
            ImageStorageService imageStorage,
            JsonMapper jsonMapper) {
        this.settingsRepository = settingsRepository;
        this.eventHandler = eventHandler;
        this.hyperLpr3Client = hyperLpr3Client;
        this.imageStorage = imageStorage;
        this.jsonMapper = jsonMapper;
    }

    public synchronized void reconnect() {
        disconnectQuietly();
        FrigateSettings settings = settingsRepository.findById(FrigateSettings.SINGLETON_ID).orElse(null);
        if (settings == null || !settings.isEnabled()) {
            log.info("Frigate MQTT subscriber idle (disabled or missing settings)");
            return;
        }
        try {
            MqttClient client = connectClient(settings, "freepark-frigate-" + UUID.randomUUID());
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("Frigate MQTT connected (reconnect={}) uri={}", reconnect, serverURI);
                    try {
                        subscribeTopics(client, settings.getTopicPrefix());
                        updateLinkStatus(FrigateLinkStatus.CONNECTED);
                    } catch (MqttException ex) {
                        log.warn("Frigate MQTT subscribe failed: {}", ex.getMessage());
                        updateLinkStatus(FrigateLinkStatus.FAILED);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("Frigate MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
                    updateLinkStatus(FrigateLinkStatus.FAILED);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    handleMessage(topic, payload, settings);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // no-op
                }
            });
            clientRef.set(client);
            subscribeTopics(client, settings.getTopicPrefix());
            updateLinkStatus(FrigateLinkStatus.CONNECTED);
            log.info(
                    "Frigate MQTT subscribed host={}:{} prefix={}",
                    settings.getMqttHost(),
                    settings.getMqttPort(),
                    settings.getTopicPrefix());
        } catch (Exception ex) {
            log.warn("Frigate MQTT connect failed: {}", ex.getMessage());
            updateLinkStatus(FrigateLinkStatus.FAILED);
            disconnectQuietly();
        }
    }

    public boolean testConnect(FrigateSettings settings) throws MqttException {
        MqttClient client = null;
        try {
            client = connectClient(settings, "freepark-frigate-test-" + UUID.randomUUID());
            return client.isConnected();
        } finally {
            if (client != null) {
                try {
                    if (client.isConnected()) {
                        client.disconnect();
                    }
                } catch (MqttException ignored) {
                    // ignore
                }
                try {
                    client.close();
                } catch (MqttException ignored) {
                    // ignore
                }
            }
        }
    }

    public boolean isConnected() {
        MqttClient client = clientRef.get();
        return client != null && client.isConnected();
    }

    @PreDestroy
    public synchronized void shutdown() {
        disconnectQuietly();
        voteScheduler.shutdownNow();
        pendingTracks.clear();
        submittedPlates.clear();
        lprSeenEventIds.clear();
    }

    private MqttClient connectClient(FrigateSettings settings, String clientId) throws MqttException {
        String broker = "tcp://" + settings.getMqttHost().trim() + ":" + settings.getMqttPort();
        MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(8);
        options.setKeepAliveInterval(30);
        if (settings.getMqttUsername() != null && !settings.getMqttUsername().isBlank()) {
            options.setUserName(settings.getMqttUsername().trim());
        }
        if (settings.getMqttPassword() != null && !settings.getMqttPassword().isBlank()) {
            options.setPassword(settings.getMqttPassword().toCharArray());
        }
        client.connect(options);
        return client;
    }

    private void subscribeTopics(MqttClient client, String topicPrefix) throws MqttException {
        String prefix = stripTrailingSlash(topicPrefix);
        client.subscribe(prefix + "/events", 0);
        client.subscribe(prefix + "/+/events", 0);
        client.subscribe(prefix + "/+", 0);
    }

    private void handleMessage(String topic, String payload, FrigateSettings settings) {
        try {
            String prefix = stripTrailingSlash(settings.getTopicPrefix());
            String cameraName = null;
            String plate = null;
            PlateColor plateColor = null;
            String trackId = null;
            String snapshotPath = null;
            JsonNode after = null;

            if (topic.equals(prefix + "/events") || topic.endsWith("/events")) {
                JsonNode root = jsonMapper.readTree(payload);
                after = root.path("after");
                if (after.isMissingNode() || after.isNull()) {
                    after = root;
                }
                cameraName = textOrNull(after.path("camera"));
                if (cameraName == null && topic.endsWith("/events")) {
                    cameraName = cameraFromTopic(topic, prefix);
                }
                trackId = textOrNull(after.path("id"));
                plate = extractPlate(after);
                plateColor = extractColor(after);
                snapshotPath = textOrNull(after.path("snapshot"));
                if (plate == null) {
                    plate = extractPlate(root);
                }
                if (plateColor == null) {
                    plateColor = extractColor(root);
                }
                if (snapshotPath == null) {
                    snapshotPath = textOrNull(root.path("snapshot"));
                }
            } else if (topic.startsWith(prefix + "/")) {
                // 非 events 主题：Dedicated LPR 的车牌结果通过 frigate/tracked_object_update 高频推送，
                // 相机名在 payload.camera 字段（topic 里的不是相机名）。
                JsonNode root = jsonMapper.readTree(payload);
                after = root;
                cameraName = firstNonBlank(textOrNull(root.path("camera")), cameraFromTopic(topic, prefix));
                trackId = textOrNull(root.path("id"));
                plate = extractPlate(root);
                plateColor = extractColor(root);
                snapshotPath = textOrNull(root.path("snapshot"));
            }

            // LPR 辅助：Frigate 原生 LPR 只出车牌不出颜色，且缺车牌文本时也需补全。
            // 有车牌 → 用本地 HyperLPR3 验证车牌并补颜色；无车牌 → 用快照补全车牌+颜色。
            if (cameraName != null && after != null && (plate == null || plate.isBlank() || plateColor == null)) {
                if (!tryReserveLprAttempt(trackId)) {
                    log.debug("Frigate LPR assist skipped (already attempted) camera={} trackId={}",
                            cameraName, trackId);
                } else {
                    log.info("Frigate event camera={} plate={} color={} start LPR assist",
                            cameraName, plate, plateColor == null ? "unknown" : plateColor.name());
                    LprResult lpr = lprCompleteFromEvent(after, cameraName, settings);
                    if (lpr == null) {
                        log.warn("Frigate event camera={} LPR assist returned no result", cameraName);
                    } else {
                        if (trackId != null && !trackId.isBlank()) {
                            String cam = cameraName;
                            PendingTrack pending = pendingTracks.computeIfAbsent(trackId, k -> new PendingTrack(cam));
                            // 首帧结果计入聚簇，并排程再抽 2 帧（间隔 200/400ms），避免只依赖单张快照误识别。
                            pending.setSnapshot(snapshotPath);
                            pending.recordHyperVote(lpr.plate(), lpr.plateColor(), lpr.confidence());
                            scheduleHyperLprFrames(pending, cam, settings);
                            // 立即排程投票：若 Frigate 后续文本全被过滤（无合法候选），
                            // 也能由 HyperLPR3 聚簇结果兜底提交，避免 pending 悬空。
                            scheduleVote(trackId, settings);
                        }
                        if (plate == null || plate.isBlank()) {
                            plate = lpr.plate();
                        } else {
                            String current = normalizePlate(plate);
                            String assisted = normalizePlate(lpr.plate());
                            if (assisted != null && assisted.equals(current)) {
                                log.info("Frigate plate {} verified by HyperLPR3", current);
                            } else if (assisted != null
                                    && !Yolo26PlateClient.isValidPlateShapePublic(current)
                                    && Yolo26PlateClient.isValidPlateShapePublic(assisted)) {
                                // Frigate 识别残缺/非法（如丢省份汉字）时，采用 HyperLPR3 的合法结果。
                                log.warn("Frigate plate {} invalid, use HyperLPR3 {}", current, assisted);
                                plate = assisted;
                            } else if (assisted != null) {
                                log.warn("Frigate plate {} != HyperLPR3 {}, keep Frigate plate", current, assisted);
                            }
                        }
                        if (plateColor == null) {
                            plateColor = lpr.plateColor();
                        }
                    }
                }
            }

            if (cameraName == null || plate == null || plate.isBlank()) {
                log.info(
                        "Frigate MQTT payload ignored topic={} camera={} plate={}",
                        topic, cameraName, plate);
                return;
            }
            String normalized = normalizePlate(plate);
            if (normalized == null) {
                log.info(
                        "Frigate MQTT payload ignored topic={} camera={} plate={} (unrecognizable)",
                        topic, cameraName, plate);
                return;
            }
            // 过滤明显不正确的车牌：必须符合国内车牌结构（省份汉字 + 字母 + 5~6 位）。
            if (!Yolo26PlateClient.isValidPlateShapePublic(normalized)) {
                log.info(
                        "Frigate MQTT payload ignored topic={} camera={} plate={} (invalid plate shape)",
                        topic, cameraName, normalized);
                return;
            }
            // 投票提交：同一 track 聚合多帧候选，track 稳定后选票数最高的合法车牌，
            // 避免单帧识别文本漂移（如浙BR7978 → BR7978 → R7976）导致错误入库。
            if (trackId != null && !trackId.isBlank()) {
                String cam = cameraName;
                PendingTrack pending = pendingTracks.computeIfAbsent(trackId, k -> new PendingTrack(cam));
                pending.setSnapshot(snapshotPath);
                pending.recordVote(normalized, plateColor);
                scheduleVote(trackId, settings);
                return;
            }
            // 无 trackId（少见）：直接提交，仍受车牌级去重保护（无快照则不带识别图片）。
            submitPlate(cameraName, normalized, plateColor, null, null);
        } catch (Exception ex) {
            log.warn("Frigate MQTT payload rejected on {}: {}", topic, ex.getMessage());
        }
    }

    /** 调度 track 投票任务：同 track 只保留一个待执行任务，track 停止更新后提交投票结果。 */
    private void scheduleVote(String trackId, FrigateSettings settings) {
        PendingTrack pending = pendingTracks.get(trackId);
        if (pending == null) {
            return;
        }
        // 容量保护：回收超时未决 track，防止异常场景下内存持续增长。
        if (pendingTracks.size() > PENDING_MAX_SIZE) {
            long now = System.currentTimeMillis();
            pendingTracks.entrySet().removeIf(e -> now - e.getValue().lastUpdateAt > TRACK_MAX_AGE_MS);
        }
        synchronized (pending) {
            if (pending.submitted || pending.future != null) {
                return;
            }
            pending.future = voteScheduler.schedule(
                    () -> decideAndSubmit(trackId, settings), TRACK_VOTE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** track 停止更新后执行：从合法候选票中选出最高者提交，无合法候选则用 HyperLPR3 兜底。 */
    private void decideAndSubmit(String trackId, FrigateSettings settings) {
        PendingTrack pending = pendingTracks.get(trackId);
        if (pending == null) {
            return;
        }
        String plate;
        PlateColor color;
        synchronized (pending) {
            if (pending.submitted) {
                return;
            }
            pending.future = null;
            long now = System.currentTimeMillis();
            boolean trackAlive = now - pending.lastUpdateAt < TRACK_VOTE_DELAY_MS;
            boolean ageExceeded = now - pending.createdAt > TRACK_MAX_AGE_MS;
            if (trackAlive && !ageExceeded) {
                // track 仍在更新：延长投票窗口。
                pending.future = voteScheduler.schedule(
                        () -> decideAndSubmit(trackId, settings), TRACK_VOTE_DELAY_MS, TimeUnit.MILLISECONDS);
                return;
            }
            String best = null;
            String source = null;
            HyperVote bestHyper = null;
            // HyperLPR3 优先：多帧采样聚簇（置信度和最高者），合法时直接采用；Frigate 多帧投票作为辅助/兜底。
            double bestConf = 0;
            for (Map.Entry<String, HyperVote> entry : pending.hyperVotes.entrySet()) {
                HyperVote v = entry.getValue();
                if (v.confidenceSum() > bestConf) {
                    bestConf = v.confidenceSum();
                    bestHyper = v;
                    best = entry.getKey();
                }
            }
            if (best != null && Yolo26PlateClient.isValidPlateShapePublic(best)) {
                source = "HyperLPR3";
            } else {
                best = null;
                int bestCount = 0;
                for (Map.Entry<String, Integer> entry : pending.votes.entrySet()) {
                    if (entry.getValue() > bestCount) {
                        best = entry.getKey();
                        bestCount = entry.getValue();
                    }
                }
                source = best == null ? null : "Frigate";
            }
            if (best == null) {
                log.info(
                        "Frigate LPR drop track={} camera={}: no confident plate hyper={} votes={}",
                        trackId, pending.camera, pending.hyperVotes, pending.votes);
                pending.submitted = true;
                pendingTracks.remove(trackId, pending);
                return;
            }
            plate = best;
            color = (bestHyper != null && bestHyper.color() != null)
                    ? bestHyper.color()
                    : pending.lastEventColor;
            pending.submitted = true;
            log.info(
                    "Frigate LPR submit track={} camera={} plate={} source={} hyper={} votes={}",
                    trackId, pending.camera, plate, source, pending.hyperVotes, pending.votes);
        }
        // 保存识别快照：优先事件 snapshot，缺失时回退相机最新帧；失败不阻断入库（识别图片留空）。
        String imageRef = null;
        String eventImage = null;
        byte[] snapshot = downloadSnapshot(
                firstNonBlank(pending.snapshotPath, "/api/" + pending.camera + "/latest.jpg"), settings);
        if (snapshot != null) {
            imageRef = imageStorage.saveImage(snapshot, "image/jpeg", pending.camera);
            eventImage = imageStorage.toPublicUrl(imageRef);
        } else {
            log.warn("Frigate LPR track={} camera={}: snapshot unavailable, record without image",
                    trackId, pending.camera);
        }
        submitPlate(pending.camera, plate, color, imageRef, eventImage);
        pendingTracks.remove(trackId, pending);
    }

    /** 车牌级去重 + 提交识别结果：同一相机识别出同一合法车牌在窗口内只入一次库。 */
    private void submitPlate(String cameraName, String plate, PlateColor color, String imageRef, String eventImage) {
        if (!shouldSubmitPlate(cameraName, plate)) {
            log.info(
                    "Frigate LPR drop camera={} plate={}: duplicate within {}ms",
                    cameraName, plate, plateDedupWindowMs);
            return;
        }
        log.info(
                "Frigate MQTT event camera={} plate={} color={} image={}",
                cameraName, plate, color == null ? "unknown" : color.name(), eventImage == null ? "none" : eventImage);
        eventHandler.onPlateRecognized(cameraName, plate, color, imageRef, eventImage);
    }

    /** 同一 track 的多帧识别候选：Frigate 文本投票计数 + HyperLPR3 多帧置信度聚簇。 */
    private static final class PendingTrack {
        final String camera;
        final long createdAt = System.currentTimeMillis();
        /** normalized 合法车牌 -> 出现次数（Frigate 文本投票，仅统计合法候选）。 */
        final Map<String, Integer> votes = new ConcurrentHashMap<>();
        /** normalized 合法车牌 -> HyperLPR3 多帧聚簇（置信度和）。 */
        final Map<String, HyperVote> hyperVotes = new ConcurrentHashMap<>();
        /** 事件快照路径（/api/events/{id}/snapshot.jpg 等），提交时用于保存识别图片。 */
        volatile String snapshotPath;
        volatile PlateColor lastEventColor;
        volatile long lastUpdateAt = System.currentTimeMillis();
        volatile ScheduledFuture<?> future;
        volatile boolean submitted;

        PendingTrack(String camera) {
            this.camera = camera;
        }

        /** 记录事件快照路径（保留首个非空值；tracked_object_update 无快照时保持原值）。 */
        void setSnapshot(String path) {
            if (path != null && !path.isBlank() && snapshotPath == null) {
                snapshotPath = path;
            }
        }

        synchronized void recordVote(String normalized, PlateColor eventColor) {
            lastUpdateAt = System.currentTimeMillis();
            votes.merge(normalized, 1, Integer::sum);
            if (eventColor != null) {
                lastEventColor = eventColor;
            }
        }

        /** 计入一帧 HyperLPR3 结果：清洗并校验车牌，合法才按置信度聚簇累加。
         *  注意：不刷新 lastUpdateAt，避免本服务自己的抽样帧延长投票窗口、拖慢出结果。 */
        synchronized boolean recordHyperVote(String rawPlate, PlateColor color, double conf) {
            String normalized = normalizePlate(rawPlate);
            if (normalized == null || !Yolo26PlateClient.isValidPlateShapePublic(normalized)) {
                return false;
            }
            hyperVotes.compute(normalized, (k, v) -> new HyperVote(
                    v != null && v.color() != null ? v.color() : color,
                    (v == null ? 0 : v.confidenceSum()) + Math.max(conf, 0.0),
                    (v == null ? 0 : v.frames()) + 1));
            if (color != null) {
                lastEventColor = color;
            }
            return true;
        }
    }

    /** HyperLPR3 单帧结果在聚簇中的累计：颜色（取首个非空）+ 置信度和 + 参与帧数。 */
    private record HyperVote(PlateColor color, double confidenceSum, int frames) {}

    private record LprResult(String plate, PlateColor plateColor, double confidence) {}

    /** 事件无车牌/缺颜色时：下载快照 → HyperLPR3 识别，失败返回 null。 */
    private LprResult lprCompleteFromEvent(JsonNode node, String cameraName, FrigateSettings settings) {
        String eventId = textOrNull(node.path("id"));
        String snapshotPath = textOrNull(node.path("snapshot"));
        if (snapshotPath == null && eventId != null && node.path("has_snapshot").asBoolean(false)) {
            snapshotPath = "/api/events/" + eventId + "/snapshot.jpg";
        }
        // Dedicated LPR 的 tracked_object_update 不带快照路径，回退到相机最新帧快照。
        if (snapshotPath == null && cameraName != null && !cameraName.isBlank()) {
            snapshotPath = "/api/" + cameraName + "/latest.jpg";
        }
        if (snapshotPath == null || snapshotPath.isBlank()) {
            log.warn("Frigate LPR skip camera={} event={}: no snapshot path", cameraName, eventId);
            return null;
        }

        byte[] image = downloadSnapshot(snapshotPath, settings);
        if (image == null) {
            log.warn("Frigate LPR skip camera={} event={}: snapshot download failed for {}",
                    cameraName, eventId, snapshotPath);
            return null;
        }
        Yolo26PlateClient.DetectedPlate best = hyperLpr3Client.recognizeBestEffort(image, eventId);
        if (best == null) {
            log.warn("Frigate LPR skip camera={} event={}: HyperLPR3 returned nothing",
                    cameraName, eventId);
            return null;
        }
        String plate = best.plate();
        if (plate == null || plate.isBlank()) {
            log.warn("Frigate LPR skip camera={} event={}: HyperLPR3 returned blank plate",
                    cameraName, eventId);
            return null;
        }
        log.info("Frigate LPR completed camera={} event={} plate={} color={} conf={}",
                cameraName, eventId, plate,
                best.plateColor() == null ? "unknown" : best.plateColor().name(),
                String.format("%.3f", best.plateConfidence()));
        return new LprResult(plate.trim(), best.plateColor(), best.plateConfidence());
    }

    /** HyperLPR3 多帧采样：assist 首次触发后，再间隔采样 2 帧 latest.jpg 识别，
     *  使车牌识别不再依赖单张快照，降低单帧噪声/时机差导致的误识别。
     *  首帧立即识别，第 2/3 帧分别在 +200ms/+400ms 触发，3 次抽帧尽量在 500ms 内完成。 */
    private void scheduleHyperLprFrames(PendingTrack pending, String cameraName, FrigateSettings settings) {
        String cam = cameraName;
        voteScheduler.schedule(() -> captureHyperLprFrame(pending, cam, settings, "frame2"), 200, TimeUnit.MILLISECONDS);
        voteScheduler.schedule(() -> captureHyperLprFrame(pending, cam, settings, "frame3"), 400, TimeUnit.MILLISECONDS);
    }

    /** 抽一帧相机最新快照交给 HyperLPR3 识别，合法结果计入 pending 的 HyperLPR3 聚簇。 */
    private void captureHyperLprFrame(PendingTrack pending, String cameraName, FrigateSettings settings, String tag) {
        try {
            byte[] image = downloadSnapshot("/api/" + cameraName + "/latest.jpg", settings);
            if (image == null) {
                log.warn("Frigate LPR frame camera={} {}: snapshot download failed", cameraName, tag);
                return;
            }
            Yolo26PlateClient.DetectedPlate best = hyperLpr3Client.recognizeBestEffort(image, tag);
            if (best == null || best.plate() == null || best.plate().isBlank()) {
                log.debug("Frigate LPR frame camera={} {}: nothing recognized", cameraName, tag);
                return;
            }
            if (!pending.recordHyperVote(best.plate(), best.plateColor(), best.plateConfidence())) {
                log.info("Frigate LPR frame camera={} {}: plate {} invalid shape", cameraName, tag, best.plate());
                return;
            }
            log.info("Frigate LPR frame camera={} {} plate={} color={} conf={}",
                    cameraName, tag, best.plate().trim(),
                    best.plateColor() == null ? "unknown" : best.plateColor().name(),
                    String.format("%.3f", best.plateConfidence()));
        } catch (Exception ex) {
            log.warn("Frigate LPR frame camera={} {} failed: {}", cameraName, tag, ex.getMessage());
        }
    }

    private byte[] downloadSnapshot(String path, FrigateSettings settings) {
        String base = "http://" + settings.getApiHost().trim() + ":" + settings.getApiPort();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = snapshotClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body().length > 0) {
                return response.body();
            }
            log.warn("Frigate snapshot HTTP {} for {}", response.statusCode(), path);
            return null;
        } catch (Exception ex) {
            log.warn("Frigate snapshot download failed {}: {}", path, ex.getMessage());
            return null;
        }
    }

    private PlateColor extractColor(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String direct = firstNonBlank(
                textOrNull(node.path("plateColor")),
                textOrNull(node.path("plate_color")),
                textOrNull(node.path("colorName")),
                textOrNull(node.path("color")),
                textOrNull(node.path("plate_color_name")),
                textOrNull(node.path("license_plate_color")));
        if (direct != null) {
            PlateColor color = tryColor(direct);
            if (color != null) {
                return color;
            }
        }
        JsonNode attrs = node.path("current_attributes");
        if (attrs.isObject()) {
            String fromAttrs = firstNonBlank(
                    textOrNull(attrs.path("plateColor")),
                    textOrNull(attrs.path("plate_color")),
                    textOrNull(attrs.path("license_plate_color")),
                    textOrNull(attrs.path("color")));
            if (fromAttrs != null) {
                PlateColor color = tryColor(fromAttrs);
                if (color != null) {
                    return color;
                }
            }
        }
        // 兜底：数字颜色索引（常见于 LPR 设备透传）
        JsonNode colorIndex = node.path("colorIndex");
        if (!colorIndex.isMissingNode() && colorIndex.canConvertToInt()) {
            return mapColorIndex(colorIndex.asInt(-1));
        }
        return null;
    }

    private PlateColor tryColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PlateColor.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return mapColorAlias(raw.trim());
        }
    }

    private PlateColor mapColorIndex(int idx) {
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
            case "蓝", "蓝色", "蓝底", "BLUE", "blue" -> PlateColor.BLUE;
            case "黄", "黄色", "黄底", "YELLOW", "yellow" -> PlateColor.YELLOW;
            case "白", "白色", "白底", "WHITE", "white" -> PlateColor.WHITE;
            case "黑", "黑色", "黑底", "BLACK", "black" -> PlateColor.BLACK;
            case "绿", "绿色", "绿底", "渐变绿", "GREEN", "green" -> PlateColor.GREEN;
            case "黄绿", "黄底黑字", "YELLOW_GREEN", "yellow_green" -> PlateColor.YELLOW_GREEN;
            default -> null;
        };
    }

    private String cameraFromTopic(String topic, String prefix) {
        String rest = topic.substring(prefix.length() + 1);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private String extractPlate(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String direct = firstNonBlank(
                textOrNull(node.path("plate")),
                textOrNull(node.path("plateNumber")),
                textOrNull(node.path("plate_number")),
                textOrNull(node.path("license_plate")),
                textOrNull(node.path("recognized_license_plate")));
        if (direct != null) {
            return direct;
        }
        JsonNode subLabel = node.path("sub_label");
        if (subLabel.isTextual()) {
            return subLabel.asText();
        }
        if (subLabel.isArray() && subLabel.size() > 0 && subLabel.get(0).isTextual()) {
            return subLabel.get(0).asText();
        }
        JsonNode attrs = node.path("current_attributes");
        if (attrs.isObject()) {
            String fromAttrs = firstNonBlank(
                    textOrNull(attrs.path("license_plate")),
                    textOrNull(attrs.path("recognized_license_plate")),
                    textOrNull(attrs.path("plate")));
            if (fromAttrs != null) {
                return fromAttrs;
            }
        }
        return null;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /** 清洗车牌：去掉中点分隔符（浙B·2V9L7 → 浙B2V9L7）、横线、空格等，并统一大写。 */
    private static String normalizePlate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replaceAll("[·・\\-\\s.．]", "").trim().toUpperCase();
        return cleaned.isBlank() ? null : cleaned;
    }

    /** 车牌级去重：同一相机同一合法车牌在窗口内只提交一次；窗口过期后允许重新识别，防止跨 track 反复入库。 */
    private boolean shouldSubmitPlate(String cameraName, String plate) {
        String key = cameraName + "|" + plate;
        long now = System.currentTimeMillis();
        Long previous = submittedPlates.putIfAbsent(key, now);
        if (previous != null && now - previous < plateDedupWindowMs) {
            return false;
        }
        if (previous != null) {
            // 窗口已过期：允许同一车牌再次识别，并刷新记录时间。
            submittedPlates.put(key, now);
        }
        // 定期清理过期条目（超过上限或每 64 次写入），防止 map 无限增长。
        if (submittedPlates.size() > PLATE_DEDUP_MAX_SIZE || (submittedPlates.size() & 63) == 0) {
            submittedPlates.entrySet().removeIf(e -> now - e.getValue() > plateDedupWindowMs);
        }
        return true;
    }

    /** LPR 辅助去重：同一 track/event id 在窗口内只允许调用一次 HyperLPR3，防止高频推送反复辅助识别。 */
    private boolean tryReserveLprAttempt(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long previous = lprSeenEventIds.putIfAbsent(eventId, now);
        if (previous != null && now - previous < LPR_DEDUP_WINDOW_MS) {
            return false;
        }
        if (previous != null) {
            // 窗口已过期：允许再次辅助识别（如 Frigate 复用同一 track id）。
            lprSeenEventIds.put(eventId, now);
        }
        if (lprSeenEventIds.size() > LPR_DEDUP_MAX_SIZE || (lprSeenEventIds.size() & 63) == 0) {
            lprSeenEventIds.entrySet().removeIf(e -> now - e.getValue() > LPR_DEDUP_WINDOW_MS);
        }
        return true;
    }

    private void updateLinkStatus(FrigateLinkStatus status) {
        settingsRepository.findById(FrigateSettings.SINGLETON_ID).ifPresent(settings -> {
            settings.setLinkStatus(status);
            settings.setLastTestAt(Instant.now());
            settingsRepository.save(settings);
        });
    }

    private synchronized void disconnectQuietly() {
        MqttClient client = clientRef.getAndSet(null);
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException ignored) {
            // ignore
        }
        try {
            client.close();
        } catch (MqttException ignored) {
            // ignore
        }
    }

    private String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
