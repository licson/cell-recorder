# Process Cleanup Specification

## Purpose

Ensures that ping processes are reliably terminated on all cancellation paths, preventing zombie processes when flow collectors are cancelled or processes hang indefinitely.

## Scope

This spec covers subprocess cleanup during recording shutdown. It does not define:
- Ping measurement behavior (see `connectivity/spec.md`).
- Service lifecycle (see `service/spec.md`).
- Recording lifecycle (see `recording/spec.md`).

## Related Specs

- `connectivity/spec.md` — ping process behavior and output parsing.
- `service/spec.md` — how the service manages the ping job lifecycle.
- `recording/spec.md` — when recording stops and triggers cleanup.

## Requirements

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
