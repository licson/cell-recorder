## Why

The recording service hot path performs multiple individual database writes per GPS tick, holds a mutex across read-only state updates, and spawns a new OS process for every ping. These inefficiencies compound under active recording — dual-SIM + CA bands means 5+ DB roundtrips per tick, mutex contention blocks GPS processing for state UI updates, and process creation overhead adds ~50-100ms per ping cycle. Additionally, the analytics engine has O(n²) algorithms and the path history uses O(n) list operations.

## What Changes

- Batch all cell record and CA band inserts for a single GPS tick into one Room `@Transaction`, eliminating per-snapshot DB roundtrips
- Remove the read-only `stateUpdateJob` from the `recordingMutex` — only `recordingJob` and `fallbackJob` need mutual exclusion
- Remove notification updates from `PointRecorder.recordPoint()` — the 1Hz `stateUpdateJob` already handles notification refresh, eliminating duplicate notification IPC
- Replace `MutableList` with `ArrayDeque` for recorded path storage, making `removeFirst` O(1) instead of O(n)
- Optimize `SessionAnalyticsEngine` algorithms from O(n²) to O(n) (sliding window for RSRP drops, deque for PCI flapping, merge sorted passes)
- Add database indices on `simSlotIndex`, `rat`, and `bandNumber` for `cell_records` table via migration v8→v9
- Replace per-ping `ProcessBuilder` spawn with a long-running `ping -i` process that streams results continuously, exposed as a `Flow<PingResult>`

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

- `recording`: Batch DB writes change the recording hot path from per-snapshot individual inserts to single-transaction batch writes; path storage moves from MutableList to ArrayDeque
- `connectivity`: Ping engine moves from single-shot process spawning to a persistent `ping -i` process streaming results via `Flow<PingResult>`
- `service`: State update job removed from mutex scope; notification updates consolidated to 1Hz from state update loop only
- `analytics`: Analytics engine algorithms optimized from O(n²) to O(n) for handoff detection, RSRP drop detection, and PCI flapping detection

## Impact

- `PointRecorder` — refactored to batch inserts; notification update removed; path storage changed to ArrayDeque
- `RecordingService` — mutex scope narrowed; state update reads without lock; `recordedPath` snapshot read without mutex
- `CellRecordDao` / `CellRecordRepository` — new batch insert transaction method
- `AppDatabase` — migration v8→v9 adding indices
- `CellRecordEntity` — new `@Index` annotations
- `PingEngine` — rewritten to manage a long-running process with `Flow<PingResult>` output
- `RecordingService` ping job — simplified to collect from `PingEngine.pingFlow()` instead of manual spawn/delay loop
- `SessionAnalyticsEngine` — `detectRsrpDrops` and `detectPciFlapping` rewritten with O(n) algorithms
