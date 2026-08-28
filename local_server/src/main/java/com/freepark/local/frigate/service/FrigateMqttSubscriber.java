package com.freepark.local.frigate.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
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
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.freepark.local.domain.FrigateLinkStatus;
import com.freepark.local.domain.FrigateSettings;
import com.freepark.local.domain.FrigateSettingsRepository;
import com.freepark.local.domain.PlateColor;

import jakarta.annotation.PreDestroy;

@Component
public class FrigateMqttSubscriber {

    private static final Logger log = LoggerFactory.getLogger(FrigateMqttSubscriber.class);

    private final FrigateSettingsRepository settingsRepository;
    private final FrigateEventHandler eventHandler;
    private final JsonMapper jsonMapper;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();

    public FrigateMqttSubscriber(
            FrigateSettingsRepository settingsRepository,
            FrigateEventHandler eventHandler,
            JsonMapper jsonMapper) {
        this.settingsRepository = settingsRepository;
        this.eventHandler = eventHandler;
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
                    handleMessage(topic, payload, settings.getTopicPrefix());
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

    private void handleMessage(String topic, String payload, String topicPrefix) {
        try {
            String prefix = stripTrailingSlash(topicPrefix);
            String cameraName = null;
            String plate = null;
            PlateColor plateColor = null;

            if (topic.equals(prefix + "/events") || topic.endsWith("/events")) {
                JsonNode root = jsonMapper.readTree(payload);
                JsonNode after = root.path("after");
                if (after.isMissingNode() || after.isNull()) {
                    after = root;
                }
                cameraName = textOrNull(after.path("camera"));
                if (cameraName == null && topic.endsWith("/events")) {
                    cameraName = cameraFromTopic(topic, prefix);
                }
                plate = extractPlate(after);
                plateColor = extractColor(after);
                if (plate == null) {
                    plate = extractPlate(root);
                }
                if (plateColor == null) {
                    plateColor = extractColor(root);
                }
            } else if (topic.startsWith(prefix + "/")) {
                cameraName = cameraFromTopic(topic, prefix);
                JsonNode root = jsonMapper.readTree(payload);
                plate = extractPlate(root);
                plateColor = extractColor(root);
            }

            if (cameraName == null || plate == null || plate.isBlank()) {
                return;
            }
            eventHandler.onPlateRecognized(cameraName, plate.trim().toUpperCase(), plateColor);
        } catch (Exception ex) {
            log.debug("Ignore Frigate MQTT payload on {}: {}", topic, ex.getMessage());
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
                textOrNull(node.path("license_plate")));
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
