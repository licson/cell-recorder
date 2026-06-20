## ADDED Requirements

### Requirement: Reliable ping process cleanup on flow cancellation

The system SHALL ensure the ping process is terminated on all cancellation paths, including when `readLine()` blocks indefinitely.

#### Scenario: Flow collector cancelled while readLine blocks
- GIVEN a running `PingEngine.pingFlow()` with a blocked `readLine()` call
- WHEN the flow collector is cancelled (e.g., recording stops)
- THEN the process `inputStream` is closed to unblock `readLine()`
- AND the process is destroyed via `destroyForcibly()`
- AND no zombie ping process remains

#### Scenario: Process hangs indefinitely
- GIVEN a running `PingEngine.pingFlow()` where the ping process hangs
- WHEN `readLine()` never returns
- THEN `awaitClose` closes `process.inputStream` to unblock the read
- AND `destroyForcibly()` is called as a backup in the coroutine's `finally` block
