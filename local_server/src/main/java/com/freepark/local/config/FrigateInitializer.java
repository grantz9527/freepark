package com.freepark.local.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public FrigateInitializer(FrigateSettingsRepository settingsRepository, FrigateMqttSubscriber mqttSubscriber) {
        this.settingsRepository = settingsRepository;
        this.mqttSubscriber = mqttSubscriber;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!settingsRepository.existsById(FrigateSettings.SINGLETON_ID)) {
            settingsRepository.save(new FrigateSettings(
                    FrigateSettings.DEFAULT_API_HOST,
                    FrigateSettings.DEFAULT_API_PORT,
                    FrigateSettings.DEFAULT_MQTT_HOST,
                    FrigateSettings.DEFAULT_MQTT_PORT,
                    FrigateSettings.DEFAULT_TOPIC_PREFIX));
            log.info("Initialized default Frigate settings");
        }
        mqttSubscriber.reconnect();
    }
}
