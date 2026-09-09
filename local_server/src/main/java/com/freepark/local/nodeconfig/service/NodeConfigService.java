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
            // 配置同步订阅前缀：可为空（空=不订阅云端配置同步）；非空时去除尾部斜杠后保存
            settings.setConfigSyncTopicPrefix(
                    trimToNull(request.configSyncTopicPrefix()) == null
                            ? null
                            : stripTrailingSlash(trimToNull(request.configSyncTopicPrefix())));
            // 停车流水上报前缀：可为空（空=默认 parking/report）；非空时去除尾部斜杠后保存
            settings.setReportTopicPrefix(
                    trimToNull(request.reportTopicPrefix()) == null
                            ? NodeSettings.DEFAULT_REPORT_TOPIC_PREFIX
                            : stripTrailingSlash(trimToNull(request.reportTopicPrefix())));
            // 节点编号：云端“边缘节点管理”创建的编号，作为心跳/上报主题末段，EDGE 模式必填
            String nodeCode = trimToNull(request.nodeCode());
            if (nodeCode == null || !isTopicSafe(nodeCode)) {
                throw new BusinessException(ErrorCode.INVALID_NODE_CONFIG);
            }
            settings.setNodeCode(nodeCode);
            // 算费请求接口仅在边缘节点模式下配置并生效：必须为 http:// 或 https:// 完整地址，
            // 避免误填文档占位写法「http(s)://…」或非 URL 内容导致运行期算费失败
            String feeApiUrl = trimToNull(request.feeApiUrl());
            if (feeApiUrl != null
                    && !feeApiUrl.startsWith("http://")
                    && !feeApiUrl.startsWith("https://")) {
                throw new BusinessException(ErrorCode.INVALID_NODE_CONFIG);
            }
            settings.setFeeApiUrl(feeApiUrl);
            // 模拟金额开关与金额
            boolean mockEnabled = Boolean.TRUE.equals(request.feeMockEnabled());
            settings.setFeeMockEnabled(mockEnabled);
            if (mockEnabled) {
                if (request.feeMockAmount() == null || request.feeMockAmount().signum() < 0) {
                    throw new BusinessException(ErrorCode.INVALID_FEE_MOCK_CONFIG);
                }
                settings.setFeeMockAmount(request.feeMockAmount());
            }
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
                settings.getConfigSyncTopicPrefix(),
                settings.getReportTopicPrefix(),
                settings.getNodeCode(),
                settings.getFeeApiUrl(),
                settings.isFeeMockEnabled(),
                settings.getFeeMockAmount(),
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

    /** 节点编号用作 MQTT 主题末段：仅允许字母数字与 - _，不允许 / 通配符空白等 */
    private boolean isTopicSafe(String value) {
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
