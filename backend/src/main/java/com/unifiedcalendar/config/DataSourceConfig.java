package com.unifiedcalendar.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    /**
     * Creates the SQLite datasource, ensuring the parent directory and WAL mode are
     * configured before Flyway or any repository touches the connection.
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        String filePath = jdbcUrl.replaceFirst("^jdbc:sqlite:", "");
        Path dbPath = Paths.get(filePath);
        if (dbPath.getParent() != null) {
            try {
                Files.createDirectories(dbPath.getParent());
            } catch (IOException e) {
                throw new RuntimeException("Cannot create database directory: " + dbPath.getParent(), e);
            }
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        // M1: SQLite JDBC ignores semicolon-delimited statements in connectionInitSql,
        // so the second PRAGMA would be silently skipped. Use driver properties instead.
        config.setConnectionInitSql("PRAGMA journal_mode=WAL;");
        config.addDataSourceProperty("foreign_keys", "true");
        return new HikariDataSource(config);
    }
}
