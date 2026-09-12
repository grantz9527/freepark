package com.freepark.local.edge.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.ParkingSession;
import com.freepark.local.domain.ParkingSessionRepository;
import com.freepark.local.parkingflow.service.ParkingSessionService;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.json.JsonMapper;

/**
 * 停车流水上报（edge → cloud）：节点以“边缘节点（EDGE）”模式运行时，把本地
 * 停车流水（入场创建/出场关闭/作废）的变化周期同步到云端 MQTT Broker，
 * 主题为 {@code {reportTopicPrefix}/{nodeCode}}（默认
 * {@code parking/report/{nodeCode}}），负载为一条完整流水快照
 * {@code edge.parking.session/1}，云端“上报数据订阅主题”需填写
 * {@code {reportTopicPrefix}/#}（默认 {@code parking/report/#}）。
 *
 * <p>可靠性：流水每次状态变化都会在事务内把 {@code sync_pending} 置为待同步；
 * 本组件每 10 秒取出待同步流水，逐条发布完整快照（QoS 1）成功后，仅在流水
 * “与刚发布快照一致（未被并发改动）”时清除待同步标记；断网/云端不可用期间的
 * 变更保持待同步，连上后自动补推，不丢单。同一流水后续变化以最新快照整体覆盖
 * 云端记录（云端按节点编号+流水 ID 幂等 upsert）。</p>
 *
 * <p>连接生命周期与 {@code EdgeHeartbeatReporter} 一致：周期自检期望参数，
 * 参数变化自动重连、非 EDGE 自动断开。与心跳/配置同步共用同一套 Broker 连接
 * 参数，但使用独立 clientId（{@code {mqttClientId}-rec}），避免同名互踢。</p>
 */
@Component
public class ParkingSessionSyncReporter {

    private static final Logger log = LoggerFactory.getLogger(ParkingSessionSyncReporter.class);

    /** 流水快照负载信封 schema（与云端 EdgeParkingSessionReceiver 约定一致） */
    public static final String SCHEMA = "edge.parking.session/1";

    /** 自愈检查/补推周期（秒） */
    private static final long TICK_INTERVAL_SECONDS = 10;

    /** 每轮最多补推的待同步流水条数（防长时间断网积压时单轮阻塞过久） */
    private static final int MAX_FLUSH_BATCH = 200;

    /** 连接失败告警日志节流（毫秒） */
    private static final long ERROR_LOG_THROTTLE_MS = 30_000;

