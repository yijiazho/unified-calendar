# TASK-002 · BE · Spring Boot Project Scaffold

## Context

Establishes the backend skeleton that all other backend tasks build on. Correct dependency selection and package structure here prevents painful refactors later. This must be completed before any other backend task.

---

## Instructions

### Generate the project

Use [start.spring.io](https://start.spring.io) or the Spring Initializr CLI with:

- **Build**: Maven
- **Language**: Java 17
- **Spring Boot**: 3.x latest
- **Group**: `com.unifiedcalendar`
- **Artifact**: `backend`
- **Packaging**: Jar

**Dependencies to add in `pom.xml`:**

```xml
<!-- Core web -->
spring-boot-starter-web
spring-boot-starter-security

<!-- Data -->
spring-boot-starter-jdbc
org.xerial:sqlite-jdbc:3.45.x
org.flywaydb:flyway-core

<!-- Scheduling -->
(included in spring-boot-starter — no extra dep needed)

<!-- Token encryption -->
javax.crypto (part of JDK — no dep needed)

<!-- Email (Resend — HTTP-based, no official Java SDK required) -->
(use Spring's RestClient or WebClient)

<!-- ICS generation -->
(manual RFC 5545 formatting — no external dep needed)

<!-- Testing -->
spring-boot-starter-test
```

> Do not add Spring Data JPA — the design uses Spring JDBC (plain SQL) intentionally to stay lightweight with SQLite.

### `application.properties`

```properties
# SQLite datasource
spring.datasource.url=jdbc:sqlite:${DB_PATH:./data/unified-calendar.db}
spring.datasource.driver-class-name=org.sqlite.JDBC

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# Server
server.port=8080

# Session
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict

# Actuator (health check for Docker)
management.endpoints.web.exposure.include=health
```

External secrets (populated from environment):
```properties
google.client-id=${GOOGLE_CLIENT_ID}
google.client-secret=${GOOGLE_CLIENT_SECRET}
microsoft.client-id=${MICROSOFT_CLIENT_ID}
microsoft.client-secret=${MICROSOFT_CLIENT_SECRET}
microsoft.tenant-id=${MICROSOFT_TENANT_ID}
encryption.secret-key=${ENCRYPTION_SECRET_KEY}
resend.api-key=${RESEND_API_KEY}
```

### Package layout

```
com.unifiedcalendar/
├── auth/
│   ├── Admin.java              (record / entity)
│   ├── AdminRepository.java
│   ├── AuthController.java
│   └── AuthService.java
├── calendar/
│   ├── CalendarAccount.java
│   ├── CalendarAccountRepository.java
│   ├── CalendarEvent.java
│   ├── CalendarEventRepository.java
│   ├── CalendarController.java
│   └── sync/
│       ├── CalendarSyncService.java
│       ├── GoogleSyncAdapter.java
│       └── OutlookSyncAdapter.java
├── availability/
│   └── AvailabilityService.java
├── booking/
│   ├── Booking.java
│   ├── BookingRepository.java
│   └── BookingController.java
├── email/
│   ├── EmailService.java
│   └── IcsService.java
├── workinghours/
│   ├── WorkingHours.java
│   ├── WorkingHoursRepository.java
│   └── WorkingHoursController.java
└── config/
    ├── SecurityConfig.java
    ├── CorsConfig.java
    └── EncryptionConfig.java
```

### Security config

- Permit all requests to `/auth/**`, `/s/**`, `/availability/**`, `/bookings/*/cancel`, `/bookings/*/reschedule` (public endpoints).
- All other endpoints require an authenticated session.
- CSRF disabled for the JSON API (stateless REST except for session cookie).
- CORS: allow `http://localhost:5173` in development (externalise in production).

### Dockerfile

See TASK-001 for the multi-stage Dockerfile expected at `backend/Dockerfile`.

---

## Acceptance Criteria

- `./mvnw spring-boot:run` starts without errors.
- `GET http://localhost:8080/actuator/health` returns `{"status":"UP"}`.
- The SQLite file is created at the configured path on first run.
- Flyway reports `Successfully applied 0 migrations` (schema task is TASK-004).
- All packages exist (even if empty) so IDE imports resolve across tasks.
- No Spring Data JPA on the classpath.
