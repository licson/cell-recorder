## MODIFIED Requirements

### Requirement: Recording Stop

The system SHALL stop the foreground service and finalize the session when the user ends the recording. The stop operation SHALL be idempotent and the database finalization SHALL survive service scope cancellation.

#### Scenario: Stop recording via button
- GIVEN an active recording
- WHEN the user taps the Stop button
- THEN the session is updated with `endedAt = now()`
- AND the recording service stops itself
- AND the database update completes even if the service scope is cancelled

#### Scenario: Double-stop is idempotent
- GIVEN a recording that has already been stopped
- WHEN `stopRecording()` is called again (e.g., from `onDestroy()`)
- THEN the second call returns early without side effects
- AND `pointRecorder.reset()` is not called a second time
