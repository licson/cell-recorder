## ADDED Requirements

### Requirement: Sibling Job Supervision

The system SHALL supervise every sibling coroutine launched under `serviceScope` (`recordingJob`, `fallbackRecordingJob`, `pingJob`, `speedTestJob`, `markerCountJob`, `stateUpdateJob`) with a `CoroutineExceptionHandler` installed on `serviceScope` and a per-job try/catch that logs via Timber and continues. Every per-job catch SHALL rethrow `CancellationException` to preserve structured concurrency. A failure in one sibling job SHALL NOT silently kill that subsystem or affect the others.

#### Scenario: CoroutineExceptionHandler installed on serviceScope
- GIVEN `RecordingService` is initializing its scopes
- WHEN `serviceScope` and `shutdownScope` are created
- THEN a `CoroutineExceptionHandler` is installed on both scopes
- AND the handler logs the exception via `Timber.e` and does NOT call `stopRecording()`

#### Scenario: Sibling job failure is logged and the job continues
- GIVEN an active recording with `pingJob` running
- WHEN `pingJob` throws an uncaught exception
- THEN the exception is logged via Timber
- AND the ping loop continues (or restarts per the backoff rule)
- AND the recording continues
- AND the other sibling jobs are unaffected

#### Scenario: CancellationException is rethrown in every coroutine catch
- GIVEN any sibling job in `service/` with a `catch (e: Exception)` block
- WHEN the caught exception is a `CancellationException`
- THEN the exception is rethrown
- AND the coroutine is marked as cancelled (not completed normally)
- AND any cleanup in the coroutine's `finally` blocks runs

### Requirement: No-Notification Mode

The system SHALL enter a no-notification mode when `notificationHelper.notify` keeps throwing (e.g., `POST_NOTIFICATIONS` revoked mid-recording on API 33+). In no-notification mode, the `stateUpdateJob` loop SHALL skip the `notificationHelper.notify` call but SHALL continue updating `RecordingStateManager` so the in-app UI still reflects live recording state.

#### Scenario: POST_NOTIFICATIONS revoked mid-recording
- GIVEN an active recording with `stateUpdateJob` running
- WHEN `notificationHelper.notify` throws `SecurityException` because `POST_NOTIFICATIONS` was revoked
- THEN the exception is logged via Timber
- AND `stateUpdateJob` enters no-notification mode
- AND subsequent loop iterations skip the `notify` call
- AND `stateManager.update` calls continue
- AND the in-app UI still reflects elapsed time, point count, and other live state
- AND the recording continues

#### Scenario: No-notification mode does not affect recording loop
- GIVEN `stateUpdateJob` is in no-notification mode
- WHEN `recordingJob` triggers a new point
- THEN the point is recorded and persisted normally
- AND the recording is unaffected by the notification failure
