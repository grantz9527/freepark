package com.freepark.local.frigate.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.FrigateBindDirection;
import com.freepark.local.domain.FrigateCamera;
import com.freepark.local.domain.FrigateCameraRepository;
import com.freepark.local.domain.FrigateLinkStatus;
import com.freepark.local.domain.FrigateSettings;
import com.freepark.local.domain.FrigateSettingsRepository;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.ParkingLaneRepository;
import com.freepark.local.domain.UserRole;
import com.freepark.local.frigate.dto.BindFrigateCameraRequest;
import com.freepark.local.frigate.dto.CreateFrigateCameraRequest;
import com.freepark.local.frigate.dto.FrigateCameraView;
import com.freepark.local.frigate.dto.FrigateSettingsView;
import com.freepark.local.frigate.dto.SimulateFrigateEventRequest;
import com.freepark.local.frigate.dto.UpdateFrigateCameraRequest;
import com.freepark.local.frigate.dto.UpdateFrigateSettingsRequest;

@Service
public class FrigateService {

    private final FrigateSettingsRepository settingsRepository;
    private final FrigateCameraRepository cameraRepository;
    private final ParkingLaneRepository laneRepository;
    private final LocalUserRepository users;
    private final FrigateMqttSubscriber mqttSubscriber;
    private final FrigateEventHandler eventHandler;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public FrigateService(
            FrigateSettingsRepository settingsRepository,
            FrigateCameraRepository cameraRepository,
            ParkingLaneRepository laneRepository,
            LocalUserRepository users,
            FrigateMqttSubscriber mqttSubscriber,
            FrigateEventHandler eventHandler) {
        this.settingsRepository = settingsRepository;
        this.cameraRepository = cameraRepository;
        this.laneRepository = laneRepository;
        this.users = users;
        this.mqttSubscriber = mqttSubscriber;
        this.eventHandler = eventHandler;
    }

    @Transactional(readOnly = true)
    public FrigateSettingsView getSettings() {
        return toSettingsView(requireSettings());
    }

    @Transactional
    public FrigateSettingsView updateSettings(UUID requesterId, UpdateFrigateSettingsRequest request) {
        requireAdmin(requesterId);
        FrigateSettings settings = requireSettings();
        String apiHost = trimToEmpty(request.apiHost());
        String mqttHost = trimToEmpty(request.mqttHost());
        String topicPrefix = stripTrailingSlash(trimToEmpty(request.topicPrefix()));
        if (topicPrefix.isEmpty()) {
            topicPrefix = FrigateSettings.DEFAULT_TOPIC_PREFIX;
        }
        settings.setApiHost(apiHost);
        settings.setApiPort(request.apiPort());
        settings.setMqttHost(mqttHost);
        settings.setMqttPort(request.mqttPort());
        settings.setTopicPrefix(topicPrefix);
        settings.setMqttUsername(trimToNull(request.mqttUsername()));
        settings.setEnabled(Boolean.TRUE.equals(request.enabled()));
        if (!isBlank(request.mqttPassword())) {
            settings.setMqttPassword(request.mqttPassword());
        }
        if (!settings.hasApiHost()) {
            settings.setApiLinkStatus(FrigateLinkStatus.DISCONNECTED);
            settings.setApiLastTestAt(null);
        }
        if (!settings.hasMqttHost() || !settings.isEnabled()) {
            settings.setLinkStatus(FrigateLinkStatus.DISCONNECTED);
            settings.setLastTestAt(null);
        }
        FrigateSettings saved = settingsRepository.save(settings);
        mqttSubscriber.reconnect();
        return toSettingsView(saved);
    }

