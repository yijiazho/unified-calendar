package com.unifiedcalendar.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unifiedcalendar.calendar.GoogleOAuthService;
import com.unifiedcalendar.calendar.GoogleProviderEventService;
import com.unifiedcalendar.calendar.GoogleTokenRefresher;
import com.unifiedcalendar.calendar.OutlookProviderEventService;
import com.unifiedcalendar.calendar.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("POST /bookings integration")
class BookingIntegrationTest {

    // Far-future Sunday → derive a Monday (dayOfWeek=0) working hours record
    private static final LocalDate FUTURE_DATE = LocalDate.of(2099, 6, 2); // Monday
    private static final int FUTURE_DAY_OF_WEEK = FUTURE_DATE.getDayOfWeek().ordinal();
    private static final Instant SLOT_START = Instant.parse("2099-06-02T09:00:00Z");
    private static final Instant SLOT_END   = SLOT_START.plus(30, ChronoUnit.MINUTES);

    @MockBean
    @SuppressWarnings("unused")
    private GoogleOAuthService googleOAuthService;

    @MockBean
    @SuppressWarnings("unused")
    private GoogleTokenRefresher googleTokenRefresher;

    /** Replaces the real Google/Outlook API calls so no network is needed. */
    @MockBean
    private GoogleProviderEventService googleProviderEventService;

    @MockBean
    @SuppressWarnings("unused")
    private OutlookProviderEventService outlookProviderEventService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long adminId;
    private String slug;

    @BeforeEach
    void setUp() {
        slug = "booking-test-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO admins (email, password_hash, slug, timezone) VALUES (?, ?, ?, ?)",
                slug + "@example.com", "hash", slug, "UTC");
        adminId = jdbc.queryForObject("SELECT id FROM admins WHERE slug = ?", Long.class, slug);

        // Working hours on FUTURE_DATE's weekday
        jdbc.update("INSERT INTO working_hours (admin_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)",
                adminId, FUTURE_DAY_OF_WEEK, "09:00", "17:00");

        // Primary Google calendar account (tokens are placeholder values — calls are mocked)
        jdbc.update(
            "INSERT INTO calendar_accounts " +
            "(admin_id, provider, provider_account_id, email, encrypted_access_token, encrypted_refresh_token, is_primary) " +
            "VALUES (?, 'GOOGLE', ?, ?, ?, ?, 1)",
            adminId, "google-sub", "admin@gmail.com", "enc-access", "enc-refresh");

        // Mock GoogleProviderEventService so no real Google API calls are made
        when(googleProviderEventService.supports(Provider.GOOGLE)).thenReturn(true);
        when(googleProviderEventService.hasConflict(any(), anyString(), any(), any())).thenReturn(false);
        when(googleProviderEventService.createEvent(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn("provider-event-id-" + UUID.randomUUID());

        // GoogleTokenRefresher must return a valid result (account has placeholder encrypted tokens)
        com.unifiedcalendar.calendar.CalendarAccount account = new com.unifiedcalendar.calendar.CalendarAccount(
                jdbc.queryForObject("SELECT id FROM calendar_accounts WHERE admin_id = ?", Long.class, adminId),
                adminId, Provider.GOOGLE, "google-sub", "admin@gmail.com",
                "enc-access", "enc-refresh", true, Instant.now(), null, null);
        when(googleTokenRefresher.refreshAccessToken(any()))
                .thenReturn(new com.unifiedcalendar.calendar.TokenRefreshResult("plain-token", account));
    }

    @Test
    @DisplayName("valid booking creates rows in bookings and calendar_events, returns 201")
    void validBookingCreatesRowsAndReturns201() throws Exception {
        Map<String, String> body = Map.of(
                "slug", slug,
                "slotStart", SLOT_START.toString(),
                "slotEnd",   SLOT_END.toString(),
                "visitorName",  "John Doe",
                "visitorEmail", "john@example.com"
        );

        MvcResult result = mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNumber())
                .andExpect(jsonPath("$.visitorName").value("John Doe"))
                .andExpect(jsonPath("$.slotStart").value(SLOT_START.toString()))
                .andExpect(jsonPath("$.slotEnd").value(SLOT_END.toString()))
                .andExpect(jsonPath("$.adminName").value(slug))
                .andExpect(jsonPath("$.cancelToken").isString())
                .andExpect(jsonPath("$.rescheduleToken").isString())
                .andReturn();

        // Both rows must exist in the DB
        Long bookingCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE admin_id = ? AND visitor_email = 'john@example.com'",
                Long.class, adminId);
        assertThat(bookingCount).isEqualTo(1L);

