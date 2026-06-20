## Context

The recording pipeline uses multiple coroutines on `Dispatchers.IO` that share mutable state without synchronization. The primary shared objects are `PointRecorder`, `GpsStateMachine`, and `RecordingStateManager`, all accessed from `recordingJob`, `fallbackRecordingJob`, `stateUpdateJob`, and `speedTestJob` in `RecordingService`. The `recordingMutex` protects writes inside `recordPoint()`/`recordIndoorPoint()`, but readers in `stateUpdateJob` access these objects without acquiring the mutex. This creates classic data races where partial or inconsistent state can be observed.

Additionally, `PingEngine.pingFlow()` spawns a native process whose `readLine()` can block indefinitely if the process hangs, and `awaitClose` may not be reached promptly, leaving zombie processes. The `stopRecording()` database update is launched on `serviceScope` which gets cancelled in `onDestroy()`, risking data loss. Sensor and location callbacks use `Looper.getMainLooper()`, causing main-thread jank and thread-safety issues with registration/unregistration.

## Goals / Non-Goals

**Goals:**
- Eliminate all data races in shared mutable state across recording coroutines
- Ensure ping processes are reliably cleaned up on all cancellation paths
- Guarantee session database writes survive service scope cancellation
- Fix correctness bugs: packet loss calculation, double-stop, 5G_NSA re-split, duplicate header, CSV double-parse
- Move sensor/location callbacks off the main thread

**Non-Goals:**
- Redesigning the recording architecture (e.g., actor model, MVI) — fixes should be minimal and localized
- Changing the Room schema beyond adding one composite index
- Modifying any UI behavior beyond the header text fix
- Addressing performance issues in Compose recomposition (separate change)

## Decisions

### D1: RecordingStateManager — Use `MutableStateFlow.update {}` instead of direct assignment

Replace `_state.value = transform(_state.value)` with `_state.update { old -> transform(old) }`. The `update` extension performs an atomic compare-and-swap loop, preventing lost updates from concurrent coroutines. This is the idiomatic Kotlin approach and requires no new dependencies.

**Alternative considered:** Wrapping all `update()` calls in `Mutex` — rejected because it adds contention and is redundant with CAS semantics.

### D2: PointRecorder — Expose snapshot methods with internal synchronization

Add a `synchronized(this)` block (or `@Synchronized`) on public property getters that read mutable state (`totalPointCount`, `lastRecordedLocation`, `lastRecordedTime`, `recordedPathSnapshot`). The `recordPoint`/`recordIndoorPoint` methods already run under `recordingMutex` in the caller, so they act as the write side. This creates a standard reader-writer pattern with synchronized readers.

**Alternative considered:** Converting all fields to `AtomicX` types — rejected because `lastRecordedLocation` is a complex object and `_recordedPath` is an `ArrayDeque`, which have no atomic counterpart. A synchronized snapshot is simpler.

### D3: GpsStateMachine — Internal lock with snapshot method

Add an internal `ReentrantLock` (or `synchronized`) and wrap all property writes in `lock`/`unlock`. Add a `snapshot()` method that copies all readable properties into a `GpsStateSnapshot` data class under the lock. The `stateUpdateJob` will read the snapshot instead of individual properties, ensuring a consistent view.

**Alternative considered:** Using `@Volatile` on each property — rejected because individual volatile reads don't provide a consistent cross-property snapshot (e.g., `hasGpsFix=true` with stale `lastValidLocation`).

### D4: PingEngine — Close inputStream in awaitClose to unblock readLine

In `pingFlow()`, add `process.inputStream.close()` in the `awaitClose` block before `process.destroyForcibly()`. Closing the input stream causes `readLine()` to throw or return null, unblocking the reader coroutine. Also call `process.destroyForcibly()` in a `finally` block within the launched coroutine as a backup.

### D5: Decouple DB writes from serviceScope — Use a shutdownScope

Create a `shutdownScope = CoroutineScope(Dispatchers.IO + SupervisorJob())` in `RecordingService`. The `stopRecording()` database update (`updateEndedAt`, `updatePrimarySimSlot`) will launch on `shutdownScope` instead of `serviceScope`. The `shutdownScope` will be cancelled only after a 5-second delay or when the DB write completes (whichever comes first), using a `withTimeoutOrNull` wrapper. This prevents `onDestroy()` from cancelling the write.

**Alternative considered:** Using `GlobalScope` — rejected because it's an anti-pattern and has no structured cancellation. Using `WorkManager` — rejected as over-engineering for a simple one-shot write.

### D6: Guard against double-stop — Add `isStopped` flag

Add a `private var isStopped = false` flag in `RecordingService`. Set it to `true` at the start of `stopRecording()` and return early if already true. Reset it only in `startRecording()`. This prevents redundant `reset()` calls and state updates from `onDestroy()`.

### D7: Dedicated HandlerThread for sensor/location callbacks

Create a `@Singleton` `CallbackHandlerThread` injected via Hilt that provides a `Looper` for all sensor and location registrations. Both `LocationCollector`, `SensorFusionCollector`, and `IndoorPositionCollector` will receive this looper and use it for `registerListener`/`unregisterListener`/`requestLocationUpdates`. This ensures registration and unregistration happen on the same thread and keeps the main thread free.

### D8: Fix PingSlidingWindow.packetLossPct() — Use actual buffer size

Replace `if (buffer.size < windowSize) return 0.0` with `if (buffer.isEmpty()) return 0.0` and compute percentage based on `buffer.size` instead of `windowSize`.

### D9: Fix ImportSessionUseCase.importCsv() — Parse once, assign sessionId

Parse the CSV once with `sessionId = 0`, detect indoor mode, then assign the real sessionId to all records via `.copy()`. This eliminates the second parse pass.

### D10: Fix batchResplit for 5G_NSA — Add NSA handling

Add a `"5G_NSA"` branch in `batchResplit()` that re-splits the NR cell's `fullCellIdentity` using the configurable bit length and re-splits the anchor's `anchorEnbOrGnbId`/`anchorLcid` using the LTE formula (`shr 8`/`and 0xFF`).

### D11: Add composite index on (sessionId, timestamp)

Add a Room migration (DB v11 → v12) creating `INDEX cell_records_session_id_timestamp ON cell_records(sessionId, timestamp)`. This benefits `ORDER BY timestamp ASC` queries used by analytics.

### D12: Replace PendingIntent.getService() with getForegroundService()

In `RecordingNotificationHelper`, replace `PendingIntent.getService()` with `PendingIntent.getForegroundService()` for the stop action. This is the correct API for foreground service interactions on Android 12+.

## Risks / Trade-offs

- [Synchronized getters on PointRecorder add minor read contention] → Acceptable; `stateUpdateJob` reads at 1Hz, contention is negligible
- [shutdownScope with 5s timeout may still lose writes if DB is locked] → Log the error; this is no worse than current behavior (writes are currently lost on every stop)
- [New HandlerThread adds a singleton lifecycle concern] → Hilt `@Singleton` exposes a `quit()` method for explicit teardown. Note: Hilt does not support `@PreDestroy` on `@Singleton`-scoped bindings, so the annotation is intentionally omitted; the thread lives for the application process lifetime (correct behavior for a singleton-scoped background looper) and the OS reclaims it on process death. Quitting it on every service stop would break subsequent recordings.
- [Room migration v11→v12 must be tested] → Add migration test in `AppDatabaseTest`; index-only migration is low risk
- [5G_NSA batchResplit must handle anchor fields too] → The anchor split uses the LTE formula which is always `shr 8 / and 0xFF`, so it's straightforward
