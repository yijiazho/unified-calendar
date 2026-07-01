# Repository Guidelines

## Project Structure & Module Organization

This repository contains a self-hosted unified calendar scheduling service. `backend/` is a Spring Boot 3.5 Java 17 app with source under `backend/src/main/java/com/unifiedcalendar`, tests under `backend/src/test/java`, and Flyway migrations in `backend/src/main/resources/db/migration`. `frontend/` is a React + TypeScript Vite app with pages, components, hooks, context, API clients, and assets under `frontend/src`. `doc/` holds product, design, testing, and task notes. `data/` is for local SQLite state and should not be treated as source.

## Build, Test, and Development Commands

- `docker compose up --build`: build and run the full stack; frontend is on `http://localhost`, backend on `http://localhost:8080`.
- `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`: run the backend locally with a local SQLite database.
- `cd backend && ./mvnw test`: run backend unit and integration tests.
- `cd frontend && npm install`: install dependencies.
- `cd frontend && npm run dev`: run the Vite dev server on `http://localhost:5173`.
- `cd frontend && npm run build`: type-check and build the frontend.
- `cd frontend && npm run lint`: run ESLint for TypeScript and React code.

## Coding Style & Naming Conventions

Backend code follows standard Java package organization by feature, such as `auth`, `calendar`, `booking`, and `workinghours`. Use descriptive class suffixes already present in the codebase: `Controller`, `Service`, `Repository`, `Dto`, and `Test`. Frontend files use PascalCase for React components and pages, camelCase for hooks and API helpers, and TypeScript `.tsx` for components. Keep formatting consistent with existing files: 2-space indentation in frontend code and conventional Java formatting in backend code.

## Testing Guidelines

Backend tests use Spring Boot Test and JUnit through Maven. Place tests beside the relevant package under `backend/src/test/java` and name them `*Test` or `*IntegrationTest` to match existing coverage. Frontend currently has lint and build checks but no test runner configured; validate UI changes with `npm run lint` and `npm run build`.

## Commit & Pull Request Guidelines

Recent history uses short, imperative commit subjects, for example `Add global nav bar and propagate working hours to calendar view` and `Apply design-review improvements across backend and frontend`. Keep commits focused and mention the affected area when useful. Pull requests should include a concise description, linked task or issue when available, test results, and screenshots for visible frontend changes.

## Security & Configuration Tips

Copy `.env.example` to `.env` for local credentials. Do not commit OAuth secrets, `RESEND_API_KEY`, encryption keys, generated databases, or Docker volume contents.
