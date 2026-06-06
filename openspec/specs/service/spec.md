# Background Service Specification

## Purpose

Defines the behavior of the foreground service that runs during active recordings, including notification display, lifecycle management, and required Android permissions.

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
- AND the notification displays elapsed time, point count, and GPS status

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

The system SHALL provide a Stop action on the recording notification.

#### Scenario: Stop via notification
- GIVEN the recording service notification is visible
- WHEN the user taps the Stop action on the notification
- THEN the recording is stopped
- AND the service terminates

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

The system SHALL require the following Android permissions for full functionality.

#### Scenario: Runtime permissions
- GIVEN the user attempts to start a recording
- WHEN `ACCESS_FINE_LOCATION` is not granted
- THEN a permission request is shown
- AND recording does not start until granted

#### Scenario: Background location
- GIVEN the user has granted fine location
- WHEN `ACCESS_BACKGROUND_LOCATION` is not granted
- THEN a permission request is shown (API 29+)

#### Scenario: Phone state
- GIVEN the user has an active recording
- WHEN `READ_PHONE_STATE` is granted
- THEN SIM subscription details are available for multi-SIM recording

#### Scenario: Notifications permission (API 33+)
- GIVEN the user starts recording on API 33+
- WHEN `POST_NOTIFICATIONS` is not granted
- THEN a permission request is shown

### Requirement: Speedtest in Notification

The system SHALL include optional speedtest status in the recording notification when speedtest is enabled.

#### Scenario: Speedtest status in notification
- GIVEN an active recording with speedtest enabled
- THEN the notification displays the current speedtest status ("Running", "Completed", "Failed")
- AND the notification is updated at the standard 1Hz rate from the state update job