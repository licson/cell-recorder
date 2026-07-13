## Context

The recording lifecycle currently has two responses to any failure: terminate the entire session (only the primary `recordingJob` has a top-level catch) or silently swallow the exception (everywhere else). The other five sibling coroutines launched under `serviceScope` (`fallbackRecordingJob`, `pingJob`, `speedTestJob`, `markerCountJob`, `stateUpdateJob`) have no try/catch at all — an uncaught exception kills that job invisibly while the user sees a stale notification. There is no logging framework in the `service/` package, so every swallowed exception is truly silent. The existing "Share Crash Log" feature captures only `Thread.setDefaultUncaughtExceptionHandler` output — the final crash, never the lead-up of transient errors that explains why the session died.

Two specs are out of sync with the implementation:
- `db-write-safety/spec.md:33-37` requires the 5s shutdown timeout to be logged and the shutdown scope cancelled — neither happens (empty catch at `RecordingService.kt:572-573`).
- `recording/spec.md:178-182` "Point Recording Resilience" says a single DB insert failure SHALL let remaining snapshots continue — the per-snapshot `continue` at `PointRecorder.kt:189/259/327` only covers *build* failures, not *DB-insert* failures. A `@Transaction` batch insert failure propagates up and terminates the recording via `RecordingService.kt:287`.

## Goals / Non-Goals

**Goals:**
- A recording SHALL only stop when: user taps Stop, `maxRecordingDurationMin` is reached, the foreground service cannot legally start, `OutOfMemoryError`, or a persistent DB write failure (disk full, read-only, migration broken).
- Every other failure is logged via Timber and the recording continues. No degraded state is surfaced to the UI — most errors are transient and the user shouldn't be bothered.
- Marker insert failures are visible to the user (Toast) but do not stop or interrupt the recording cycle.
- The point count never drifts from the actual row count in `cell_records`.
- Shutdown finalization survives process death via WorkManager durable retry.
- The "Share Logs" feature captures the full narrative (rolling runtime log + latest crash file), not just the final crash.

**Non-Goals:**
- No new user-visible "degraded" UI state, snackbar event flow, or notification warning line. The existing `RecordingState.errorMessage` remains the only error channel, reserved for truly fatal stops.
- No retry/backoff for non-ping subsystems (speedtest, location, sensors) — these continue-on-next-tick naturally.
- No new Room migration (no schema change to `SessionEntity`; finalization retry uses existing `updateEndedAt` which is idempotent).
- No replacement of the existing uncaught-exception crash logger — `crash_logs/` is preserved; Timber adds a rolling log alongside it.
- No telemetry/remote logging — all logs stay on-device and are user-initiated to share.

## Decisions

### Decision 1: Fatal set = { user stop, max duration, foreground service start failure, OOM, persistent DB failure }

**Rationale:** "Recording only stops when the user presses Stop" requires a crisp definition of the exceptions. User stop and max duration are obvious. Foreground service start failure is genuinely fatal because Android will kill a service that doesn't call `startForeground` in time. OOM cannot be recovered. Persistent DB failure (disk full, read-only filesystem, broken migration) is fatal because a multi-hour recording with zero persistence is worse than stopping with a clear `errorMessage` — the user would otherwise accumulate unflushed data indefinitely.

**Alternatives considered:**
- *Make persistent DB failures degrade with in-memory buffering*: rejected because buffer growth is unbounded for long recordings, and a disk-full condition rarely clears mid-session.
- *Add foreground service revocation mid-session as fatal*: rejected because the service is already running; Android's behavior on mid-session permission revocation is to kill the service, which we cannot prevent anyway.

### Decision 2: No degraded UI state — errors are transient and logged only

**Rationale:** The user explicitly chose "no degraded state" because most errors (modem hiccup, ping timeout, speedtest insert failure, sensor unregister timeout) are transient and self-resolve on the next tick. Surfacing each one as a banner/notification would create noise without actionable information. Logging via Timber captures the diagnostic narrative for power users who share logs.

**Trade-off:** A user who wants to know "is my ping working right now?" cannot see that from the recording UI. They can infer it from the `currentLatency` field showing `"---"` (existing behavior) but not from an explicit "ping degraded" flag. Accepted.

### Decision 3: Timber 5.0.1 + custom `RollingFileTree` (1 MB rolling file log)

