package com.freepark.local.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.freepark.local.domain.FrigateSettings;
import com.freepark.local.domain.FrigateSettingsRepository;
import com.freepark.local.frigate.service.FrigateMqttSubscriber;

@Component
@Order(50)
public class FrigateInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FrigateInitializer.class);

    private final FrigateSettingsRepository settingsRepository;
    private final FrigateMqttSubscriber mqttSubscriber;
    private final String defaultApiHost;
    private final String defaultMqttHost;

    public FrigateInitializer(
            FrigateSettingsRepository settingsRepository,
            FrigateMqttSubscriber mqttSubscriber,
            @Value("${freepark.frigate.api-host:127.0.0.1}") String defaultApiHost,
            @Value("${freepark.frigate.mqtt-host:127.0.0.1}") String defaultMqttHost) {
        this.settingsRepository = settingsRepository;
        this.mqttSubscriber = mqttSubscriber;
        this.defaultApiHost = defaultApiHost;
        this.defaultMqttHost = defaultMqttHost;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!settingsRepository.existsById(FrigateSettings.SINGLETON_ID)) {
            FrigateSettings settings = new FrigateSettings(
                    defaultApiHost,
                    FrigateSettings.DEFAULT_API_PORT,
                    defaultMqttHost,
                    FrigateSettings.DEFAULT_MQTT_PORT,
                    FrigateSettings.DEFAULT_TOPIC_PREFIX);
            settings.setMqttUsername("freepark");
            settings.setMqttPassword("freepark");
            settingsRepository.save(settings);
            log.info("Initialized default Frigate settings apiHost={} mqttHost={}", defaultApiHost, defaultMqttHost);
        } else {
            remapLoopbackDefaults();
        }
        mqttSubscriber.reconnect();
    }

    /** Docker 里 127.0.0.1 是容器自己；仅当库里仍是回环地址时，改成 compose 注入的主机名。 */
    private void remapLoopbackDefaults() {
        FrigateSettings settings = settingsRepository.findById(FrigateSettings.SINGLETON_ID).orElse(null);
        if (settings == null) {
            return;
        }
        boolean changed = false;
        if (isLoopback(settings.getApiHost()) && !isLoopback(defaultApiHost)) {
            settings.setApiHost(defaultApiHost);
            changed = true;
        }
        if (isLoopback(settings.getMqttHost()) && !isLoopback(defaultMqttHost)) {
            settings.setMqttHost(defaultMqttHost);
            changed = true;
        }
        if (changed) {
            settingsRepository.save(settings);
            log.info("Remapped loopback Frigate hosts to apiHost={} mqttHost={}", settings.getApiHost(), settings.getMqttHost());
        }
    }

    private static boolean isLoopback(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String h = host.trim();
        return "127.0.0.1".equals(h) || "localhost".equalsIgnoreCase(h) || "::1".equals(h);
    }
}
