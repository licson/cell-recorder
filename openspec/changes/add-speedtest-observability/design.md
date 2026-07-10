## Context

The speedtest subsystem has three pain points that this change addresses as one coherent capability:

1. **Timing is half-recorded.** `SpeedTestResult` carries no time fields. `RecordingService.kt:377` captures `testStart` and persists it as `timestamp` on `SpeedTestRecordEntity` (line 421), but the finish time is computed at line 438 (`elapsed = now() - testStart`) only to schedule the next cycle and then discarded. A speedtest is a *duration* (config fetch + server selection + gauge + download + optional upload), not an instant sample — but the persisted shape pretends it's an instant.

2. **The engine has silent failure paths.** `SpeedTestEngine.runTest()` (`SpeedTestEngine.kt:36-165`) has seven exit paths; three of them — SKIPPED_WIFI (line 47), config fetch failure (line 54), server selection failure (line 74) — emit zero engine-level logs. Per-request `Log.w` calls exist downstream in `SpeedTestServerSelector` (lines 170, 183) and `SpeedTestMeasurer` (lines 87, 176, 268), but the engine's own decisions (which URL was tried, how many servers were found, why ping failed) are invisible. When the user reports "it gets stuck with unable to connect or server selection failure messages," there is no on-device way to diagnose.

3. **No priming path.** The only way to run a speedtest is to start a recording session. `RecordingService.kt:375` unconditionally calls `invalidateCache()` at session start, so even if a user wanted to "warm up" the connection, the next session starts cold. Mobile radio conditions vary; a known-good server binding established before recording would help.

The engine is a `@Singleton` (`SpeedTestEngine.kt:15`) holding three cached fields: `cachedServer`, `cachedConfig`, `cachedGaugeBps` (+ `gaugeAttempted` flag). `invalidateCache()` (line 201) wipes all four. The `SpeedTestServerSelector.fetchAndSelect()` (line 27) bypasses geo/ping when `preferredServerId` is set (lines 39-41), so a "fixed server" config is already a fast path. The existing logging pattern elsewhere in the codebase is `if (BuildConfig.DEBUG) android.util.Log.d(...)` (e.g. `PermissionHelper.kt:169`, `RecordingScreen.kt:138`); the speedtest subsystem uses bare `Log.w` without the DEBUG gate.

The existing "Share Crash Log" button (`SettingsScreen.kt:189-208`) reads from `filesDir/crash_logs` and shares via `Intent.ACTION_SEND`. The existing speedtest EULA gate (`SettingsScreen.kt:104-111`) requires acceptance before `speedTestEnabled` can be toggled true.

## Goals / Non-Goals

**Goals:**
- Persist both start and finish times per speedtest record, with `finishedAt` always non-null (`= startedAt` for instant bail-outs).
- Surface structured phase/decision events from the engine to an in-memory ring buffer consumable by an in-settings debug card.
- Add a manual "Launch Test" button in Settings that primes the connection (re-primes server + gauge, respects fixed-server config) and produces a `SpeedTestResult` without persisting it.
- Conditional cache handoff: a successful manual prime warms the next recording session; a failed prime or no prime leaves the session to start cold.
- Additive CSV column for `finished_at`; additive Room migration; no breaking changes to existing exports, queries, or schemas.

**Non-Goals:**
- Renaming `timestamp` → `startedAt` on the entity or in the CSV header. (Additive only — preserves any spreadsheet/automation users have built on the current header.)
- Persisting manual "Launch Test" results. The `sessionId` foreign key is non-null; we do not create a sentinel session. Manual tests are diagnostic only.
- Making duration a recorded analytics dimension. `SpeedTestAnalyticsEngine` is unchanged; `finishedAt` is persisted but not yet consumed by analytics. Flagged for future investigation.
- A dedicated debug screen or adb-only logging. The debug surface is a card/popup *inside* the Settings page.
- Changing the speedtest EULA flow, the WiFi-skip policy, or the upload-enabled config toggle. The manual launch mirrors the service's existing guards.
- Persisting the `primedSinceLastInvalidation` flag across process restarts. In-memory is sufficient: manual primes only happen while the app is foregrounded, and a process restart between prime and session-start is rare enough that cold-starting the session is acceptable.

## Decisions

### D1. Engine owns timing; `SpeedTestResult` gains `startedAt`/`finishedAt`

**Choice:** `SpeedTestResult` gains `startedAt: Long` and `finishedAt: Long`. `SpeedTestEngine.runTest()` sets `startedAt` at entry, `finishedAt` at every return path. For instant bail-outs (SKIPPED_WIFI, config fetch failure, server selection failure, exception), `finishedAt = startedAt`. `RecordingService` reads both from the result and persists them.