**Rationale:** Timber is the de facto Android logging library, fits the existing dep-heavy stack (Hilt/Room/Compose/OkHttp/osmdroid), has a tiny API surface, and supports custom `Tree` implementations. The latest stable is `5.0.1` (verified against Maven Central metadata; no later stable release exists — Timber is in maintenance mode). A `RollingFileTree` writing to `app_logs/runtime.log` (1 MB cap, rotates to `runtime.log.1`) captures the lead-up narrative that the crash logger misses.

**Alternatives considered:**
- *`android.util.Log` only*: rejected because it requires a TAG at every call site, no tree abstraction, and zero persistence — the crash-log upgrade problem would remain unsolved.
- *Custom thin wrapper around `Log`*: rejected because it duplicates Timber's API for no benefit and we'd re-invent the Tree pattern.
- *Timber writes into `crash_logs/`*: rejected because mixing transient-error logs with crash files conflates two concerns and the file grows unboundedly between crashes.

### Decision 4: WorkManager (`androidx.work:work-runtime:2.11.2`) for durable shutdown finalization retry

**Rationale:** If the 5-second in-process `updateEndedAt` / `updatePrimarySimSlot` attempt fails or times out (e.g., process killed by the system, disk transient), the session is left with `endedAt = null` forever — the session list shows it as "still recording" and analytics duration is wrong. WorkManager survives process death and respects backoff constraints. The latest stable is `2.11.2` (verified against Google Maven metadata, released 2026-03-25). Per AndroidX release notes, `work-runtime-ktx` is now an empty stub — `CoroutineWorker` lives in the main `work-runtime` artifact, so we depend on `work-runtime` directly (not `-ktx`).

**Alternatives considered:**
- *`pending_finalization` flag on `SessionEntity` + reconcile on next app launch*: rejected because it needs a Room migration and delays finalization until the user reopens the app.
- *Just log, accept rare null `endedAt`*: rejected because it leaves the spec gap with `db-write-safety/spec.md:33-37` unaddressed and produces the "stuck recording" UX.

### Decision 5: Two-tier DB batch resilience (try batch → fall back to per-snapshot)

**Rationale:** The spec `recording/spec.md:178-182` requires a single insert failure to let remaining snapshots continue. A `@Transaction` batch insert is atomic — one bad row rolls back the whole batch. The two-tier strategy preserves the fast path (single transaction) for the common case and only falls back to per-snapshot inserts when a transient DB error occurs. Persistent DB errors (classified by `DbExceptionClassifier`) propagate as fatal per Decision 1.

**Classification rule** (`DbExceptionClassifier`, pure-logic, testable):
- `Fatal`: `SQLiteFullException`, `SQLiteReadOnlyDatabaseException`, `IllegalStateException` containing "migration" / "schema", `SQLiteDatabaseLockedException` when persistent.
- `Transient`: `SQLiteConstraintException`, `IOException`, generic `SQLException`, `SQLiteDatabaseLockedException` when transient.
- `CancellationException`: rethrown (never classified).

**Alternatives considered:**
- *Always insert per-snapshot*: rejected because it loses the atomicity guarantee that "all snapshots for one trigger are written together or none are" — useful for crash recovery.
- *Drop the `@Transaction` and use individual inserts always*: rejected for the same reason.

### Decision 6: Point-count increments by rows actually inserted

**Rationale:** Today `incrementPointCount` + `totalPointCount++` run unconditionally after `insertRecordBatch`, even when `records` is empty (all per-snapshot builds failed). This causes `pointCount` to drift above the actual row count in `cell_records`, corrupting analytics and export. `insertBatch` will return the actual inserted count; `incrementPointCount(sessionId, insertedCount)` and `totalPointCount += insertedCount` will use that. Empty batch → zero increment.

**Tension with spec:** `recording/spec.md:160-163` says "increments by one per location trigger, not per SIM." Under the new semantics, a trigger where all snapshots fail produces zero increment. We update the spec to clarify: "increments by the number of rows actually inserted for that trigger."

### Decision 7: Marker insert failure → Toast, no recording interruption

**Rationale:** A marker insert is a user-initiated action (they tapped "Mark Note" from the notification or UI). Silent failure there is more surprising than a background ping hiccup. A brief Toast ("Marker could not be saved") on the main thread via `Handler(Looper.getMainLooper())` gives feedback from both the notification action path and the UI button path. The recording cycle is not interrupted — the marker insert runs in its own fire-and-forget coroutine under `serviceScope`.

### Decision 8: Ping exponential backoff (1s → 60s cap)

