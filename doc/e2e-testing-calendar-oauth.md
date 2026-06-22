# Calendar OAuth — End-to-End Testing Guide (Docker)

Covers both Google Calendar (TASK-007) and Microsoft Outlook (TASK-008) OAuth flows. Both providers share the same backend infrastructure (HMAC state, AES-256-GCM token encryption, SQLite upsert) — only the provider URLs and credentials differ.

---

## Prerequisites

| Requirement | Check |
|---|---|
| Docker and Docker Compose installed | `docker compose version` |
| `.env` file at repo root populated | See keys below |
| Port 80 **and** 8080 free on the host | `lsof -i :80 && lsof -i :8080` |
| Google Cloud project configured | Cloud Console → APIs & Services → OAuth consent screen |
| Azure app registration configured | portal.azure.com → App registrations |

### Required `.env` keys

```dotenv
# Google
GOOGLE_CLIENT_ID=<your-client-id>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<your-client-secret>

# Microsoft
MICROSOFT_CLIENT_ID=<application-client-id>
MICROSOFT_CLIENT_SECRET=<secret-value-not-the-guid-id>
MICROSOFT_TENANT_ID=<tenant-id-or-common>

# Shared
ENCRYPTION_SECRET_KEY=<at-least-32-random-characters>
APP_BASE_URL=http://localhost
```

### Provider console setup

**Google** — Cloud Console → Credentials → OAuth 2.0 Client ID → Authorized redirect URIs:
```
http://localhost:8080/calendar/google/callback
```

**Azure** — App registrations → your app → Authentication → Web redirect URIs:
```
http://localhost:8080/calendar/outlook/callback
```

API permissions required (Microsoft Graph, Delegated): `Calendars.Read`, `Calendars.ReadWrite`, `offline_access`, `User.Read`. Grant admin consent if your tenant requires it.

> **Azure client secret**: in the portal, go to Certificates & secrets → Client secrets. The **Value** column (the long alphanumeric string) is what goes in `.env` — not the GUID in the Secret ID column. The value is only shown once at creation.

---

## Starting the Stack

```bash
docker compose up --build
```

Wait for:
```
unified-calendar-frontend-1  | /docker-entrypoint.sh: Configuration complete; ready for start up
```

Open **http://localhost** in a browser. Sign up and log in before running any OAuth tests.

---

## Google OAuth Test Cases

### G1 — Unauthenticated connect is blocked

1. Open a new private/incognito window (no session).
2. Navigate to `http://localhost/api/calendar/google/connect`.

**Expected**: HTTP 401. No redirect to Google occurs.

---

### G2 — Authenticated connect redirects to Google's consent screen

1. Log in at `http://localhost/login`.
2. Navigate to `http://localhost/api/calendar/google/connect`.

**Expected**: the browser redirects to `https://accounts.google.com/o/oauth2/auth?...` and the URL contains:
- `scope=openid+email+https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcalendar.readonly+https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcalendar.events`
- `access_type=offline`
- `prompt=consent`
- `redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fcalendar%2Fgoogle%2Fcallback`
- A `state` parameter (opaque base64url string, not the admin ID in plaintext)

---

### G3 — Completing consent creates a `calendar_accounts` row

1. Complete the Google consent flow.
2. The browser forwards to `http://localhost/settings/calendars`.

**Expected**:
```bash
sqlite3 ./data/unified-calendar.db \
  "SELECT id, admin_id, provider, email, is_primary FROM calendar_accounts WHERE provider='GOOGLE';"
```
One row with `provider = GOOGLE`.

---

### G4 — Tokens are encrypted at rest

```bash
sqlite3 ./data/unified-calendar.db \
  "SELECT encrypted_access_token, encrypted_refresh_token FROM calendar_accounts WHERE provider='GOOGLE' LIMIT 1;"
```

**Expected**: both values are base64-encoded blobs that do **not** start with `eyJ` (a raw JWT). A correctly encrypted value looks like random base64 characters.

