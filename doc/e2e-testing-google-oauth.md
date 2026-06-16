# Google Calendar OAuth — End-to-End Testing Guide (Docker)

## Prerequisites

| Requirement | Check |
|---|---|
| Docker and Docker Compose installed | `docker compose version` |
| `.env` file at repo root populated | See required keys below |
| Port 80 **and** 8080 free on the host | `lsof -i :80 && lsof -i :8080` |
| Google Cloud project with OAuth consent screen configured | Cloud Console → APIs & Services → OAuth consent screen |

### Required `.env` keys for this feature

```dotenv
GOOGLE_CLIENT_ID=<your-client-id>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<your-client-secret>
ENCRYPTION_SECRET_KEY=<at-least-32-random-characters>

# Tell the backend where to send the browser after the callback.
# In Docker, nginx serves the frontend on port 80.
APP_BASE_URL=http://localhost
```

### Google Cloud Console — Authorized redirect URI

Add **exactly** this URI under your OAuth 2.0 Client ID → Authorized redirect URIs:

```
http://localhost:8080/calendar/google/callback
```

The backend is exposed directly on port 8080 (in addition to the nginx proxy on port 80), so the browser can reach the callback endpoint on that port even though the frontend is served via nginx on port 80.

---

## Starting the Stack

```bash
# From repo root
docker compose up --build
```

Wait for:

```
unified-calendar-frontend-1  | /docker-entrypoint.sh: Configuration complete; ready for start up
```

Open **http://localhost** in a browser. Sign up and log in using the steps from `e2e-testing-auth.md` before running the OAuth tests below.

---

## Test Cases

### 1 — Unauthenticated connect is blocked

1. Open a new private/incognito window (no session).
2. Navigate directly to `http://localhost/api/calendar/google/connect`.

**Expected**: the request returns HTTP 401. No redirect to Google occurs.

> **Why**: `SessionAuthFilter` promotes the admin session to a Spring Security `Authentication`. Without a session, Spring Security rejects the request before the controller runs.

---

### 2 — Authenticated connect redirects to Google's consent screen

1. Log in at `http://localhost/login`.
2. Navigate to `http://localhost/api/calendar/google/connect` (or trigger it from the future Settings page).

**Expected**: the browser redirects to `https://accounts.google.com/o/oauth2/auth?...` and the URL contains:
- `scope=openid+email+https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcalendar.readonly+https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcalendar.events`
- `access_type=offline`
- `prompt=consent`
- `redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fcalendar%2Fgoogle%2Fcallback`
- A `state` parameter (opaque base64url string, not your admin ID in plaintext)

---

### 3 — Completing consent creates a `calendar_accounts` row

1. On Google's consent screen, choose the Google account you want to connect and click **Allow**.
2. The browser is redirected back, handled by the backend, then forwarded to `http://localhost/settings/calendars`.

**Expected after step 2**: run the following to confirm the row was written:

```bash
sqlite3 ./data/unified-calendar.db \
  "SELECT id, admin_id, provider, email, is_primary, connected_at FROM calendar_accounts;"
```

The output should contain one row with `provider = GOOGLE` and the email of the Google account you authorized.

---

### 4 — Tokens are encrypted at rest (not plaintext)

Immediately after test 3, inspect the raw token columns:

```bash
sqlite3 ./data/unified-calendar.db \
  "SELECT encrypted_access_token, encrypted_refresh_token FROM calendar_accounts LIMIT 1;"
```

**Expected**: both values are base64-encoded blobs that do **not** start with `eyJ` (which would indicate a raw, unencrypted JWT). A correctly encrypted value looks like random characters, e.g.:

```
A1B2C3D4E5F6G7H8I9...==
```

---

### 5 — Reconnecting the same account updates tokens without duplicating

1. Navigate to `http://localhost/api/calendar/google/connect` again while logged in.
2. Complete the Google consent flow a second time with the **same** Google account.

**Expected**: run the row count query:

```bash
sqlite3 ./data/unified-calendar.db \
  "SELECT COUNT(*) FROM calendar_accounts WHERE provider = 'GOOGLE';"
```

The count must still be **1**. The existing row's `encrypted_access_token` is updated and its `id`, `is_primary`, and `connected_at` are preserved.

---

### 6 — Tampered state in callback is rejected

1. After initiating a connect flow (step 2), copy the `state` parameter from the Google consent URL.
2. Manually construct a callback URL with the last few characters of the state replaced:

```
http://localhost:8080/calendar/google/callback?code=fake-code&state=<original-state-with-last-4-chars-changed>
```

3. Paste it into the browser address bar.

**Expected**: the browser lands on `http://localhost/settings/calendars?error=google_oauth_failed`. A `WARN`-level log line is written by the backend:

```bash
docker compose logs backend | grep "OAuth callback rejected"
```

---

### 7 — Invalid authorization code is rejected

1. Visit the callback URL with a valid-looking state but a bogus code:

```
http://localhost:8080/calendar/google/callback?code=totally-invalid&state=<valid-state>
```

To obtain a valid state without completing the flow, temporarily add a log statement — or just let the flow begin and copy the state from the Google consent URL before clicking Allow.

**Expected**: browser redirects to `http://localhost/settings/calendars?error=google_oauth_failed`. The backend logs an `ERROR` for the failed token exchange with Google.

---

## Database Inspection Reference

The database file is mounted to `./data/` on your host, so use the local `sqlite3` CLI directly — no need for it inside the container.

```bash
# All calendar accounts
sqlite3 ./data/unified-calendar.db \
  "SELECT id, admin_id, provider, email, is_primary, connected_at FROM calendar_accounts;"

# Confirm tokens are not plaintext
sqlite3 ./data/unified-calendar.db \
  "SELECT id, length(encrypted_access_token), length(encrypted_refresh_token) FROM calendar_accounts;"

# Delete all accounts (reset for re-testing)
sqlite3 ./data/unified-calendar.db \
  "DELETE FROM calendar_accounts;"
```

---

## Caveats

### Post-callback redirect target

After a successful callback the backend redirects to `${APP_BASE_URL}/settings/calendars`. The Settings page is not yet implemented (planned for a future task), so the browser will land on the React app's catch-all route or show a 404. This is expected; the important signal is that the redirect happens (confirming the callback succeeded) and that the `calendar_accounts` row exists.

### Refresh token only issued once

Google only returns a `refresh_token` on the **first** authorization for a given client + account pair. If you revoke and reconnect you will receive one again. If you reconnect without revoking, Google omits the refresh token and the backend will reject the callback with an error (by design — storing an empty refresh token would cause silent failures later). To force a new refresh token without the error, revoke access at https://myaccount.google.com/permissions first.

### Callback hits port 8080 directly

The redirect URI is `http://localhost:8080/calendar/google/callback` (backend port), not the nginx port 80. This means the Google redirect bypasses nginx. If port 8080 is not accessible (e.g., a firewall blocks it), the callback will fail. In a production deployment both ports should be replaced with a single HTTPS URL through a reverse proxy.

### `APP_BASE_URL` must be set

Without `APP_BASE_URL=http://localhost` in `.env`, the backend defaults to `http://localhost:5173` (the Vite dev server) for the post-callback redirect. The Vite server is not running in Docker, so the browser would land on a connection-refused page after a successful OAuth flow.

---

## Teardown

```bash
docker compose down          # stop containers, keep the SQLite volume
docker compose down -v       # stop and delete the ./data volume (fresh DB next run)
```
