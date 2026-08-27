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
@Table(name = "frigate_settings")
@EntityListeners(AuditingEntityListener.class)
public class FrigateSettings {

    public static final String SINGLETON_ID = "default";
    public static final String DEFAULT_API_HOST = "127.0.0.1";
    public static final int DEFAULT_API_PORT = 5000;
    public static final String DEFAULT_MQTT_HOST = "127.0.0.1";
    public static final int DEFAULT_MQTT_PORT = 1883;
    public static final String DEFAULT_TOPIC_PREFIX = "frigate";

    @Id
    @Column(length = 32, nullable = false, updatable = false)
    private String id = SINGLETON_ID;

    @Column(name = "api_host", nullable = false, length = 255)
    private String apiHost = DEFAULT_API_HOST;

    @Column(name = "api_port", nullable = false)
    private int apiPort = DEFAULT_API_PORT;

    @Column(name = "mqtt_host", nullable = false, length = 255)
    private String mqttHost = DEFAULT_MQTT_HOST;

    @Column(name = "mqtt_port", nullable = false)
    private int mqttPort = DEFAULT_MQTT_PORT;

    @Column(name = "topic_prefix", nullable = false, length = 255)
    private String topicPrefix = DEFAULT_TOPIC_PREFIX;

    @Column(name = "mqtt_username", length = 128)
    private String mqttUsername;

    @Column(name = "mqtt_password", length = 255)
    private String mqttPassword;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", nullable = false, length = 16)
    private FrigateLinkStatus linkStatus = FrigateLinkStatus.DISCONNECTED;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected FrigateSettings() {
    }

    public FrigateSettings(String apiHost, int apiPort, String mqttHost, int mqttPort, String topicPrefix) {
        this.apiHost = apiHost;
        this.apiPort = apiPort;
        this.mqttHost = mqttHost;
        this.mqttPort = mqttPort;
        this.topicPrefix = topicPrefix;
    }

    public String getId() {
        return id;
    }

    public String getApiHost() {
        return apiHost;
    }

    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public int getApiPort() {
        return apiPort;
    }

    public void setApiPort(int apiPort) {
        this.apiPort = apiPort;
    }

    public String getMqttHost() {
        return mqttHost;
    }

    public void setMqttHost(String mqttHost) {
        this.mqttHost = mqttHost;
    }

    public int getMqttPort() {
        return mqttPort;
    }

    public void setMqttPort(int mqttPort) {
        this.mqttPort = mqttPort;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FrigateLinkStatus getLinkStatus() {
        return linkStatus;
    }

    public void setLinkStatus(FrigateLinkStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    public Instant getLastTestAt() {
        return lastTestAt;
    }

    public void setLastTestAt(Instant lastTestAt) {
        this.lastTestAt = lastTestAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
