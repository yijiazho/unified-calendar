package com.unifiedcalendar.availability;

import com.unifiedcalendar.calendar.GoogleOAuthService;
import com.unifiedcalendar.calendar.GoogleTokenRefresher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("GET /s/{slug} integration")
class PublicControllerIntegrationTest {

    @MockBean
    @SuppressWarnings("unused")
    private GoogleOAuthService googleOAuthService;

    @MockBean
    @SuppressWarnings("unused")
    private GoogleTokenRefresher googleTokenRefresher;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Long adminId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO admins (email, password_hash, slug, timezone) VALUES (?, ?, ?, ?)",
                "jane-smith@example.com", "hash", "jane-smith", "America/New_York");
        adminId = jdbc.queryForObject("SELECT id FROM admins WHERE slug = ?", Long.class, "jane-smith");
    }

    @Test
    @DisplayName("returns 200 with slug, name derived from email, and timezone")
    void returnsAdminPublicInfo() throws Exception {
        mvc.perform(get("/s/jane-smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("jane-smith"))
                .andExpect(jsonPath("$.name").value("jane-smith"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"));
    }

    @Test
    @DisplayName("returns 404 for an unknown slug")
    void returns404ForUnknownSlug() throws Exception {
        mvc.perform(get("/s/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("endpoint is accessible without a session cookie")
    void accessibleWithoutSession() throws Exception {
        mvc.perform(get("/s/jane-smith"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("authenticated admin session is ignored — endpoint still returns 200")
    void authenticatedAdminSessionSucceeds() throws Exception {
        mvc.perform(get("/s/jane-smith").sessionAttr("adminId", adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("jane-smith"));
    }

    @Test
    @DisplayName("name uses the part of the email before @")
    void nameDerivedFromEmail() throws Exception {
        jdbc.update("INSERT INTO admins (email, password_hash, slug, timezone) VALUES (?, ?, ?, ?)",
                "alice.wonder@corp.io", "hash", "alice-w", "UTC");
        mvc.perform(get("/s/alice-w"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("alice.wonder"));
    }
}
