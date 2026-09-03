package com.freepark.local.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 一次性迁移：将历史内部枚举值（TENANT/APPOINTMENT/VISITOR）映射为
 * driver-api 车辆类型枚举（VehicleType）的值，兼容旧库存量数据。
 *
 * <p>映射关系：TENANT→MONTHLY、APPOINTMENT→RESERVED、VISITOR→OTHER（OWNER/OTHER 不变）。
 * 幂等执行，无旧值时为空操作。
 *
 * <p>历史表 vehicle_type 列可能为 MySQL ENUM（仅含旧值），新枚举值无法写入，
 * 故迁移前先将其放宽为 VARCHAR(16)；其余方言（如测试用 H2）列已是 VARCHAR，跳过 ALTER。
 */
@Component
@Order(1)
public class VehicleTypeMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VehicleTypeMigrationRunner.class);

    private static final Map<String, String> MIGRATION = Map.of(
            "TENANT", "MONTHLY",
            "APPOINTMENT", "RESERVED",
            "VISITOR", "OTHER");

    private static final List<String> TABLES = List.of("whitelist_vehicle", "internal_vehicle");

    private final DataSource dataSource;

    public VehicleTypeMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int migrated = 0;
        try (Connection connection = dataSource.getConnection()) {
            boolean mysqlLike = isMySqlLike(connection);
            for (String table : TABLES) {
                if (mysqlLike && isEnumColumn(connection, table)) {
                    widenVehicleTypeColumn(connection, table);
                }
                migrated += migrateTable(connection, table);
            }
        }
        if (migrated > 0) {
            log.info(
                    "Migrated {} vehicle records to driver-api VehicleType values "
                            + "(TENANT->MONTHLY, APPOINTMENT->RESERVED, VISITOR->OTHER)",
                    migrated);
        }
    }

    private static boolean isMySqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if (product == null) {
            return false;
        }
        String name = product.toLowerCase(Locale.ROOT);
        return name.contains("mysql") || name.contains("maria");
    }

    private static boolean isEnumColumn(Connection connection, String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, table, "vehicle_type")) {
            if (columns.next()) {
                String typeName = columns.getString("TYPE_NAME");
                return typeName != null && typeName.toUpperCase(Locale.ROOT).contains("ENUM");
            }
        }
        return false;
    }

    private static void widenVehicleTypeColumn(Connection connection, String table) throws SQLException {
        String sql = "alter table " + table
                + " modify column vehicle_type varchar(16) not null default 'OTHER'";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        log.info("Widened {} vehicle_type column to VARCHAR(16)", table);
    }

    private static int migrateTable(Connection connection, String table) throws SQLException {
        int migrated = 0;
        String sql = "update " + table + " set vehicle_type = ? where vehicle_type = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : MIGRATION.entrySet()) {
                statement.setString(1, entry.getValue());
                statement.setString(2, entry.getKey());
                migrated += statement.executeUpdate();
            }
        }
        return migrated;
    }
}