---

### G5 — Reconnecting the same account updates tokens without duplicating

1. Go through the Google connect flow a second time with the **same** account.

**Expected**:
```bash
sqlite3 ./data/unified-calendar.db \
  "SELECT COUNT(*) FROM calendar_accounts WHERE provider='GOOGLE';"
```
Count is still **1**. The row's `encrypted_access_token` is updated; `id`, `is_primary`, and `connected_at` are preserved.

---

### G6 — Tampered state in callback is rejected

1. Start a connect flow and copy the `state` parameter from the Google consent URL.
2. Modify the last few characters of the state and paste this into the browser:

```
http://localhost:8080/calendar/google/callback?code=fake-code&state=<modified-state>
```

**Expected**: browser lands on `http://localhost/settings/calendars?error=google_oauth_failed`.

```bash
docker compose logs backend | grep "OAuth callback rejected"
```
A `WARN`-level log line confirms the HMAC check failed.

---

### G7 — Invalid authorization code is rejected

```
http://localhost:8080/calendar/google/callback?code=totally-invalid&state=<valid-state>
```

**Expected**: browser lands on `http://localhost/settings/calendars?error=google_oauth_failed`. The backend logs an `ERROR` for the failed token exchange.

---

## Outlook OAuth Test Cases

### O1 — Unauthenticated connect is blocked

1. Open a new private/incognito window.
2. Navigate to `http://localhost/api/calendar/outlook/connect`.

**Expected**: HTTP 401. No redirect to Microsoft occurs.

---

### O2 — Authenticated connect redirects to Microsoft's consent screen

1. Log in at `http://localhost/login`.
2. Navigate to `http://localhost/api/calendar/outlook/connect`.

**Expected**: the browser redirects to `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize?...` and the URL contains:
- `scope=https%3A%2F%2Fgraph.microsoft.com%2FCalendars.Read+https%3A%2F%2Fgraph.microsoft.com%2FCalendars.ReadWrite+offline_access+User.Read`
- `response_type=code`
- `redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fcalendar%2Foutlook%2Fcallback`
- A `state` parameter (opaque, not the admin ID in plaintext)

---

### O3 — Completing consent creates a `calendar_accounts` row

1. On Microsoft's consent screen, choose the account and click **Accept**.
2. The browser forwards to `http://localhost/settings/calendars`.

If you land on `http://localhost/settings/calendars?error=outlook_oauth_failed` instead, check the backend logs immediately:

```bash
docker compose logs backend --tail 30 | grep -A 5 "Outlook OAuth callback failed"
```

**Expected on success**:
```bash
sqlite3 ./data/unified-calendar.db \
  "SELECT id, admin_id, provider, provider_account_id, email, is_primary FROM calendar_accounts WHERE provider='OUTLOOK';"
```
One row with `provider = OUTLOOK`. The `provider_account_id` column contains the Microsoft Object ID (OID), not the email address.

---

### O4 — Tokens are encrypted at rest

```bash
sqlite3 ./data/unified-calendar.db \
  "SELECT encrypted_access_token, encrypted_refresh_token FROM calendar_accounts WHERE provider='OUTLOOK' LIMIT 1;"
```

**Expected**: both values are base64-encoded blobs (random-looking characters, not JWTs).

---

### O5 — Reconnecting the same account updates tokens without duplicating

1. Go through the Outlook connect flow a second time with the **same** Microsoft account.

**Expected**:
```bash
sqlite3 ./data/unified-calendar.db \
  "SELECT COUNT(*) FROM calendar_accounts WHERE provider='OUTLOOK';"
```
Count is still **1**. Tokens are updated; `id`, `is_primary`, and `connected_at` are preserved (SQLite upsert on `(admin_id, provider, provider_account_id)`).

---

### O6 — Tampered state in callback is rejected

