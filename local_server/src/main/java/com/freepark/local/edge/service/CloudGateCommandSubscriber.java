package com.freepark.local.edge.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import com.freepark.local.parkingflow.service.CloudSessionApplyService;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 云端指令订阅：EDGE 模式下连接云端 Broker，订阅
 * {@code {commandTopicPrefix}/{nodeCode}}（默认 {@code parking/command/{nodeCode}}）。
 * 接收 {@code edge.gate.command/1}（缴费开闸）以及 {@code edge.parking.session/1}
 *（origin=CLOUD，管理端改流水后的下行快照）。
 *
 * <p>连接生命周期与心跳/配置同步一致：周期对齐参数，变化自动重连；
 * clientId 使用 {@code {mqttClientId}-cmd}，避免与其它链路互踢。</p>
 */
@Component
public class CloudGateCommandSubscriber {

    private static final Logger log = LoggerFactory.getLogger(CloudGateCommandSubscriber.class);

    static final String SCHEMA = "edge.gate.command/1";
    static final String COMMAND_OPEN = "OPEN";
    static final String REASON_PAYMENT = "PAYMENT";

    private static final long TICK_INTERVAL_SECONDS = 10;
    private static final long ERROR_LOG_THROTTLE_MS = 30_000;
    private static final long COMMAND_ID_TTL_MS = 10 * 60_000L;

