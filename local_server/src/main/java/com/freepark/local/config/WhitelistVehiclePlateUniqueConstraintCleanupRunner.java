package com.freepark.local.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 一次性迁移：清理 whitelist_vehicle 表历史版本建立的 (lot_id, plate_number) 唯一索引。
 *
 * <p>白名单承载停车卡功能后，同一车场同一车牌允许多条记录（每张卡带各自时间区间），
 * 实体已不再声明唯一约束；但 ddl-auto=update 不会删除数据库中已存在的唯一索引，
 * 此处动态查找并删除该唯一索引。幂等执行：无匹配索引或表不存在时为空操作，仅对 MySQL/MariaDB 生效
 * （H2 等测试库由实体重新建表，本身不含该索引）。
 */
@Component
@Order(2)
public class WhitelistVehiclePlateUniqueConstraintCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WhitelistVehiclePlateUniqueConstraintCleanupRunner.class);

    private static final String TABLE = "whitelist_vehicle";

    private final DataSource dataSource;

    public WhitelistVehiclePlateUniqueConstraintCleanupRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            if (!isMySqlLike(connection) || !tableExists(connection)) {
                return;
            }
            List<String> indexes = findPlateUniqueIndexes(connection);
            for (String indexName : indexes) {
                dropIndex(connection, indexName);
            }
        } catch (SQLException e) {
            // 迁移失败不应阻断启动：加卡时若索引仍存在会出现唯一冲突，日志明确提示。
            log.warn("清理 whitelist_vehicle (lot_id, plate_number) 唯一索引失败：{}", e.getMessage());
        }
    }

    /** 查找恰好由 lot_id + plate_number 两列构成的唯一索引（即历史约束生成的索引）。 */
    private static List<String> findPlateUniqueIndexes(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = """
                select index_name from information_schema.statistics
                where table_schema = database()
                  and table_name = ?
                  and non_unique = 0
                group by index_name
                having count(distinct column_name) = 2
                   and sum(column_name in ('lot_id', 'plate_number')) = 2
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TABLE);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString(1));
                }
            }
        }
        return names;
    }

    private static void dropIndex(Connection connection, String indexName) throws SQLException {
        ensureLotIdSupportIndex(connection, indexName);
        String sql = "alter table " + TABLE + " drop index `" + indexName + "`";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        log.info("已删除 whitelist_vehicle 唯一索引 {}（白名单/停车卡允许同车牌多条）", indexName);
    }

    /**
     * whitelist_vehicle.lot_id 上存在指向 parking_lot 的外键（FKog600o8g7dxgqf5y0kqe0qt4u）。
     * 删除唯一索引前需保证仍有一个以 lot_id 为第一列的索引供该外键使用，
     * 否则 MySQL 报 1553（Cannot drop index ... needed in a foreign key constraint）拒绝删除。
     */
    private static void ensureLotIdSupportIndex(Connection connection, String droppingIndex) throws SQLException {
        String sql = """
                select 1 from information_schema.statistics
                where table_schema = database()
                  and table_name = ?
                  and column_name = 'lot_id'
                  and index_name <> ?
                  and seq_in_index = 1
                limit 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TABLE);
            statement.setString(2, droppingIndex);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return; // 已有其它以 lot_id 为首的索引，可直接删除
                }
            }
        }
        String createSql = "alter table " + TABLE + " add index idx_lot_id (lot_id)";
        try (Statement statement = connection.createStatement()) {
            statement.execute(createSql);
        }
        log.info("已为 whitelist_vehicle.lot_id 补建普通索引 idx_lot_id（供外键使用，替代被删除的唯一索引）");
    }

    private static boolean tableExists(Connection connection) throws SQLException {
        String sql = """
                select 1 from information_schema.tables
                where table_schema = database() and table_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, TABLE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
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
}
