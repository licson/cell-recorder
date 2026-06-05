# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-06-05

### Added

- Carrier Aggregation support: secondary LTE bands are now recorded alongside the primary band
- Live Info screen now shows secondary (CA) bands in real time per SIM
- Band Distribution charts now include secondary CA bands
- CSV and GeoJSON exports now include secondary CA bands
- Re-imported sessions correctly restore CA band data
- When GPS signal is lost during recording, the app now estimates your speed using the accelerometer to keep your position on track until GPS returns
- 5G NSA (Non-Standalone) recording support: when on a 5G NSA network, the app now correctly records the NR cell (band, PCI, signal) and saves the underlying LTE anchor cell's details alongside it
- Replay and session detail screens now show the LTE anchor's band, PCI, and signal strength for 5G NSA records
- Live Info screen now shows the LTE anchor cell info when connected via 5G NSA
- 5G NSA recordings now include carrier aggregation bands from the LTE anchor

### Fixed

- Fixed direction tracking during GPS loss — movement now follows the correct heading
- Fixed 5G NSA recordings being incorrectly labeled as "4G" — now correctly recorded as "5G_NSA"
- Fixed NR cell data (PCI, RSRP, RSRQ, SINR, band) being lost during 5G NSA sessions
- Analytics "Massive MIMO Candidate" insight now includes 5G NSA handoff events alongside 5G SA

### Changed

- CSV and GeoJSON export now include anchor cell columns for 5G NSA records (prefixed with `anchor_`)
- Room database schema updated to v8 (automatic migration, no data loss)

## [1.0.3] - 2026-06-03

### Added

- Added filter chips to the anomaly inspector for quicker issue navigation
- Repeated anomaly messages are now grouped, showing how long each issue lasted

### Fixed

- Fixed floating action button position
- Fixed extra spacing in landscape mode
- Fixed duplicate bottom spacing on all screens

### Changed

- Improved overall UI smoothness and responsiveness

## [1.0.2] - 2026-06-03

### Fixed

- Fixed a crash that could occur while recording

## [1.0.1] - 2026-06-03

### Fixed

- Fixed version info display on the About screen

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