    private final NodeSettingsRepository settingsRepository;
    private final ParkingSessionRepository sessionRepository;
    private final ParkingLotRepository lotRepository;
    private final ParkingSessionService sessionService;
    private final JsonMapper jsonMapper;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();
    /** 当前已连接的期望参数指纹（host:port|clientId|username）；null=空闲 */
    private volatile String connectedKey;
    private final AtomicLong lastErrorLoggedAt = new AtomicLong();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "parking-session-sync");
        t.setDaemon(true);
        return t;
    });

    public ParkingSessionSyncReporter(
            NodeSettingsRepository settingsRepository,
            ParkingSessionRepository sessionRepository,
            ParkingLotRepository lotRepository,
            ParkingSessionService sessionService,
            JsonMapper jsonMapper) {
        this.settingsRepository = settingsRepository;
        this.sessionRepository = sessionRepository;
        this.lotRepository = lotRepository;
        this.sessionService = sessionService;
        this.jsonMapper = jsonMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::tick, 5, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("停车流水上报已启动（周期 {} 秒，EDGE 模式启用后自动连接补推待同步流水）",
                TICK_INTERVAL_SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        disconnectIfActive();
    }

    /** 周期入口：按当前节点设置对齐连接状态，已连接则补推一批待同步流水。 */
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
            String prefix = settings.getReportTopicPrefix();
            if (prefix == null || prefix.isBlank()) {
                prefix = NodeSettings.DEFAULT_REPORT_TOPIC_PREFIX;
            }
            prefix = prefix.replaceAll("/+$", "");
            String clientId = settings.getMqttClientId();
            if (clientId == null || clientId.isBlank()) {
                clientId = NodeSettings.DEFAULT_MQTT_CLIENT_ID;
            }
            // 与心跳/配置同步共用一套连接参数，但必须用独立 clientId，避免同名互踢。
            clientId = clientId + "-rec";
            String username = settings.getMqttUsername();
            String password = settings.getMqttPassword();

            String key = host.trim() + ":" + port + "|" + clientId + "|" + (username == null ? "" : username);
            MqttClient client = clientRef.get();
            if (client == null || !client.isConnected() || !key.equals(connectedKey)) {
                disconnectIfActive();
                client = connectQuietly(host.trim(), port, clientId, username, password);
                if (client == null) {
                    return;
                }
                clientRef.set(client);
                connectedKey = key;
                log.info("已连接云端 MQTT Broker {}:{} clientId={}，上报流水主题 {}",
                        host.trim(), port, clientId, reportTopic(prefix, nodeCode));
            }
            flushPending(settings);
        } catch (Exception ex) {
            throttleError("停车流水上报周期异常：{}", ex.getMessage());
        }
    }

    /** 补推一批待同步流水：逐条发布完整快照，成功后按快照比对清除待同步标记。 */
    private void flushPending(NodeSettings settings) {
        MqttClient client = clientRef.get();
        if (client == null || !client.isConnected()) {
            return;
        }
        String prefix = settings.getReportTopicPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = NodeSettings.DEFAULT_REPORT_TOPIC_PREFIX;
        }
        String topic = reportTopic(prefix.replaceAll("/+$", ""), settings.getNodeCode());
        List<ParkingSession> pending = sessionRepository
                .findTop200BySyncPendingTrueOrderByEntryTimeAsc();
        for (ParkingSession session : pending) {
            if (session.getLotId() == null) {
                // 无车场引用的流水无法映射到云端，保留待同步并在超时告警里提示
                throttleError("跳过无车场引用的待同步流水 sessionId={}", session.getId());
                continue;
            }
            ParkingLot lot = lotRepository.findById(session.getLotId()).orElse(null);
            if (lot == null || lot.getCode() == null || lot.getCode().isBlank()) {
                throttleError("待同步流水所属车场不存在或缺少编码 lotId={} sessionId={}",
                        session.getLotId(), session.getId());
                continue;
            }
            try {
                byte[] body = buildPayload(settings.getNodeCode(), lot.getCode(), session);
                MqttMessage message = new MqttMessage(body);
                message.setQos(1);
                message.setRetained(false);
                client.publish(topic, message);
                boolean cleared = sessionService.markSessionReported(
                        session.getId(),
                        session.getEntryTime(),
                        session.getExitTime(),
                        session.getStatus());
                if (cleared) {
                    log.debug("已上报停车流水并清除待同步标记 {} {}", topic,
                            new String(body, StandardCharsets.UTF_8));
                } else {
                    log.debug("流水上报后已被并发改动，保持待同步等待下一轮补推 sessionId={}",
                            session.getId());
                }
            } catch (Exception ex) {
                // 单条失败（含连接中断）即终止本轮，未清除的流水下轮继续补推
                throttleError("发布停车流水到 {} 失败，下轮补推：{}", topic, ex.getMessage());
                break;
            }
        }
    }

    /** 构造一条流水完整快照：结构字段见 MQTT 对接文档（图片/内部 ID 不在 v1 负载内）。 */
    private byte[] buildPayload(String nodeCode, String lotCode, ParkingSession session)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema", SCHEMA);
        payload.put("edgeCode", nodeCode);
        payload.put("sessionId", session.getId().toString());
        if (session.getCloudId() != null) {
            payload.put("cloudId", session.getCloudId());
        }
        if (session.getCloudRevision() != null) {
            payload.put("cloudRevision", session.getCloudRevision());
        }
        payload.put("lotCode", lotCode);
        if (isNotBlank(session.getLotName())) {
            payload.put("lotName", session.getLotName());
        }
        if (isNotBlank(session.getPlateNumber())) {
            payload.put("plateNumber", session.getPlateNumber());
        }
        if (session.getPlateColor() != null) {
            payload.put("plateColor", session.getPlateColor().name());
        }
        payload.put("status", session.getStatus().name());
        payload.put("entryTime", session.getEntryTime().toString());
        if (session.getExitTime() != null) {
            payload.put("exitTime", session.getExitTime().toString());
        }
        if (isNotBlank(session.getEntryLaneName())) {
            payload.put("entryLaneName", session.getEntryLaneName());
        }
        if (isNotBlank(session.getExitLaneName())) {
            payload.put("exitLaneName", session.getExitLaneName());
        }
        payload.put("reportedAt", Instant.now().toString());
        return jsonMapper.writeValueAsBytes(payload);
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
            throttleError("连接云端 MQTT Broker {}:{}（流水上报）失败：{}", host, port, ex.getMessage());
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

    /** 构造流水上报主题：{上报前缀}/{节点编号} */
    private static String reportTopic(String prefix, String nodeCode) {
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

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void throttleError(String format, Object... args) {
        long now = System.currentTimeMillis();
        long last = lastErrorLoggedAt.get();
        if (now - last >= ERROR_LOG_THROTTLE_MS && lastErrorLoggedAt.compareAndSet(last, now)) {
            log.warn(format, args);
        }
    }
}
