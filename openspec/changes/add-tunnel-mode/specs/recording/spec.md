## ADDED Requirements

### Requirement: Tunnel Recording Mode Branch

The system SHALL support a `TUNNEL` recording mode as a third arm of the recording loop in `RecordingService`, parallel to the existing `OUTDOOR` and `INDOOR` arms. Tunnel mode uses a pure time-driven ticker (`recordingIntervalMs`) and writes cell records with `locationSource = "TUNNEL"` and sentinel coordinates (`latitude = 0, longitude = 0`, null `relativeX`/`relativeY`). Tunnel mode behavior is defined in `tunnel/spec.md`.

#### Scenario: Tunnel recording starts without GPS or sensors

- GIVEN a session with `recordingMode = "TUNNEL"`
- WHEN the user starts recording
- THEN the recording service begins collecting cell data using a time-based ticker
- AND no GPS location requests are made
- AND no step/rotation sensors are registered
- AND `IndoorPositionCollector` is NOT initialized
- (Tunnel mode: `tunnel/spec.md`)

#### Scenario: Tunnel time-based recording triggers

- GIVEN an active tunnel recording
- WHEN `recordingIntervalMs` (the existing outdoor recording interval, default 5000 ms) has elapsed since the last recorded point
- THEN a new point is recorded with `locationSource = "TUNNEL"` and sentinel coordinates
- AND no distance-based trigger is used
- (Tunnel mode: `tunnel/spec.md`)

#### Scenario: Tunnel mode does not use GPS loss extrapolation

- GIVEN an active tunnel recording
- THEN GPS loss extrapolation SHALL NOT be used
- AND no fallback recording job for GPS loss detection is launched
- (Tunnel mode: `tunnel/spec.md`)
