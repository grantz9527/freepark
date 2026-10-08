package com.freepark.local.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.freepark.local.dbconfig.ReloadableDataSource;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseDataSourceConfig {

    @Bean
    @Primary
    ReloadableDataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password) {
        HikariDataSource hikari = new HikariDataSource();
        hikari.setJdbcUrl(url);
        hikari.setUsername(username);
        hikari.setPassword(password);
        hikari.setPoolName("freepark-local-hikari");
        hikari.setMaximumPoolSize(20);
        hikari.setMinimumIdle(5);
        return new ReloadableDataSource(hikari);
    }
}
