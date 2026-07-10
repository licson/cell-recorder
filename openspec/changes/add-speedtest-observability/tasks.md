## 1. Database Schema and Migration

- [x] 1.1 Add `finishedAt: Long = 0L` field to `SpeedTestRecordEntity` (after `timestamp`); document in the field's KDoc that `timestamp` is the test start time and `finishedAt` is the test finish time (wall-clock ms), with `finishedAt = timestamp` for instant bail-outs and `finishedAt = 0` for legacy rows
- [x] 1.2 Bump `@Database(version = 15)` in `AppDatabase.kt` (currently `version = 14`)
- [x] 1.3 Add `MIGRATION_14_15` in `AppDatabase.kt` that runs `ALTER TABLE speed_test_records ADD COLUMN finishedAt INTEGER NOT NULL DEFAULT 0`; register it in `DatabaseModule.addMigrations(...)` at the end of the existing chain
- [x] 1.4 Add a migration test in the existing migration test class: seed a v14 database with one session and one speedtest row (no `finishedAt`), run `MIGRATION_14_15`, assert the column exists with `DEFAULT 0` and the existing row's `finishedAt = 0`; assert existing `cell_records`/`session_markers` data round-trips unchanged
- [x] 1.5 Add a `SpeedTestRecordDaoTest` (or extend the existing one) covering: insert with explicit `finishedAt`, query returns `finishedAt`; legacy rows with `finishedAt = 0` round-trip; `finishedAt = timestamp` for instant bail-outs persists correctly

## 2. Speedtest Engine — Timing, Ring Buffer, and Re-prime

