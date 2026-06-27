# Design Review — Tasks 001–014

Design-level improvements identified after reviewing all implemented tasks. No requirements are altered. Grouped by impact within backend and frontend layers.

---

## Backend

### High Impact

**1. `CalendarController` violates single responsibility**
`CalendarController` handles four unrelated concerns: Google OAuth flow, Outlook OAuth flow, account CRUD, and event queries + sync triggers. As Phase 2 adds booking-related event endpoints, this class will become unmaintainable. Split into `GoogleOAuthController`, `OutlookOAuthController`, `CalendarAccountController`, and `CalendarEventController`.

**2. No `Provider` enum — provider is a raw `String` throughout**
`CalendarAccount.provider`, all SQL queries, sync dispatch, and response DTOs all compare against the string literals `"GOOGLE"` and `"OUTLOOK"`. A single typo anywhere is a silent runtime bug. Define a `Provider` enum with `GOOGLE`, `OUTLOOK` and use it in the domain record, SQL parameters, and the switch in `CalendarSyncService`.

**3. Token refreshers mix retrieval and persistence as a hidden side effect**
`GoogleTokenRefresher.refresh(account)` and `OutlookTokenRefresher.refresh(account)` both call `repository.save(...)` internally. From the caller's perspective (`CalendarSyncService.syncAccount`) this is an invisible side effect — the method name implies "get a new token" but it also mutates the database. Extract the save to the sync service so the refreshers return updated `CalendarAccount` values and the service decides when to persist.

**4. `CalendarSyncService` dispatches on provider via implicit coupling**
The sync service selects `GoogleSyncAdapter` or `OutlookSyncAdapter` based on the provider string (visible in `syncAccount`). Define a `SyncAdapter` interface with a `supports(String provider)` method. The service then holds a `List<SyncAdapter>` and finds the right one, making adding a third provider (e.g., Apple Calendar) a zero-change to the service.

**5. All validation is manual — no Bean Validation**
`WorkingHoursService.validate`, `AuthService.signup`, and working hours DTO checks all do manual null checks and regex matching via ad-hoc code. Spring Boot ships Jakarta Validation. Annotating DTOs with `@NotBlank`, `@Pattern`, `@Min`/`@Max` and calling `@Valid` on controller parameters moves validation to the right layer, removes duplicated code, and produces consistent 400 error bodies for free.

**6. `@Autowired(required=false)` for sync service is a code smell**
`CalendarController` uses `@Autowired(required=false)` on `CalendarSyncService` to survive the `@Profile("!test")` exclusion. The right fix is to define a no-op `@Bean @Profile("test") CalendarSyncService noOpSync()` that implements the same interface. Then the controller can inject it normally and the test profile path is explicit.

### Medium Impact

**7. `markSyncFailed` encodes failure status in the timestamp field**
Setting `last_sync_at = NULL` to mean "last sync failed" conflates two different things: "never synced" and "last attempt failed." The `CalendarConnectPage` shows "Connected X ago" from this field — a failed sync makes the account appear as-if it's never been connected. Add a `last_sync_error TEXT` column (nullable) to `calendar_accounts` and keep `last_sync_at` as the last *successful* sync time.

**8. Date parsing lives in the controller layer**
`CalendarController`'s `parseStart` / `parseEnd` private methods parse date-or-datetime strings and return `Instant`. This is business logic (it defines the query window semantics) and belongs in `CalendarEventRepository` or a service, not the controller. Controllers should work with already-parsed values.

**9. Working hours time comparison uses string comparison**
`WorkingHoursService` compares `startTime < endTime` as `String`. This happens to be correct for `HH:MM` but is semantically wrong and will confuse readers. Parse both with `LocalTime.parse(time)` before comparing.

**10. `GlobalExceptionHandler` has no catch-all**
`GlobalExceptionHandler` maps known exceptions but lets unexpected `RuntimeException` fall through to Spring's default handler, which may expose stack traces in the response body. Add `@ExceptionHandler(Exception.class)` returning a generic 500 `{"error":"Internal server error"}`.

**11. `EncryptionConfig.java` is an empty stub**
`EncryptionConfig.java` is a `@Configuration` class with no beans. `EncryptionService` is not actually configured through it. Either move the key-derivation logic into this class (making it produce the `SecretKeySpec` as a bean that `EncryptionService` injects) or delete the file. An empty config class implies intent that wasn't followed through.

**12. `SessionAuthFilter` uses `UsernamePasswordAuthenticationToken` for session auth**
`SessionAuthFilter` creates a `UsernamePasswordAuthenticationToken` whose principal is a raw `Long` (adminId). This is semantically misleading — username/password tokens are for form-login flows. Define a lightweight `AdminAuthentication extends AbstractAuthenticationToken` that carries the `Long adminId` explicitly. `SessionUtils.requireAdminId` can then pull from the `SecurityContext` via `AdminAuthentication` rather than reading the raw session attribute twice.

**13. `OutlookSyncAdapter.logTokenClaims` parses JWT with regex**
`OutlookSyncAdapter` splits the JWT on `.`, base64-decodes the payload, then extracts `aud`, `scp`, `tid`, `roles` via regex on the raw JSON string. This is fragile — key ordering, whitespace, or nested objects can break it. Use `Base64.getDecoder().decode(payload)` + a `Map` read via `ObjectMapper` (already on the classpath) instead.

### Lower Impact

