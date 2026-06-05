## 1. Database Layer — Batch Writes & Indices

- [x] 1.1 Add `insertRecordBatch` method to `CellRecordDao`: a `@Transaction` method accepting `List<CellRecordEntity>` and `List<CellRecordCaBandEntity>`, delegating to existing `insertAll` and `insertCaBands`
- [x] 1.2 Add `insertRecordBatch` passthrough method to `CellRecordRepository`
- [x] 1.3 Add `Index("simSlotIndex", "rat")` and `Index("bandNumber")` annotations to `CellRecordEntity`
- [x] 1.4 Create `MIGRATION_8_9` in `AppDatabase` with `CREATE INDEX IF NOT EXISTS` statements for `index_cell_records_simSlotIndex_rat` and `index_cell_records_bandNumber`
- [x] 1.5 Bump database version to 9 in `@Database` annotation
- [x] 1.6 Export new schema JSON (version 9) via `./gradlew exportSchemas` or Room auto-export
- [x] 1.7 Verify migration test passes (or add one) for v8→v9

## 2. PointRecorder — Batch Insert & ArrayDeque Path

- [x] 2.1 Change `_recordedPath` from `mutableListOf<Pair<Double, Double>>()` to `ArrayDeque<Pair<Double, Double>>(MAX_PATH_SIZE)`
- [x] 2.2 Replace `add` with `addLast` and `removeAt(0)` with `removeFirst()` in `recordPoint()`
- [x] 2.3 Refactor `recordPoint()` to collect all `CellRecordEntity` and `CellRecordCaBandEntity` into lists first, then call `cellRecordRepository.insertRecordBatch()` instead of per-snapshot `insert`/`insertCaBands`
- [x] 2.4 Wrap the batch insert call in try/catch, skipping invalid snapshots before building the lists (maintain per-snapshot resilience)
- [x] 2.5 Remove `notificationHelper.notify()` call from `recordPoint()` (line ~134)

## 3. RecordingService — Mutex Scope & State Updates

- [x] 3.1 Remove `recordingMutex.withLock` from `stateUpdateJob` — read `pointRecorder` fields (`totalPointCount`, `lastRecordedLocation`, `recordedPathSnapshot`) and `gpsState` fields directly without the mutex
- [x] 3.2 Keep `recordingMutex.withLock` in `recordingJob` and `fallbackRecordingJob` unchanged
- [x] 3.3 Verify that `stateUpdateJob` does not write to any shared mutable state (only reads + writes to `stateManager` which is its own `MutableStateFlow`)

## 4. PingEngine — Long-Running Process

- [x] 4.1 Add `pingFlow(host: String, intervalSec: Float, timeoutMs: Long): Flow<PingResult>` method to `PingEngine` that starts a `ping -i <intervalSec> <host>` process
- [x] 4.2 Read stdout line-by-line in a coroutine, parsing latency from each output line using the existing `parsePingOutput` regex
- [x] 4.3 Emit `PingResult` for each parsed line; emit `PingResult(latencyMs = null)` on stream EOF or read timeout
- [x] 4.4 Handle process death: detect via stream EOF or `process.isAlive`, auto-restart with a short delay
- [x] 4.5 On flow collection cancellation, destroy the process (`process.destroyForcibly()`)
- [x] 4.6 Refactor `RecordingService.pingJob` to collect from `pingEngine.pingFlow()` instead of the manual spawn/delay loop
- [x] 4.7 Remove the old `ping(host, timeoutMs)` single-shot method (or mark deprecated)

## 5. Analytics Engine — O(n) Algorithms

- [x] 5.1 Rewrite `detectRsrpDrops`: replace nested i→j loop with a single-pass sliding window approach — maintain a pointer to the next candidate beyond the time window, track min RSRP within window
- [x] 5.2 Rewrite `detectPciFlapping`: replace nested loop with a deque-based sliding window — advance window start past expired timestamps, track distinct PCI count and site IDs within the window
- [x] 5.3 Verify analytics engine produces identical results for an existing session before and after refactoring (regression test)

## 6. Integration Verification

- [x] 6.1 Run `./gradlew assembleDebug` to confirm clean build
- [x] 6.2 Verify DB migration v8→v9 works by installing over an existing debug build with v8 data
- [x] 6.3 Run existing unit tests for `SessionAnalyticsEngine`, `PingSlidingWindow`, `CellRecordDao`
- [x] 6.4 Manual smoke test: start a recording on a dual-SIM device, verify points are recorded, notifications update at 1Hz, ping latency values appear in recorded points
