# TASK-008 · BE · Microsoft Outlook OAuth Integration

## Context

Mirrors TASK-007 but for Microsoft Graph. The OAuth2 flow uses the Microsoft identity platform (Entra ID). Same encryption and storage approach as Google — only the token endpoint URLs and HTTP client differ.

Depends on: TASK-002, TASK-004, TASK-005, TASK-007 (reuses `EncryptionService`, `CalendarAccountRepository`, `CalendarAccount` entity).

---

## Instructions

### Azure App Registration setup (prerequisite, not code)

- Register an app in Entra ID (Azure portal → App registrations).
- Add a Web platform redirect URI: `http://localhost:8080/calendar/outlook/callback`.
- Add API permissions (Microsoft Graph, Delegated): `Calendars.Read`, `Calendars.ReadWrite`, `offline_access`, `User.Read`.
- Grant admin consent if required by the tenant.
- Note the `client_id`, `client_secret`, and `tenant_id` (use `common` for multi-tenant or the specific tenant ID).

### `OutlookOAuthService`

No additional Maven dependency is needed — use Spring's `RestClient` (Spring Boot 3.2+) or `RestTemplate` for all Microsoft HTTP calls.

```java
// Step 1 — build the authorization URL
String buildAuthorizationUrl(Long adminId);
// Base URL: https://login.microsoftonline.com/{tenant_id}/oauth2/v2.0/authorize
// Params: client_id, response_type=code, redirect_uri, scope, state
// scope: https://graph.microsoft.com/Calendars.Read https://graph.microsoft.com/Calendars.ReadWrite offline_access User.Read
// state: same HMAC/JWT approach as TASK-007

// Step 2 — handle the callback
CalendarAccount handleCallback(String code, String state);
// 1. Validate state → extract adminId
// 2. POST to https://login.microsoftonline.com/{tenant_id}/oauth2/v2.0/token
//    Body (form-encoded): grant_type=authorization_code, code, client_id, client_secret, redirect_uri
// 3. GET https://graph.microsoft.com/v1.0/me to retrieve email and oid (object ID)
// 4. Encrypt tokens, upsert calendar_accounts (provider="OUTLOOK", provider_account_id=oid)
// 5. Return saved CalendarAccount
```

### Controller endpoints

Add to the existing `CalendarController`:

```
GET /calendar/outlook/connect
  → Require session
  → Build Microsoft authorization URL
  → Redirect 302

GET /calendar/outlook/callback?code=&state=
  → Call OutlookOAuthService.handleCallback
  → On success: redirect to frontend /settings/calendars
  → On error: redirect to frontend /settings/calendars?error=outlook_oauth_failed
```

### Token refresh

Implement `OutlookTokenRefresher`:

```java
String refreshAccessToken(CalendarAccount account);
// POST to https://login.microsoftonline.com/{tenant_id}/oauth2/v2.0/token
// Body: grant_type=refresh_token, refresh_token (decrypted), client_id, client_secret, scope
// Update encrypted access_token in calendar_accounts
// Return new decrypted access token
```

### `GET /calendar/accounts` — list connected accounts

Add this endpoint to `CalendarController` (serves both TASK-007 and TASK-008):

```
GET /calendar/accounts
Response 200:
[
  {
    "id": 1,
    "provider": "GOOGLE",
    "email": "user@gmail.com",
    "isPrimary": true,
    "connectedAt": "2024-01-01T00:00:00Z"
  }
]
```

### `DELETE /calendar/accounts/{id}` and `PUT /calendar/primary`

```
DELETE /calendar/accounts/{id}
  → Require session, verify account belongs to admin
  → Delete row from calendar_accounts (cascades to calendar_events)
  → Response 204

PUT /calendar/primary
Body: { "accountId": 2 }
  → Require session, verify account belongs to admin and is connected
  → Set is_primary=0 for all admin's accounts, then is_primary=1 for target
  → Response 200: updated account list
```

---

## Acceptance Criteria

- `GET /calendar/outlook/connect` redirects to `login.microsoftonline.com` with correct scopes.
- Completing Microsoft login and consent creates a `calendar_accounts` row with `provider='OUTLOOK'`.
- Connecting the same Outlook account twice updates the existing row.
- `GET /calendar/accounts` returns both Google and Outlook accounts together when both are connected.
- `DELETE /calendar/accounts/{id}` removes the account and its cached events.
- `PUT /calendar/primary` sets exactly one account as primary; the previous primary is cleared.
- Attempting to set a non-existent or foreign account as primary returns 404.
