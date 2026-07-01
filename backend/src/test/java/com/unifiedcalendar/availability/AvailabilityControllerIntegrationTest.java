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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("GET /availability integration")
class AvailabilityControllerIntegrationTest {

    private static final LocalDate FUTURE_DATE = LocalDate.of(2099, 6, 1);
    private static final int FUTURE_DAY_OF_WEEK = FUTURE_DATE.getDayOfWeek().ordinal();

    // Suppressed to prevent Spring from attempting live OAuth network calls during context startup.
    // Removing these mocks causes the test context to fail with a connection error, not a compile error.
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

    private String slug;

    @BeforeEach
    void setUp() {
        slug = "avail-ctrl-slug";
        jdbc.update("INSERT INTO admins (email, password_hash, slug, timezone) VALUES (?, ?, ?, ?)",
                "avail-ctrl@example.com", "hash", slug, "UTC");
        Long adminId = jdbc.queryForObject("SELECT id FROM admins WHERE slug = ?", Long.class, slug);
        jdbc.update("INSERT INTO working_hours (admin_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)",
                adminId, FUTURE_DAY_OF_WEEK, "09:00", "17:00");
    }

    @Test
    @DisplayName("returns 200 with 16 slots for a free day")
    void returnsSlots() throws Exception {
        mvc.perform(get("/availability").param("slug", slug).param("date", FUTURE_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(FUTURE_DATE.toString()))
                .andExpect(jsonPath("$.adminTimezone").value("UTC"))
                .andExpect(jsonPath("$.slots.length()").value(16))
                .andExpect(jsonPath("$.slots[0].start").value("2099-06-01T09:00:00Z"))
                .andExpect(jsonPath("$.slots[0].end").value("2099-06-01T09:30:00Z"));
    }

    @Test
    @DisplayName("returns 200 with empty slots array when no working hours on that day")
    void returnsEmptyWhenNoWorkingHours() throws Exception {
        // Use a different date whose day of week has no working hours configured
        LocalDate differentDay = FUTURE_DATE.plusDays(1);
        mvc.perform(get("/availability").param("slug", slug).param("date", differentDay.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(0));
    }

    @Test
    @DisplayName("returns 404 for unknown slug")
    void returns404ForUnknownSlug() throws Exception {
        mvc.perform(get("/availability").param("slug", "no-such-slug").param("date", FUTURE_DATE.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("returns 400 for invalid date format")
    void returns400ForBadDate() throws Exception {
        mvc.perform(get("/availability").param("slug", slug).param("date", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("endpoint is accessible without a session cookie")
    void accessibleWithoutSession() throws Exception {
        mvc.perform(get("/availability").param("slug", slug).param("date", FUTURE_DATE.toString()))
                .andExpect(status().isOk());
    }
}