        Long eventCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM calendar_events WHERE admin_id = ? AND is_booking_event = 1",
                Long.class, adminId);
        assertThat(eventCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("cancel_token and reschedule_token in response are distinct UUIDs")
    void tokensAreDistinctUuids() throws Exception {
        Map<String, String> body = Map.of(
                "slug", slug,
                "slotStart", SLOT_START.toString(),
                "slotEnd",   SLOT_END.toString(),
                "visitorName",  "Alice",
                "visitorEmail", "alice@example.com"
        );

        MvcResult result = mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        String cancelToken     = (String) responseBody.get("cancelToken");
        String rescheduleToken = (String) responseBody.get("rescheduleToken");

        assertThat(cancelToken).isNotBlank();
        assertThat(rescheduleToken).isNotBlank();
        assertThat(cancelToken).isNotEqualTo(rescheduleToken);
        // Verify UUID format
        assertThat(UUID.fromString(cancelToken)).isNotNull();
        assertThat(UUID.fromString(rescheduleToken)).isNotNull();
    }

    @Test
    @DisplayName("returns 404 for non-existent slug")
    void returns404ForUnknownSlug() throws Exception {
        Map<String, String> body = Map.of(
                "slug", "no-such-slug",
                "slotStart", SLOT_START.toString(),
                "slotEnd",   SLOT_END.toString(),
                "visitorName",  "John",
                "visitorEmail", "john@example.com"
        );

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Admin not found"));
    }

    @Test
    @DisplayName("returns 409 when slot is already booked (SQLite cache check)")
    void returns409ForAlreadyBookedSlot() throws Exception {
        // Insert a blocking calendar_event covering the slot
        Long accountId = jdbc.queryForObject(
                "SELECT id FROM calendar_accounts WHERE admin_id = ?", Long.class, adminId);
        jdbc.update(
            "INSERT INTO calendar_events " +
            "(admin_id, calendar_account_id, provider, provider_event_id, title, start_time_utc, end_time_utc, is_booking_event, last_synced_at) " +
            "VALUES (?, ?, 'GOOGLE', ?, 'Blocking Event', ?, ?, 0, ?)",
            adminId, accountId, "blocker-event-id",
            SLOT_START.toString(), SLOT_END.toString(), Instant.now().toString());

        Map<String, String> body = Map.of(
                "slug", slug,
                "slotStart", SLOT_START.toString(),
                "slotEnd",   SLOT_END.toString(),
                "visitorName",  "John",
                "visitorEmail", "john@example.com"
        );

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("This time slot is no longer available."));
    }

    @Test
    @DisplayName("returns 409 when live provider check finds a conflict the cache missed")
    void returns409WhenLiveProviderConflictFound() throws Exception {
        when(googleProviderEventService.hasConflict(any(), anyString(), any(), any())).thenReturn(true);

        Map<String, String> body = Map.of(
                "slug", slug,
                "slotStart", SLOT_START.toString(),
                "slotEnd",   SLOT_END.toString(),
                "visitorName",  "John",
                "visitorEmail", "john@example.com"
        );

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("This time slot is no longer available."));
    }

    @Test
    @DisplayName("returns 400 when required fields are missing")
    void returns400ForMissingRequiredFields() throws Exception {
        Map<String, String> body = Map.of(
                "slug", slug,
                "slotStart", SLOT_START.toString(),
                "slotEnd",   SLOT_END.toString()
                // visitorName and visitorEmail missing
        );

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @DisplayName("endpoint is accessible without a session cookie")
    void accessibleWithoutSession() throws Exception {
        Map<String, String> body = Map.of(
                "slug", slug,
                "slotStart", SLOT_START.toString(),
                "slotEnd",   SLOT_END.toString(),
                "visitorName",  "Anon Visitor",
                "visitorEmail", "anon@example.com"
        );

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }
}
