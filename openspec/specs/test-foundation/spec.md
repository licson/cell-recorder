# Test Foundation Specification

## Purpose

Defines the coverage contract for the JVM unit test layer (`app/src/test/`): pure-logic domain components, extensions to existing tests for documented behavioral gaps, and extraction of pure logic from Android-coupled classes so that logic can be unit-tested without a device. Ensures pure-logic regressions are caught at the JVM level and that behavior-preserving refactors are guarded by characterization tests.

## Requirements

### Requirement: Pure-logic domain components SHALL have unit tests

The system SHALL provide JVM unit tests (under `app/src/test/`) covering the public API of every pure-logic domain component — components whose logic has no Android framework dependency and no I/O side effect. The tests SHALL execute via `./gradlew test` without a device or emulator.

The following components are in scope:
- `domain/model/BandResolver` — band number resolution and `formatBand` prefix selection
- `domain/speedtest/SpeedTestConfigParser` — speedtest-config XML parsing
- `domain/speedtest/SpeedTestServerSelector` — haversine distance and server XML element parsing (pure helpers only)
- `domain/speedtest/SpeedTestAnalyticsEngine` — download/upload percentiles and correlation binning
- `domain/usecase/import_/CsvRecordParser` — CSV line splitting, column mapping, CA-band JSON parsing
- `domain/usecase/import_/GeoJsonRecordParser` — GeoJSON FeatureCollection parsing, dual-key tolerance, geometry validation
- `service/GpsStateMachine` — fix-lost detection, settling window, extrapolation age, estimated accuracy (pure state-machine logic)

#### Scenario: BandResolver unit tests exist and pass
- **WHEN** `./gradlew test` is run
- **THEN** tests under `app/src/test/java/com/cellrecorder/app/domain/model/BandResolverTest.kt` execute
- **AND** they assert `formatBand` chooses `n` prefix for 5G RATs with `earfcn >= 82000` or `null earfcn`, and `B` prefix otherwise
- **AND** they assert `fallbackNrBand` maps each of the five NR EARFCN ranges to its band number, and returns `null` outside all ranges
- **AND** they assert `mapEarfcn` dispatches to `BandTableLte`, `BandTableNr` (with fallback), or `BandTableWcdma` based on RAT prefix

#### Scenario: Import parsers handle malformed input
- **WHEN** `CsvRecordParser` is given a row with missing required columns
- **THEN** the parser collects the row-level error without throwing
- **AND** the test asserts the error list is non-empty
- **WHEN** `GeoJsonRecordParser` is given a Feature with non-Point geometry or missing timestamp
- **THEN** the parser returns null or an error for that feature
- **AND** valid sibling features are still parsed

#### Scenario: GpsStateMachine state transitions are exercised
- **WHEN** the machine receives a fix after `gpsLostAtMs` is set
- **THEN** `isFixLost()` returns false and `extrapolationAgeSec()` returns zero
- **WHEN** the machine is within the settling period
- **THEN** `isFixLost()` returns false even if no fresh fix has arrived
- **AND** `isInSettling()` returns true until the settling window elapses

### Requirement: Existing unit tests SHALL be extended to close documented gaps

The system SHALL extend the existing unit test files to cover behavioral branches that are currently untested:

- `SessionAnalyticsEngineTest` SHALL add assertions for `generatePciInsights` (Massive MIMO Candidate, Load Balancing, Cross-Site Handoff Impact cards), `computeCorrelation` output bins, `classifyMobility` branch labels (STATIONARY, WALKING, DRIVING, TUNNEL, INDOOR), indoor recording mode (disables handoffs, returns single INDOOR mobility segment), anomaly severity levels, and `LatencyStats.jitterMs`/`stddev`.
- `ExportSessionUseCaseTest` SHALL add tests for the indoor recording mode branch (both CSV and GeoJSON), `csvField()` escaping with commas/quotes/newlines/carriage returns, and presence of `isLocationEstimated`/`locationSource`/`anchor*` fields in output.
- `PingEngineTest` SHALL add tests that invoke `pingFlow()` with a mocked `Process` and assert emitted `PingResult` items, process-restart on `null` line, and `awaitClose` cleanup.
- `PermissionHelperTest` SHALL add tests for `foregroundPermissions`, `indoorPermissions`, `requiredPermissions` (with `Build.VERSION.SDK_INT` mocked at API 29 and API 30+), `allGranted`, `allGrantedForMode`, `allForegroundGranted`, `allBackgroundGranted`, `allIndoorGranted`, `missingForegroundPermissions`, `missingIndoorPermissions`, `missingBackgroundPermissions`, `missingPermissionsForMode`, `missingAllForMode`.

#### Scenario: SessionAnalyticsEngine indoor mode is tested
- **WHEN** `analyze()` is called with `recordingMode = "INDOOR"`
- **THEN** handoff events are empty
- **AND** mobility segments contain exactly one segment classified as INDOOR
- **AND** the test asserts the segment label directly rather than only "segments is not empty"

