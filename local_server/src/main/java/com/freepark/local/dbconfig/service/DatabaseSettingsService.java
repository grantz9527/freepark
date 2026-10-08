package com.freepark.local.dbconfig.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.dbconfig.DatabaseFileConfig;
import com.freepark.local.dbconfig.DatabaseJdbc;
import com.freepark.local.dbconfig.DatabaseSettingsStore;
import com.freepark.local.dbconfig.ReloadableDataSource;
import com.freepark.local.dbconfig.dto.DatabaseSettingsView;
import com.freepark.local.dbconfig.dto.UpdateDatabaseSettingsRequest;
import com.freepark.local.domain.LocalUser;
import com.freepark.local.domain.LocalUserRepository;
import com.freepark.local.domain.UserRole;
import com.zaxxer.hikari.HikariDataSource;

@Service
public class DatabaseSettingsService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSettingsService.class);
    private static final int CONNECT_TIMEOUT_MS = 8_000;

    private final ReloadableDataSource dataSource;
    private final LocalUserRepository users;
    private final ObjectMapper mapper;
    private final Path configFile;

    public DatabaseSettingsService(
            ReloadableDataSource dataSource,
            LocalUserRepository users,
            @Value("${FREEPARK_DB_CONFIG:}") String configPath) {
        this.dataSource = dataSource;
        this.users = users;
        this.configFile = DatabaseSettingsStore.resolveFile(configPath);
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public DatabaseSettingsView getSettings(UUID requesterId) {
        requireAdmin(requesterId);
        return toView();
    }

    public DatabaseSettingsView test(UUID requesterId, UpdateDatabaseSettingsRequest request) {
        requireAdmin(requesterId);
        DatabaseFileConfig config = resolveForConnect(request);
        probe(config);
        return toView();
    }

    public DatabaseSettingsView update(UUID requesterId, UpdateDatabaseSettingsRequest request) {
        requireAdmin(requesterId);
        DatabaseFileConfig config = resolveForConnect(request);
        probe(config);
        HikariDataSource next = openPool(config);
        try {
            writeFile(config);
        } catch (RuntimeException ex) {
            next.close();
            throw ex;
        }
        dataSource.replace(next);
        log.info("Database settings saved host={}:{} database={}", config.host(), config.port(), config.database());
        return toView();
    }

    public DatabaseSettingsView restoreDefaults(UUID requesterId) {
        requireAdmin(requesterId);
        DatabaseFileConfig defaults = envOrDefaultConfig();
        probe(defaults);
        HikariDataSource next = openPool(defaults);
        try {
            Files.deleteIfExists(configFile);
        } catch (Exception ex) {
            next.close();
            throw new BusinessException(ErrorCode.DB_SAVE_FAILED);
        }
        dataSource.replace(next);
        log.info("Database settings restored to environment/defaults host={}:{}", defaults.host(), defaults.port());
        return toView();
    }

    private DatabaseSettingsView toView() {
        DatabaseFileConfig file = readFile();
        String host;
        int port;
        String database;
        String username;
        boolean passwordSet;
        String source;
        if (file != null) {
            host = file.host();
            port = file.port();
            database = file.database();
            username = file.username();
            passwordSet = file.password() != null && !file.password().isBlank();
            source = "FILE";
        } else {
            DatabaseFileConfig env = envOrDefaultConfig();
            host = env.host();
            port = env.port();
            database = env.database();
            username = env.username();
            passwordSet = env.password() != null && !env.password().isBlank();
            source = System.getenv("MYSQL_HOST") != null ? "ENVIRONMENT" : "DEFAULT";
        }
        Instant updatedAt = null;
        try {
            if (Files.isRegularFile(configFile)) {
                updatedAt = Files.getLastModifiedTime(configFile).toInstant();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return new DatabaseSettingsView(
                host,
                port,
                database,
                username,
                passwordSet,
                source,
                Files.isRegularFile(configFile),
                currentPoolAlive(),
                updatedAt);
    }

    private DatabaseFileConfig resolveForConnect(UpdateDatabaseSettingsRequest request) {
        String host = DatabaseJdbc.normalizeHost(request.host());
        if (host.isEmpty()) {
            throw new BusinessException(ErrorCode.DB_INVALID_CONFIG);
        }
        String database = request.database().trim();
        String username = request.username().trim();
        if (!database.matches("[A-Za-z0-9_]+") || database.length() > 64) {
            throw new BusinessException(ErrorCode.DB_INVALID_CONFIG);
        }
        DatabaseFileConfig existing = readFile();
        String password = request.password() == null ? "" : request.password();
        if (password.isBlank()) {
            if (existing != null && existing.password() != null && !existing.password().isBlank()) {
                password = existing.password();
            } else {
                password = envOrDefaultConfig().password();
            }
        }
        return new DatabaseFileConfig(host, request.port(), database, username, password);
    }

    private void probe(DatabaseFileConfig config) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // DriverManager SPI still registers the connector when present.
        }
        String url = DatabaseJdbc.jdbcUrl(config.host(), config.port(), config.database())
                + "&connectTimeout=" + CONNECT_TIMEOUT_MS;
        try (Connection connection = DriverManager.getConnection(url, config.username(), config.password())) {
            if (!connection.isValid(5)) {
                throw new BusinessException(ErrorCode.DB_CONNECT_FAILED, "invalid");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new BusinessException(ErrorCode.DB_CONNECT_FAILED, ex.getMessage());
        }
    }

    private HikariDataSource openPool(DatabaseFileConfig config) {
        HikariDataSource next = new HikariDataSource();
        DataSource target = dataSource.getTargetDataSource();
        if (target instanceof HikariDataSource current) {
            next.setPoolName(current.getPoolName());
            next.setMaximumPoolSize(current.getMaximumPoolSize());
            next.setMinimumIdle(current.getMinimumIdle());
        } else {
            next.setPoolName("freepark-local-hikari");
            next.setMaximumPoolSize(20);
            next.setMinimumIdle(5);
        }
        next.setJdbcUrl(DatabaseJdbc.jdbcUrl(config.host(), config.port(), config.database()));
        next.setUsername(config.username());
        next.setPassword(config.password() == null ? "" : config.password());
        try (Connection connection = next.getConnection()) {
            if (!connection.isValid(5)) {
                next.close();
                throw new BusinessException(ErrorCode.DB_CONNECT_FAILED, "invalid");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (SQLException ex) {
            next.close();
            throw new BusinessException(ErrorCode.DB_CONNECT_FAILED, ex.getMessage());
        }
        return next;
    }

    private boolean currentPoolAlive() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(3);
        } catch (Exception ex) {
            return false;
        }
    }

    private DatabaseFileConfig readFile() {
        if (!Files.isRegularFile(configFile)) {
            return null;
        }
        try {
            return mapper.readValue(configFile.toFile(), DatabaseFileConfig.class);
        } catch (Exception ex) {
            log.warn("Failed to parse {}: {}", configFile, ex.getMessage());
            return null;
        }
    }

    private void writeFile(DatabaseFileConfig config) {
        try {
            Path parent = configFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writeValue(configFile.toFile(), config);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.DB_SAVE_FAILED);
        }
    }

    private DatabaseFileConfig envOrDefaultConfig() {
        String host = firstNonBlank(System.getenv("MYSQL_HOST"), "localhost");
        int port = parsePort(System.getenv("MYSQL_PORT"), 8306);
        String database = firstNonBlank(System.getenv("MYSQL_DATABASE"), "freepark_local");
        String username = firstNonBlank(System.getenv("MYSQL_USER"), "freepark_local");
        String password = firstNonBlank(System.getenv("MYSQL_PASSWORD"), "freepark_local");
        return new DatabaseFileConfig(host, port, database, username, password);
    }

    private static int parsePort(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void requireAdmin(UUID userId) {
        LocalUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
