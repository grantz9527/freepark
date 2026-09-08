package com.freepark.local.configsync;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.freepark.local.domain.NodeMode;
import com.freepark.local.domain.NodeSettings;
import com.freepark.local.domain.NodeSettingsRepository;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 云端配置同步订阅器：节点以“边缘节点（EDGE）”模式运行且配置了
 * {@code configSyncTopicPrefix} 时，自愈地连接云端 MQTT Broker 并订阅
 * {@code {configSyncTopicPrefix}/{nodeCode}}，接收 {@code edge.config.sync/3}
 * 的全量/增量帧。
 *
 * <p>云端一次下发的帧共享同一 {@code snapshotId} 并按 {@code seq 1..total}
 * 编号（同一长连接保证有序）；本订阅器先把帧按 snapshotId 聚合进内存，
 * 收齐 total 帧后按 seq 排序整批交给 {@link ConfigSyncApplyService} 顺序应用。
 * 中途缺帧则等待下一轮全量（旧聚合会按超时清理），无需落库缓存。</p>
 *
 * <p>连接生命周期与 {@code EdgeHeartbeatReporter} 一致：周期 tick 检查期望连接参数，
 * 参数变化自动重连、配置被清空/非 EDGE 自动断开；重连后由
 * {@code MqttCallbackExtended.connectComplete} 自动补订阅。</p>
 */
@Component
public class CloudConfigSyncSubscriber {

    private static final Logger log = LoggerFactory.getLogger(CloudConfigSyncSubscriber.class);

    /** 负载信封 schema（与云端 EdgeConfigSyncDispatcher 约定一致） */
    static final String SCHEMA = "edge.config.sync/3";
    static final String KIND_FULL = "full";
    static final String KIND_DELTA = "delta";

    /** 自愈检查周期（秒） */
    private static final long TICK_INTERVAL_SECONDS = 10;
    /** 未收齐帧的聚合超时（毫秒）：超时视为一次失败下发，清空等待下一轮 */
    private static final long PENDING_EXPIRE_MS = 10 * 60_000L;
    /** 聚合缓冲上限：超过时丢弃最旧聚合，防止异常主题反复灌帧导致内存增长 */
    private static final int PENDING_MAX_SIZE = 8;
    /** 连接失败告警日志节流（毫秒） */
    private static final long ERROR_LOG_THROTTLE_MS = 30_000;