**14. `deleteByCalendarAccountIdAndProviderEventIdNotIn` fetches IDs client-side**
`JdbcCalendarEventRepository` loads all current event IDs into memory, diffs in Java, and deletes in batches of 500. A cleaner approach (while still respecting SQLite's variable limit) is to use a temporary table: `INSERT OR IGNORE INTO temp_seen_ids VALUES (?)`, then `DELETE FROM calendar_events WHERE calendar_account_id = ? AND provider_event_id NOT IN (SELECT id FROM temp_seen_ids)`. This is one round-trip for any size set.

**15. Pool size is 1 but WAL mode requires no further changes — document this**
`DataSourceConfig` sets pool size to 1. This is the correct choice for SQLite, but it's silent. A short inline comment ("SQLite is single-writer; pool=1 avoids lock contention") prevents future confusion when someone sees HikariCP with a pool of 1 and assumes it's misconfigured.

---

## Frontend

### High Impact

**16. `availability.ts` duplicates `workingHours.ts`**
`availability.ts` exports `getWorkingHours` and `saveWorkingHours` that are identical to the functions in `workingHours.ts`. Delete the duplicates from `availability.ts` and import from `workingHours.ts` where needed.

**17. `App.css` contains unused Vite scaffold styles**
`App.css` contains `.counter`, `.hero`, `#center`, `#next-steps`, `#spacer`, `.ticks`, and perspective-transform styles from the Vite starter template. None are referenced by any app component. Delete the file or replace its contents.

### Medium Impact

**18. `useAuth.ts` hook is a pointless re-export**
`hooks/useAuth.ts` is a single-line re-export of `useAuth` from `AuthContext`. This creates an extra indirection layer with no benefit. Components can import from the context directly, or the re-export convention needs to be applied consistently to all hooks. As-is it's inconsistent: `useBrowserTimezone` lives only in `hooks/` with no context counterpart.

**19. Color assignment uses database ID modulo**
`DashboardPage` uses `PALETTE[accountId % PALETTE.length]` to assign colors. After an account is deleted and a new one added, the new account may get the same color as a remaining one (IDs are non-contiguous after deletions). Assign colors based on the account's index in the sorted `accounts` array returned from the API instead.

**20. Inline popover positioning in `DashboardPage` should be a component**
The click popover state, positioning logic, and rendering in `DashboardPage` is all inline. This is already sizeable and will grow when booking events get additional actions in Phase 2. Extract a `<EventPopover event={...} position={...} onClose={...}>` component.

**21. `publicClient.ts` and `client.ts` share duplicated base config**
Both Axios clients set `baseURL: '/api'`. Extract a `createApiClient(config?)` factory so the common configuration lives once.

**22. `WorkingHoursPage` dirty check via `JSON.stringify` is fragile**
`WorkingHoursPage` detects unsaved changes with `JSON.stringify(form) !== JSON.stringify(savedForm)`. Key insertion order makes this work today, but it's not a contract. A simple shallow/deep equality utility (one function, no library needed) makes the intent explicit.

### Lower Impact

**23. `DashboardPage` sync re-enable uses a fixed 3-second `setTimeout`**
After clicking "Sync Now," the button is re-enabled after 3 seconds regardless of whether sync has completed. The backend sync can take longer. A more accurate approach is to re-fetch accounts after the delay and compare `lastSyncAt` timestamps — or simply keep the button disabled until the `refetchEvents` call resolves.

**24. `connectGoogle` and `connectOutlook` break the API module abstraction**
`calendar.ts` does `window.location.href = ...` inside API module functions. Every other function in `api/` returns a promise. The OAuth redirects could instead return the URL string (or be moved to the page component) so the API layer stays pure.

---

## Summary Table

| # | Area | Item | Impact |
|---|---|---|---|
| 1 | BE | Split `CalendarController` by concern | High |
| 2 | BE | Introduce `Provider` enum | High |
| 3 | BE | Make token refreshers side-effect-free | High |
| 4 | BE | `SyncAdapter` interface for provider dispatch | High |
| 5 | BE | Use Bean Validation on DTOs | High |
| 6 | BE | Replace `@Autowired(required=false)` with test no-op bean | High |
| 7 | FE | Remove `availability.ts` working-hours duplication | High |
| 8 | FE | Delete `App.css` scaffold dead code | High |
| 9 | BE | `markSyncFailed` — separate failure status from timestamp | Medium |
| 10 | BE | Move date parsing out of controller | Medium |
| 11 | BE | Use `LocalTime.parse` for time comparison | Medium |
| 12 | BE | Add catch-all handler in `GlobalExceptionHandler` | Medium |
| 13 | BE | Delete or complete `EncryptionConfig.java` | Medium |
| 14 | BE | Replace `UsernamePasswordAuthenticationToken` with `AdminAuthentication` | Medium |
| 15 | BE | Replace regex JWT parsing in `OutlookSyncAdapter` | Medium |
| 16 | FE | Delete `useAuth.ts` re-export indirection | Medium |
| 17 | FE | Assign event colors by account index, not ID modulo | Medium |
| 18 | FE | Extract `EventPopover` component | Medium |
| 19 | FE | Extract shared Axios factory | Medium |
| 20 | FE | Replace `JSON.stringify` dirty check with equality fn | Medium |
| 21 | BE | Use temp table in `deleteStale` instead of client-side diff | Lower |
| 22 | BE | Document pool-size-1 rationale in `DataSourceConfig` | Lower |
| 23 | FE | Sync button: disable until refetch resolves | Lower |
| 24 | FE | Move OAuth redirects out of API module | Lower |
