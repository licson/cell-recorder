# Background Service Specification

## Purpose

Defines the behavior of the foreground service that runs during active recordings, including notification display, lifecycle management, and required Android permissions.

## Scope

This spec covers the foreground service mechanics only. It does not define:
- The recording lifecycle or triggers (see `recording/spec.md`).
- Permission UI logic (see `permission-flow/spec.md`).
- Indoor positioning math (see `indoor/spec.md`).
- Cell identity processing (see `cell-info/spec.md`).
- Speedtest protocol (see `speedtest/spec.md`).
- Ping measurement (see `connectivity/spec.md`).
- Screen rendering (see `ui/spec.md`).

## Related Specs

- `recording/spec.md` — when and why the service starts and stops.
- `permission-flow/spec.md` — how runtime permissions are requested.
- `indoor/spec.md` — how indoor positioning replaces GPS within the service.
- `cell-info/spec.md` — how cell data is collected by the service.
- `connectivity/spec.md` — how the ping process runs within the service.
- `speedtest/spec.md` — how speedtest runs within the service.
- `thread-safety/spec.md` — concurrency rules for shared service state.
- `db-write-safety/spec.md` — database durability during service teardown.
- `process-cleanup/spec.md` — subprocess cleanup on service cancellation.
- `ui/spec.md` — how the notification tap action opens the UI.

## Requirements

### Requirement: Foreground Service

The system SHALL run recording as a foreground service with `FOREGROUND_SERVICE_TYPE_LOCATION`.

#### Scenario: Service starts
- GIVEN the user initiates a recording
- THEN a foreground service is started
- AND it runs continuously until stopped

### Requirement: Persistent Notification

The system SHALL display a persistent notification while the recording service is running, updated at a maximum frequency of 1Hz.

#### Scenario: Notification content
- GIVEN the recording service is active
- THEN a notification is shown on the `cell_recorder_channel`
- AND the notification displays elapsed time, point count, and GPS status (outdoor) or tracking confidence (indoor)

#### Scenario: Notification update rate
- GIVEN the recording service is active
- WHEN recording points are triggered
- THEN the notification is updated at most once per second from the state update job
- AND point recording does not trigger additional notification updates

#### Scenario: Notification tap action
- GIVEN the recording service notification is visible
- WHEN the user taps the notification
- THEN the MainActivity is opened

### Requirement: Notification Stop Action

The system SHALL provide a Stop action on the recording notification using `PendingIntent.getForegroundService()`.

#### Scenario: Stop via notification
- GIVEN the recording service notification is visible
- WHEN the user taps the Stop action on the notification
- THEN the recording is stopped
- AND the service terminates
- AND the stop action PendingIntent uses `getForegroundService()` instead of `getService()`

### Requirement: Service Auto-Stop

The system SHALL automatically stop the recording service under defined conditions.

#### Scenario: Auto-stop on max duration
- GIVEN the recording service is active
- WHEN the maximum recording duration is reached
- THEN the service stops itself

#### Scenario: Auto-stop on user stop
- GIVEN the recording service is active
- WHEN the user taps the Stop button on the recording screen
- THEN the service stops itself

### Requirement: Service Restart

The system SHALL use `START_STICKY` behavior and check the recording flag on restart.

#### Scenario: System kill recovery
- GIVEN the recording service was killed by the system
- WHEN the service is automatically restarted via `START_STICKY`
- THEN the service checks the recording flag
- AND does not restart recording if the flag is not set

### Requirement: Android Permissions

The system SHALL require the following Android permissions for full functionality. Runtime permission requests SHALL follow the unified permission decision logic defined in `permission-flow/spec.md`. The service does not implement its own permission UI; it delegates to the shared flow.

#### Scenario: Runtime permissions
- GIVEN the user attempts to start a recording
- WHEN a required permission is missing
- THEN the permission request is handled per `permission-flow/spec.md`
- AND recording does not start until granted

#### Scenario: Coarse location declared alongside fine
- GIVEN the app's AndroidManifest.xml
- THEN `ACCESS_COARSE_LOCATION` is declared as a `<uses-permission>` element
- AND it is declared alongside `ACCESS_FINE_LOCATION` (required on Android 12+ so the system can offer a coarse-only grant)

#### Scenario: Background location
- GIVEN the user has granted fine location
- WHEN `ACCESS_BACKGROUND_LOCATION` is not granted
- THEN a permission request is shown (API 29+) per `permission-flow/spec.md`