    private final NodeSettingsRepository settingsRepository;
    private final JsonMapper jsonMapper;
    private final ConfigSyncApplyService applyService;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    /** 当前连接的期望参数指纹（host:port|clientId|username|topic）；null=空闲 */
    private volatile String connectedKey;
    /** 当前已订阅的主题（用于过滤无关消息） */
    private volatile String subscribedTopic;
    private final AtomicLong lastErrorLoggedAt = new AtomicLong();
    /** snapshotId -> 未收齐的聚合帧 */
    private final Map<String, PendingSnapshot> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cloud-config-sync");
        t.setDaemon(true);
        return t;
    });

    public CloudConfigSyncSubscriber(
            NodeSettingsRepository settingsRepository,
            JsonMapper jsonMapper,
            ConfigSyncApplyService applyService) {
        this.settingsRepository = settingsRepository;
        this.jsonMapper = jsonMapper;
        this.applyService = applyService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::tick, 5, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("云端配置同步订阅已启动（周期 {} 秒，EDGE 模式并配置同步主题前缀后自动连接）",
                TICK_INTERVAL_SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        disconnectIfActive();
        pending.clear();
    }

    /** 周期入口：按当前节点设置对齐连接状态，并顺带清理超时的未收齐聚合。 */
    private void tick() {
        try {
            expireStalePending();
            NodeSettings settings = settingsRepository.findById(NodeSettings.SINGLETON_ID).orElse(null);
            String nodeCode = settings == null ? null : settings.getNodeCode();
            String prefix = settings == null ? null : settings.getConfigSyncTopicPrefix();
            if (settings == null
                    || settings.getMode() != NodeMode.EDGE
                    || nodeCode == null || nodeCode.isBlank()
                    || !isTopicSafe(nodeCode)
                    || prefix == null || prefix.isBlank()) {
                disconnectIfActive();
                return;
            }
            String host = settings.getMqttHost();
            Integer port = settings.getMqttPort();
            if (host == null || host.isBlank() || port == null || port < 1 || port > 65535) {
                disconnectIfActive();
                return;
            }
            String clientId = settings.getMqttClientId();
            if (clientId == null || clientId.isBlank()) {
                clientId = NodeSettings.DEFAULT_MQTT_CLIENT_ID;
            }
            // 配置同步与心跳（可能同 brokker）共用一套连接参数，但必须用独立 clientId，
            // 避免与 EdgeHeartbeatReporter 的“同名 client”互踢。
            clientId = clientId + "-cfg";
            String username = settings.getMqttUsername();
            String password = settings.getMqttPassword();
            String topic = stripTrailingSlash(prefix) + "/" + nodeCode;

            String key = host.trim() + ":" + port + "|" + clientId + "|"
                    + (username == null ? "" : username) + "|" + topic;
            MqttClient client = clientRef.get();
            if (client == null || !client.isConnected() || !key.equals(connectedKey)) {
                disconnectIfActive();
                client = connectQuietly(host.trim(), port, clientId, username, password, topic, nodeCode);
                if (client == null) {
                    return;
                }
                clientRef.set(client);
                connectedKey = key;
                subscribedTopic = topic;
                log.info("已订阅云端配置同步主题 {}（broker {}:{}）", topic, host.trim(), port);
            }
        } catch (Exception ex) {
            throttleError("配置同步自检异常：{}", ex.getMessage());
        }
    }

    /** 建立连接并注册回调；回调的 connectComplete 会自动补订阅（含断线自动重连场景）。 */
    private MqttClient connectQuietly(String host, int port, String clientId,
            String username, String password, String topic, String nodeCode) {
        try {
            MqttClient client = new MqttClient("tcp://" + host + ":" + port, clientId, new MemoryPersistence());
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("云端配置同步 MQTT 已连接 (reconnect={}) uri={}", reconnect, serverURI);
                    try {
                        client.subscribe(topic, 1);
                    } catch (MqttException ex) {
                        throttleError("订阅配置同步主题 {} 失败：{}", topic, ex.getMessage());
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("云端配置同步 MQTT 连接断开：{}",
                            cause == null ? "unknown" : cause.getMessage());
                }

                @Override
                public void messageArrived(String arrivedTopic, MqttMessage message) {
                    if (!topic.equals(arrivedTopic)) {
                        return;
                    }
                    // 云端每轮全量前先发一条零字节 retained 消息清理 v2 遗留；订阅/重连时
                    // broker 会回放它，属于协议噪声直接忽略，避免触发解析告警。
                    if (message == null || message.getPayload() == null
                            || message.getPayload().length == 0) {
                        return;
                    }
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    handleMessage(payload, nodeCode);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // no-op
                }
            });
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(8);
            options.setKeepAliveInterval(30);
            if (username != null && !username.isBlank()) {
                options.setUserName(username);
            }
            if (password != null && !password.isBlank()) {
                options.setPassword(password.toCharArray());
            }
            client.connect(options);
            // connect 返回后 connectComplete 可能已订阅；补一次确保首连即生效（重复订阅幂等）。
            if (client.isConnected()) {
                client.subscribe(topic, 1);
            }
            return client;
        } catch (MqttException ex) {
            throttleError("连接云端 MQTT Broker {}:{}（配置同步）失败：{}", host, port, ex.getMessage());
            return null;
        }
    }

    /** 处理一帧负载：校验信封，按 snapshotId/seq/total 聚合，收齐后整批交给应用器。 */
    private void handleMessage(String payload, String nodeCode) {
        try {
            JsonNode root = jsonMapper.readTree(payload);
            String schema = textOrNull(root.path("schema"));
            String edgeCode = textOrNull(root.path("edgeCode"));
            String kind = textOrNull(root.path("kind"));
            String snapshotId = textOrNull(root.path("snapshotId"));
            if (!SCHEMA.equals(schema) || !nodeCode.equals(edgeCode)
                    || kind == null || snapshotId == null) {
                log.debug("配置同步负载忽略（schema={} edgeCode={} kind={}）", schema, edgeCode, kind);
                return;
            }
            if (!KIND_FULL.equals(kind) && !KIND_DELTA.equals(kind)) {
                log.debug("配置同步负载忽略：未知 kind={}", kind);
                return;
            }
            int seq = root.path("seq").asInt(-1);
            int total = root.path("total").asInt(-1);
            if (seq < 1 || total < 1 || seq > total) {
                log.warn("配置同步帧序号非法 snapshotId={} seq={}/{}，忽略", snapshotId, seq, total);
                return;
            }
            PendingSnapshot snapshot = pending.computeIfAbsent(snapshotId,
                    key -> new PendingSnapshot(kind, total));
            if (!kind.equals(snapshot.kind) || snapshot.total != total) {
                // 同一 snapshotId 复用了不同批次（理论上不会发生），按新批次重建
                snapshot.frames.clear();
                snapshot.kind = kind;
                snapshot.total = total;
            }
            snapshot.frames.put(seq, root);
            snapshot.lastFrameAt = System.currentTimeMillis();
            if (snapshot.frames.size() == snapshot.total
                    && snapshot.frames.containsKey(1)
                    && snapshot.frames.containsKey(snapshot.total)) {
                pending.remove(snapshotId, snapshot);
                List<JsonNode> ordered = new ArrayList<>(snapshot.frames.size());
                for (int i = 1; i <= snapshot.total; i++) {
                    ordered.add(snapshot.frames.get(i));
                }
                log.info("配置同步帧已收齐 snapshotId={} kind={} total={}，开始应用",
                        snapshotId, snapshot.kind, snapshot.total);
                try {
                    applyService.applyFrames(ordered);
                } catch (RuntimeException ex) {
                    log.error("配置同步快照应用失败 snapshotId={}：{}", snapshotId, ex.getMessage(), ex);
                }
            }
        } catch (Exception ex) {
            throttleError("配置同步负载解析失败：{}", ex.getMessage());
        }
    }

    /** 超时未收齐的聚合清空：缺帧时等下一轮全量补齐，不残留陈旧缓冲。 */
    private void expireStalePending() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue().lastFrameAt > PENDING_EXPIRE_MS);
        while (pending.size() > PENDING_MAX_SIZE) {
            pending.entrySet().stream()
                    .min(Map.Entry.comparingByValue(
                            (a, b) -> Long.compare(a.lastFrameAt, b.lastFrameAt)))
                    .ifPresent(e -> pending.remove(e.getKey(), e.getValue()));
        }
    }

    private void disconnectIfActive() {
        MqttClient client = clientRef.getAndSet(null);
        if (client == null && connectedKey == null) {
            return;
        }
        connectedKey = null;
        subscribedTopic = null;
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

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    /** 节点编号用作主题末段：仅允许字母数字与 - _，不允许 / 通配符空白等 */
    private static boolean isTopicSafe(String value) {
        if (value.length() > NodeSettings.MAX_NODE_CODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private void throttleError(String format, Object... args) {
        long now = System.currentTimeMillis();
        long last = lastErrorLoggedAt.get();
        if (now - last >= ERROR_LOG_THROTTLE_MS && lastErrorLoggedAt.compareAndSet(last, now)) {
            log.warn(format, args);
        }
    }

    /** 单个 snapshotId 的未收齐帧缓冲（同一批次各帧在内存按 seq 暂存）。 */
    private static final class PendingSnapshot {
        String kind;
        int total;
        final Map<Integer, JsonNode> frames = new ConcurrentHashMap<>();
        volatile long lastFrameAt = System.currentTimeMillis();

        PendingSnapshot(String kind, int total) {
            this.kind = kind;
            this.total = total;
        }
    }
}
