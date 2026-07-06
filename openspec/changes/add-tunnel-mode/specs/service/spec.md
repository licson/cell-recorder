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
- AND the notification includes a "Mark Note" action button (per `markers/spec.md`) that broadcasts an intent to create a new `NOTE` marker

#### Scenario: Tunnel recording time-based triggers

- GIVEN an active tunnel recording
- WHEN `recordingIntervalMs` has elapsed since the last recorded point
- THEN a point is recorded with sentinel coordinates and `locationSource = "TUNNEL"`
- AND no distance-based trigger is used
- (Tunnel mode: `tunnel/spec.md`)

### Requirement: Universal "Mark Note" Notification Action

The system SHALL include a "Mark Note" action button on the foreground service notification for every recording mode (OUTDOOR, INDOOR, TUNNEL), allowing the user to drop a `NOTE` marker directly from the lock screen without unlocking the device or navigating back to the app. Marker behavior is defined in `markers/spec.md`.

#### Scenario: Mark Note action present for every recording mode

- GIVEN an active recording (any mode)
- WHEN the foreground notification is displayed
- THEN the notification includes a "Mark Note" action button
- AND tapping the action creates a new `NOTE` marker via `SessionMarkerRepository.insertMarker(...)` with an auto-generated label `"NOTE #<seq> HH:MM:SS"`
- AND the action uses an immutable `PendingIntent` with `FLAG_IMMUTABLE`
- AND the user is not required to open the app or unlock the screen

#### Scenario: Mark Note action serialized with recording tick

- GIVEN an active recording (any mode)
- WHEN the "Mark Note" action fires at the same instant a recording tick fires
- THEN both writes are serialized through the recording mutex
- AND the marker's `timestamp` reflects the wall-clock at lock acquisition
- (Concurrency rules: `thread-safety/spec.md`; Marker creation: `markers/spec.md`)