#### Scenario: PingEngine pingFlow emits parsed results
- **WHEN** `pingFlow()` is collected and the mocked `Process` produces lines `64 bytes from ...: icmp_seq=0 ttl=64 time=12.3 ms`
- **THEN** the flow emits a `PingResult` with `outcome = SUCCESS`, `latencyMs = 12.3f`, `seq = 0`
- **WHEN** the mocked `Process` produces a `null` line (EOF)
- **THEN** the flow restarts the process rather than completing

#### Scenario: PermissionHelper version-gated methods are tested across SDK levels
- **WHEN** `Build.VERSION.SDK_INT` is mocked to 29
- **THEN** `backgroundPermissions()` returns an empty array
- **WHEN** `Build.VERSION.SDK_INT` is mocked to 30
- **THEN** `backgroundPermissions()` returns `android.permission.ACCESS_BACKGROUND_LOCATION`

### Requirement: Pure logic SHALL be extracted from Android-coupled classes to enable unit testing

The system SHALL extract pure-logic helpers from Android-coupled classes into testable `object` singletons or top-level functions. The original class SHALL delegate to the extracted helper. The extraction SHALL NOT change the observable behavior or public API of the original class.

The following extractions are in scope:
- `SimLiveStateMapper` — extracted from the duplicated `CellRecordSnapshot → SimLiveState` population logic in `RecordingViewModel` and `LiveInfoViewModel`. Both ViewModels SHALL call the mapper.
- `PointRecorder` indoor discontinuity shift math — the `ArrayDeque<Int>` index shift when `_recordedPath` overflows `MAX_PATH_SIZE`.
- `SensorFusionCollector` heading-smoothing and speed-delta math — the `0.85/0.15` exponential, 360° wrap, `tau = 10s` decay, and `±0.5×initialSpeed` clamp.
- `IndoorPositionCollector` step detection — accelerometer fallback `STEP_THRESHOLD = 1.15×gravity`, 20-sample baseline calibration, 350ms cooldown, and drift rate `0.02 + elapsedMin * 0.004` (max 0.20).
- `RecordingService` `movePoint()` and `calculateDistance()` — haversine extrapolation and distance.

#### Scenario: SimLiveStateMapper is shared by both ViewModels
- **WHEN** `RecordingViewModel` populates `SimLiveState` from a `CellRecordSnapshot`
- **THEN** it delegates to `SimLiveStateMapper.map(snapshot, simSlotIndex, plmn)`
- **WHEN** `LiveInfoViewModel` populates `SimLiveState` from the same snapshot
- **THEN** it delegates to the same `SimLiveStateMapper.map(...)` call
- **AND** both produce identical `SimLiveState` values for identical inputs

#### Scenario: PointRecorder indoor discontinuity shift is correct
- **WHEN** the recorded path exceeds `MAX_PATH_SIZE` and a discontinuity exists at index 0
- **THEN** the discontinuity at index 0 is removed
- **AND** all remaining discontinuity indices are decremented by 1
- **AND** the extracted helper produces this result as a pure function of the input deque

#### Scenario: SensorFusion heading smoothing wraps at 360 degrees
- **WHEN** the raw heading delta jumps from 350° to 10°
- **THEN** the smoothed delta accounts for the 360° wrap
- **AND** the extracted helper produces the same output as the inline implementation for the same inputs

#### Scenario: RecordingService movePoint extrapolates via haversine
- **WHEN** `movePoint(lat, lon, bearingDeg, distanceM)` is called
- **THEN** the result is the haversine-extrapolated lat/lon
- **AND** the extracted helper produces this result as a pure function of its four arguments

### Requirement: Behavior-preserving extraction SHALL be verified by characterization tests

Before any extraction in the previous requirement is performed, the system SHALL capture the current behavior of the original class method via a characterization test (golden-master or input/output snapshot). After extraction, the same test SHALL pass against the original class (now delegating to the helper) AND against the extracted helper directly.

#### Scenario: Characterization test guards SimLiveStateMapper extraction
- **WHEN** a `CellRecordSnapshot` with 5G NSA anchor fields and two CA bands is mapped to `SimLiveState`
- **THEN** the pre-extraction test captures the expected `SimLiveState` field values
- **AND** the post-extraction test against `RecordingViewModel.populate` output produces the same values
- **AND** the post-extraction test against `SimLiveStateMapper.map` directly produces the same values

#### Scenario: Characterization test guards PointRecorder extraction
- **WHEN** a path with three discontinuities overflows `MAX_PATH_SIZE`
- **THEN** the pre-extraction test captures the resulting discontinuity deque
- **AND** the post-extraction test against the extracted shift helper produces the same deque for the same input
