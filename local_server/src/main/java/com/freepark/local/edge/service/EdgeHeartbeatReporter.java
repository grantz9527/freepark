package com.freepark.local.edge.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
import tools.jackson.databind.json.JsonMapper;

/**
 * 云端心跳上报：节点以“边缘节点（EDGE）”模式运行时，周期向云端 MQTT Broker
 * 发布 {@code edge.heartbeat/1} 心跳，主题为
 * {@code {主题前缀}/{节点编号}}（主题前缀需包含 {@code /heartbeat} 段，默认
 * {@code parking/heartbeat/{nodeCode}}），云端“心跳订阅主题”需填写
 * {@code {主题前缀}/#}（默认 {@code parking/heartbeat/#}），
 * 由此判定本节点及其管辖车场在线。
 *
 * <p>配置缺省/模式非 EDGE 时保持空闲；运行中周期性检查期望连接参数，
 * 参数变化自动重连，配置被清空自动断开（自愈，无需外部触发）。
 */
@Component
public class EdgeHeartbeatReporter {

    private static final Logger log = LoggerFactory.getLogger(EdgeHeartbeatReporter.class);

    /** 心跳负载信封 schema（与云端 EdgeHeartbeatReceiver 约定一致） */
    public static final String SCHEMA = "edge.heartbeat/1";

    /** 心跳/自愈检查周期（秒） */
    private static final long TICK_INTERVAL_SECONDS = 10;

    /** 连接失败告警日志节流（毫秒） */
    private static final long ERROR_LOG_THROTTLE_MS = 30_000;

    private final NodeSettingsRepository settingsRepository;
    private final JsonMapper jsonMapper;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    /** 当前已连接的期望参数指纹（host:port|clientId|username）；null=空闲 */
    private volatile String connectedKey;
    private final AtomicLong lastErrorLoggedAt = new AtomicLong();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "edge-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public EdgeHeartbeatReporter(NodeSettingsRepository settingsRepository, JsonMapper jsonMapper) {
        this.settingsRepository = settingsRepository;
        this.jsonMapper = jsonMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::tick, 5, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("边缘节点心跳上报已启动（周期 {} 秒，EDGE 模式启用后自动连接）", TICK_INTERVAL_SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        disconnectIfActive();
    }

    /** 周期入口：按当前节点设置对齐连接状态，已连接则补发一次心跳。 */
    private void tick() {
        try {
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
            String prefix = settings.getMqttTopicPrefix();
            if (prefix == null || prefix.isBlank()) {
                prefix = NodeSettings.DEFAULT_MQTT_TOPIC_PREFIX;
            }
            prefix = prefix.replaceAll("/+$", "");
            String clientId = settings.getMqttClientId();
            if (clientId == null || clientId.isBlank()) {
                clientId = NodeSettings.DEFAULT_MQTT_CLIENT_ID;
            }
            String username = settings.getMqttUsername();
            String password = settings.getMqttPassword();

            String key = host.trim() + ":" + port + "|" + clientId + "|" + (username == null ? "" : username);
            MqttClient client = clientRef.get();
            if (client == null || !key.equals(connectedKey)) {
                disconnectIfActive();
                client = connectQuietly(host.trim(), port, clientId, username, password);
                if (client == null) {
                    return;
                }
                clientRef.set(client);
                connectedKey = key;
                log.info(
                        "已连接云端 MQTT Broker {}:{} clientId={}，上报心跳主题 {}",
                        host.trim(), port, clientId, heartbeatTopic(prefix, nodeCode));
            }
            publishHeartbeat(settings);
        } catch (Exception ex) {
            throttleError("心跳上报周期异常：{}", ex.getMessage());
        }
    }

    private void publishHeartbeat(NodeSettings settings) {
        MqttClient client = clientRef.get();
        if (client == null || !client.isConnected()) {
            return;
        }
        String topic = heartbeatTopic(
                settings.getMqttTopicPrefix() == null || settings.getMqttTopicPrefix().isBlank()
                        ? NodeSettings.DEFAULT_MQTT_TOPIC_PREFIX
                        : settings.getMqttTopicPrefix().replaceAll("/+$", ""),
                settings.getNodeCode());
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schema", SCHEMA);
            payload.put("edgeCode", settings.getNodeCode());
            payload.put("reportedAt", Instant.now().toString());
            byte[] body = jsonMapper.writeValueAsBytes(payload);
            MqttMessage message = new MqttMessage(body);
            message.setQos(1);
            message.setRetained(false);
            client.publish(topic, message);
            log.debug("已发布边缘节点心跳 {} {}", topic, new String(body, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throttleError("发布心跳到 {} 失败：{}", topic, ex.getMessage());
        }
    }

    private MqttClient connectQuietly(String host, int port, String clientId, String username, String password) {
        try {
            MqttClient client = new MqttClient("tcp://" + host + ":" + port, clientId, new MemoryPersistence());
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
            return client;
        } catch (MqttException ex) {
            throttleError("连接云端 MQTT Broker {}:{} 失败：{}", host, port, ex.getMessage());
            return null;
        }
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

    /** 构造心跳主题：{主题前缀}/{节点编号}；主题前缀需包含 /heartbeat 段，如 parking/heartbeat */
    private static String heartbeatTopic(String prefix, String nodeCode) {
        return prefix + "/" + nodeCode;
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

    private void throttleError(String format, Object... args) {
        long now = System.currentTimeMillis();
        long last = lastErrorLoggedAt.get();
        if (now - last >= ERROR_LOG_THROTTLE_MS && lastErrorLoggedAt.compareAndSet(last, now)) {
            log.warn(format, args);
        }
    }
}