    private final NodeSettingsRepository settingsRepository;
    private final JsonMapper jsonMapper;
    private final CloudGateCommandHandler handler;
    private final CloudSessionApplyService sessionApply;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    private volatile String connectedKey;
    private final AtomicLong lastErrorLoggedAt = new AtomicLong();
    private final ConcurrentHashMap<String, Long> seenCommandIds = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cloud-gate-command");
        t.setDaemon(true);
        return t;
    });

    public CloudGateCommandSubscriber(
            NodeSettingsRepository settingsRepository,
            JsonMapper jsonMapper,
            CloudGateCommandHandler handler,
            CloudSessionApplyService sessionApply) {
        this.settingsRepository = settingsRepository;
        this.jsonMapper = jsonMapper;
        this.handler = handler;
        this.sessionApply = sessionApply;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::tick, 5, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("云端指令订阅已启动（开闸 + 流水下发，周期 {} 秒，EDGE 模式自动连接）", TICK_INTERVAL_SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        disconnectIfActive();
        seenCommandIds.clear();
    }

    private void tick() {
        try {
            expireSeen();
            NodeSettings settings = settingsRepository.findById(NodeSettings.SINGLETON_ID).orElse(null);
            String nodeCode = settings == null ? null : settings.getNodeCode();
            if (settings == null
                    || settings.getMode() != NodeMode.EDGE
                    || nodeCode == null || nodeCode.isBlank()
                    || !isTopicSafe(nodeCode)) {
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
            clientId = clientId + "-cmd";
            String username = settings.getMqttUsername();
            String password = settings.getMqttPassword();
            String prefix = commandPrefix(settings);
            String topic = prefix + "/" + nodeCode;

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
                log.info("已订阅云端指令主题 {}（broker {}:{}）", topic, host.trim(), port);
            }
        } catch (Exception ex) {
            throttleError("开闸指令订阅自检异常：{}", ex.getMessage());
        }
    }

    private MqttClient connectQuietly(String host, int port, String clientId,
            String username, String password, String topic, String nodeCode) {
        try {
            MqttClient client = new MqttClient("tcp://" + host + ":" + port, clientId, new MemoryPersistence());
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("云端开闸指令 MQTT 已连接 (reconnect={}) uri={}", reconnect, serverURI);
                    try {
                        client.subscribe(topic, 1);
                    } catch (MqttException ex) {
                        throttleError("订阅开闸指令主题 {} 失败：{}", topic, ex.getMessage());
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("云端开闸指令 MQTT 连接断开：{}",
                            cause == null ? "unknown" : cause.getMessage());
                }

                @Override
                public void messageArrived(String arrivedTopic, MqttMessage message) {
                    if (!topic.equals(arrivedTopic)) {
                        return;
                    }
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
            if (client.isConnected()) {
                client.subscribe(topic, 1);
            }
            return client;
        } catch (MqttException ex) {
            throttleError("连接云端 MQTT Broker {}:{}（开闸指令）失败：{}", host, port, ex.getMessage());
            return null;
        }
    }

    private void handleMessage(String payload, String nodeCode) {
        try {
            JsonNode root = jsonMapper.readTree(payload);
            String schema = textOrNull(root.path("schema"));
            String edgeCode = textOrNull(root.path("edgeCode"));
            if (!nodeCode.equals(edgeCode)) {
                log.debug("云端指令忽略（schema={} edgeCode={}）", schema, edgeCode);
                return;
            }
            if (CloudSessionApplyService.SCHEMA.equals(schema)) {
                applyCloudSession(root, nodeCode);
                return;
            }
            if (!SCHEMA.equals(schema)) {
                log.debug("云端指令忽略（schema={} edgeCode={}）", schema, edgeCode);
                return;
            }
            String command = textOrNull(root.path("command"));
            if (!COMMAND_OPEN.equals(command)) {
                log.debug("开闸指令忽略：未知 command={}", command);
                return;
            }
            String reason = textOrNull(root.path("reason"));
            if (reason != null && !REASON_PAYMENT.equals(reason)) {
                log.debug("开闸指令忽略：非缴费原因 reason={}", reason);
                return;
            }
            String commandId = textOrNull(root.path("commandId"));
            if (commandId != null && seenCommandIds.putIfAbsent(commandId, System.currentTimeMillis()) != null) {
                log.info("开闸指令重复，忽略 commandId={}", commandId);
                return;
            }
            String plate = textOrNull(root.path("plate"));
            String plateColor = textOrNull(root.path("plateColor"));
            String lotCode = textOrNull(root.path("lotCode"));
            handler.openAfterPayment(plate, plateColor, lotCode, commandId);
        } catch (Exception ex) {
            throttleError("云端指令负载解析失败：{}", ex.getMessage());
        }
    }

    private void applyCloudSession(JsonNode root, String nodeCode) {
        String origin = textOrNull(root.path("origin"));
        if (!CloudSessionApplyService.ORIGIN_CLOUD.equals(origin)) {
            log.debug("忽略非云端来源流水快照 origin={}", origin);
            return;
        }
        sessionApply.apply(
                nodeCode,
                longOrNull(root.path("cloudId")),
                longOrNull(root.path("cloudRevision")),
                textOrNull(root.path("sessionId")),
                textOrNull(root.path("lotCode")),
                textOrNull(root.path("lotName")),
                textOrNull(root.path("plateNumber")),
                textOrNull(root.path("plateColor")),
                textOrNull(root.path("status")),
                instantOrNull(root.path("entryTime")),
                instantOrNull(root.path("exitTime")),
                textOrNull(root.path("entryLaneName")),
                textOrNull(root.path("exitLaneName")));
    }

    private void expireSeen() {
        long cutoff = System.currentTimeMillis() - COMMAND_ID_TTL_MS;
        seenCommandIds.entrySet().removeIf(e -> e.getValue() < cutoff);
    }

    private void disconnectIfActive() {
        MqttClient client = clientRef.getAndSet(null);
        if (client == null && connectedKey == null) {
            return;
        }
        connectedKey = null;
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

    private static String commandPrefix(NodeSettings settings) {
        String prefix = settings.getCommandTopicPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = NodeSettings.DEFAULT_COMMAND_TOPIC_PREFIX;
        }
        return stripTrailingSlash(prefix);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static Long longOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static Instant instantOrNull(JsonNode node) {
        String text = textOrNull(node);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

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
}
