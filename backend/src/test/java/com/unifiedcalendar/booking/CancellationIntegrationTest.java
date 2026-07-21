package com.unifiedcalendar.booking;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.calendar.CalendarAccount;
import com.unifiedcalendar.calendar.GoogleProviderEventService;
import com.unifiedcalendar.calendar.GoogleTokenRefresher;
import com.unifiedcalendar.calendar.OutlookProviderEventService;
import com.unifiedcalendar.calendar.Provider;
import com.unifiedcalendar.calendar.TokenRefreshResult;
import com.unifiedcalendar.email.EmailService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("POST /bookings/{cancelToken}/cancel integration")
class CancellationIntegrationTest {

    private static final Instant FUTURE_START = Instant.parse("2099-06-02T09:00:00Z");
    private static final Instant FUTURE_END = FUTURE_START.plus(30, ChronoUnit.MINUTES);

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private GoogleTokenRefresher googleTokenRefresher;
    @MockBean private GoogleProviderEventService googleProviderEventService;
    @MockBean private OutlookProviderEventService outlookProviderEventService;
    @MockBean private EmailService emailService;

    private Long adminId;
    private Long accountId;
    private CalendarAccount account;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO admins (email, password_hash, slug, timezone) VALUES (?, ?, ?, ?)",
                "cancel-" + suffix + "@example.com", "hash", "cancel-" + suffix, "UTC");
        adminId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug = ?", Long.class, "cancel-" + suffix);

        jdbc.update(
                "INSERT INTO calendar_accounts "
                        + "(admin_id, provider, provider_account_id, email, encrypted_access_token, "
                        + "encrypted_refresh_token, is_primary) VALUES (?, 'GOOGLE', ?, ?, ?, ?, 1)",
                adminId, "google-" + suffix, "admin@example.com", "enc-access", "enc-refresh");
        accountId = jdbc.queryForObject(
                "SELECT id FROM calendar_accounts WHERE admin_id = ?", Long.class, adminId);
        account = new CalendarAccount(
                accountId, adminId, Provider.GOOGLE, "google-" + suffix, "admin@example.com",
                "enc-access", "enc-refresh", true, Instant.now(), null, null);

        when(googleProviderEventService.supports(Provider.GOOGLE)).thenReturn(true);
        when(googleTokenRefresher.refreshAccessToken(any()))
                .thenReturn(new TokenRefreshResult("plain-token", account));
    }

    @Test
    @DisplayName("cancels without a session, removes the event, updates status, and sends emails")
    void cancelsBookingSuccessfully() throws Exception {
        TestBooking testBooking = insertBooking("CONFIRMED", FUTURE_START, FUTURE_END);

        mvc.perform(post("/bookings/{token}/cancel", testBooking.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment cancelled successfully."))
                .andExpect(jsonPath("$.slotStart").value(FUTURE_START.toString()))
                .andExpect(jsonPath("$.slotEnd").value(FUTURE_END.toString()));

        assertThat(bookingStatus(testBooking.bookingId())).isEqualTo("CANCELLED");
        assertThat(eventCount(testBooking.eventId())).isZero();
        verify(googleProviderEventService)
                .deleteEvent(eq(account), eq("plain-token"), eq(testBooking.providerEventId()));
        verify(emailService).sendCancellationEmails(
                any(Booking.class), any(Admin.class), eq(FUTURE_START));
    }

    @Test
    @DisplayName("returns 409 on a second cancellation attempt")
    void rejectsSecondCancellation() throws Exception {
        TestBooking testBooking = insertBooking("CONFIRMED", FUTURE_START, FUTURE_END);

        mvc.perform(post("/bookings/{token}/cancel", testBooking.token()))
                .andExpect(status().isOk());
        mvc.perform(post("/bookings/{token}/cancel", testBooking.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"))
                .andExpect(jsonPath("$.error")
                        .value("This appointment has already been cancelled."));
    }

    @Test
    @DisplayName("returns 404 for an unknown cancellation token")
    void rejectsUnknownToken() throws Exception {
        mvc.perform(post("/bookings/{token}/cancel", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Booking not found."));
    }

    @Test
    @DisplayName("returns 410 for an appointment in the past")
    void rejectsPastAppointment() throws Exception {
        Instant start = Instant.now().minus(60, ChronoUnit.MINUTES);
        TestBooking testBooking = insertBooking("CONFIRMED", start, start.plus(30, ChronoUnit.MINUTES));

        mvc.perform(post("/bookings/{token}/cancel", testBooking.token()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error")
                        .value("This appointment is in the past and cannot be cancelled."));

        assertThat(bookingStatus(testBooking.bookingId())).isEqualTo("CONFIRMED");
        assertThat(eventCount(testBooking.eventId())).isOne();
    }

    @Test
    @DisplayName("returns the MVP conflict message for a rescheduled booking")
    void rejectsRescheduledBooking() throws Exception {
        TestBooking testBooking = insertBooking("RESCHEDULED", FUTURE_START, FUTURE_END);

        mvc.perform(post("/bookings/{token}/cancel", testBooking.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESCHEDULED"))
                .andExpect(jsonPath("$.error").value(
                        "This appointment has been rescheduled. "
                                + "Use your reschedule confirmation email to manage it."));
    }

    @Test
    @DisplayName("provider deletion failure still completes the local cancellation")
    void providerFailureDoesNotBlockCancellation() throws Exception {
        TestBooking testBooking = insertBooking("CONFIRMED", FUTURE_START, FUTURE_END);
        doThrow(new RuntimeException("provider event already gone"))
                .when(googleProviderEventService)
                .deleteEvent(any(), any(), eq(testBooking.providerEventId()));

        mvc.perform(post("/bookings/{token}/cancel", testBooking.token()))
                .andExpect(status().isOk());

        assertThat(bookingStatus(testBooking.bookingId())).isEqualTo("CANCELLED");
        assertThat(eventCount(testBooking.eventId())).isZero();
        verify(emailService).sendCancellationEmails(
                any(Booking.class), any(Admin.class), eq(FUTURE_START));
    }

    private TestBooking insertBooking(String status, Instant start, Instant end) {
        String providerEventId = "provider-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO calendar_events "
                        + "(admin_id, calendar_account_id, provider, provider_event_id, title, "
                        + "start_time_utc, end_time_utc, is_booking_event, last_synced_at) "
                        + "VALUES (?, ?, 'GOOGLE', ?, 'Meeting', ?, ?, 1, ?)",
                adminId, accountId, providerEventId, start.toString(), end.toString(), Instant.now().toString());
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM calendar_events WHERE provider_event_id = ?", Long.class, providerEventId);

        String token = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO bookings "
                        + "(admin_id, calendar_event_id, visitor_name, visitor_email, status, "
                        + "cancel_token, reschedule_token) VALUES (?, ?, ?, ?, ?, ?, ?)",
                adminId, eventId, "Visitor", "visitor@example.com", status,
                token, UUID.randomUUID().toString());
        Long bookingId = jdbc.queryForObject(
                "SELECT id FROM bookings WHERE cancel_token = ?", Long.class, token);
        return new TestBooking(bookingId, eventId, providerEventId, token);
    }

    private String bookingStatus(Long bookingId) {
        return jdbc.queryForObject(
                "SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private long eventCount(Long eventId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM calendar_events WHERE id = ?", Long.class, eventId);
    }

    private record TestBooking(Long bookingId, Long eventId, String providerEventId, String token) {}
}
