package com.freepark.local.domain;

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
    public static final String DEFAULT_MQTT_TOPIC_PREFIX = "freepark/edge";

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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
