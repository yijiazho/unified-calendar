package com.unifiedcalendar.email;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.booking.Booking;
import com.unifiedcalendar.calendar.CalendarEvent;
import com.unifiedcalendar.calendar.CalendarEventRepository;
import com.unifiedcalendar.calendar.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private ResendClient resendClient;
    @Mock private IcsService icsService;
    @Mock private CalendarEventRepository calendarEventRepository;

    private EmailService service;
    private Booking booking;
    private Admin admin;
    private CalendarEvent event;

    @BeforeEach
    void setUp() {
        service = new EmailService(
                resendClient, icsService, calendarEventRepository,
                "Unified Calendar <noreply@example.com>", "https://calendar.example/");
        booking = new Booking(
                7L, 1L, 22L, "Jane <Doe>", "jane@example.com", "+1-555-0100", "Window seat & tea",
                "CONFIRMED", "cancel-token", "reschedule-token", Instant.parse("2024-03-01T00:00:00Z"));
        admin = new Admin(
                1L, "owner@example.com", "hash", "calendar-owner", "America/Los_Angeles",
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"));
        event = new CalendarEvent(
                22L, 1L, 3L, Provider.GOOGLE, "provider-id", "Appointment",
                Instant.parse("2024-03-15T14:00:00Z"), Instant.parse("2024-03-15T14:30:00Z"),
                true, Instant.parse("2024-03-01T00:00:00Z"), Instant.parse("2024-03-01T00:00:00Z"));
    }

    @Test
    void sendsVisitorAndAdminBookingEmailsWithLinksDetailsAndIcs() {
        when(calendarEventRepository.findById(22L)).thenReturn(Optional.of(event));
        when(icsService.generate(any(), any(), any(), any(), any(), any())).thenReturn("ICS".getBytes(StandardCharsets.UTF_8));

        service.sendBookingEmails(booking, admin);

        ArgumentCaptor<SendEmailRequest> requests = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(resendClient, org.mockito.Mockito.times(2)).send(requests.capture());
        List<SendEmailRequest> sent = requests.getAllValues();

        SendEmailRequest visitor = sent.get(0);
        assertThat(visitor.to()).containsExactly("jane@example.com");
        assertThat(visitor.subject()).contains("Appointment Confirmed", "Friday, March 15, 2024", "7:00 AM");
        assertThat(visitor.html()).contains(
                "Hi Jane &lt;Doe&gt;",
                "https://calendar.example/cancel/cancel-token",
                "https://calendar.example/reschedule/reschedule-token",
                "(PDT)");
        assertThat(visitor.attachments()).hasSize(1);
        assertThat(visitor.attachments().get(0).filename()).isEqualTo("invite.ics");
        assertThat(Base64.getDecoder().decode(visitor.attachments().get(0).content()))
                .isEqualTo("ICS".getBytes(StandardCharsets.UTF_8));
        verify(icsService).generate(
                org.mockito.ArgumentMatchers.eq("cancel-token"),
                org.mockito.ArgumentMatchers.eq("Meeting with calendar-owner"),
                any(),
                org.mockito.ArgumentMatchers.eq(event.startTimeUtc()),
                org.mockito.ArgumentMatchers.eq(event.endTimeUtc()),
                org.mockito.ArgumentMatchers.eq("owner@example.com"));

        SendEmailRequest owner = sent.get(1);
        assertThat(owner.to()).containsExactly("owner@example.com");
        assertThat(owner.html()).contains(
                "Jane &lt;Doe&gt;", "jane@example.com", "+1-555-0100", "Window seat &amp; tea");
        assertThat(owner.attachments()).isNull();
    }

    @Test
    void formatsEnglishDatesWhenJvmDefaultLocaleIsNotEnglish() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            when(calendarEventRepository.findById(22L)).thenReturn(Optional.of(event));
            when(icsService.generate(any(), any(), any(), any(), any(), any())).thenReturn(new byte[]{1});

            service.sendBookingEmails(booking, admin);

            ArgumentCaptor<SendEmailRequest> requests = ArgumentCaptor.forClass(SendEmailRequest.class);
            verify(resendClient, org.mockito.Mockito.times(2)).send(requests.capture());
            assertThat(requests.getAllValues()).extracting(SendEmailRequest::subject)
                    .allMatch(subject -> subject.contains("Friday, March 15, 2024"));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void stripsCrLfFromVisitorNameInEverySubject() {
        Booking unsafeNameBooking = new Booking(
                booking.id(), booking.adminId(), booking.calendarEventId(),
                "Jane\r\nBcc: victim@example.com", booking.visitorEmail(), booking.visitorPhone(), booking.notes(),
                booking.status(), booking.cancelToken(), booking.rescheduleToken(), booking.createdAt());
        when(calendarEventRepository.findById(22L)).thenReturn(Optional.of(event));
        when(icsService.generate(any(), any(), any(), any(), any(), any())).thenReturn(new byte[]{1});

        service.sendBookingEmails(unsafeNameBooking, admin);
        service.sendCancellationEmails(unsafeNameBooking, admin, event.startTimeUtc());
        service.sendRescheduleEmails(unsafeNameBooking, admin, event.startTimeUtc(), event.endTimeUtc());

        ArgumentCaptor<SendEmailRequest> requests = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(resendClient, org.mockito.Mockito.times(6)).send(requests.capture());
        assertThat(requests.getAllValues()).extracting(SendEmailRequest::subject)
                .noneMatch(subject -> subject.contains("\r") || subject.contains("\n"));
        assertThat(requests.getAllValues()).extracting(SendEmailRequest::subject)
                .filteredOn(subject -> subject.startsWith("New Booking")
                        || subject.startsWith("Booking Cancelled")
                        || subject.startsWith("Booking Rescheduled"))
                .allMatch(subject -> subject.contains("JaneBcc: victim@example.com"));
    }

    @Test
    void resendFailureDoesNotPreventSecondRecipientAttempt() {
        when(calendarEventRepository.findById(22L)).thenReturn(Optional.of(event));
        when(icsService.generate(any(), any(), any(), any(), any(), any())).thenReturn(new byte[]{1});
        doThrow(new RuntimeException("Resend unavailable"))
                .doNothing()
                .when(resendClient).send(any());

        service.sendBookingEmails(booking, admin);

        verify(resendClient, org.mockito.Mockito.times(2)).send(any());
    }

    @Test
    void sendsTwoCancellationEmailsAfterCalendarEventWasRemoved() {
        service.sendCancellationEmails(booking, admin, event.startTimeUtc());

        ArgumentCaptor<SendEmailRequest> requests = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(resendClient, org.mockito.Mockito.times(2)).send(requests.capture());
        assertThat(requests.getAllValues()).extracting(SendEmailRequest::subject)
                .allMatch(subject -> subject.contains("Cancelled"));
        verifyNoInteractions(calendarEventRepository);
    }

    @Test
    void rescheduleUsesNewTimeAndStableIcsUid() {
        Instant newStart = Instant.parse("2024-03-16T18:00:00Z");
        Instant newEnd = Instant.parse("2024-03-16T18:30:00Z");
        when(icsService.generate(any(), any(), any(), any(), any(), any())).thenReturn(new byte[]{1});

        service.sendRescheduleEmails(booking, admin, newStart, newEnd);

        verify(icsService).generate(
                org.mockito.ArgumentMatchers.eq("cancel-token"),
                org.mockito.ArgumentMatchers.eq("Meeting with calendar-owner"), any(),
                org.mockito.ArgumentMatchers.eq(newStart), org.mockito.ArgumentMatchers.eq(newEnd),
                org.mockito.ArgumentMatchers.eq("owner@example.com"));
        ArgumentCaptor<SendEmailRequest> requests = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(resendClient, org.mockito.Mockito.times(2)).send(requests.capture());
        assertThat(requests.getAllValues().get(0).html()).contains("Saturday, March 16, 2024", "11:00 AM", "PDT");
    }
}
