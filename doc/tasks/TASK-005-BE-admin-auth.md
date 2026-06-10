# TASK-005 · BE · Admin Authentication

## Context

Provides session-based authentication for the admin. The session cookie is the only auth mechanism for admin-facing endpoints. Visitor-facing endpoints are public and do not use this.

Depends on: TASK-002 (scaffold), TASK-004 (schema — `admins` table).

---

## Instructions

### Entities and repository

**`Admin.java`** — a plain Java record (no JPA annotations):

```java
public record Admin(
    Long id,
    String email,
    String passwordHash,
    String slug,
    String timezone,
    Instant createdAt,
    Instant updatedAt
) {}
```

**`AdminRepository.java`** — use `JdbcTemplate` or `NamedParameterJdbcTemplate`:

```java
Optional<Admin> findByEmail(String email);
Optional<Admin> findBySlug(String slug);
Admin save(Admin admin);   // INSERT, return with generated id
```

### Service

**`AuthService.java`**

- `signup(email, password, slug, timezone)`:
  1. Check `email` is not already taken → throw `EmailAlreadyUsedException` (→ 409) if so.
  2. Check `slug` is URL-safe (regex `^[a-z0-9-]+$`) → throw `InvalidSlugException` (→ 400) if not.
  3. BCrypt-hash the password (`BCryptPasswordEncoder` with strength 12).
  4. Insert into `admins`, return saved `Admin`.

- `login(email, password)`:
  1. Load admin by email → throw `AuthenticationException` (→ 401) if not found.
  2. Verify BCrypt hash → throw `AuthenticationException` if mismatch.
  3. Return admin (caller creates the session).

### Controller

**`AuthController.java`** — `@RestController`, mapped to `/auth`

```
POST /auth/signup
Body: { email, password, slug, timezone }
Response 201: { id, email, slug, timezone }

POST /auth/login
Body: { email, password }
Response 200: { id, email, slug, timezone }
Side effect: sets HttpSession attribute "adminId"

POST /auth/logout
Response 204
Side effect: invalidates HttpSession

GET /auth/me
Response 200: { id, email, slug, timezone }  (for frontend to check session on load)
Response 401 if no session
```

Session management: after successful login, store the admin ID in the `HttpSession`:

```java
session.setAttribute("adminId", admin.id());
```

Create a `SessionUtils` helper that reads `adminId` from the session and throws `UnauthorizedException` (→ 401) if absent. All protected controllers call this at the top of each handler.

### Spring Security config

In `SecurityConfig.java`, permit the following without authentication:

```java
.requestMatchers("/auth/**", "/s/**", "/availability/**",
                 "/bookings/*/cancel", "/bookings/*/reschedule")
.permitAll()
```

Disable CSRF (JSON API with session cookie — CSRF handled via `SameSite=Strict`):

```java
.csrf(csrf -> csrf.disable())
```

Do not use Spring Security's built-in `UserDetailsService` for login — the `AuthController` manages session manually to keep the logic explicit and testable.

### Error responses

All errors return:

```json
{ "error": "message" }
```

with the appropriate HTTP status code.

---

## Acceptance Criteria

- `POST /auth/signup` with valid data creates an admin and returns 201.
- `POST /auth/signup` with a duplicate email returns 409.
- `POST /auth/login` with correct credentials returns 200 and a `Set-Cookie` header with an `HttpOnly` session cookie.
- `POST /auth/login` with wrong password returns 401.
- `GET /auth/me` with the session cookie returns admin details; without cookie returns 401.
- `POST /auth/logout` invalidates the session; subsequent `GET /auth/me` returns 401.
- Passwords are stored as BCrypt hashes (not plaintext) in the database.
