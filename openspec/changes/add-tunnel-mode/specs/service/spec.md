## ADDED Requirements

### Requirement: Tunnel Mode Service Behavior

The system SHALL adapt the foreground service behavior for tunnel recording mode. Tunnel mode uses `FOREGROUND_SERVICE_TYPE_LOCATION` (required for cell info access on Android 11+) but does not register any location listener, step detector, accelerometer, or rotation sensor. Tunnel mode behavior is defined in `tunnel/spec.md`.

#### Scenario: Tunnel recording starts without GPS or sensors

- GIVEN a session with `recordingMode = "TUNNEL"`
- WHEN the recording service starts
- THEN the service uses `FOREGROUND_SERVICE_TYPE_LOCATION` (required for cell info access on Android 11+)
- AND no GPS location requests are made
- AND `IndoorPositionCollector` is NOT initialized
- AND no step detector, accelerometer, or rotation sensor is registered
- AND the fallback recording job (GPS loss detection) is NOT launched
- (Tunnel mode: `tunnel/spec.md`; recording lifecycle: `recording/spec.md`)

#### Scenario: Tunnel notification content

- GIVEN an active tunnel recording
- THEN the notification displays elapsed time, point count, and marker count (e.g., "Tunnel recording — 5 markers")
- AND the notification does not display GPS status (irrelevant in tunnel mode)
- AND the notification does not display indoor tracking confidence (irrelevant in tunnel mode)
- AND the notification includes a "Mark Note" action button that broadcasts an intent to create a new `NOTE` marker

#### Scenario: Tunnel recording time-based triggers

- GIVEN an active tunnel recording
- WHEN `recordingIntervalMs` has elapsed since the last recorded point
- THEN a point is recorded with sentinel coordinates and `locationSource = "TUNNEL"`
- AND no distance-based trigger is used
- (Tunnel mode: `tunnel/spec.md`)
