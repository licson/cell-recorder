## MODIFIED Requirements

### Requirement: Session database writes survive service scope cancellation

The system SHALL ensure that `updateEndedAt` and `updatePrimarySimSlot` database writes complete even when the service scope is cancelled in `onDestroy()`. If the 5-second in-process attempt fails or times out, the system SHALL enqueue a `WorkManager` one-shot worker that retries the writes with exponential backoff (10s base, max 5 retries) so that finalization survives process death. The worker SHALL be idempotent: `updateEndedAt` is only applied when `endedAt` is still null.

#### Scenario: DB write completes after service stop
- GIVEN an active recording that is being stopped
- WHEN `stopRecording()` is called and `onDestroy()` cancels `serviceScope`
- THEN the `updateEndedAt` and `updatePrimarySimSlot` database writes still complete via the separate `shutdownScope`
- AND the session record has a valid `endedAt` timestamp
- AND the shutdown scope is cancelled by the existing 5-second `onDestroy` timer

#### Scenario: DB write timeout
- GIVEN a shutdown scope with a 5-second timeout
- WHEN the database write does not complete within the timeout
- THEN the error is logged via Timber
- AND the shutdown scope is cancelled
- AND a `SessionFinalizationWorker` is enqueued via WorkManager to retry the writes

#### Scenario: DB write fails with exception
- GIVEN a shutdown scope attempting finalization
- WHEN `updateEndedAt` or `updatePrimarySimSlot` throws an exception
- THEN the exception is logged via Timber
- AND a `SessionFinalizationWorker` is enqueued via WorkManager to retry the writes

#### Scenario: WorkManager retry is idempotent
- GIVEN a `SessionFinalizationWorker` is enqueued for a session
- WHEN the worker executes and the session already has a non-null `endedAt`
- THEN the worker does not overwrite `endedAt`
- AND the worker applies `updatePrimarySimSlot` (idempotent)
- AND the worker returns `Result.success()`

#### Scenario: WorkManager retry backoff
- GIVEN a `SessionFinalizationWorker` is enqueued
- WHEN the worker's finalization attempt fails
- THEN the worker returns `Result.retry()`
- AND WorkManager schedules the next attempt with exponential backoff (10s base)
- AND after 5 failed attempts the worker returns `Result.failure()`

#### Scenario: WorkManager retry survives process death
- GIVEN a `SessionFinalizationWorker` is enqueued and the app process is killed before the 5-second in-process attempt completes
- WHEN WorkManager restarts the worker in a new process
- THEN the worker re-attempts `updateEndedAt` and `updatePrimarySimSlot`
- AND the session is finalized even though the original process is gone
