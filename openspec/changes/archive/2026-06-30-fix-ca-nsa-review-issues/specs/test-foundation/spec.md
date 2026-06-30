## MODIFIED Requirements

### Requirement: Pure-logic domain components SHALL have unit tests

The system SHALL provide JVM unit tests (under `app/src/test/`) covering the public API of every pure-logic domain component — components whose logic has no Android framework dependency and no I/O side effect. The tests SHALL execute via `./gradlew test` without a device or emulator. The `CellInfoCollector` is considered pure-logic because its single framework dependency (`INetMonster`) is an interface that can be mocked or faked.

The following components are in scope:
- `domain/model/BandResolver` — band number resolution and `formatBand` prefix selection
- `domain/speedtest/SpeedTestConfigParser` — speedtest-config XML parsing
- `domain/speedtest/SpeedTestServerSelector` — haversine distance and server XML element parsing (pure helpers only)
- `domain/speedtest/SpeedTestAnalyticsEngine` — download/upload percentiles and correlation binning
- `domain/usecase/import_/CsvRecordParser` — CSV line splitting, column mapping, CA-band JSON parsing
- `domain/usecase/import_/GeoJsonRecordParser` — GeoJSON FeatureCollection parsing, dual-key tolerance, geometry validation
- `service/GpsStateMachine` — fix-lost detection, settling window, extrapolation age, estimated accuracy (pure state-machine logic)
- `service/CellInfoCollector` — 5G NSA logic, fallback handling, LTE CA extraction, and cell ID splitting

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

#### Scenario: CellInfoCollector NSA fallback and CA extraction are verified
- **WHEN** `CellInfoCollector` processes an NSA network type with no NR cell but an LTE anchor
- **THEN** it produces a `4G` or `4G_CA` record with full LTE details
- **WHEN** it processes an LTE cell with secondary cells
- **THEN** it correctly extracts `CaBandSnapshot` lists with bandwidth and assigns `4G_CA`
