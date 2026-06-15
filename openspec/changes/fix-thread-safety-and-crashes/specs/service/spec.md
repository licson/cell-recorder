## MODIFIED Requirements

### Requirement: Notification Stop Action

The system SHALL provide a Stop action on the recording notification using `PendingIntent.getForegroundService()`.

#### Scenario: Stop via notification
- GIVEN the recording service notification is visible
- WHEN the user taps the Stop action on the notification
- THEN the recording is stopped
- AND the service terminates
- AND the stop action PendingIntent uses `getForegroundService()` instead of `getService()`
