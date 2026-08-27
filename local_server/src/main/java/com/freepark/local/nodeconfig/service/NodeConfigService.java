package com.freepark.local.nodeconfig.service;

import com.freepark.local.nodeconfig.dto.NodeSettingsView;
import com.freepark.local.nodeconfig.dto.UpdateNodeSettingsRequest;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.NodeMode;
import com.freepark.local.domain.NodeSettings;
import com.freepark.local.domain.NodeSettingsRepository;
import com.freepark.local.domain.UserRole;

@Service
public class NodeConfigService {

    private final NodeSettingsRepository settingsRepository;
    private final LocalUserRepository users;

    public NodeConfigService(NodeSettingsRepository settingsRepository, LocalUserRepository users) {
        this.settingsRepository = settingsRepository;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public NodeSettingsView getSettings() {
        return toView(requireSettings());
    }

    @Transactional
    public NodeSettingsView updateSettings(UUID requesterId, UpdateNodeSettingsRequest request) {
        requireAdmin(requesterId);
        NodeSettings settings = requireSettings();

        NodeMode mode = request.mode() == null ? NodeMode.OFFLINE : request.mode();
        settings.setMode(mode);

        if (mode == NodeMode.EDGE) {
            String host = trimToNull(request.mqttHost());
            if (host == null) {
                throw new BusinessException(ErrorCode.INVALID_NODE_CONFIG);
            }
            settings.setMqttHost(host);
            int port = request.mqttPort() == null
                    ? NodeSettings.DEFAULT_MQTT_PORT
                    : request.mqttPort();
            settings.setMqttPort(port);
            settings.setMqttClientId(trimToNull(request.mqttClientId()) != null
                    ? trimToNull(request.mqttClientId())
                    : NodeSettings.DEFAULT_MQTT_CLIENT_ID);
            settings.setMqttUsername(trimToNull(request.mqttUsername()));
            settings.setMqttTopicPrefix(trimToNull(request.mqttTopicPrefix()) != null
                    ? stripTrailingSlash(trimToNull(request.mqttTopicPrefix()))
                    : NodeSettings.DEFAULT_MQTT_TOPIC_PREFIX);
            if (!isBlank(request.mqttPassword())) {
                settings.setMqttPassword(request.mqttPassword());
            }
        } else {
            // 离线服务：不上传任何信息，MQTT 配置保留但不再生效。
        }
        return toView(settingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public NodeMode getMode() {
        return requireSettings().getMode();
    }

    private NodeSettings requireSettings() {
        return settingsRepository.findById(NodeSettings.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private NodeSettingsView toView(NodeSettings settings) {
        String password = settings.getMqttPassword();
        return new NodeSettingsView(
                settings.getMode(),
                settings.getMqttHost(),
                settings.getMqttPort(),
                settings.getMqttClientId(),
                settings.getMqttUsername(),
                password != null && !password.isBlank(),
                settings.getMqttTopicPrefix(),
                settings.getUpdatedAt());
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
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

    private String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
