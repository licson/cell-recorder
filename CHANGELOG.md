# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-06-04

### Added

- Carrier Aggregation (4G_CA) support: logs all secondary connected LTE bands with EARFCN, PCI, and signal metrics
- New `cell_record_ca_bands` child table in Room (FK with CASCADE delete)
- CA bands displayed live on the Live Info screen per SIM
- CA bands included in Band Distribution analytics and statistics charts
- CA bands exported in CSV (`ca_bands` JSON column) and GeoJSON (`caBands` array)
- CA bands parsed on import for round-trip fidelity
- Room migration 6→7 with `MIGRATION_6_7`
- Accelerometer-based speed adjustment during GPS loss: sensor fusion now subscribes to `TYPE_LINEAR_ACCELERATION`, transforms acceleration to world frame, and projects onto the heading direction to estimate speed changes; adjustment decays exponentially (τ=10s) and clamps at ±50% of initial GPS speed to limit drift

### Changed

- Data collector now extracts secondary cells (`SecondaryConnection`) in addition to the primary serving cell
- Session Detail and Replay use `CellRecordWithCaBands` wrapper for full cell data
- `SensorFusionCollector.start()` now accepts `bearing` and `speedMps` parameters to initialize accelerometer processing

### Fixed

- Flipped heading direction in dead reckoning: sensor yaw now correctly follows clockwise-positive (navigational) convention instead of counterclockwise-positive (Euler)

## [1.0.3] - 2026-06-03

### Added

- Anomaly inspector bottom sheet with filter chips and lazy virtualization
- Group repeated anomaly messages into duration-aware anomalies (peak latency, episode duration)

### Fixed

- FAB position offset from corner
- Extra left padding in landscape mode
- Double bottom padding on all screens

### Changed

- Improved UI performance: lifecycle-aware state collection, off-main-thread dispatchers, reduced recompositions

## [1.0.2] - 2026-06-03

### Fixed

- Fix invalid null looper crash during recording

## [1.0.1] - 2026-06-03

### Fixed

- Fix git hash resolution in ProcessBuilder (missing working directory)

## [1.0.0] - 2026-06-03

### Added

- Initial release
- Real-time cell tower recording (signal strength, Cell ID, RAT, frequency bands)
- GPS coordinate logging during recording sessions
- Background recording via foreground service
- Session management with custom names
- CSV and GeoJSON export
- Session replay on interactive OpenStreetMap
- In-app crash logger with device info capture
- About screen showing version, git hash, source link, and pre-filled GitHub issue reporting