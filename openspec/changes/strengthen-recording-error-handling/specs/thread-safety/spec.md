## ADDED Requirements

### Requirement: Volatile stop guard

The system SHALL mark the `isStopped` boolean that guards `stopRecording()` idempotency as `@Volatile` so that reads and writes are visible across threads, even if a future refactor calls `stopRecording()` from a background coroutine.

#### Scenario: Stop guard is volatile
- GIVEN `RecordingService` declares `isStopped`
- THEN the field is annotated `@Volatile`
- AND reads and writes are visible across threads without explicit synchronization
- AND the double-stop idempotency guarantee holds regardless of the calling thread

### Requirement: CancellationException rethrow hygiene in coroutine catches

The system SHALL rethrow `CancellationException` in every `catch (e: Exception)` block that executes in a coroutine context within the `service/` package, so that structured concurrency is preserved and cancelled coroutines are reported as cancelled (not completed normally).

#### Scenario: Coroutine catch rethrows CancellationException
- GIVEN a coroutine in `service/` with a `catch (e: Exception)` block
- WHEN the caught exception is a `CancellationException`
- THEN the exception is rethrown
- AND the coroutine is marked as cancelled
- AND any `finally` cleanup blocks run as part of cancellation

#### Scenario: Non-cancellation exception is handled normally
- GIVEN a coroutine in `service/` with a `catch (e: Exception)` block
- WHEN the caught exception is not a `CancellationException`
- THEN the exception is handled (logged via Timber, continue, or rethrow as appropriate to the call site)
- AND the coroutine is not marked as cancelled unless the handler explicitly rethrows