    @Transactional
    public FrigateSettingsView testSettings(UUID requesterId) {
        requireAdmin(requesterId);
        FrigateSettings settings = requireSettings();
        if (!settings.hasMqttHost()) {
            settings.setLinkStatus(FrigateLinkStatus.DISCONNECTED);
            settings.setLastTestAt(null);
            settingsRepository.save(settings);
            throw new BusinessException(ErrorCode.FRIGATE_MQTT_NOT_CONFIGURED);
        }
        Instant now = Instant.now();
        try {
            boolean ok = mqttSubscriber.testConnect(settings);
            settings.setLinkStatus(ok ? FrigateLinkStatus.CONNECTED : FrigateLinkStatus.FAILED);
            settings.setLastTestAt(now);
            settingsRepository.save(settings);
            if (settings.isEnabled() && ok) {
                mqttSubscriber.reconnect();
            }
            if (!ok) {
                throw new BusinessException(ErrorCode.FRIGATE_MQTT_CONNECT_FAILED);
            }
            return toSettingsView(settings);
        } catch (MqttException ex) {
            settings.setLinkStatus(FrigateLinkStatus.FAILED);
            settings.setLastTestAt(now);
            settingsRepository.save(settings);
            throw new BusinessException(ErrorCode.FRIGATE_MQTT_CONNECT_FAILED);
        }
    }

