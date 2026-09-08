package com.freepark.local.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "node_settings")
@EntityListeners(AuditingEntityListener.class)
public class NodeSettings {

    public static final String SINGLETON_ID = "default";
    public static final String DEFAULT_MQTT_HOST = "127.0.0.1";
    public static final int DEFAULT_MQTT_PORT = 1883;
    public static final String DEFAULT_MQTT_CLIENT_ID = "freepark-local-edge";
    /**
     * 心跳主题路径前缀（缺省时使用）：心跳按 {@code {prefix}/{nodeCode}} 发布，
     * 前缀需包含 {@code /heartbeat} 段，与云端「心跳订阅主题」{@code {prefix}/#} 匹配。
     */
    public static final String DEFAULT_MQTT_TOPIC_PREFIX = "parking/heartbeat";
    /**
     * 停车流水上报主题路径前缀（缺省时使用）：流水变化按
     * {@code {reportTopicPrefix}/{nodeCode}} 发布（默认 {@code parking/report/{nodeCode}}），
     * 与云端「上报数据订阅主题」{@code {reportTopicPrefix}/#} 匹配（如 {@code parking/report/#}）。
     */
    public static final String DEFAULT_REPORT_TOPIC_PREFIX = "parking/report";
    /** 节点编号最大长度（与云端“边缘节点管理”创建的节点编号一致，创建后不可更改） */
    public static final int MAX_NODE_CODE_LENGTH = 64;

    @Id
    @Column(length = 32, nullable = false, updatable = false)
    private String id = SINGLETON_ID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NodeMode mode = NodeMode.OFFLINE;

    @Column(name = "mqtt_host", length = 255)
    private String mqttHost;

    @Column(name = "mqtt_port")
    private Integer mqttPort;

    @Column(name = "mqtt_client_id", length = 128)
    private String mqttClientId;

    @Column(name = "mqtt_username", length = 128)
    private String mqttUsername;

    @Column(name = "mqtt_password", length = 255)
    private String mqttPassword;

    @Column(name = "mqtt_topic_prefix", length = 255)
    private String mqttTopicPrefix;

    /**
     * 配置同步订阅主题前缀（可为空）：为空表示本节点不订阅云端配置同步
     * （edge.config.sync/3 全量/增量帧）；非空时订阅主题为
     * {@code {configSyncTopicPrefix}/{nodeCode}}，nodeCode 复用下方节点编号。
     */
    @Column(name = "config_sync_topic_prefix", length = 255)
    private String configSyncTopicPrefix;

    /**
     * 停车流水上报主题前缀（可为空）：为空按默认值 {@code parking/report} 处理；
     * 非空时发布主题为 {@code {reportTopicPrefix}/{nodeCode}}，nodeCode 复用下方节点编号。
     */
    @Column(name = "report_topic_prefix", length = 255)
    private String reportTopicPrefix;

    /** 云端节点编号：云端“边缘节点管理”中创建的节点编号，作为心跳/上报主题末段；仅在 EDGE 模式生效。 */
    @Column(name = "node_code", length = 64)
    private String nodeCode;

    /** 算费请求接口地址：边缘节点向远程算费服务请求费用（入参车牌+车牌颜色，返回金额）。 */
    @Column(name = "fee_api_url", length = 255)
    private String feeApiUrl;

    /** 是否启用模拟金额（本地调试）：启用后算费请求直接返回模拟金额，不再调用远程算费接口。 */
    @Column(name = "fee_mock_enabled", nullable = false)
    private boolean feeMockEnabled = false;

    /** 模拟金额：feeMockEnabled=true 时算费请求返回的固定金额。 */
    @Column(name = "fee_mock_amount", precision = 10, scale = 2)
    private BigDecimal feeMockAmount;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected NodeSettings() {
    }

    public NodeSettings(NodeMode mode) {
        this.mode = mode;
    }

    public String getId() {
        return id;
    }

    public NodeMode getMode() {
        return mode;
    }

    public void setMode(NodeMode mode) {
        this.mode = mode;
    }

    public String getMqttHost() {
        return mqttHost;
    }

    public void setMqttHost(String mqttHost) {
        this.mqttHost = mqttHost;
    }

    public Integer getMqttPort() {
        return mqttPort;
    }

    public void setMqttPort(Integer mqttPort) {
        this.mqttPort = mqttPort;
    }

    public String getMqttClientId() {
        return mqttClientId;
    }

    public void setMqttClientId(String mqttClientId) {
        this.mqttClientId = mqttClientId;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public void setMqttUsername(String mqttUsername) {
        this.mqttUsername = mqttUsername;
    }

    public String getMqttPassword() {
        return mqttPassword;
    }

    public void setMqttPassword(String mqttPassword) {
        this.mqttPassword = mqttPassword;
    }

    public String getMqttTopicPrefix() {
        return mqttTopicPrefix;
    }

    public void setMqttTopicPrefix(String mqttTopicPrefix) {
        this.mqttTopicPrefix = mqttTopicPrefix;
    }

    public String getConfigSyncTopicPrefix() {
        return configSyncTopicPrefix;
    }

    public void setConfigSyncTopicPrefix(String configSyncTopicPrefix) {
        this.configSyncTopicPrefix = configSyncTopicPrefix;
    }

    public String getReportTopicPrefix() {
        return reportTopicPrefix;
    }

    public void setReportTopicPrefix(String reportTopicPrefix) {
        this.reportTopicPrefix = reportTopicPrefix;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getFeeApiUrl() {
        return feeApiUrl;
    }

    public void setFeeApiUrl(String feeApiUrl) {
        this.feeApiUrl = feeApiUrl;
    }

    public boolean isFeeMockEnabled() {
        return feeMockEnabled;
    }

    public void setFeeMockEnabled(boolean feeMockEnabled) {
        this.feeMockEnabled = feeMockEnabled;
    }

    public BigDecimal getFeeMockAmount() {
        return feeMockAmount;
    }

    public void setFeeMockAmount(BigDecimal feeMockAmount) {
        this.feeMockAmount = feeMockAmount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
