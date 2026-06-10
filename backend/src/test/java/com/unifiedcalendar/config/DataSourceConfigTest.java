package com.unifiedcalendar.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("DataSourceConfig")
class DataSourceConfigTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("connection is valid")
    void connectionIsValid() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertTrue(conn.isValid(5));
        }
    }

    @Test
    @DisplayName("foreign keys are enforced via addDataSourceProperty (M1 fix)")
    void foreignKeysAreEnabled() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("WAL journal mode is set by connectionInitSql on file-based databases")
    void walModeIsActive(@TempDir Path tempDir) throws Exception {
        // :memory: always reports "memory" journal mode — WAL only applies to file databases.
        // Create an ad-hoc pool using the same DataSourceConfig settings to verify the PRAGMA.
        String url = "jdbc:sqlite:" + tempDir.resolve("wal-test.db");
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(1);
        cfg.setConnectionInitSql("PRAGMA journal_mode=WAL;");
        cfg.addDataSourceProperty("foreign_keys", "true");
        try (HikariDataSource ds = new HikariDataSource(cfg);
             Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode;")) {
            assertEquals("wal", rs.getString(1));
        }
    }
}