#### Scenario: Phone state
- GIVEN the user has an active recording
- WHEN `READ_PHONE_STATE` is granted
- THEN SIM subscription details are available for multi-SIM recording
- (Multi-SIM recording: `recording/spec.md`)

#### Scenario: Network state
- GIVEN the app's AndroidManifest.xml
- THEN `ACCESS_NETWORK_STATE` is declared as a `<uses-permission>` element
- AND the speedtest Wi-Fi availability check is permitted without a runtime request (normal permission)
- (Speedtest Wi-Fi skip: `speedtest/spec.md`)

#### Scenario: Notifications permission (API 33+)
- GIVEN the user starts recording on API 33+
- WHEN `POST_NOTIFICATIONS` is not granted
- THEN a permission request is shown per `permission-flow/spec.md`

#### Scenario: Activity recognition (API 29+)
- GIVEN the user starts an indoor recording on API 29+
- WHEN `ACTIVITY_RECOGNITION` is not granted
- THEN a permission request is shown per `permission-flow/spec.md`
- AND indoor recording does not start until granted
- (Indoor permission requirements: `indoor/spec.md`)

### Requirement: Speedtest in Notification

The system SHALL include optional speedtest status in the recording notification when speedtest is enabled.

#### Scenario: Speedtest status in notification
- GIVEN an active recording with speedtest enabled
- THEN the notification displays the current speedtest status ("Running", "Completed", "Failed")
- AND the notification is updated at the standard 1Hz rate from the state update job

### Requirement: Indoor Mode Service Behavior

The system SHALL adapt the foreground service behavior for indoor recording mode. Indoor positioning details are defined in `indoor/spec.md`; recording lifecycle is defined in `recording/spec.md`.

#### Scenario: Indoor recording starts without GPS
- GIVEN a session with `recordingMode = "INDOOR"`
- WHEN the recording service starts
- THEN the service uses `FOREGROUND_SERVICE_TYPE_LOCATION` (required for cell info access on Android 11+)
- AND no GPS location requests are made
- AND `IndoorPositionCollector` is initialized instead of `LocationCollector`
- AND the fallback recording job (GPS loss detection) is NOT launched
- (Indoor positioning: `indoor/spec.md`; recording lifecycle: `recording/spec.md`)

#### Scenario: Indoor notification content
- GIVEN an active indoor recording
- THEN the notification displays elapsed time, point count, and indoor tracking status instead of GPS status
- AND the notification indicates the current drift level (Confident / Degrading / High drift)
- (Drift levels: `indoor/spec.md`)

#### Scenario: Indoor recording time-based triggers
- GIVEN an active indoor recording
- WHEN `indoorRecordingIntervalMs` has elapsed since the last recorded point
- THEN a point is recorded with the current indoor position
- AND no distance-based trigger is used
- (Indoor interval config: `indoor/spec.md`)

### Requirement: Sensor Registration Verification

The system SHALL verify that sensor registration succeeds before continuing with indoor recording. See `indoor/spec.md` for step-detector and accelerometer fallback registration rules.

#### Scenario: Verify sensor registration success
- GIVEN an indoor recording is started
- WHEN `registerListener()` is called for step detection or accelerometer sensors
- THEN the return value of each `registerListener()` call is captured into a stored boolean
- AND `isAnyStepDetectionActive()` returns true only when at least one step source actually registered successfully
- AND if all sensor registrations fail, indoor recording is aborted with an error
- (Full sensor registration requirements: `indoor/spec.md`)

### Requirement: Sensor Health Monitoring

The system SHALL monitor step detection activity during indoor recording and provide feedback when no steps are detected. See `indoor/spec.md` for the step-detection algorithm and `ui/spec.md` for the warning UI.

#### Scenario: No steps detected within 10 seconds
- GIVEN an active indoor recording
- WHEN no step events have been received for more than 10 seconds
- THEN a sensor health warning is emitted: "No steps detected. Try moving the phone to your pocket."
- AND the tracking confidence indicator shows "No steps"
- (Step-detection logic: `indoor/spec.md`; UI warning display: `ui/spec.md`)

#### Scenario: Steps resume after warning
- GIVEN a sensor health warning is displayed
- WHEN step events resume
- THEN the sensor health warning is cleared
- AND the tracking confidence indicator returns to its normal drift-based state
- (Drift-based confidence: `indoor/spec.md`)