package com.unifiedcalendar.email;

import com.unifiedcalendar.auth.Admin;
import com.unifiedcalendar.booking.Booking;
import com.unifiedcalendar.calendar.CalendarEvent;
import com.unifiedcalendar.calendar.CalendarEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Async("emailTaskExecutor")
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter ZONE_FORMATTER =
            DateTimeFormatter.ofPattern("z", Locale.ENGLISH);

    private final ResendClient resendClient;
    private final IcsService icsService;
    private final CalendarEventRepository calendarEventRepository;
    private final String fromAddress;
    private final String baseUrl;

    public EmailService(ResendClient resendClient,
                        IcsService icsService,
                        CalendarEventRepository calendarEventRepository,
                        @Value("${email.from}") String fromAddress,
                        @Value("${app.base-url}") String baseUrl) {
        this.resendClient = resendClient;
        this.icsService = icsService;
        this.calendarEventRepository = calendarEventRepository;
        this.fromAddress = fromAddress;
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    public void sendBookingEmails(Booking booking, Admin admin) {
        Optional<CalendarEvent> event = findEvent(booking);
        if (event.isEmpty()) {
            return;
        }
        Instant start = event.get().startTimeUtc();
        Instant end = event.get().endTimeUtc();
        TimeDetails time = format(start, admin);

        String visitorSubject = "Appointment Confirmed — " + time.date() + " at " + time.time();
        String visitorHtml = """
                <p>Hi %s,</p>
                <p>Your appointment has been confirmed.</p>
                <ul>
                  <li><strong>Date:</strong> %s</li>
                  <li><strong>Time:</strong> %s (%s)</li>
                </ul>
                <p>Need to cancel or reschedule?</p>
                <p>
                  <a href="%s/cancel/%s">Cancel appointment</a> |
                  <a href="%s/reschedule/%s?slug=%s">Reschedule</a>
                </p>
                <p>A calendar invite is attached.</p>
                """.formatted(
                html(booking.visitorName()), html(time.date()), html(time.time()), html(time.timezone()),
                html(baseUrl), urlToken(booking.cancelToken()), html(baseUrl), urlToken(booking.rescheduleToken()),
                urlToken(admin.slug()));

        byte[] ics = icsService.generate(
                booking.cancelToken(),
                "Meeting with " + admin.displayName(),
                icsDescription(booking),
                start,
                end,
                admin.email());
        Attachment attachment = new Attachment("invite.ics", Base64.getEncoder().encodeToString(ics));

        String adminSubject = "New Booking — " + subjectText(booking.visitorName()) + ", " + time.date();
        String adminHtml = adminDetails("A new appointment has been booked.", booking, time);

        sendSafely("visitor booking confirmation", booking.id(), new SendEmailRequest(
                fromAddress, List.of(booking.visitorEmail()), visitorSubject, visitorHtml, List.of(attachment)));
        sendSafely("admin new-booking notification", booking.id(), new SendEmailRequest(
                fromAddress, List.of(admin.email()), adminSubject, adminHtml, null));
    }

    public void sendCancellationEmails(Booking booking, Admin admin, Instant cancelledStart) {
        TimeDetails time = format(cancelledStart, admin);
        String visitorHtml = """
                <p>Hi %s,</p>
                <p>Your appointment on <strong>%s</strong> at <strong>%s (%s)</strong> has been cancelled.</p>
                """.formatted(html(booking.visitorName()), html(time.date()), html(time.time()), html(time.timezone()));
        String adminHtml = adminDetails("An appointment has been cancelled.", booking, time);

        sendSafely("visitor cancellation confirmation", booking.id(), new SendEmailRequest(
                fromAddress, List.of(booking.visitorEmail()),
                "Appointment Cancelled — " + time.date() + " at " + time.time(), visitorHtml, null));
        sendSafely("admin cancellation notification", booking.id(), new SendEmailRequest(
                fromAddress, List.of(admin.email()),
                "Booking Cancelled — " + subjectText(booking.visitorName()) + ", " + time.date(), adminHtml, null));
    }

    public void sendRescheduleEmails(Booking booking, Admin admin, Instant newStart, Instant newEnd) {
        TimeDetails time = format(newStart, admin);
        String visitorHtml = """
                <p>Hi %s,</p>
                <p>Your appointment has been rescheduled.</p>
                <ul>
                  <li><strong>New date:</strong> %s</li>
                  <li><strong>New time:</strong> %s (%s)</li>
                </ul>
                <p>
                  <a href="%s/cancel/%s">Cancel appointment</a> |
                  <a href="%s/reschedule/%s?slug=%s">Reschedule again</a>
                </p>
                <p>An updated calendar invite is attached.</p>
                """.formatted(
                html(booking.visitorName()), html(time.date()), html(time.time()), html(time.timezone()),
                html(baseUrl), urlToken(booking.cancelToken()), html(baseUrl), urlToken(booking.rescheduleToken()),
                urlToken(admin.slug()));
        Attachment attachment = new Attachment("invite.ics", Base64.getEncoder().encodeToString(
                icsService.generate(
                        booking.cancelToken(),
                        "Meeting with " + admin.displayName(),
                        icsDescription(booking),
                        newStart,
                        newEnd,
                        admin.email())));
        String adminHtml = adminDetails("An appointment has been rescheduled.", booking, time);

        sendSafely("visitor reschedule confirmation", booking.id(), new SendEmailRequest(
                fromAddress, List.of(booking.visitorEmail()),
                "Appointment Rescheduled — " + time.date() + " at " + time.time(), visitorHtml, List.of(attachment)));
        sendSafely("admin reschedule notification", booking.id(), new SendEmailRequest(
                fromAddress, List.of(admin.email()),
                "Booking Rescheduled — " + subjectText(booking.visitorName()) + ", " + time.date(), adminHtml, null));
    }

    private Optional<CalendarEvent> findEvent(Booking booking) {
        if (booking.calendarEventId() == null) {
            log.error("Cannot send emails for booking {} because it has no calendar event", booking.id());
            return Optional.empty();
        }
        Optional<CalendarEvent> event = calendarEventRepository.findById(booking.calendarEventId());
        if (event.isEmpty()) {
            log.error("Cannot send emails for booking {} because calendar event {} was not found",
                    booking.id(), booking.calendarEventId());
        }
        return event;
    }

    private TimeDetails format(Instant instant, Admin admin) {
        ZoneId zone;
        try {
            zone = ZoneId.of(admin.timezone());
        } catch (DateTimeException | NullPointerException invalidTimezone) {
            log.warn("Invalid timezone '{}' for admin {}; using UTC for email formatting",
                    admin.timezone(), admin.id());
            zone = ZoneId.of("UTC");
        }
        ZonedDateTime local = instant.atZone(zone);
        return new TimeDetails(
                local.format(DATE_FORMATTER),
                local.format(TIME_FORMATTER),
                local.format(ZONE_FORMATTER));
    }

    private String adminDetails(String intro, Booking booking, TimeDetails time) {
        return """
                <p>%s</p>
                <ul>
                  <li><strong>Visitor:</strong> %s (%s)</li>
                  <li><strong>Phone:</strong> %s</li>
                  <li><strong>Date/Time:</strong> %s at %s (%s)</li>
                  <li><strong>Notes:</strong> %s</li>
                </ul>
                """.formatted(
                html(intro), html(booking.visitorName()), html(booking.visitorEmail()),
                html(orNotProvided(booking.visitorPhone())), html(time.date()), html(time.time()),
                html(time.timezone()), html(orNotProvided(booking.notes())));
    }

    private String icsDescription(Booking booking) {
        return "Visitor: " + booking.visitorName()
                + "\nEmail: " + booking.visitorEmail()
                + "\nPhone: " + orNotProvided(booking.visitorPhone())
                + "\nNotes: " + orNotProvided(booking.notes());
    }

    private void sendSafely(String emailType, Long bookingId, SendEmailRequest request) {
        try {
            resendClient.send(request);
        } catch (Exception exception) {
            log.error("Resend failed for {} on booking {}: {}", emailType, bookingId, exception.getMessage(), exception);
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String orNotProvided(String value) {
        return value == null || value.isBlank() ? "Not provided" : value;
    }

    private static String urlToken(String value) {
        return html(value == null ? "" : value.replaceAll("[^A-Za-z0-9._~-]", ""));
    }

    private static String subjectText(String value) {
        return value == null ? "" : value.replace("\r", "").replace("\n", "");
    }

    private static String html(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record TimeDetails(String date, String time, String timezone) {}
}
