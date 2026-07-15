## MODIFIED Requirements

### Requirement: Manual Speedtest Launch

The system SHALL provide a "Launch Test" affordance in the Settings screen that runs a single speedtest outside of a recording session for the purpose of priming the mobile connection and diagnosing engine behavior. The launch uses the same `SpeedTestEngine.runTest()` code path as the recording service.

#### Scenario: Launch Test button gated by speedtest enabled

- GIVEN the Settings screen is displayed
- WHEN `speedTestEnabled` is false
- THEN the "Launch Test" button is NOT rendered in the Speed Test card
- AND no manual speedtest can be triggered

#### Scenario: Launch Test button visible when enabled

- GIVEN the Settings screen is displayed
- WHEN `speedTestEnabled` is true (EULA previously accepted)
- THEN a "Launch Test" button is rendered inside the Speed Test card

#### Scenario: Launch Test refuses WiFi

- GIVEN the "Launch Test" button is tapped and the active network is WiFi
- WHEN the engine's WiFi-skip check fires
- THEN the test is not run
- AND the debug card displays a "SKIPPED_WIFI" status
- AND no result is persisted

#### Scenario: Launch Test re-primes server selection and gauge

- GIVEN the "Launch Test" button is tapped and the active network is cellular
- WHEN the manual launch begins
- THEN the engine calls `reprimeServerAndGauge()` which clears the cached server, cached gauge, and gauge-attempted flag
- AND the cached config is retained
- AND `runTest()` then executes with a fresh server selection and fresh gauge

#### Scenario: Launch Test respects configured server ID

- GIVEN `speedTestServerId` is set in config
- WHEN the manual launch calls `runTest()`
- THEN `preferredServerId` is passed from config
- AND the selector bypasses geographic discovery and latency pings for the configured server
- AND the manual test exercises the same server-selection code path the recording service uses

#### Scenario: Launch Test respects upload toggle

- GIVEN `speedTestUploadEnabled` is false in config
- WHEN the manual launch calls `runTest()`
- THEN `uploadEnabled = false` is passed
- AND only the download phase runs

#### Scenario: Launch Test does not persist results

- GIVEN a manual launch completes (success, partial success, or failure)
- WHEN the result is produced
- THEN the result is NOT written to `speed_test_records`
- AND no `sessionId` foreign key is violated
- AND the result is displayed only in the debug card

#### Scenario: Launch Test result displayed in debug card

- GIVEN a manual launch has completed
- WHEN the debug card is visible
- THEN the card shows: startedAt, finishedAt, duration (finishedAt − startedAt), download bps, upload bps, server name/host, per-phase result label (Success / Download only / Failed), and error message (if any)
- AND the per-phase result label is derived from `downloadSucceeded` and `uploadSucceeded`: "Success" when both are true, "Download only" when download succeeded but upload did not run or failed, "Failed" when download failed

### Requirement: Speedtest Debug Ring Buffer

The system SHALL maintain an in-memory, bounded ring buffer of structured speedtest debug events emitted by the engine at every phase and decision point. The buffer is `@Singleton`-scoped and does not survive process restart.

#### Scenario: Ring buffer structure

- GIVEN the speedtest engine is running
- WHEN a phase or decision point is reached
- THEN a `SpeedTestDebugEvent` is appended to the ring buffer with: timestampMs, phase, status, message, and optional serverId/serverHost/bytes
- AND phases are one of: `config_fetch`, `server_select`, `gauge`, `download`, `probe`, `upload`, `done`, `error`
- AND statuses are one of: `ok`, `warn`, `fail`, `info`

#### Scenario: Ring buffer bounded capacity

- GIVEN the ring buffer has reached its capacity (200 events)
- WHEN a new event is appended
- THEN the oldest event is evicted
- AND the new event is appended

#### Scenario: Ring buffer cleared on re-prime

- GIVEN the ring buffer contains events from a previous run
- WHEN `reprimeServerAndGauge()` is called for a new manual launch
- THEN the ring buffer is cleared
- AND the new launch's events start fresh

#### Scenario: Ring buffer live stream

- GIVEN the debug card is visible and a manual launch is in progress
- WHEN new events are appended to the ring buffer
- THEN the debug card updates live via a `Flow<List<SpeedTestDebugEvent>>`
- AND the list auto-scrolls to show the newest event

#### Scenario: Ring buffer mirrored to logcat

- GIVEN the engine emits a debug event
- WHEN the event status is `info` or `ok`
- THEN the engine also calls `Log.d` gated on `BuildConfig.DEBUG`
- WHEN the event status is `warn` or `fail`
- THEN the engine also calls `Log.w` unconditionally

### Requirement: Speedtest Prime State Flag

The system SHALL track an in-memory `primedSinceLastInvalidation` flag on the speedtest engine that records whether a successful manual prime has occurred since the last cache invalidation. The flag does not survive process restart.

#### Scenario: Flag set on manual success

- GIVEN a manual launch completes successfully
- WHEN the result has `downloadSucceeded = true` and `uploadSucceeded != false` (upload succeeded or was not run)
- THEN `primedSinceLastInvalidation` is set to `true`

#### Scenario: Flag cleared on invalidation

- GIVEN `invalidateCache()` is called (download failure, exception path, or session-start cold-start)
- WHEN the invalidation completes
- THEN `primedSinceLastInvalidation` is set to `false`

#### Scenario: Flag not set on manual download failure

- GIVEN a manual launch completes with `downloadSucceeded = false`
- WHEN the existing measurement-failure invalidation path fires
- THEN `primedSinceLastInvalidation` is `false`
- AND the next recording session cold-starts

#### Scenario: Flag lost on process restart

- GIVEN the app process is restarted
- WHEN the engine singleton is re-initialized
- THEN `primedSinceLastInvalidation` is `false`
- AND the next recording session cold-starts (today's behavior)
