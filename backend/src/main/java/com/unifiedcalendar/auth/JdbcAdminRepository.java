package com.unifiedcalendar.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcAdminRepository implements AdminRepository {

    private final JdbcTemplate jdbc;

    JdbcAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Admin> ADMIN_ROW_MAPPER = (rs, rowNum) -> new Admin(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("slug"),
            rs.getString("timezone"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
    );

    @Override
    public Optional<Admin> findByEmail(String email) {
        List<Admin> results = jdbc.query(
                "SELECT id, email, password_hash, slug, timezone, created_at, updated_at FROM admins WHERE email = ?",
                ADMIN_ROW_MAPPER, email);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<Admin> findById(Long id) {
        List<Admin> results = jdbc.query(
                "SELECT id, email, password_hash, slug, timezone, created_at, updated_at FROM admins WHERE id = ?",
                ADMIN_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<Admin> findBySlug(String slug) {
        List<Admin> results = jdbc.query(
                "SELECT id, email, password_hash, slug, timezone, created_at, updated_at FROM admins WHERE slug = ?",
                ADMIN_ROW_MAPPER, slug);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Inserts a new admin row and returns the saved entity with its generated id. */
    @Override
    public Admin save(Admin admin) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO admins (email, password_hash, slug, timezone) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, admin.email());
            ps.setString(2, admin.passwordHash());
            ps.setString(3, admin.slug());
            ps.setString(4, admin.timezone());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("INSERT into admins did not return a generated key");
        }
        long id = key.longValue();
        return findById(id)
                .orElseThrow(() -> new IllegalStateException("Failed to load saved admin id=" + id));
    }
}
