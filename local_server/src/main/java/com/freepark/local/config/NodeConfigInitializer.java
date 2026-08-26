package com.freepark.local.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.freepark.local.domain.NodeMode;
import com.freepark.local.domain.NodeSettings;
import com.freepark.local.domain.NodeSettingsRepository;

@Component
public class NodeConfigInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NodeConfigInitializer.class);

    private final NodeSettingsRepository settingsRepository;

    public NodeConfigInitializer(NodeSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!settingsRepository.existsById(NodeSettings.SINGLETON_ID)) {
            settingsRepository.save(new NodeSettings(NodeMode.OFFLINE));
            log.info("Initialized default node settings (mode=OFFLINE)");
        }
    }
}