**Rationale:** The engine owns the test lifecycle; timing belongs there. Making the result self-describing means any caller (service or manual launch) gets timing for free, and engine tests can assert timing invariants (`finishedAt >= startedAt`; `finishedAt == startedAt` for instant bail-outs). The alternative — wrapping `runTest()` in the service — would leave `SpeedTestResult` "pure" but couple timing capture to the service caller, so the manual launch path would need its own wrapper.

**Alternatives considered:**
- *Service wraps `runTest()`:* Rejected. Duplicates timing logic across two callers; `SpeedTestResult` can't be round-tripped (e.g. exported) with its timing intact.
- *Capture only `finishedAt`, derive start from `timestamp`:* Rejected. `timestamp` on the entity is start, but `SpeedTestResult` has no such field today; adding only `finishedAt` to the result would force callers to mix entity and result semantics.

### D2. Schema: additive `finishedAt` column, no rename

**Choice:** Add `finishedAt: Long` to `SpeedTestRecordEntity` (default `0` for backfill). `MIGRATION_14_15` runs `ALTER TABLE speed_test_records ADD COLUMN finishedAt INTEGER NOT NULL DEFAULT 0`. Keep `timestamp` as the start time. Bump `@Database(version = 15)`.

**Rationale:** A speedtest is a duration, so honest naming would be `startedAt`/`finishedAt`. But renaming `timestamp` requires a table rebuild (Room can't rename in place), touches 4 DAO queries that `ORDER BY timestamp` (`SpeedTestRecordDao.kt:18,21,24,42`), the `Index("timestamp")`, and the CSV header. The additive path is one `ALTER TABLE`, zero query changes, zero index rebuilds, zero CSV breakage. The semantic ambiguity of `timestamp` (= start) is documented in the spec and accepted as a trade-off.

**Alternatives considered:**
- *Rename `timestamp` → `startedAt`:* Rejected for migration cost and CSV breakage. Documented as a v2 candidate if the ambiguity becomes painful.
- *Nullable `finishedAt`:* Rejected. Always-non-null (`= startedAt` for instant bail-outs) gives a uniform shape and lets `duration = finishedAt - startedAt` be computed everywhere without null checks. Duration = 0 cleanly signals "test never ran."

### D3. Re-prime scope: server + gauge, keep config

**Choice:** New `SpeedTestEngine.reprimeServerAndGauge()` clears `cachedServer`, `cachedGaugeBps`, and `gaugeAttempted`; keeps `cachedConfig`. The manual "Launch Test" button calls this before `runTest()`.

**Rationale:** "Re-prime server selection" means re-running discovery. But the gauge depends on the server's throughput estimate, so a stale gauge against a fresh server is inconsistent — gauge must be cleared too. The config XML (`speedtest-config.php`) changes rarely and costs ~1s + a request to re-fetch; keeping it avoids a redundant fetch on every manual prime. This is the middle ground between "server only" (cheapest, but stale gauge) and "server + gauge + config" (most expensive, ~3-5s).

**Alternatives considered:**
- *Server only:* Rejected. Stale gauge against a fresh server produces wrong file-size selection on the next download.
- *Server + gauge + config (full cold start):* Rejected. Config re-fetch on every manual prime is wasteful; config changes rarely. Available implicitly via `invalidateCache()` if a future need arises.

### D4. Respect `preferredServerId` on manual launch

**Choice:** The manual launch passes `config.speedTestServerId?.toIntOrNull()` to `runTest()`, exactly as the service does (`RecordingService.kt:390`). `SpeedTestServerSelector.fetchAndSelect()` bypasses geo/ping when `preferredServerId` is set (lines 39-41).

**Rationale:** Consistency with the service path. If the user has fixed a server in Settings, the manual test should exercise the same code path the session will use. Forcing auto-select on manual launch would test a *different* path than the session hits, reducing diagnostic value.

**Alternatives considered:**
- *Force auto-select to exercise the fallback path:* Rejected. Would make manual test behavior diverge from session behavior.
- *Two buttons ("Test configured" + "Test auto-select"):* Rejected. Too much UI surface for a diagnostic feature.

### D5. Conditional cache handoff via in-memory flag

**Choice:** `SpeedTestEngine` gains `@Volatile var primedSinceLastInvalidation: Boolean = false`. Set `true` on manual launch success; set `false` in `invalidateCache()`. `RecordingService` at session start (line 375) checks the flag: if `true`, keep the cache (warm handoff) and reset the flag; if `false`, call `invalidateCache()` (cold start).

**Rationale:** Today's unconditional `invalidateCache()` at session start makes any manual prime useless. Conditional handoff lets a successful prime warm the next session. In-memory is sufficient: the manual prime happens in the foreground, and the session typically starts shortly after. A process restart between prime and session-start loses the flag, but that just means the session cold-starts — which is today's behavior, not a regression. Persisting the flag to `AppConfigEntity` would add schema/IO for a marginal edge case.

The flag is reset on consumption (read-once semantics): the session that benefits from the prime clears the flag, so a second session without a fresh prime still cold-starts. This prevents a stale prime from warming arbitrarily many future sessions.

**Alternatives considered:**
- *Persist the flag in config:* Rejected. Adds schema/IO; the prime-then-restart-then-session edge case is rare.
- *Always wipe at session start (today's behavior):* Rejected. Contradicts the "prime the connection" goal.
- *Read-many flag (don't reset on consumption):* Rejected. A prime could warm many future sessions across hours, even if radio conditions change. Read-once keeps the prime fresh.

### D6. Auto-invalidate on manual failure (today's path)

**Choice:** The engine's existing measurement-failure invalidation path (`SpeedTestEngine.kt:132`) fires unchanged on manual launch failure. `invalidateCache()` sets `primedSinceLastInvalidation = false`. The user can retry by tapping "Launch Test" again, which calls `reprimeServerAndGauge()` first anyway.

**Rationale:** Consistency with the service path. Keeping a stale cache on failure would let the user "retry against" a known-bad server, which is the opposite of diagnosing the stuck state. The existing path already does the right thing.

**Alternatives considered:**
- *Keep stale cache on failure for retry:* Rejected. Contradicts the diagnostic intent; the user wants to know *why* it's stuck, not paper over it.

### D7. Debug ring buffer: in-memory, `@Singleton`, last N events

**Choice:** New `SpeedTestDebugRingBuffer` (`@Singleton`) holds a bounded ring buffer (capacity 200) of structured `SpeedTestDebugEvent` records: `{ timestampMs: Long, phase: String, status: String, message: String, serverId: Long?, serverHost: String?, bytes: Long? }`. Phases: `config_fetch`, `server_select`, `gauge`, `download`, `upload`, `done`, `error`. Statuses: `ok`, `warn`, `fail`, `info`. The engine emits at every decision point (config fetch result, server count found, per-server ping outcome, gauge result, download/upload start/finish, measurement failure, exception).

The buffer exposes a `Flow<List<SpeedTestDebugEvent>>` for live UI consumption and a `snapshot(): List<SpeedTestDebugEvent>` for the "Share Debug Log" export. The engine also mirrors each event to `Log.*` (gated on `BuildConfig.DEBUG` for `info`/`ok`, unconditional for `warn`/`fail`) so adb users see the same stream.

**Rationale:** A ring buffer is the simplest structure that supports both live in-app display and post-hoc sharing. Capacity 200 covers a full speedtest cycle (config + ~5 server pings × 3 samples + gauge + ~30 download samples + ~30 upload samples) with headroom. In-memory avoids IO on the hot path; the "Share Debug Log" button serializes the snapshot to text on demand. Mirroring to `Log.*` preserves the existing adb workflow without forcing the in-app surface.

**Alternatives considered:**
- *Logcat-only:* Rejected. User may not be on adb; the "stuck" symptom is exactly when on-device diagnosis matters most.
- *File-based log:* Rejected. IO on the hot path; the existing crash-log pattern is for crashes, not periodic diagnostics.
- *Persistent across process restart:* Rejected. The buffer is for *the current manual launch*; cross-restart history adds storage complexity for little diagnostic value.

### D8. Debug surface: dedicated card/popup inside Settings

**Choice:** The Settings Speed Test card grows a "Launch Test" button and a "Debug" expandable region. Tapping "Launch Test" runs the test and expands the debug region, which shows the ring buffer events live (auto-scrolling list, newest at bottom). A "Share Debug Log" action (icon button in the debug region header) exports the current ring buffer snapshot as text via `Intent.ACTION_SEND`, mirroring the existing "Share Crash Log" pattern (`SettingsScreen.kt:189-208`).

**Rationale:** A card/popup inside Settings keeps the diagnostic context co-located with the speedtest configuration (server ID, interval, upload toggle) — the user can tweak config and re-launch in one place. A dedicated screen would add a navigation hop and a new route; a modal sheet would obscure the config the user is diagnosing. The expandable region collapses when not in use, so it doesn't dominate the Settings page for users who never run manual tests.

**Alternatives considered:**
- *Modal bottom sheet:* Rejected. Obscures the Speed Test config rows the user is diagnosing.
- *Dedicated screen:* Rejected. Adds navigation; the debug context is Settings-local.
- *Separate "Diagnostics" tab:* Rejected. Overkill for a single feature.

### D9. Manual test not persisted; no sentinel session

**Choice:** The manual "Launch Test" result is discarded after display (duration, download/upload bps, server, error). It is NOT written to `speed_test_records`. The `sessionId` foreign key on `SpeedTestRecordEntity` is non-null (`SpeedTestRecordEntity.kt:11-16`), and we do not create a sentinel session.

**Rationale:** Mixing diagnostic tests with recorded data would pollute analytics (`SpeedTestAnalyticsEngine` aggregates per-session) and CSV exports. The manual test's value is diagnostic (did it succeed? what was the error? how long did it take?), not longitudinal. The result lives in the debug card and ring buffer; that's enough.

**Alternatives considered:**
- *Sentinel session (`sessionId = -1` or reserved row):* Rejected. Requires schema/DAO exceptions for a non-real session; pollutes session lists if not carefully filtered.
- *Persist to most recent active session:* Rejected. Confusing — the user may not realize a manual test got attributed to a session.

### D10. WiFi guard and EULA gate mirror the service

**Choice:** The "Launch Test" button is visible only when `config.speedTestEnabled` is true (which already required EULA acceptance via `SpeedTestEulaDialog`). `SpeedTestEngine.runTest()` already refuses on WiFi (`SpeedTestEngine.kt:42-49`), so the manual path inherits this check. No new prompts.

**Rationale:** The existing gating is sufficient. Adding a fresh EULA prompt on manual launch would be redundant (the user already accepted to enable the feature). Adding a metered-data warning would be friction the service path doesn't have.

**Alternatives considered:**
- *Fresh EULA on manual launch:* Rejected. Redundant.
- *Metered-data warning:* Rejected. The service doesn't warn; manual launch shouldn't either.

## Risks / Trade-offs

- **[Risk] `finishedAt = 0` for pre-migration rows** → Mitigation: spec documents that `finishedAt = 0` means "legacy row, finish time unknown"; UI and analytics treat `finishedAt == 0` as `null`-equivalent (don't show duration badge, don't compute duration). The migration's `DEFAULT 0` makes the backfill trivial.
- **[Risk] Manual launch consumes cellular data (~5-30 MB per test)** → Mitigation: the existing EULA dialog already warns about data usage; the manual launch inherits the same gate. The debug card shows the data consumed per launch.
- **[Risk] Ring buffer grows unbounded on rapid re-launches** → Mitigation: bounded capacity (200); each `reprimeServerAndGauge()` clears the buffer so a new launch starts fresh.
- **[Risk] Conditional handoff: stale prime warms a session after radio conditions change** → Mitigation: read-once flag semantics (D5); the session that consumes the prime clears the flag, so a second session cold-starts. Manual prime is opt-in and recent by definition.
- **[Risk] Process restart loses the prime flag** → Mitigation: acceptable; cold-start is today's behavior, not a regression.
- **[Trade-off] `timestamp` semantic ambiguity** → Accepted. Additive migration preserves existing data/queries/CSV at the cost of a slightly muddy column name. Documented in spec; v2 rename is possible if it becomes painful.
- **[Trade-off] Duration not yet an analytics dimension** → Accepted. `finishedAt` is persisted for future use; `SpeedTestAnalyticsEngine` is unchanged in this change. Avoids scope creep.

## Migration Plan

1. Bump `@Database(version = 15)` and add `MIGRATION_14_15` (`ALTER TABLE speed_test_records ADD COLUMN finishedAt INTEGER NOT NULL DEFAULT 0`).
2. Register `MIGRATION_14_15` in `DatabaseModule.addMigrations(...)`.
3. Add `finishedAt: Long = 0L` to `SpeedTestRecordEntity` (default `0L` for the Room-generated no-arg path; the service always sets it explicitly going forward).
4. `RecordingService` populates `finishedAt = result.finishedAt` on insert (line 419).
5. No data backfill is needed — legacy rows keep `finishedAt = 0`, treated as unknown.

**Rollback:** Reverting the migration is not supported (Room doesn't support down-migrations here). If the change must be rolled back, bumping to version 16 with a no-op migration restores the previous code path while keeping the column (harmless orphans). The column is additive and nullable-via-default, so old code reading the table simply ignores it.

## Open Questions

- **Ring buffer capacity:** 200 is a guess based on one full cycle. If the debug card feels truncated on slow connections (more ping samples, longer download), bump to 500. Confirm during implementation by observing a real manual launch.
- **Debug event granularity for download/upload samples:** Emitting one event per ~500ms throughput sample could flood the buffer. Decision: emit only phase-level events for download/upload (start, finish, failure), not per-sample. Per-sample data stays in `MeasurementResult` for speed calculation, not the ring buffer.
- **"Share Debug Log" format:** Plain text with one event per line (`HH:mm:ss.SSS [phase] status: message`) is the default. Consider also offering JSON for machine-parsable bug reports — defer to implementation unless the user requests it.
