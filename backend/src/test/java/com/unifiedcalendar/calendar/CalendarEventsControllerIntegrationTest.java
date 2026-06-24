package com.unifiedcalendar.calendar;

import com.unifiedcalendar.calendar.sync.CalendarSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("GET /calendar/events and POST /calendar/sync integration")
class CalendarEventsControllerIntegrationTest {

    // CalendarSyncService is @Profile("!test") — provide a mock so the context loads.
    @MockBean
    @SuppressWarnings("unused")
    private CalendarSyncService calendarSyncService;

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

    private static final String SIGNUP_URL = "/auth/signup";
    private static final String LOGIN_URL  = "/auth/login";
    private static final String EVENTS_URL = "/calendar/events";
    private static final String SYNC_URL   = "/calendar/sync";

    private MockHttpSession session;
    private Long adminId;
    private Long accountId;

    @BeforeEach
    void setUp() throws Exception {
        mvc.perform(post(SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"pass\",\"slug\":\"admin-slug\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isCreated());

        MvcResult loginResult = mvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andReturn();

        session = (MockHttpSession) loginResult.getRequest().getSession();

        adminId = jdbc.queryForObject("SELECT id FROM admins WHERE slug = 'admin-slug'", Long.class);
        jdbc.update(
                "INSERT INTO calendar_accounts " +
                "(admin_id, provider, provider_account_id, email, encrypted_access_token, encrypted_refresh_token) " +
                "VALUES (?, 'GOOGLE', 'sub-001', 'user@gmail.com', 'enc_access', 'enc_refresh')",
                adminId);
        accountId = jdbc.queryForObject(
                "SELECT id FROM calendar_accounts WHERE admin_id = ?", Long.class, adminId);
    }

    private void insertEvent(String providerEventId, String start, String end) {
        jdbc.update(
                "INSERT INTO calendar_events " +
                "(admin_id, calendar_account_id, provider, provider_event_id, title, start_time_utc, end_time_utc) " +
                "VALUES (?, ?, 'GOOGLE', ?, 'Team standup', ?, ?)",
                adminId, accountId, providerEventId, start, end);
    }

    @Test
    @DisplayName("GET /calendar/events returns events in range with calendarEmail")
    void getEventsInRange() throws Exception {
        insertEvent("evt-001", "2024-03-12T09:00:00Z", "2024-03-12T09:30:00Z");

        mvc.perform(get(EVENTS_URL)
                .param("start", "2024-03-01")
                .param("end", "2024-03-31")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Team standup"))
                .andExpect(jsonPath("$[0].calendarEmail").value("user@gmail.com"))
                .andExpect(jsonPath("$[0].provider").value("GOOGLE"))
                .andExpect(jsonPath("$[0].start").value("2024-03-12T09:00:00Z"));
    }

    @Test
    @DisplayName("GET /calendar/events returns empty array when no events match")
    void getEventsEmptyRange() throws Exception {
        mvc.perform(get(EVENTS_URL)
                .param("start", "2024-03-01")
                .param("end", "2024-03-31")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("GET /calendar/events includes an event that spans the start of the range")
    void getEventsIncludesSpanningEvent() throws Exception {
        // Starts before range start, ends after range start — must be included (overlap query)
        insertEvent("spans-start", "2024-02-28T22:00:00Z", "2024-03-01T01:00:00Z");

        mvc.perform(get(EVENTS_URL)
                .param("start", "2024-03-01T00:00:00Z")
                .param("end", "2024-03-31T23:59:59Z")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /calendar/events accepts datetime strings in addition to date-only")
    void getEventsAcceptsDatetime() throws Exception {
        insertEvent("evt-dt", "2024-03-15T14:00:00Z", "2024-03-15T15:00:00Z");

        mvc.perform(get(EVENTS_URL)
                .param("start", "2024-03-01T00:00:00Z")
                .param("end", "2024-03-31T23:59:59Z")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /calendar/events includes isBookingEvent events and marks them correctly")
    void getEventsIncludesBookingEvents() throws Exception {
        jdbc.update(
                "INSERT INTO calendar_events " +
                "(admin_id, calendar_account_id, provider, provider_event_id, title, " +
                " start_time_utc, end_time_utc, is_booking_event) " +
                "VALUES (?, ?, 'GOOGLE', 'booking-001', 'Booking', " +
                " '2024-03-10T10:00:00Z', '2024-03-10T10:30:00Z', 1)",
                adminId, accountId);

        mvc.perform(get(EVENTS_URL)
                .param("start", "2024-03-01")
                .param("end", "2024-03-31")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isBookingEvent").value(true));
    }

    @Test
    @DisplayName("GET /calendar/events with malformed start param returns 400")
    void getEventsWithBadStartReturns400() throws Exception {
        mvc.perform(get(EVENTS_URL)
                .param("start", "notadate")
                .param("end", "2024-03-31")
                .session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /calendar/events with malformed end param returns 400")
    void getEventsWithBadEndReturns400() throws Exception {
        mvc.perform(get(EVENTS_URL)
                .param("start", "2024-03-01")
                .param("end", "notadate")
                .session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /calendar/events without session returns 401")
    void getEventsWithoutSessionReturns401() throws Exception {
        mvc.perform(get(EVENTS_URL)
                .param("start", "2024-03-01")
                .param("end", "2024-03-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /calendar/sync without session returns 401")
    void postSyncWithoutSessionReturns401() throws Exception {
        mvc.perform(post(SYNC_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /calendar/sync with session returns 202")
    void postSyncWithSessionReturns202() throws Exception {
        mvc.perform(post(SYNC_URL).session(session))
                .andExpect(status().isAccepted());
    }
}