    @Transactional
    public FrigateSettingsView testApi(UUID requesterId) {
        requireAdmin(requesterId);
        FrigateSettings settings = requireSettings();
        if (!settings.hasApiHost()) {
            settings.setApiLinkStatus(FrigateLinkStatus.DISCONNECTED);
            settings.setApiLastTestAt(null);
            settingsRepository.save(settings);
            throw new BusinessException(ErrorCode.FRIGATE_API_NOT_CONFIGURED);
        }
        Instant now = Instant.now();
        try {
            boolean ok = probeFrigateApi(settings);
            settings.setApiLinkStatus(ok ? FrigateLinkStatus.CONNECTED : FrigateLinkStatus.FAILED);
            settings.setApiLastTestAt(now);
            settingsRepository.save(settings);
            if (!ok) {
                throw new BusinessException(ErrorCode.FRIGATE_API_CONNECT_FAILED);
            }
            return toSettingsView(settings);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            settings.setApiLinkStatus(FrigateLinkStatus.FAILED);
            settings.setApiLastTestAt(now);
            settingsRepository.save(settings);
            throw new BusinessException(ErrorCode.FRIGATE_API_CONNECT_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public List<FrigateCameraView> listCameras() {
        return cameraRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toCameraView).toList();
    }

    @Transactional
    public FrigateCameraView createCamera(UUID requesterId, CreateFrigateCameraRequest request) {
        requireAdmin(requesterId);
        String cameraName = trimRequired(request.cameraName());
        if (cameraRepository.existsByCameraNameIgnoreCase(cameraName)) {
            throw new BusinessException(ErrorCode.FRIGATE_CAMERA_EXISTS);
        }
        FrigateCamera camera = new FrigateCamera(trimRequired(request.name()), cameraName, Boolean.TRUE.equals(request.enabled()));
        return toCameraView(cameraRepository.save(camera));
    }

    @Transactional
    public FrigateCameraView updateCamera(UUID requesterId, UUID cameraId, UpdateFrigateCameraRequest request) {
        requireAdmin(requesterId);
        FrigateCamera camera = requireCamera(cameraId);
        String cameraName = trimRequired(request.cameraName());
        if (cameraRepository.existsByCameraNameIgnoreCaseAndIdNot(cameraName, cameraId)) {
            throw new BusinessException(ErrorCode.FRIGATE_CAMERA_EXISTS);
        }
        boolean nameChanged = !camera.getCameraName().equalsIgnoreCase(cameraName);
        camera.setName(trimRequired(request.name()));
        camera.setCameraName(cameraName);
        camera.setEnabled(Boolean.TRUE.equals(request.enabled()));
        if (nameChanged) {
            camera.setLinkStatus(FrigateLinkStatus.DISCONNECTED);
            camera.setLastTestAt(null);
        }
        return toCameraView(cameraRepository.save(camera));
    }

    @Transactional
    public FrigateCameraView testCamera(UUID requesterId, UUID cameraId) {
        requireAdmin(requesterId);
        FrigateSettings settings = requireSettings();
        if (!settings.hasMqttHost() || !settings.isEnabled()) {
            throw new BusinessException(ErrorCode.FRIGATE_MQTT_NOT_CONFIGURED);
        }
        if (settings.getLinkStatus() != FrigateLinkStatus.CONNECTED && !mqttSubscriber.isConnected()) {
            throw new BusinessException(ErrorCode.FRIGATE_MQTT_CONNECT_FAILED);
        }
        FrigateCamera camera = requireCamera(cameraId);
        camera.setLinkStatus(FrigateLinkStatus.CONNECTED);
        camera.setLastTestAt(Instant.now());
        return toCameraView(cameraRepository.save(camera));
    }

    @Transactional
    public FrigateCameraView bindCamera(UUID requesterId, UUID cameraId, BindFrigateCameraRequest request) {
        requireAdmin(requesterId);
        FrigateCamera camera = requireCamera(cameraId);
        if (!laneRepository.existsById(request.laneId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        camera.setLaneId(request.laneId());
        camera.setBindDirection(request.bindDirection());
        camera.setLinkageEnabled(Boolean.TRUE.equals(request.linkageEnabled()));
        return toCameraView(cameraRepository.save(camera));
    }

    @Transactional
    public FrigateCameraView unbindCamera(UUID requesterId, UUID cameraId) {
        requireAdmin(requesterId);
        FrigateCamera camera = requireCamera(cameraId);
        camera.setLaneId(null);
        camera.setBindDirection(null);
        return toCameraView(cameraRepository.save(camera));
    }

    @Transactional
    public FrigateCameraView simulateEvent(UUID requesterId, UUID cameraId, SimulateFrigateEventRequest request) {
        requireAdmin(requesterId);
        FrigateCamera camera = requireCamera(cameraId);
        String plate = trimRequired(request.plate()).toUpperCase();
        eventHandler.onPlateRecognized(camera.getCameraName(), plate, request.plateColor());
        return toCameraView(requireCamera(cameraId));
    }

    private FrigateSettings requireSettings() {
        return settingsRepository.findById(FrigateSettings.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private FrigateCamera requireCamera(UUID cameraId) {
        return cameraRepository.findById(cameraId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private FrigateSettingsView toSettingsView(FrigateSettings settings) {
        String password = settings.getMqttPassword();
        return new FrigateSettingsView(
                settings.getApiHost(),
                settings.getApiPort(),
                settings.getApiLinkStatus(),
                settings.getApiLastTestAt(),
                settings.getMqttHost(),
                settings.getMqttPort(),
                settings.getTopicPrefix(),
                settings.getMqttUsername() == null ? "" : settings.getMqttUsername(),
                password != null && !password.isBlank(),
                settings.isEnabled(),
                settings.getLinkStatus(),
                settings.getLastTestAt(),
                settings.getUpdatedAt());
    }

    private FrigateCameraView toCameraView(FrigateCamera camera) {
        return new FrigateCameraView(
                camera.getId(),
                camera.getName(),
                camera.getCameraName(),
                camera.isEnabled(),
                camera.getLinkStatus(),
                camera.getLastTestAt(),
                camera.getLaneId(),
                camera.getBindDirection(),
                camera.isLinkageEnabled(),
                camera.getLastPlate(),
                camera.getLastPlateColor(),
                camera.getLastEventAt(),
                camera.getCreatedAt(),
                camera.getUpdatedAt());
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String trimRequired(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.INVALID_FRIGATE_CONFIG);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean probeFrigateApi(FrigateSettings settings) throws Exception {
        URI uri = URI.create("http://" + settings.getApiHost().trim() + ":" + settings.getApiPort() + "/api/version");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
