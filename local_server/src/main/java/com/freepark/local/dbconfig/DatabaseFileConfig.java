package com.freepark.local.dbconfig;

public record DatabaseFileConfig(
        String host,
        int port,
        String database,
        String username,
        String password) {
}
