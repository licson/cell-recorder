## Why

Today, a recording session stops on far more than just user actions. A transient modem hiccup from `CellInfoCollector`, a single bad row in a batch DB insert, a config load failure, a permission revocation, or a swallowed exception in any of five unsupervised sibling coroutines can silently terminate a multi-hour recording — or freeze its notification while the session keeps running invisibly. The result is lost data, stale UI, and a "Share Crash Log" feature that captures only the final crash, never the lead-up of transient errors that explains why the session died. We need a recording lifecycle that **only stops when the user presses Stop**, backed by logging that captures the full narrative of what happened along the way.

## What Changes

- **Recording continuity contract**: a recording SHALL only stop on (a) user stop, (b) `maxRecordingDurationMin`, (c) foreground service start failure, (d) `OutOfMemoryError`, or (e) persistent DB failure. Everything else is logged and the recording continues.
- **Reclassify fatal → transient** for: config load failure, `SecurityException` on location, indoor step-sensor unavailability, generic exceptions in the recording loop, and unguarded `CellInfoCollector.snapshots()` calls. Each degrades gracefully (sentinel coordinates, time-based triggers, empty snapshots) rather than stopping.
- **Sibling job supervision**: install a `CoroutineExceptionHandler` on `serviceScope` and wrap every sibling job (`fallback`, `ping`, `speedTest`, `markerCount`, `stateUpdate`) in try/catch with `CancellationException` rethrown. A failure in one job no longer silently kills that subsystem.
- **No-notification mode**: if `notificationHelper.notify` keeps throwing (e.g., `POST_NOTIFICATIONS` revoked mid-recording), `stateUpdateJob` skips the notify call but keeps updating `RecordingStateManager` so the in-app UI still reflects live state.
- **Marker insert failure visibility**: a marker insert failure shows a Toast ("Marker could not be saved") and is logged via Timber. It does NOT stop or interrupt the recording cycle.
- **Point-count integrity**: `pointCount` increments by rows actually inserted, not by trigger count. Empty batches produce zero increments, eliminating the drift where `pointCount` exceeded actual rows in `cell_records`.
- **Two-tier DB batch resilience**: on a transient DB exception, `PointRecorder` falls back to per-snapshot inserts, skipping individual failures and continuing the session. On a persistent DB exception (disk full, read-only, migration broken), the recording stops fatally with a clear `errorMessage`.
- **Durable shutdown finalization**: if the 5-second in-process `updateEndedAt` / `updatePrimarySimSlot` attempt fails or times out, a `WorkManager` one-shot worker retries with exponential backoff (max 5 attempts). Sessions no longer get stuck with `endedAt = null` after process death.
- **Ping exponential backoff**: replaces the fixed 1 Hz `delay(1000)` in `PingEngine` with exponential backoff (1s → 2s → 4s → … → 60s cap). A long network outage no longer spawns ping processes at 1 Hz for hours.
- **Timber logging in the service layer**: the service package currently has zero logging. Every caught exception is truly silent. Timber is planted with a `DebugTree` (debug builds, logcat) and a new `RollingFileTree` (always, app files).
- **Rolling runtime log**: `app_logs/runtime.log` (1 MB cap, rotates to `runtime.log.1`) captures every `Timber.e/w` call. The existing `crash_logs/crash_<ts>.txt` files (5 most recent) are preserved.
- **Unified "Share Logs"**: the Settings "Share Crash Log" action is renamed "Share Logs" and now concatenates the latest crash file with the rolling runtime log, so a user reporting a degraded recording can share the full lead-up context.
- **Sensor unregister timeout logging**: `IndoorPositionCollector` and `SensorFusionCollector` now inspect the `latch.await` return value and `Timber.w` on timeout (previously the listener could stay registered silently).
- **`@Volatile` on `isStopped`**: documents and enforces the main-thread assumption on the double-stop guard.

## Capabilities

### New Capabilities

- `logging`: Runtime log capture via Timber — a 1 MB rolling file log plus the existing crash log files, exposed through a unified "Share Logs" action in Settings.

### Modified Capabilities

- `recording`: New "Recording Continuity" requirement (only stop on user/max-duration/foreground-start/OOM/persistent-DB). Refine "Point Recording Resilience" to cover DB-level failures with per-snapshot fallback. Clarify point-count semantics (increment by rows inserted). New "Marker Insert Failure" requirement (Toast + continue, no recording interruption).
- `db-write-safety`: Shutdown finalization SHALL retry via WorkManager if the 5s in-process attempt fails or times out. Existing "log + cancel scope" requirement now actually implemented.
- `service`: Sibling jobs SHALL be supervised via `CoroutineExceptionHandler` + per-job catches that rethrow `CancellationException`. Notification failure SHALL enter no-notification mode. Timber SHALL be used for all caught exceptions in the service layer.
- `thread-safety`: `isStopped` SHALL be `@Volatile`.
- `process-cleanup`: Ping restart SHALL use exponential backoff (1s → 60s cap) instead of a fixed 1-second delay.

## Impact

- **New dependencies**: `com.jakewharton.timber:timber:5.0.1` (logging), `androidx.work:work-runtime:2.11.2` (durable finalization retry).
- **New Kotlin files**: `RollingFileTree` (Timber tree), `DbExceptionClassifier` (pure-logic fatal/transient classifier), `PingBackoff` (pure-logic backoff calculator), `SessionFinalizationWorker` (WorkManager CoroutineWorker).
- **Modified files**: `RecordingService.kt` (supervision, reclassification, shutdown retry, markNote toast), `PointRecorder.kt` (two-tier insert, point-count), `CellInfoCollector.kt` (snapshot try/catch), `PingEngine.kt` (backoff), `IndoorPositionCollector.kt` and `SensorFusionCollector.kt` (latch timeout logging), `LocationCollector.kt` (CancellationException rethrow), `CellRecorderApp.kt` (Timber planting, crash logger preserved), `SettingsViewModel.kt` (`getLatestCrashLog()` → `getLogsForShare()`), `SettingsScreen.kt` ("Share Crash Log" → "Share Logs").
- **New DAO method**: `CellRecordDao.insertSingle(record, caBands)` for per-snapshot fallback.
- **Spec drift closed**: `db-write-safety/spec.md` (log + cancel requirement now implemented); `recording/spec.md` "Point Recording Resilience" (DB-level failures now handled per spec).
- **Tests**: new pure-logic tests for `DbExceptionClassifier`, `BatchInsertStrategy`, `PointCountPolicy`, `PingBackoff`, `RollingFileTree`, `SessionFinalizationWorker`; extended `RecordingServiceTest` (androidTest) and `SettingsViewModelTest` for error paths.
- **No breaking API changes** for downstream consumers — all changes are internal to the recording lifecycle.
