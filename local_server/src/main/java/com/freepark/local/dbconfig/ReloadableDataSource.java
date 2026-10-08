package com.freepark.local.dbconfig;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import com.zaxxer.hikari.HikariDataSource;

public final class ReloadableDataSource extends DelegatingDataSource {

    public ReloadableDataSource(DataSource target) {
        super(target);
    }

    public synchronized void replace(HikariDataSource next) {
        DataSource previous = getTargetDataSource();
        setTargetDataSource(next);
        if (previous instanceof HikariDataSource hikari && hikari != next) {
            hikari.close();
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        DataSource target = getTargetDataSource();
        return target == null ? super.unwrap(iface) : target.unwrap(iface);
    }
}
