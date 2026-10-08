package com.freepark.local.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freepark.local.dbconfig.DatabaseSettingsStore;

public class DatabaseConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfigEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path file = DatabaseSettingsStore.resolveFile(environment.getProperty("FREEPARK_DB_CONFIG"));
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(file.toFile());
            String host = text(root, "host");
            String database = text(root, "database");
            String username = text(root, "username");
            if (host.isEmpty() || database.isEmpty() || username.isEmpty()) {
                log.warn("Ignore database config file {} (host/database/username required)", file.toAbsolutePath());
                return;
            }
            int port = root.path("port").isNumber() ? root.path("port").asInt() : 8306;
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("MYSQL_HOST", host);
            props.put("MYSQL_PORT", String.valueOf(port));
            props.put("MYSQL_DATABASE", database);
            props.put("MYSQL_USER", username);
            String password = text(root, "password");
            if (!password.isEmpty()) {
                props.put("MYSQL_PASSWORD", password);
            }
            environment.getPropertySources().addFirst(new MapPropertySource("freeparkDbFile", props));
            log.info("Loaded database config from {}", file.toAbsolutePath());
        } catch (Exception ex) {
            log.warn("Failed to read database config {}: {}", file.toAbsolutePath(), ex.getMessage());
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isMissingNode() || node.isNull() ? "" : node.asText("").trim();
    }
}
