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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("POST /bookings/{rescheduleToken}/reschedule integration")
class RescheduleIntegrationTest {

    private static final Instant ORIGINAL_START = Instant.parse("2099-06-01T09:00:00Z");
    private static final Instant ORIGINAL_END = ORIGINAL_START.plus(30, ChronoUnit.MINUTES);
    private static final Instant NEW_START = Instant.parse("2099-06-02T10:00:00Z");
    private static final Instant NEW_END = NEW_START.plus(30, ChronoUnit.MINUTES);

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
        String slug = "reschedule-" + suffix;
        jdbc.update("INSERT INTO admins (email, password_hash, slug, timezone) VALUES (?, ?, ?, 'UTC')",
                slug + "@example.com", "hash", slug);
        adminId = jdbc.queryForObject(
                "SELECT id FROM admins WHERE slug = ?", Long.class, slug);

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

        int newSlotDay = NEW_START.atZone(ZoneOffset.UTC).getDayOfWeek().ordinal();
        jdbc.update(
                "INSERT INTO working_hours (admin_id, day_of_week, start_time, end_time) "
                        + "VALUES (?, ?, '08:00', '17:00')",
                adminId, newSlotDay);

        when(googleProviderEventService.supports(Provider.GOOGLE)).thenReturn(true);
        when(googleTokenRefresher.refreshAccessToken(any()))
                .thenReturn(new TokenRefreshResult("plain-token", account));
    }

    @Test
    @DisplayName("reschedules without a session while preserving event ID, status, and tokens")
    void reschedulesSuccessfully() throws Exception {
        TestBooking testBooking = insertBooking("CONFIRMED", ORIGINAL_START, ORIGINAL_END);

        mvc.perform(post("/bookings/{token}/reschedule", testBooking.rescheduleToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(NEW_START, NEW_END)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(testBooking.bookingId()))
                .andExpect(jsonPath("$.visitorName").value("Visitor"))
                .andExpect(jsonPath("$.newSlotStart").value(NEW_START.toString()))
                .andExpect(jsonPath("$.newSlotEnd").value(NEW_END.toString()))
                .andExpect(jsonPath("$.cancelToken").value(testBooking.cancelToken()))
                .andExpect(jsonPath("$.rescheduleToken").value(testBooking.rescheduleToken()));

        assertThat(eventStart(testBooking.eventId())).isEqualTo(NEW_START.toString());
        assertThat(eventEnd(testBooking.eventId())).isEqualTo(NEW_END.toString());
        assertThat(bookingValue(testBooking.bookingId(), "status")).isEqualTo("CONFIRMED");
        assertThat(bookingValue(testBooking.bookingId(), "cancel_token"))
                .isEqualTo(testBooking.cancelToken());
        assertThat(bookingValue(testBooking.bookingId(), "reschedule_token"))
                .isEqualTo(testBooking.rescheduleToken());
        assertThat(providerEventId(testBooking.eventId())).isEqualTo(testBooking.providerEventId());
        assertThat(reservationCount()).isZero();

        verify(googleProviderEventService).updateEvent(
                account, "plain-token", testBooking.providerEventId(), NEW_START, NEW_END);
        verify(emailService).sendRescheduleEmails(
                any(Booking.class), any(Admin.class), eq(NEW_START), eq(NEW_END));
    }

    @Test
    @DisplayName("returns 404 for an unknown reschedule token")
    void rejectsUnknownToken() throws Exception {
        mvc.perform(post("/bookings/{token}/reschedule", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(NEW_START, NEW_END)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Booking not found."));
    }

    @Test
    @DisplayName("returns 409 for a cancelled booking")
    void rejectsCancelledBooking() throws Exception {
        TestBooking testBooking = insertBooking("CANCELLED", ORIGINAL_START, ORIGINAL_END);

        mvc.perform(post("/bookings/{token}/reschedule", testBooking.rescheduleToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(NEW_START, NEW_END)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("This appointment has already been cancelled."));
    }

    @Test
    @DisplayName("returns 409 for a terminal rescheduled booking")
    void rejectsRescheduledBooking() throws Exception {
        TestBooking testBooking = insertBooking("RESCHEDULED", ORIGINAL_START, ORIGINAL_END);

        mvc.perform(post("/bookings/{token}/reschedule", testBooking.rescheduleToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(NEW_START, NEW_END)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "This appointment has been rescheduled. "
                                + "Use your reschedule confirmation email to manage it."));

        verify(googleProviderEventService, never())
                .updateEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns 410 when the current appointment is in the past")
    void rejectsPastAppointment() throws Exception {
        Instant oldStart = Instant.now().minus(60, ChronoUnit.MINUTES);
        TestBooking testBooking = insertBooking(
                "CONFIRMED", oldStart, oldStart.plus(30, ChronoUnit.MINUTES));

        mvc.perform(post("/bookings/{token}/reschedule", testBooking.rescheduleToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(NEW_START, NEW_END)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error")
                        .value("Cannot reschedule a past appointment."));
    }

    @Test
    @DisplayName("returns 400 when the new slot is not exactly 30 minutes")
    void rejectsInvalidDuration() throws Exception {
        TestBooking testBooking = insertBooking("CONFIRMED", ORIGINAL_START, ORIGINAL_END);

        mvc.perform(post("/bookings/{token}/reschedule", testBooking.rescheduleToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(NEW_START, NEW_START.plus(45, ChronoUnit.MINUTES))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("New slot must be exactly 30 minutes."));
    }

    @Test
    @DisplayName("returns 400 when the new slot is not in the future")
    void rejectsPastNewSlot() throws Exception {
        TestBooking testBooking = insertBooking("CONFIRMED", ORIGINAL_START, ORIGINAL_END);
        Instant pastStart = Instant.now().minus(60, ChronoUnit.MINUTES);

        mvc.perform(post("/bookings/{token}/reschedule", testBooking.rescheduleToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(pastStart, pastStart.plus(30, ChronoUnit.MINUTES))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("New slot must be in the future."));
    }

    @Test
    @DisplayName("returns 409 when the cached calendar contains a conflict")
    void rejectsCachedConflict() throws Exception {
        TestBooking testBooking = insertBooking("CONFIRMED", ORIGINAL_START, ORIGINAL_END);
        insertCalendarEvent("busy-" + UUID.randomUUID(), NEW_START, NEW_END, false);

        mvc.perform(post("/bookings/{token}/reschedule", testBooking.rescheduleToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(NEW_START, NEW_END)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("The selected time slot is no longer available."));

        verify(googleProviderEventService, never())
                .updateEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns 409 when another request has reserved the destination slot")
    void rejectsReservedDestination() throws Exception {
        TestBooking testBooking = insertBooking("CONFIRMED", ORIGINAL_START, ORIGINAL_END);
        jdbc.update(
                "INSERT INTO slot_reservations (admin_id, slot_start, slot_end) VALUES (?, ?, ?)",
                adminId, NEW_START.toString(), NEW_END.toString());

        mvc.perform(post("/bookings/{token}/reschedule", testBooking.rescheduleToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(NEW_START, NEW_END)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("The selected time slot is no longer available."));

        verify(googleProviderEventService, never())
                .updateEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns 409 when the live provider check finds a conflict")
    void rejectsLiveConflict() throws Exception {
        TestBooking testBooking = insertBooking("CONFIRMED", ORIGINAL_START, ORIGINAL_END);
        when(googleProviderEventService.hasConflict(account, "plain-token", NEW_START, NEW_END))
                .thenReturn(true);

        mvc.perform(post("/bookings/{token}/reschedule", testBooking.rescheduleToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(NEW_START, NEW_END)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("The selected time slot is no longer available."));

        verify(googleProviderEventService, never())
                .updateEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns the safe 500 response and leaves local time unchanged on provider failure")
    void providerFailureDoesNotUpdateDatabase() throws Exception {
        TestBooking testBooking = insertBooking("CONFIRMED", ORIGINAL_START, ORIGINAL_END);
        doThrow(new RuntimeException("provider unavailable"))
                .when(googleProviderEventService)
                .updateEvent(account, "plain-token", testBooking.providerEventId(), NEW_START, NEW_END);

        mvc.perform(post("/bookings/{token}/reschedule", testBooking.rescheduleToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(NEW_START, NEW_END)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error")
                        .value("Failed to update calendar event. Please try again."));

        assertThat(eventStart(testBooking.eventId())).isEqualTo(ORIGINAL_START.toString());
        assertThat(eventEnd(testBooking.eventId())).isEqualTo(ORIGINAL_END.toString());
        assertThat(reservationCount()).isZero();
        verify(emailService, never())
                .sendRescheduleEmails(any(), any(), any(), any());
    }

    private TestBooking insertBooking(String status, Instant start, Instant end) {
        String providerEventId = "provider-" + UUID.randomUUID();
        Long eventId = insertCalendarEvent(providerEventId, start, end, true);
        String cancelToken = UUID.randomUUID().toString();
        String rescheduleToken = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO bookings "
                        + "(admin_id, calendar_event_id, visitor_name, visitor_email, status, "
                        + "cancel_token, reschedule_token) VALUES (?, ?, 'Visitor', ?, ?, ?, ?)",
                adminId, eventId, "visitor@example.com", status, cancelToken, rescheduleToken);
        Long bookingId = jdbc.queryForObject(
                "SELECT id FROM bookings WHERE reschedule_token = ?", Long.class, rescheduleToken);
        return new TestBooking(
                bookingId, eventId, providerEventId, cancelToken, rescheduleToken);
    }

    private Long insertCalendarEvent(
            String providerEventId, Instant start, Instant end, boolean bookingEvent) {
        jdbc.update(
                "INSERT INTO calendar_events "
                        + "(admin_id, calendar_account_id, provider, provider_event_id, title, "
                        + "start_time_utc, end_time_utc, is_booking_event, last_synced_at) "
                        + "VALUES (?, ?, 'GOOGLE', ?, 'Meeting', ?, ?, ?, ?)",
                adminId, accountId, providerEventId, start.toString(), end.toString(),
                bookingEvent ? 1 : 0, Instant.now().toString());
        return jdbc.queryForObject(
                "SELECT id FROM calendar_events WHERE provider_event_id = ?",
                Long.class, providerEventId);
    }

    private String eventStart(Long eventId) {
        return jdbc.queryForObject(
                "SELECT start_time_utc FROM calendar_events WHERE id = ?", String.class, eventId);
    }

    private String eventEnd(Long eventId) {
        return jdbc.queryForObject(
                "SELECT end_time_utc FROM calendar_events WHERE id = ?", String.class, eventId);
    }

    private String providerEventId(Long eventId) {
        return jdbc.queryForObject(
                "SELECT provider_event_id FROM calendar_events WHERE id = ?", String.class, eventId);
    }

    private String bookingValue(Long bookingId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private long reservationCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM slot_reservations WHERE admin_id = ?", Long.class, adminId);
    }

    private String request(Instant start, Instant end) {
        return "{\"newSlotStart\":\"" + start + "\",\"newSlotEnd\":\"" + end + "\"}";
    }

    private record TestBooking(
            Long bookingId,
            Long eventId,
            String providerEventId,
            String cancelToken,
            String rescheduleToken) {}
}