**Rationale:** Today `PingEngine` restarts the ping process with a fixed `delay(1000)` after every failure. On a long network outage, this spawns and destroys ping processes at 1 Hz for hours, wasting CPU and battery. A pure-logic `PingBackoff` calculator returns: failure 0 → 1s, 1 → 2s, 2 → 4s, 3 → 8s, 4 → 16s, 5 → 32s, ≥6 → 60s (cap). The cap prevents unbounded growth; 60s is short enough that connectivity recovery is detected within a minute.

### Decision 9: No-notification mode for `stateUpdateJob`

**Rationale:** If `notificationHelper.notify` keeps throwing (e.g., `POST_NOTIFICATIONS` revoked mid-recording on API 33+), the state-update loop currently dies silently — the notification freezes, but worse, the `stateManager.update` calls in the same loop body also stop, so the in-app UI freezes too. The fix: catch the notify exception, log via Timber, and continue the loop, skipping only the notify call on subsequent iterations (a "no-notification mode" flag). `stateManager.update` calls continue, so the in-app UI still reflects live state.

### Decision 10: `@Volatile` on `isStopped` + CancellationException hygiene

**Rationale:** `isStopped` at `RecordingService.kt:71` is read/written without synchronization. It's safe today because `onStartCommand` and `onDestroy` are main-thread only, but a future refactor calling `stopRecording()` from a background coroutine would introduce a visibility race. `@Volatile` is a zero-cost guarantee. Separately, every `catch (e: Exception)` in a coroutine context that doesn't rethrow `CancellationException` breaks structured concurrency (a cancelled coroutine may be reported as "completed normally" instead of cancelled, skipping cleanup). The fix is mechanical: add `catch (e: CancellationException) { throw e }` before the generic catch at every site in `service/`.

## Risks / Trade-offs

- **[Risk] `DbExceptionClassifier` is fragile across SQLite versions** → Mitigation: tests pin the expected exception types; classifier falls back to `Transient` for unknown types (fail-open, never fatal-by-default). Reviewed when raising minSdk.
- **[Risk] WorkManager adds ~200 KB and a new runtime dependency** → Mitigation: small relative to existing deps; WorkManager is a first-party AndroidX library with long-term support.
- **[Risk] `RollingFileTree` writes from any thread** → Mitigation: delegate writes to a single-thread executor inside the tree to serialize file I/O. Tested for concurrent appends. Never block the calling thread on disk I/O.
- **[Risk] "Share Logs" output size exceeds share-intent limits** → Mitigation: rolling log is capped at 1 MB (plus 1 MB rotated `.1`), crash files capped at 5. Total worst case ~11 MB. If a share target chokes, truncate the rolling log to last 256 KB when sharing. (Not in v1; revisit if reported.)
- **[Risk] Toast from a service context may be suppressed on API 33+ if the app is in the background and lacks `POST_NOTIFICATIONS`** → Mitigation: Toasts are not notifications and are not affected by `POST_NOTIFICATIONS`. They do require the app process to be alive, which it is during recording. Acceptable.
- **[Trade-off] No degraded UI state means a user cannot tell from the UI that ping/speedtest/notifications have failed** → Accepted per Decision 2. The user can infer ping failure from `currentLatency = "---"` (existing) and speedtest failure from `speedTestStatus = "Failed"` (existing). Notification failure is invisible by design — the recording continues either way.
- **[Trade-off] Persistent DB failure as fatal contradicts "only stops on user press"** → Accepted per Decision 1. The alternative (unbounded in-memory buffering) is worse.

## Migration Plan

No database migration required. Dependency additions only.

**Rollout:**
1. Add `com.jakewharton.timber:timber:5.0.1` and `androidx.work:work-runtime:2.11.2` to `app/build.gradle.kts`.
2. Plant Timber trees in `CellRecorderApp.onCreate` (DebugTree in debug, RollingFileTree always). Existing uncaught-exception crash logger is preserved unchanged.
3. Implement the supervision/reclassify/DB-resilience/finalization changes in `service/`.
4. Rename "Share Crash Log" → "Share Logs" and update `SettingsViewModel.getLatestCrashLog()` → `getLogsForShare()`.
5. Tests (pure-logic units + androidTest extensions).

**Rollback:** revert the dependency additions and the `service/` changes. The renamed Settings action and `getLogsForShare()` method can stay (they're backward-compatible — they just read more files when present).

## Open Questions

None outstanding — all decisions are locked per user confirmation.
