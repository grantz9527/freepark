package com.freepark.local.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "freepark")
public record FreeparkProperties(Jwt jwt, Admin admin) {

    public record Jwt(String secret, Duration ttl) {
    }

    public record Admin(String username, String password, String displayName) {
    }
}
