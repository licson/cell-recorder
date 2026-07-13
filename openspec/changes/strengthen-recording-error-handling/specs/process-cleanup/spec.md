## ADDED Requirements

### Requirement: Ping restart exponential backoff

The system SHALL apply exponential backoff to ping process restarts after consecutive failures, replacing the previous fixed 1-second delay. The backoff sequence SHALL be 1s → 2s → 4s → 8s → 16s → 32s → 60s (cap). The cap prevents unbounded growth while ensuring connectivity recovery is detected within approximately one minute.

#### Scenario: Backoff sequence on consecutive failures
- GIVEN `PingEngine.pingFlow()` is restarting after a process failure
- WHEN the restart count is 0, 1, 2, 3, 4, 5, 6, 7, ...
- THEN the delay before the next restart attempt is 1s, 2s, 4s, 8s, 16s, 32s, 60s, 60s, ...
- AND the delay never exceeds 60 seconds

#### Scenario: Backoff resets on successful ping
- GIVEN `PingEngine.pingFlow()` has been restarting with backoff
- WHEN a ping process succeeds (produces a valid ping result)
- THEN the restart counter resets to 0
- AND the next failure (if any) starts the backoff sequence from 1 second again

#### Scenario: Backoff does not affect cleanup paths
- GIVEN `PingEngine.pingFlow()` is in a backoff delay
- WHEN the flow collector is cancelled (e.g., recording stops)
- THEN the backoff delay is interrupted by cancellation
- AND the process cleanup (`inputStream` close, `destroyForcibly()`) runs as defined in the existing cleanup requirement
- AND no zombie ping process remains
