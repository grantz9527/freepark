package com.freepark.local.dbconfig.dto;

import java.time.Instant;

public record DatabaseSettingsView(
        String host,
        int port,
        String database,
        String username,
        boolean passwordSet,
        String source,
        boolean filePresent,
        boolean connected,
        Instant updatedAt) {
}