- [x] 2.1 Add `startedAt: Long` and `finishedAt: Long` fields to `SpeedTestResult` (after `succeeded`/`errorMessage` or in a sensible position); update every construction site in `SpeedTestEngine.runTest()` (7 return paths: SKIPPED_WIFI, config fetch failure, server selection failure, measurement failure, success, exception, and any others) to set `startedAt` at entry and `finishedAt` at return — `finishedAt = startedAt` for instant bail-outs
- [x] 2.2 Create `SpeedTestDebugEvent` data class with fields: `timestampMs: Long`, `phase: String` (`config_fetch`/`server_select`/`gauge`/`download`/`upload`/`done`/`error`), `status: String` (`ok`/`warn`/`fail`/`info`), `message: String`, `serverId: Long? = null`, `serverHost: String? = null`, `bytes: Long? = null`
- [x] 2.3 Create `SpeedTestDebugRingBuffer` as a `@Singleton` Hilt class with: bounded capacity 200, `append(event)`, `clear()`, `events: Flow<List<SpeedTestDebugEvent>>` (or `StateFlow`), `snapshot(): List<SpeedTestDebugEvent>`; eviction of oldest on overflow; thread-safe (the engine runs on `Dispatchers.IO` with parallel measurement coroutines)
- [x] 2.4 Inject `SpeedTestDebugRingBuffer` into `SpeedTestEngine`; emit structured events at every decision point in `runTest()`: config fetch result (ok/fail), server selection result (count found, selected server, ok/fail), gauge result (bytes, ok/fail), download start/finish, upload start/finish, measurement failure, exception. Mirror each event to `Log.d` (gated on `BuildConfig.DEBUG` for `info`/`ok`) or `Log.w` (unconditional for `warn`/`fail`). Phase-level events only for download/upload (not per-~500ms sample) per design D7
- [x] 2.5 Add `fun reprimeServerAndGauge()` to `SpeedTestEngine` that clears `cachedServer`, `cachedGaugeBps`, `gaugeAttempted` (keeps `cachedConfig`); also clears the ring buffer so the next manual launch starts fresh
- [x] 2.6 Add `@Volatile var primedSinceLastInvalidation: Boolean = false` to `SpeedTestEngine`; set `true` on manual launch success (see task 4.3), set `false` in `invalidateCache()`; add `fun consumePrimeFlag(): Boolean` that reads-and-resets the flag (read-once semantics per design D5) for the service to call at session start
- [x] 2.7 Update `invalidateCache()` to also set `primedSinceLastInvalidation = false` (in addition to today's clearing of `cachedServer`/`cachedConfig`/`cachedGaugeBps`/`gaugeAttempted`)
- [x] 2.8 Extend `SpeedTestEngineTest` (existing pure-logic tests): assert `startedAt`/`finishedAt` are set on every return path; assert `finishedAt = startedAt` for SKIPPED_WIFI, config failure, server failure, exception; assert `finishedAt > startedAt` for the success path (use a fake clock or mock delays); assert `reprimeServerAndGauge()` clears server+gauge but keeps config; assert `consumePrimeFlag()` returns true once then false

## 3. RecordingService — Conditional Cache Invalidation and Finish Time Persistence

- [x] 3.1 In `RecordingService.kt` at line 375 (session start, `speedTestEngine.invalidateCache()`), replace the unconditional call with: `if (!speedTestEngine.consumePrimeFlag()) speedTestEngine.invalidateCache()` — warm handoff when a prime is available, cold start otherwise (per `service/spec.md`)
- [x] 3.2 In `RecordingService.kt` at line 419-435 (`SpeedTestRecordEntity` insert), add `finishedAt = result.finishedAt` to the constructor call
- [x] 3.3 Extend the existing `RecordingServiceTest` (instrumented) covering: session start with `primedSinceLastInvalidation = true` does NOT call `invalidateCache` (warm); session start with flag `false` DOES call `invalidateCache` (cold); `finishedAt` is persisted from the result; `finishedAt = timestamp` for SKIPPED_WIFI records

## 4. Settings — Launch Test Button, Debug Card, and Share Debug Log

- [x] 4.1 In `SettingsViewModel`, inject `SpeedTestEngine` and `SpeedTestDebugRingBuffer`; expose `debugEvents: StateFlow<List<SpeedTestDebugEvent>>` collected from the ring buffer; expose `manualLaunchState: StateFlow<ManualLaunchUiState>` (idle/running/finished with result fields)
- [x] 4.2 Add `fun launchTest(): Job` to `SettingsViewModel` that: checks `speedTestEnabled` (no-op if false), calls `speedTestEngine.reprimeServerAndGauge()`, calls `speedTestEngine.runTest(preferredServerId = config.speedTestServerId?.toIntOrNull(), uploadEnabled = config.speedTestUploadEnabled)`, updates `manualLaunchState` with the result (startedAt/finishedAt/duration/download/upload/server/error), and sets `primedSinceLastInvalidation = true` on success (the engine's success path should set this — confirm in task 2.6 that the engine sets it, not the VM)
- [x] 4.3 Add `fun shareDebugLog(context: Context): Job` to `SettingsViewModel` that serializes `ringBuffer.snapshot()` to plain text (one event per line, `HH:mm:ss.SSS [phase] status: message`) and launches an `Intent.ACTION_SEND` chooser (mirror `getLatestCrashLog` + share pattern in `SettingsScreen.kt:189-208`)
- [x] 4.4 In `SettingsScreen.kt` Speed Test card (after the existing rows, inside `if (config.speedTestEnabled)`), add a "Launch Test" `OutlinedButton` bound to `viewModel.launchTest()`; show a progress indicator while `manualLaunchState` is running
- [x] 4.5 In `SettingsScreen.kt` Speed Test card, add a debug region (collapsible `Card` or `Column` with animated visibility) that expands when `manualLaunchState` is running or finished; render a vertically-scrolling `LazyColumn` of `debugEvents` (newest at bottom, auto-scroll via `LaunchedEffect`), one row per event showing timestamp, phase, status (color-coded: green=ok, blue=info, yellow=warn, red=fail), and message
- [x] 4.6 In the debug region header, add a "Share Debug Log" `IconButton` (use `Icons.Default.Share` or `BugReport`) bound to `viewModel.shareDebugLog(context)`; show a toast "No debug events" if the ring buffer is empty
- [x] 4.7 Below the event list (or in the debug region footer), render the manual launch result summary when finished: startedAt, finishedAt, duration, download bps, upload bps, server name/host, succeeded flag, error message (if any)
- [x] 4.8 Add a `SettingsScreenTest` (instrumented Compose smoke test) covering: "Launch Test" button is rendered when `speedTestEnabled = true` and NOT rendered when false; tapping the button triggers `launchTest()`; the debug region expands when `manualLaunchState` is running; the event list renders rows from `debugEvents`; "Share Debug Log" button is present in the debug region

## 5. CSV Export — Additive finished_at Column

- [x] 5.1 In `ExportSpeedTestUseCase.exportCsv(...)`, update the header line to insert `finished_at` immediately after `timestamp` (new header: `timestamp,finished_at,download_bps,upload_bps,server_name,server_host,server_location,succeeded,error_message,data_sim_slot,rat_at_test,rsrp_at_test,band_at_test,network_type`)
- [x] 5.2 In the row-building `buildString` block, insert `append(r.finishedAt); append(',')` immediately after `append(r.timestamp); append(',')`
- [x] 5.3 Update any existing `ExportSpeedTestUseCase` test (or add one) to assert: the CSV header contains `finished_at` in the correct position; each row's second field is `r.finishedAt`; legacy rows with `finishedAt = 0` export `0` in that field; instant bail-out rows export `finishedAt = timestamp`
- [x] 5.4 If `ImportSessionUseCase` parses speedtest CSV, update it to tolerate the new column (read by header name, not by fixed index — verify the current parsing approach and adjust if it uses positional indexing)

## 6. Session Detail — Duration Badge

- [x] 6.1 In `SessionDetailScreen.kt` (or the composable that renders speedtest entries in the session detail), add a duration badge next to each speedtest entry; compute `duration = finishedAt - timestamp`; render the badge only when `finishedAt > 0 && finishedAt > timestamp` (hide for legacy rows and instant bail-outs per `sessions/spec.md`)
- [x] 6.2 Format the duration as human-readable time (e.g., "2.3s" for <10s, "12s" for <60s, "1m 5s" for ≥60s); reuse any existing duration formatter in the codebase if one exists (search before implementing)
- [x] 6.3 Add a Compose smoke test or unit test covering: badge renders for `finishedAt > timestamp > 0`; badge does NOT render for `finishedAt = 0` (legacy); badge does NOT render for `finishedAt = timestamp` (instant bail-out)

## 7. Replay — Speedtest Range Indicator on RAT Timeline

- [x] 7.1 In `ReplayScreen.kt` (or the RAT timeline composable, around line 413 where `onMarkerClick: (SpeedTestRecordEntity) -> Unit` is defined), extend the speedtest marker rendering: when `record.finishedAt > 0 && record.finishedAt > record.timestamp`, render a range indicator spanning from the start timestamp's x-position to the finish timestamp's x-position (instead of a point marker); color-code by download speed (same coloring as point markers)
- [x] 7.2 When `record.finishedAt = 0` (legacy) or `record.finishedAt = record.timestamp` (instant bail-out), render a point marker (today's behavior) — no range
- [x] 7.3 In the speedtest detail card (tapped marker), add finish time and duration fields when `finishedAt > 0 && finishedAt > timestamp`; hide those fields for legacy/instant rows
- [x] 7.4 In `ReplayViewModel` (around line 133 where `indexOfLast { it.record.timestamp <= speedRec.timestamp }` is computed), confirm the lookup still works for range indicators (the range starts at `timestamp`, so the existing lookup by start is correct; verify no off-by-one when the playback position is mid-range)
- [x] 7.5 Add a Compose smoke test or instrumented test covering: range indicator renders for `finishedAt > timestamp > 0`; point marker renders for `finishedAt = 0` and `finishedAt = timestamp`; tapping a range indicator opens the detail card with finish time and duration

## 8. Build Verification

- [x] 8.1 Run `./gradlew clean` then `./gradlew assembleDebug` to confirm a clean build succeeds
- [x] 8.2 Run `./gradlew lint` (or the project's lint command) and address any new findings introduced by this change
- [x] 8.3 Run the unit test suite (`./gradlew testDebugUnitTest`) and the instrumented test suite (`./gradlew connectedDebugAndroidTest` if a device/emulator is available) — all tests green
- [x] 8.4 Run the `code-review` subagent against the full diff: pass the proposal, design, specs, and the modified files; address any major comments and re-review until clean (per AGENTS.md "Code Working Flow")
