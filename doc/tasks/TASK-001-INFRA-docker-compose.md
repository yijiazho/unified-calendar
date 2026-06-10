# TASK-001 · INFRA · Docker Compose Setup

## Context

The application runs as two services — a Spring Boot backend and a React frontend — with SQLite stored as a file on the host. Docker Compose is the deployment unit for the MVP. This task sets up the Compose file and any supporting build configs so the entire stack can be launched with a single command.

Depends on: TASK-002 (backend scaffold), TASK-003 (frontend scaffold) — the Dockerfiles for each service are defined there; this task wires them together.

---

## Instructions

### File layout

```
unified-calendar/
├── docker-compose.yml
├── backend/
│   └── Dockerfile
├── frontend/
│   └── Dockerfile
└── data/              ← gitignored directory mounted as SQLite volume
```

### `docker-compose.yml`

Define two services:

**backend**
- Build from `./backend/Dockerfile`
- Expose port `8080`
- Mount `./data:/app/data` so the SQLite file persists across container restarts
- Pass all secrets as environment variables (loaded from a `.env` file at the repo root — gitignored)
- Environment variables needed:
  ```
  GOOGLE_CLIENT_ID
  GOOGLE_CLIENT_SECRET
  MICROSOFT_CLIENT_ID
  MICROSOFT_CLIENT_SECRET
  MICROSOFT_TENANT_ID
  ENCRYPTION_SECRET_KEY
  RESEND_API_KEY
  DB_PATH=/app/data/unified-calendar.db
  ```

**frontend**
- Build from `./frontend/Dockerfile`
- Expose port `80`
- In production mode, nginx serves the built React app and reverse-proxies `/api` → `http://backend:8080`
- Depends on `backend`

### `backend/Dockerfile`

Multi-stage build:
1. Stage `build`: use `eclipse-temurin:21-jdk`, copy source, run `./mvnw package -DskipTests`
2. Stage `run`: use `eclipse-temurin:21-jre`, copy the fat JAR from `build`, set `ENTRYPOINT`

### `frontend/Dockerfile`

Multi-stage build:
1. Stage `build`: use `node:20-alpine`, `npm ci`, `npm run build`
2. Stage `run`: use `nginx:alpine`, copy `dist/` to `/usr/share/nginx/html`, copy a custom `nginx.conf` that reverse-proxies `/api/` to `http://backend:8080/`

### `nginx.conf` (inside `frontend/`)

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://backend:8080/;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

This enables SPA routing (all unknown paths fall back to `index.html`) and proxies API calls so the frontend never needs to know the backend host.

### `.env.example`

Commit a `.env.example` file at the repo root (`.env` itself is gitignored):

```
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
MICROSOFT_CLIENT_ID=
MICROSOFT_CLIENT_SECRET=
MICROSOFT_TENANT_ID=
ENCRYPTION_SECRET_KEY=
RESEND_API_KEY=
```

---

## Acceptance Criteria

- `docker compose up --build` from repo root starts both services with no errors.
- `http://localhost` serves the React app.
- `http://localhost/api/actuator/health` returns `{"status":"UP"}`.
- The SQLite file is created at `./data/unified-calendar.db` on the host on first start.
- Stopping and restarting with `docker compose restart` preserves data in the SQLite file.
- No secrets appear in the committed `docker-compose.yml`; all are read from environment variables.
