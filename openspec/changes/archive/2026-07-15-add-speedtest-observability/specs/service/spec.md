## ADDED Requirements

### Requirement: Conditional Speedtest Cache Invalidation at Session Start

The system SHALL conditionally invalidate the speedtest engine's cache at recording session start based on whether a successful manual prime has occurred since the last invalidation. The prime state flag is defined in `speedtest-diagnostics/spec.md`.

#### Scenario: Warm handoff after successful manual prime

- GIVEN a manual "Launch Test" has completed successfully since the last cache invalidation
- WHEN a recording session starts
- THEN the engine's `primedSinceLastInvalidation` flag is `true`
- AND `RecordingService` does NOT call `invalidateCache()`
- AND the cached server, config, and gauge are retained (warm handoff)
- AND the prime flag is reset (read-once semantics) so a second session without a fresh prime cold-starts

#### Scenario: Cold start when no successful prime

- GIVEN no manual "Launch Test" has completed successfully since the last cache invalidation (or the flag was reset by a previous session, or the process was restarted)
- WHEN a recording session starts
- THEN the engine's `primedSinceLastInvalidation` flag is `false`
- AND `RecordingService` calls `invalidateCache()` (today's behavior)
- AND the session performs fresh config fetch, server selection, and gauge

#### Scenario: Cold start after manual prime failure

- GIVEN a manual "Launch Test" has completed with failure since the last cache invalidation
- WHEN a recording session starts
- THEN the engine's `primedSinceLastInvalidation` flag is `false` (failure path auto-invalidates)
- AND `RecordingService` calls `invalidateCache()`
- AND the session cold-starts

#### Scenario: Cold start after process restart

- GIVEN the app process has been restarted
- WHEN a recording session starts
- THEN the engine's `primedSinceLastInvalidation` flag is `false` (in-memory flag does not survive restart)
- AND `RecordingService` calls `invalidateCache()` (today's behavior)
