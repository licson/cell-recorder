## ADDED Requirements

### Requirement: Session database writes survive service scope cancellation

The system SHALL ensure that `updateEndedAt` and `updatePrimarySimSlot` database writes complete even when the service scope is cancelled in `onDestroy()`.

#### Scenario: DB write completes after service stop
- GIVEN an active recording that is being stopped
- WHEN `stopRecording()` is called and `onDestroy()` cancels `serviceScope`
- THEN the `updateEndedAt` and `updatePrimarySimSlot` database writes still complete
- AND the session record has a valid `endedAt` timestamp

#### Scenario: DB write timeout
- GIVEN a shutdown scope with a timeout
- WHEN the database write does not complete within the timeout
- THEN the error is logged
- AND the shutdown scope is cancelled

### Requirement: Guard against double-stop

The system SHALL prevent `stopRecording()` from executing twice, which can cause redundant `reset()` calls and state inconsistencies.

#### Scenario: ACTION_STOP followed by onDestroy
- GIVEN an active recording
- WHEN `ACTION_STOP` triggers `stopRecording()` and then `onDestroy()` triggers a second `stopRecording()` call
- THEN the second call returns early without executing
- AND no redundant `pointRecorder.reset()` or state updates occur
