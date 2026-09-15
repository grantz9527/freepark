package com.freepark.local.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "freepark")
public record FreeparkProperties(Jwt jwt, Admin admin, Preview preview) {

    public FreeparkProperties {
        if (preview == null) {
            preview = Preview.defaults();
        }
    }

    public record Jwt(String secret, Duration ttl) {
    }

    public record Admin(String username, String password, String displayName) {
    }

    /**
     * 一体机视频预览：经 Frigate 内置 go2rtc 拉流，不在本进程内置 ffmpeg。
     */
    public record Preview(
            Integer go2rtcPort,
            Boolean tryFrigateApiProxy,
            Integer maxSessions,
            Duration connectTimeout) {
        public Preview {
            if (go2rtcPort == null || go2rtcPort < 1 || go2rtcPort > 65535) {
                go2rtcPort = 1984;
            }
            if (tryFrigateApiProxy == null) {
                tryFrigateApiProxy = true;
            }
            if (maxSessions == null || maxSessions < 1) {
                maxSessions = 2;
            }
            if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
                connectTimeout = Duration.ofSeconds(20);
            }
        }

        static Preview defaults() {
            return new Preview(1984, true, 2, Duration.ofSeconds(20));
        }
    }
}
