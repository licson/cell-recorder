## Why

Critical thread-safety bugs exist in the recording pipeline where multiple coroutines on `Dispatchers.IO` concurrently read and write shared mutable state without synchronization. These data races can cause corrupted recordings, lost database updates, zombie processes, and crashes. Additionally, several performance issues (duplicate parsing, blocking main-thread callbacks, missing DB indexes) degrade the user experience during recording sessions.

## What Changes

- Fix non-atomic read-modify-write in `RecordingStateManager.update()` by replacing direct `_state.value = transform(_state.value)` with `MutableStateFlow.update { }` (atomic CAS loop)
- Synchronize `PointRecorder` public property reads from `stateUpdateJob` with the `recordingMutex` to prevent data races on `totalPointCount`, `lastRecordedLocation`, `lastRecordedTime`, and `_recordedPath`
- Synchronize `GpsStateMachine` reads from `stateUpdateJob` by introducing a snapshot method that copies state under its own internal lock
- Fix `PingEngine.pingFlow()` process cleanup by closing `process.inputStream` in `awaitClose` to unblock `readLine()`, preventing zombie processes
- Decouple `stopRecording()` database writes (`updateEndedAt`, `updatePrimarySimSlot`) from `serviceScope` to prevent cancellation when `onDestroy()` calls `serviceScope.cancel()`
- Fix `PingSlidingWindow.packetLossPct()` to calculate percentage from actual buffer contents regardless of window fill
- Fix `stopRecording()` double-call by adding an `isStopped` guard to prevent redundant `reset()` and state updates
- Fix `LocationCollector` and `SensorFusionCollector`/`IndoorPositionCollector` to use a dedicated `HandlerThread` looper instead of `Looper.getMainLooper()`, preventing main-thread jank and ensuring registration/unregistration happen on the same thread
- Fix `ImportSessionUseCase.importCsv()` to parse CSV once instead of twice
- Fix `CellRecordRepository.batchResplit()` to handle `5G_NSA` records (currently skipped)
- Fix duplicate `"RSRP (dBm)"` column header in `SessionDetailScreen.ColumnHeadersRow`
- Add composite DB index on `(sessionId, timestamp)` for analytics query performance
- Add `@Transaction` annotation to import batch insert operations
- Replace `PendingIntent.getService()` with `PendingIntent.getForegroundService()` in `RecordingNotificationHelper`

## Capabilities

### New Capabilities

- `thread-safety`: Thread-safety guarantees for shared mutable state across recording coroutines (PointRecorder, GpsStateMachine, RecordingStateManager)
- `process-cleanup`: Reliable subprocess cleanup for PingEngine to prevent zombie processes
- `db-write-safety`: Decoupled database write scope for service shutdown to prevent data loss

### Modified Capabilities

- `recording`: Recording state updates and stop lifecycle now require thread-safe state management and guarded shutdown
- `connectivity`: PingEngine process lifecycle and sliding window packet loss calculation corrected
- `service`: Service stop lifecycle reworked to prevent double-stop and ensure DB writes survive scope cancellation; notification intents updated to getForegroundService
- `data`: CSV import parses once instead of twice; batch re-split handles 5G_NSA; import uses @Transaction; composite index added

## Impact

- Core recording pipeline: `RecordingService`, `PointRecorder`, `RecordingStateManager`, `GpsStateMachine`
- Connectivity: `PingEngine`, `PingSlidingWindow`, `LocationCollector`, `SensorFusionCollector`, `IndoorPositionCollector`
- Data layer: `CellRecordRepository`, `ImportSessionUseCase`, `CellRecordDao`, Room migration (new index)
- Service: `RecordingNotificationHelper`
- UI: `SessionDetailScreen` (header fix only, no behavioral changes)
