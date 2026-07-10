## ADDED Requirements

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

- GIVEN a manual launch completes (success or failure)
- WHEN the result is produced
- THEN the result is NOT written to `speed_test_records`
- AND no `sessionId` foreign key is violated
- AND the result is displayed only in the debug card

#### Scenario: Launch Test result displayed in debug card

- GIVEN a manual launch has completed
- WHEN the debug card is visible
- THEN the card shows: startedAt, finishedAt, duration (finishedAt − startedAt), download bps, upload bps, server name/host, succeeded flag, and error message (if any)

### Requirement: Speedtest Debug Ring Buffer

The system SHALL maintain an in-memory, bounded ring buffer of structured speedtest debug events emitted by the engine at every phase and decision point. The buffer is `@Singleton`-scoped and does not survive process restart.

#### Scenario: Ring buffer structure

- GIVEN the speedtest engine is running
- WHEN a phase or decision point is reached
- THEN a `SpeedTestDebugEvent` is appended to the ring buffer with: timestampMs, phase, status, message, and optional serverId/serverHost/bytes
- AND phases are one of: `config_fetch`, `server_select`, `gauge`, `download`, `upload`, `done`, `error`
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

### Requirement: Speedtest Debug Card in Settings

The system SHALL render a dedicated debug card inside the Settings screen (within or adjacent to the Speed Test card) that displays the ring buffer events live during a manual launch and provides a "Share Debug Log" action.

#### Scenario: Debug card collapsed by default

- GIVEN the Settings screen is displayed and speedtest is enabled
- WHEN no manual launch is in progress
- THEN the debug card is collapsed (not visible or summary-only)
- AND does not dominate the Settings page

#### Scenario: Debug card expands on launch

- GIVEN the "Launch Test" button is tapped
- WHEN the manual launch begins
- THEN the debug card expands to show the live event stream
- AND the event list auto-scrolls to the newest event

#### Scenario: Share Debug Log action

- GIVEN the debug card is expanded
- WHEN the user taps the "Share Debug Log" action
- THEN the current ring buffer snapshot is serialized as plain text (one event per line, format `HH:mm:ss.SSS [phase] status: message`)
- AND an `Intent.ACTION_SEND` chooser is displayed with the text
- AND the share intent mirrors the existing "Share Crash Log" pattern

### Requirement: Speedtest Prime State Flag

The system SHALL track an in-memory `primedSinceLastInvalidation` flag on the speedtest engine that records whether a successful manual prime has occurred since the last cache invalidation. The flag does not survive process restart.

#### Scenario: Flag set on manual success

- GIVEN a manual launch completes successfully
- WHEN the result has `succeeded = true`
- THEN `primedSinceLastInvalidation` is set to `true`

#### Scenario: Flag cleared on invalidation

- GIVEN `invalidateCache()` is called (measurement failure, exception path, or session-start cold-start)
- WHEN the invalidation completes
- THEN `primedSinceLastInvalidation` is set to `false`

#### Scenario: Flag not set on manual failure

- GIVEN a manual launch completes with `succeeded = false`
- WHEN the existing measurement-failure invalidation path fires
- THEN `primedSinceLastInvalidation` is `false`
- AND the next recording session cold-starts

#### Scenario: Flag lost on process restart

- GIVEN the app process is restarted
- WHEN the engine singleton is re-initialized
- THEN `primedSinceLastInvalidation` is `false`
- AND the next recording session cold-starts (today's behavior)
