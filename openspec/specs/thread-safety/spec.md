# Thread Safety Specification

## Purpose

Defines thread-safety requirements for shared mutable state accessed by concurrent coroutines during active recording sessions, ensuring consistent snapshots and preventing lost updates.

## Requirements

### Requirement: Thread-safe state snapshots for shared recording objects

The system SHALL provide thread-safe snapshot access to shared mutable state in `PointRecorder` and `GpsStateMachine` so that concurrent readers (e.g., `stateUpdateJob`) observe consistent values without acquiring the recording mutex.

#### Scenario: PointRecorder consistent snapshot read
- GIVEN an active recording with `stateUpdateJob` reading `PointRecorder` properties at 1Hz
- WHEN `recordPoint()` writes to `totalPointCount`, `lastRecordedLocation`, `lastRecordedTime`, or `_recordedPath` under `recordingMutex`
- THEN the `stateUpdateJob` reads a consistent snapshot where all properties come from the same logical point in time
- AND no partial or torn reads occur

#### Scenario: GpsStateMachine consistent snapshot read
- GIVEN an active recording with `stateUpdateJob` reading `GpsStateMachine` properties
- WHEN `recordingJob` or `fallbackRecordingJob` updates `hasGpsFix`, `lastKnownSpeedMps`, `lastValidLocation`, `isExtrapolating`, or `gpsLostAtMs` under `recordingMutex`
- THEN the `stateUpdateJob` reads a consistent `GpsStateSnapshot` where all properties come from the same logical state
- AND no partial or torn reads occur

### Requirement: Atomic RecordingStateManager updates

The system SHALL use atomic compare-and-swap semantics for all `RecordingStateManager.update()` calls to prevent lost updates from concurrent coroutines.

#### Scenario: Concurrent update calls do not lose state
- GIVEN multiple coroutines calling `RecordingStateManager.update(transform)` simultaneously from `PointRecorder.updateLiveState()`, `stateUpdateJob`, and `speedTestJob`
- WHEN two transforms execute concurrently
- THEN both transforms are applied (the second re-reads the state after the first's CAS succeeds)
- AND no intermediate state is lost
