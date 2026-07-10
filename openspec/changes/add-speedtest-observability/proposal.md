## Why

Speedtest results today record only a start timestamp; the finish time is captured implicitly in `RecordingService` (line 438) but thrown away. When the speedtest engine "gets stuck" — unable to connect, server selection failure — the user has no on-device diagnostics: the engine's silent exit paths (config fetch, server selection, exceptions) emit no logs visible to the user, and there is no way to prime the mobile connection outside of starting a recording session. We need to (1) persist both start and finish times per speedtest, (2) add a manual "Launch Test" button in Settings to prime the connection and diagnose stuck states, and (3) surface structured debug events in a dedicated card in the settings page.

## What Changes

- Add `startedAt`/`finishedAt` fields to `SpeedTestResult` (engine-owned timing); `finishedAt = startedAt` for instant bail-out paths (SKIPPED_WIFI, config/selection failure, exception).
- Add a `finishedAt` column to `speed_test_records` via an additive Room migration (`MIGRATION_14_15`); keep existing `timestamp` as the start time.
- Persist `finishedAt` from `RecordingService` when inserting the `SpeedTestRecordEntity`.
- Add an additive `finished_at` column to the speedtest CSV export (`ExportSpeedTestUseCase`); keep the existing `timestamp` header.
- Show a "Duration" badge per speedtest entry in Session Detail, and a range indicator on the Replay RAT timeline (start → finish) when `finishedAt > startedAt`.
- Add a `SpeedTestDebugRingBuffer` (`@Singleton`, in-memory, last N structured events) that the engine emits to at every phase/decision point; also mirror to `Log.*` for adb users.
- Add a "Launch Test" button to the Settings screen (Speed Test card), gated by `speedTestEnabled` and refused on WiFi (mirrors the service's existing guards); does NOT persist results (the `sessionId` foreign key is non-null).
- The manual launch re-primes server selection + gauge by calling a new `SpeedTestEngine.reprimeServerAndGauge()` that clears `cachedServer`, `cachedGaugeBps`, and `gaugeAttempted` (keeps `cachedConfig`); respects `preferredServerId` from config; respects `speedTestUploadEnabled`.
- On manual success, set an in-memory `@Volatile primedSinceLastInvalidation = true` flag on the engine; on failure, the existing measurement-failure invalidation path fires (auto-invalidate).
- Change `RecordingService` (line 375) to conditionally invalidate the cache at session start: keep cache if a manual prime has succeeded since the last invalidation, else `invalidateCache()` (cold start).
- Add a dedicated debug card accessible inside the Settings page (popup/card) showing the ring buffer events live during a manual launch, plus a "Share Debug Log" action that bundles the ring buffer as text (mirrors the existing "Share Crash Log" pattern).

## Capabilities

### New Capabilities
- `speedtest-diagnostics`: Manual "Launch Test" affordance outside of recording, in-memory debug ring buffer lifecycle, structured phase/decision event emission, the in-settings debug card/popup surface, and the "Share Debug Log" export. Covers the priming lifecycle (`reprimeServerAndGauge`), the in-memory `primedSinceLastInvalidation` flag, and the conditional cache handoff contract between manual launch and recording sessions.

### Modified Capabilities
- `speedtest`: `SpeedTestResult` gains `startedAt`/`finishedAt`; cache lifecycle gains `reprimeServerAndGauge()` and the `primedSinceLastInvalidation` flag.
- `recording`: `SpeedTestRecordEntity` gains `finishedAt` (additive column, non-null with `= startedAt` for instant bail-outs); `RecordingService` persists it.
- `sessions`: Session Detail shows a duration badge per speedtest entry; Replay shows a range indicator on the RAT timeline per speedtest (start → finish).
- `data`: Speedtest CSV export gains an additive `finished_at` column.
- `ui`: Settings screen gains the "Launch Test" button (under Speed Test card) and the debug card/popup surface with "Share Debug Log".
- `service`: `RecordingService` conditionally invalidates the engine cache at session start based on the `primedSinceLastInvalidation` flag.

## Impact

- **Database**: One additive migration (`MIGRATION_14_15`) adds `finishedAt INTEGER NOT NULL DEFAULT 0` to `speed_test_records`; no `timestamp` rename, no DAO query changes, no index rebuild. Bumps `@Database(version = 15)`.
- **Domain**: `SpeedTestResult` gains two `Long` fields; `SpeedTestEngine` gains `reprimeServerAndGauge()`, a `@Volatile primedSinceLastInvalidation` flag, and structured emit hooks to the new `SpeedTestDebugRingBuffer`. Existing engine tests assert timing invariants on the new fields.
- **Service**: `RecordingService` speedtest job (line 375) changes from unconditional `invalidateCache()` to conditional based on the prime flag; line 419 `SpeedTestRecordEntity` insert populates `finishedAt`.
- **UI**: `SettingsScreen` Speed Test card grows a "Launch Test" button + debug card/popup; `SessionDetailScreen` speedtest entry grows a duration badge; `ReplayScreen` RAT timeline grows a range indicator. No new screens.
- **Data**: `ExportSpeedTestUseCase` appends `finished_at` after `timestamp` in the CSV header; row bodies follow.
- **Analytics**: `SpeedTestAnalyticsEngine` does NOT consume `finishedAt` in this change (duration is not yet a recorded dimension); flagged for future investigation.
- **Specs**: New `specs/speedtest-diagnostics/spec.md`; delta specs for `speedtest`, `recording`, `sessions`, `data`, `ui`, `service`.
- **Persistence**: Manual test results are NOT persisted; the `sessionId` foreign key on `speed_test_records` is non-null, and we do not create a sentinel session.
- **Permissions**: No new permissions — the manual launch uses the existing `speedTestEnabled` gate (EULA-accepted) and the existing WiFi-skip check.
