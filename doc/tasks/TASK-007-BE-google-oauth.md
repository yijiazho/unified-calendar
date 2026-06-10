# TASK-007 · BE · Google Calendar OAuth Integration

## Context

Implements the OAuth2 Authorization Code Flow for Google Calendar. After the admin completes the OAuth consent screen, the backend exchanges the authorization code for access and refresh tokens, encrypts them, and stores a `calendar_account` record.

Depends on: TASK-002 (scaffold), TASK-004 (schema — `calendar_accounts` table), TASK-005 (session — to identify the logged-in admin).

---

## Instructions

### Google Cloud setup (prerequisite, not code)

- Enable the Google Calendar API in the project.
- Add an OAuth 2.0 Client ID (Web application type).
- Authorized redirect URI: `http://localhost:8080/calendar/google/callback` (add production URL later).
- Required scopes: `https://www.googleapis.com/auth/calendar.readonly` and `https://www.googleapis.com/auth/calendar.events`.

### Dependencies

Add to `pom.xml`:

```xml
<dependency>
  <groupId>com.google.api-client</groupId>
  <artifactId>google-api-client</artifactId>
  <version>2.x</version>
</dependency>
<dependency>
  <groupId>com.google.apis</groupId>
  <artifactId>google-api-services-calendar</artifactId>
  <version>v3-rev20240111-2.0.0</version>
</dependency>
```

### Token encryption

Implement `EncryptionService` in `com.unifiedcalendar.config`:

- Use AES-256-GCM (authenticated encryption — no padding oracle attack).
- Key derived from `encryption.secret-key` property (must be at least 32 chars; derive with SHA-256 if shorter).
- `String encrypt(String plaintext)` → Base64-encoded ciphertext + nonce.
- `String decrypt(String ciphertext)` → original plaintext.

All token storage and retrieval passes through this service.

### `GoogleOAuthService`

```java
// Step 1 — build the authorization URL
String buildAuthorizationUrl(Long adminId);
// Generates a state parameter = signed JWT or HMAC of adminId to prevent CSRF
// Returns full Google OAuth URL with scopes, redirect_uri, access_type=offline, prompt=consent

// Step 2 — handle the callback
CalendarAccount handleCallback(String code, String state);
// 1. Validate state parameter → extract adminId
// 2. Exchange code for tokens via Google Token Endpoint (POST to oauth2.googleapis.com/token)
// 3. Call Google userinfo or tokeninfo to get the account email and sub
// 4. Encrypt access_token and refresh_token
// 5. Upsert into calendar_accounts (INSERT OR REPLACE based on admin_id + provider + provider_account_id)
// 6. Return saved CalendarAccount
```

### Controller endpoints

**`CalendarController.java`** (shared with Outlook, TASK-008):

```
GET /calendar/google/connect
  → Require session (adminId)
  → Build Google authorization URL
  → Redirect 302 to Google

GET /calendar/google/callback?code=&state=
  → Call GoogleOAuthService.handleCallback
  → On success: redirect to frontend /settings/calendars
  → On error: redirect to frontend /settings/calendars?error=google_oauth_failed
```

### `CalendarAccountRepository`

```java
List<CalendarAccount> findAllByAdminId(Long adminId);
Optional<CalendarAccount> findById(Long id, Long adminId);
CalendarAccount save(CalendarAccount account);           // INSERT OR REPLACE
void delete(Long id, Long adminId);
void setPrimary(Long id, Long adminId);                 // UPDATE: set all is_primary=0, then set target=1
```

### `CalendarAccount` entity

```java
public record CalendarAccount(
    Long id,
    Long adminId,
    String provider,              // "GOOGLE" or "OUTLOOK"
    String providerAccountId,
    String email,
    String encryptedAccessToken,
    String encryptedRefreshToken,
    boolean isPrimary,
    Instant connectedAt,
    Instant lastSyncAt
) {}
```

### Token refresh

Implement `GoogleTokenRefresher`:

```java
String refreshAccessToken(CalendarAccount account);
// POST to oauth2.googleapis.com/token with grant_type=refresh_token
// Encrypt and update the access_token in calendar_accounts
// Return new decrypted access token for immediate use
```

This is called by the sync engine (TASK-012) when the access token is expired.

---

## Acceptance Criteria

- `GET /calendar/google/connect` (with valid session) redirects to accounts.google.com with correct scopes and redirect URI.
- Completing the Google consent screen and being redirected back results in a new `calendar_accounts` row in the database.
- Access and refresh tokens in the database are encrypted (not plaintext).
- Connecting the same Google account a second time updates the existing row instead of creating a duplicate.
- `EncryptionService.decrypt(EncryptionService.encrypt(plaintext))` round-trips correctly.
- `GoogleTokenRefresher.refreshAccessToken` returns a valid token when given an account with a valid refresh token.
