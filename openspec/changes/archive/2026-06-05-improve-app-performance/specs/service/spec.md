## MODIFIED Requirements

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
