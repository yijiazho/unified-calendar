# TASK-020 · BE · Email Service (Resend)

## Context

Sends transactional emails to both the visitor and admin for all booking lifecycle events: confirmation, cancellation, and rescheduling. Uses Resend as the email provider. All sends are async so they never block the booking response.

Depends on: TASK-002 (scaffold — `resend.api-key` property), TASK-018 (booking entity), TASK-021 (ICS attachment, called from within this service).

---

## Instructions

### Resend integration

Resend exposes a simple REST API. Use Spring's `RestClient` — no Java SDK required:

```java
@Service
public class ResendClient {

    private final RestClient restClient;
    private final String apiKey;

    public ResendClient(@Value("${resend.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
            .baseUrl("https://api.resend.com")
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    public void send(SendEmailRequest request) {
        restClient.post()
            .uri("/emails")
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }
}

public record SendEmailRequest(
    String from,
    List<String> to,
    String subject,
    String html,
    List<Attachment> attachments   // nullable
) {}

public record Attachment(
    String filename,
    String content   // Base64-encoded
) {}
```

Configure a `from` address (e.g. `"Unified Calendar <noreply@yourdomain.com>"`) as a property:
```properties
email.from=Unified Calendar <noreply@yourdomain.com>
```

### `EmailService`

Six email scenarios — each sends two emails (to visitor and to admin):

```java
@Service
@Async
public class EmailService {

    void sendBookingEmails(Booking booking, Admin admin);
    // - Visitor: confirmation with ICS attachment
    // - Admin: new booking notification

    void sendCancellationEmails(Booking booking, Admin admin);
    // - Visitor: cancellation confirmation
    // - Admin: cancellation notification

    void sendRescheduleEmails(Booking booking, Admin admin, Instant newStart, Instant newEnd);
    // - Visitor: reschedule confirmation with updated ICS
    // - Admin: reschedule notification
}
```

Mark the class `@Async` so every public method runs in a background thread pool. Add `@EnableAsync` to a configuration class.

### Email templates

Use plain Java string templates (no Thymeleaf needed for MVP):

**Visitor booking confirmation** subject: `"Appointment Confirmed — {date} at {time}"`

HTML body (minimal but readable):

```html
<p>Hi {visitorName},</p>
<p>Your appointment has been confirmed.</p>
<ul>
  <li><strong>Date:</strong> {date}</li>
  <li><strong>Time:</strong> {time} ({timezone})</li>
</ul>
<p>Need to cancel or reschedule?</p>
<p>
  <a href="{baseUrl}/cancel/{cancelToken}">Cancel appointment</a> |
  <a href="{baseUrl}/reschedule/{rescheduleToken}">Reschedule</a>
</p>
<p>A calendar invite is attached.</p>
```

**Admin new booking notification** subject: `"New Booking — {visitorName}, {date}"`

```html
<p>A new appointment has been booked.</p>
<ul>
  <li><strong>Visitor:</strong> {visitorName} ({visitorEmail})</li>
  <li><strong>Phone:</strong> {visitorPhone}</li>
  <li><strong>Date/Time:</strong> {date} at {time}</li>
  <li><strong>Notes:</strong> {notes}</li>
</ul>
```

**Cancellation / reschedule emails** follow the same structure — keep them brief and factual.

### Time formatting for emails

Display times in the **admin's configured timezone** (stored in the `admins` table). Use `java.time.ZoneId` and `DateTimeFormatter`:

```java
ZonedDateTime localTime = booking.slotStart().atZone(ZoneId.of(admin.timezone()));
String formatted = localTime.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a z"));
```

### Base URL configuration

```properties
app.base-url=http://localhost:5173
```

Override in production with the actual domain. Used to build cancel/reschedule links.

---

## Acceptance Criteria

- A successful booking results in two emails: one to `visitorEmail`, one to the admin's email.
- The visitor email contains a cancel link (`/cancel/{cancelToken}`) and a reschedule link.
- The visitor email has an ICS file attached (see TASK-021).
- The admin email contains the visitor's name, email, phone, and notes.
- Email sending does not block the `POST /bookings` response (verified by response latency).
- Cancellation triggers two cancellation emails (visitor + admin).
- Rescheduling triggers two reschedule emails with the new time.
- If Resend returns an error, it is logged but does not crash the application.
