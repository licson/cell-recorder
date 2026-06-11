## ADDED Requirements

### Requirement: Indoor Mode Service Behavior

The system SHALL adapt the foreground service behavior for indoor recording mode.

#### Scenario: Indoor recording starts without GPS
- GIVEN a session with `recordingMode = "INDOOR"`
- WHEN the recording service starts
- THEN the service uses `FOREGROUND_SERVICE_TYPE_LOCATION` (required for cell info access on Android 11+)
- AND no GPS location requests are made
- AND `IndoorPositionCollector` is initialized instead of `LocationCollector`
- AND the fallback recording job (GPS loss detection) is NOT launched

#### Scenario: Indoor notification content
- GIVEN an active indoor recording
- THEN the notification displays elapsed time, point count, and indoor tracking status instead of GPS status
- AND the notification indicates the current drift level (Confident / Degrading / High drift)

#### Scenario: Indoor recording time-based triggers
- GIVEN an active indoor recording
- WHEN `indoorRecordingIntervalMs` has elapsed since the last recorded point
- THEN a point is recorded with the current indoor position
- AND no distance-based trigger is used
