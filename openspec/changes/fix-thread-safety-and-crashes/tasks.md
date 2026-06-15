## 1. Thread-Safety Core Fixes

- [ ] 1.1 Replace `RecordingStateManager.update()` direct `_state.value = transform(_state.value)` with `_state.update { transform(it) }` for atomic CAS semantics
- [ ] 1.2 Add `@Synchronized` to `PointRecorder` public property getters: `totalPointCount`, `lastRecordedLocation`, `lastRecordedTime`, `recordedPathSnapshot`
- [ ] 1.3 Add internal `ReentrantLock` to `GpsStateMachine` wrapping all property writes, and add a `snapshot()` method returning a `GpsStateSnapshot` data class with consistent copies of all readable properties
- [ ] 1.4 Update `RecordingService.stateUpdateJob` to read `pointRecorder` properties and `gpsStateMachine.snapshot()` instead of individual unsynchronized property reads

## 2. PingEngine Process Cleanup

- [ ] 2.1 Add `process.inputStream.close()` in `PingEngine.pingFlow()` `awaitClose` block before `process.destroyForcibly()`
- [ ] 2.2 Add `process.destroyForcibly()` in a `finally` block within the launched coroutine as a backup cleanup path

## 3. Service Shutdown Safety

- [ ] 3.1 Create `shutdownScope = CoroutineScope(Dispatchers.IO + SupervisorJob())` in `RecordingService`
- [ ] 3.2 Move `sessionRepository.updateEndedAt()` and `sessionRepository.updatePrimarySimSlot()` calls from `serviceScope.launch` to `shutdownScope.launch` with a `withTimeoutOrNull(5_000)` wrapper and error logging on timeout
- [ ] 3.3 Cancel `shutdownScope` after a 5-second delay in `onDestroy()` (after `serviceScope.cancel()`)
- [ ] 3.4 Add `isStopped` boolean guard to `stopRecording()` — return early if true, set true at start, reset in `startRecording()`

## 4. Callback HandlerThread

- [ ] 4.1 Create `CallbackHandlerThread` singleton class with Hilt `@Singleton` injection providing a `Looper` for sensor/location callbacks
- [ ] 4.2 Add `@PreDestroy` method to quit the handler thread looper
- [ ] 4.3 Update `LocationCollector` to use the injected `CallbackHandlerThread.looper` instead of `Looper.getMainLooper()` for `requestLocationUpdates()`
- [ ] 4.4 Update `SensorFusionCollector` to use the injected looper for `registerListener`/`unregisterListener` calls (call from the looper's thread via `Handler(looper).post { ... }`)
- [ ] 4.5 Update `IndoorPositionCollector` to use the injected looper for `registerListener`/`unregisterListener` calls

## 5. Correctness Bug Fixes

- [ ] 5.1 Fix `PingSlidingWindow.packetLossPct()` to compute percentage from `buffer.size` instead of `windowSize`, and return 0.0 only when `buffer.isEmpty()`
- [ ] 5.2 Fix `ImportSessionUseCase.importCsv()` to parse CSV once with `sessionId=0`, detect indoor mode, then assign real `sessionId` via `.copy()` instead of parsing twice
- [ ] 5.3 Fix `CellRecordRepository.batchResplit()` to handle `5G_NSA` records — re-split NR `fullCellIdentity` using configurable bit length and anchor `anchorEnbOrGnbId`/`anchorLcid` using LTE formula
- [ ] 5.4 Fix duplicate `"RSRP (dBm)"` column header in `SessionDetailScreen.ColumnHeadersRow` line 364 — change to `"RSRQ (dBm)"`
- [ ] 5.5 Replace `PendingIntent.getService()` with `PendingIntent.getForegroundService()` in `RecordingNotificationHelper` for the stop action

## 6. Database Performance

- [ ] 6.1 Add Room migration v11→v12 creating composite index `index_cell_records_session_id_timestamp ON cell_records(sessionId, timestamp)`
- [ ] 6.2 Increment `AppDatabase` version to 12 and add the migration to the migration list
- [ ] 6.3 Add `@Transaction` annotation to `CellRecordDao.insertRecordBatch()` if not already present (verify and add if missing)

## 7. Verification

- [ ] 7.1 Run `./gradlew assembleDebug` to verify clean build
- [ ] 7.2 Run lint checks (`./gradlew lint`) and fix any new warnings
- [ ] 7.3 Run database migration tests if they exist