1. Start a connect flow and copy the `state` parameter from the Microsoft consent URL (URL-decode it first — it contains `:` characters encoded as `%3A`).
2. Modify the last few characters and paste this into the browser:

```
http://localhost:8080/calendar/outlook/callback?code=fake-code&state=<modified-state>
```

**Expected**: browser lands on `http://localhost/settings/calendars?error=outlook_oauth_failed`.

```bash
docker compose logs backend | grep "Outlook OAuth callback rejected"
```

---

### O7 — Invalid authorization code is rejected

```
http://localhost:8080/calendar/outlook/callback?code=totally-invalid&state=<valid-state>
```

**Expected**: browser lands on `http://localhost/settings/calendars?error=outlook_oauth_failed`. Backend logs an `ERROR` for the failed token exchange with Microsoft.

---

## Account Management Endpoints

Once one or both providers are connected, verify the management API:

```bash
# List all connected accounts (requires session cookie from login)
curl -b cookies.txt http://localhost:8080/calendar/accounts

# Delete an account (replace 1 with the actual id)
curl -b cookies.txt -X DELETE http://localhost:8080/calendar/accounts/1
# Expected: HTTP 204

# Set primary account
curl -b cookies.txt -X PUT http://localhost:8080/calendar/primary \
  -H "Content-Type: application/json" \
  -d '{"accountId": 1}'
# Expected: HTTP 200, updated account list with is_primary=true on the target
```

Attempting any of the above **without** a valid session returns HTTP 401.

---

## Database Inspection Reference

The database is mounted to `./data/` on the host — use `sqlite3` directly without entering the container.

```bash
# All connected accounts
sqlite3 ./data/unified-calendar.db \
  "SELECT id, admin_id, provider, email, is_primary, connected_at FROM calendar_accounts;"

# Confirm tokens are not plaintext (token length should be ~100+ chars of base64)
sqlite3 ./data/unified-calendar.db \
  "SELECT id, provider, length(encrypted_access_token), length(encrypted_refresh_token) FROM calendar_accounts;"

# Reset for re-testing
sqlite3 ./data/unified-calendar.db "DELETE FROM calendar_accounts;"
```

---

## Caveats

### Callback hits port 8080 directly

Both callback URIs (`/calendar/google/callback` and `/calendar/outlook/callback`) are registered with the provider as `http://localhost:8080/...` (backend port), not through the nginx proxy on port 80. After the provider redirects, the browser contacts the backend directly on port 8080. If a firewall blocks port 8080, callbacks will fail silently — the provider redirect will time out rather than hitting the error handler.

### Post-callback redirect target

After a successful callback the backend redirects to `${APP_BASE_URL}/settings/calendars`. The Settings page is not yet implemented (Phase 2), so the browser lands on the React app's catch-all route. This is expected; the important signal is that the redirect happens (not the `?error=` variant) and the `calendar_accounts` row exists.

### Google: refresh token only issued once

Google returns a `refresh_token` only on the first authorization for a given client + account pair. Reconnecting without revoking access causes the backend to reject the callback (the stored empty refresh token would cause silent sync failures later). To force a new refresh token, revoke access at https://myaccount.google.com/permissions first.

### Microsoft: rolling refresh tokens

Microsoft may return a new `refresh_token` on every token refresh (configurable per tenant). The backend persists the new token when present to prevent `invalid_grant` errors on subsequent syncs. If sync stops working after the first automatic refresh, check the backend logs for `invalid_grant` — this indicates the tenant rotated the token but an older stored value was used.

### `APP_BASE_URL` must match the frontend origin

Without `APP_BASE_URL=http://localhost` in `.env`, the backend defaults to `http://localhost:5173` (the Vite dev server). The Vite server does not run in Docker, so the browser would land on a connection-refused page after a successful OAuth flow even though the `calendar_accounts` row was written correctly.

---

## Teardown

```bash
docker compose down          # stop containers, keep SQLite volume
docker compose down -v       # stop and delete ./data volume (fresh DB next run)
```
