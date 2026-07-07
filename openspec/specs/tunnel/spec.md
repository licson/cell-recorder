# Tunnel Specification

## Purpose

Linear, time-driven recording mode for cellular coverage mapping inside metro tunnels. The phone samples on a fixed time cadence and the user manually stamps the timeline with landmarks (stations, tunnel entry/exit) so the recorded samples can later be mapped to known tunnel geometry. Tunnel mode does not require GPS or step/rotation sensors.

## Requirements

## ADDED Requirements

### Requirement: Tunnel Recording Mode

The system SHALL support a `TUNNEL` recording mode that samples cell information and ping measurements on a pure time-driven cadence, without any GPS location requests or step/rotation sensor registration. Tunnel mode is intended for linear coverage mapping inside metro tunnels where the rider is stationary relative to the moving vehicle. Recording lifecycle is defined in `recording/spec.md`; foreground service mechanics in `service/spec.md`; marker behavior in `markers/spec.md`; export/import formats in `data/spec.md`.

#### Scenario: Tunnel recording lifecycle

- GIVEN a session with `recordingMode = "TUNNEL"`
- WHEN the user starts recording
- THEN a foreground service is started
- AND the service begins collecting cell data using time-based triggers
- AND no GPS location requests are made
- AND no step/rotation sensors are registered
- AND `IndoorPositionCollector` is NOT initialized
- (Recording lifecycle: `recording/spec.md`; service mechanics: `service/spec.md`)

#### Scenario: Tunnel cell record sentinel coordinates

- GIVEN an active tunnel recording
- WHEN a recording point is triggered
- THEN a `CellRecordEntity` row is written with `latitude = 0.0`, `longitude = 0.0`, `altitude = 0.0`, `accuracy = 0f`
- AND `relativeX` and `relativeY` are NULL (tunnel records are temporal, not spatial)
- AND `locationSource = "TUNNEL"` and `isLocationEstimated = false`
- AND the row is distinguished from indoor records (which have non-null `relativeX`/`relativeY` and `locationSource = "INDOOR_IMU"`) and outdoor records (which have non-sentinel `latitude`/`longitude` and `locationSource` in `{"GPS", "SENSOR_FUSION"}`)

#### Scenario: Tunnel mode sampling interval

- GIVEN an active tunnel recording
- WHEN `recordingIntervalMs` (the existing outdoor recording interval, default 5000 ms) has elapsed since the last recorded point
- THEN a new point is recorded
- AND no distance-based trigger and no `locationChangeThresholdM` check applies

#### Scenario: Tunnel mode multi-SIM recording

- GIVEN an active tunnel recording with multiple active SIM slots
- WHEN a recording tick fires
- THEN one `CellRecordEntity` row is created per SIM with visible cells
- AND all rows are written in a single batched database transaction
- (Multi-SIM recording rules: `recording/spec.md`)

#### Scenario: Tunnel mode max recording duration

- GIVEN an active tunnel recording
- WHEN the elapsed time reaches `maxRecordingDurationMin`
- THEN the recording is stopped automatically
- (Max recording duration: `recording/spec.md`)

### Requirement: Tunnel Mode Permissions

The system SHALL require neither `ACTIVITY_RECOGNITION` nor background-location stalking for tunnel mode. Tunnel mode requires only the foreground service permission, phone-state permission for cell info, and notifications permission for the persistent notification. Permission flow logic is defined in `permission-flow/spec.md`.

#### Scenario: Tunnel mode does not require ACTIVITY_RECOGNITION

- GIVEN the user attempts to start a tunnel recording
- WHEN `ACTIVITY_RECOGNITION` is not granted
- THEN tunnel recording SHALL start
- AND no `ACTIVITY_RECOGNITION` request is shown for tunnel mode

#### Scenario: Tunnel mode does not require GPS

- GIVEN the user attempts to start a tunnel recording
- THEN no `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, or `ACCESS_BACKGROUND_LOCATION` runtime request is shown for tunnel mode
- AND recording proceeds with no GPS listener registered
- (Permission decision logic: `permission-flow/spec.md`)

### Requirement: Tunnel Session Export and Import

The system SHALL export and import tunnel sessions, distinguishing them from outdoor and indoor sessions via a `"tunnelMode": true` session-level flag in GeoJSON and via sentinel coordinates (`latitude = 0, longitude = 0`) with `locationSource = "TUNNEL"` in CSV. Marker export/import is defined in `markers/spec.md`; export formats in `data/spec.md`.

#### Scenario: Tunnel GeoJSON export includes tunnelMode flag

- GIVEN a tunnel session with recorded points
- WHEN the user exports the session to GeoJSON
- THEN the FeatureCollection includes a `"tunnelMode": true` session-level property
- AND the FeatureCollection does NOT include `"indoorMode": true`
- AND each cell record Feature's geometry coordinates are sentinel `[0, 0]` (since tunnel records have `latitude = 0, longitude = 0`)

#### Scenario: Tunnel CSV import sets recording mode

- GIVEN the import dialog is open
- WHEN the user selects a cell-records CSV where any row has `location_source = "TUNNEL"`
- THEN the imported session's `recordingMode` is set to `"TUNNEL"`
- (Markers companion detection: `markers/spec.md`)

#### Scenario: Tunnel GeoJSON import sets recording mode

- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file with `"tunnelMode": true`
- THEN the imported session's `recordingMode` is set to `"TUNNEL"`
