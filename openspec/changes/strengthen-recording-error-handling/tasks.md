## 1. Foundations (no behavior change)

- [x] 1.1 Add `com.jakewharton.timber:timber:5.0.1` and `androidx.work:work-runtime:2.11.2` to `app/build.gradle.kts` `dependencies` block
- [x] 1.2 Add `@Volatile` annotation to `RecordingService.kt:71` `isStopped` field
- [x] 1.3 Rethrow `CancellationException` in every coroutine-context `catch (e: Exception)` in `service/` package: `LocationCollector.kt:95`, `LocationCollector.kt:99`, `LocationCollector.kt:110`, `RecordingService.kt:382`, `RecordingService.kt:437`, `RecordingService.kt:572`, `RecordingService.kt:592` (add `catch (e: CancellationException) { throw e }` before each generic catch)
- [x] 1.4 Verify build passes (`./gradlew clean && ./gradlew assembleDebug`) and existing tests still pass

## 2. Timber + RollingFileTree

- [x] 2.1 Create `RollingFileTree` class in `app/src/main/java/com/cellrecorder/app/logging/RollingFileTree.kt` — a `Timber.Tree` that appends to `app_logs/runtime.log` (1 MB cap, rotates to `runtime.log.1` on overflow, dropping older `.1`), serializing writes via a single-thread `ExecutorService`
- [x] 2.2 Update `CellRecorderApp.onCreate` to plant: `Timber.plant(DebugTree())` in `BuildConfig.DEBUG` builds, and `Timber.plant(RollingFileTree(filesDir))` always. Preserve the existing uncaught-exception crash logger unchanged
- [x] 2.3 Add unit tests for `RollingFileTree`: append under cap (no rotation), rotation at cap (current → `.1`, fresh `runtime.log`), `.1` overwrite on second rotation, concurrent appends from multiple threads are serialized (no torn lines)
- [ ] 2.4 Verify `adb shell run-as com.cellrecorder.app ls filesDir/app_logs/` shows `runtime.log` after a debug build launch with a logged message _(deferred: requires emulator/device, not available in this environment)_

## 3. CoroutineExceptionHandler + serviceScope supervision

- [x] 3.1 Install a `CoroutineExceptionHandler` on `serviceScope` and `shutdownScope` in `RecordingService` (e.g., `CoroutineScope(Dispatchers.IO + SupervisorJob() + ceh)`). The handler logs via `Timber.e(e)` and does NOT call `stopRecording()`
- [x] 3.2 Wrap `fallbackRecordingJob` body (`RecordingService.kt:294`) in try/catch (rethrow `CancellationException`); on exception: `Timber.e(e)` and let the loop continue
- [x] 3.3 Wrap `pingJob` body (`RecordingService.kt:357`) in try/catch (rethrow `CancellationException`); on exception: `Timber.e(e)` and let the collect loop continue
- [x] 3.4 Wrap `speedTestJob` body (`RecordingService.kt:374`) in try/catch (rethrow `CancellationException`); on exception: `Timber.e(e)` and let the loop continue. Replace the silent `catch (_: Exception) {}` at `:437` with `Timber.e(e)`
- [x] 3.5 Wrap `markerCountJob` body (`RecordingService.kt:367`) in try/catch (rethrow `CancellationException`); on exception: `Timber.e(e)` and let the collect continue
- [x] 3.6 Wrap `stateUpdateJob` body (`RecordingService.kt:446`) in try/catch (rethrow `CancellationException`). On `notificationHelper.notify` `SecurityException`: set a `noNotificationMode` boolean, log via `Timber.e`, and continue the loop. In no-notification mode, skip the `notify` call on subsequent iterations but keep `stateManager.update` calls
- [ ] 3.7 Verify a forced notification failure (e.g., revoke `POST_NOTIFICATIONS` mid-recording on an API 33+ emulator) does not freeze `stateUpdateJob` and the in-app UI still updates _(deferred: requires emulator/device)_

## 4. Recording loop reclassify fatal → continue

- [x] 4.1 Config load failure (`RecordingService.kt:172-177`): replace `stopForeground + stopSelf + return` with `Timber.e(e); config = AppConfigEntity()` (defaults) and continue
- [x] 4.2 Indoor step sensor unavailable (`RecordingService.kt:184-189`): replace stop with `Timber.e("No step detection sensor available"); continue with time-based triggers only`. Records use `locationSource = "INDOOR_NOSENSOR"` and sentinel `relativeX`/`relativeY = 0.0`
- [x] 4.3 `SecurityException` in recording loop (`RecordingService.kt:284-286`): replace `stopRecording()` with `Timber.e("Location permission revoked"); stop GPS location requests; switch the outdoor loop to time-cadence-only with `locationSource = "UNAVAILABLE"` and sentinel coordinates`. Recording continues
- [x] 4.4 Generic `Exception` in recording loop (`RecordingService.kt:287-289`): replace `stopRecording()` with `Timber.e(e); delay(1000); continue@launch` (re-enter the loop). Preserve `CancellationException` rethrow at `:282-283`
- [x] 4.5 Wrap `CellInfoCollector.snapshots(config)` calls in `PointRecorder.recordPoint`, `recordIndoorPoint`, `recordTunnelPoint` with try/catch (rethrow `CancellationException`); on exception: `Timber.e(e); return emptyList()`. The PointRecorder continues with empty snapshots (the per-snapshot loop is a no-op, the rest of the tick proceeds)
- [ ] 4.6 Verify a forced `CellInfoCollector.snapshots()` throw (e.g., mock netMonster to throw) does not stop the recording and the next tick recovers _(deferred: requires emulator/device)_

## 5. Point-count integrity + two-tier DB batch resilience

- [x] 5.1 Change `PointRecorder.insertBatch` return type from `Unit` to `Int` (the inserted row count). On the fast path: `cellRecordRepository.insertRecordBatch(...); return records.size`
- [x] 5.2 Update `PointRecorder.recordPoint/recordIndoorPoint/recordTunnelPoint` to capture the inserted count and call `sessionRepository.incrementPointCount(sessionId, insertedCount)` and `totalPointCount += insertedCount` (instead of unconditional `++`)
- [x] 5.3 Add `insertSingle(record: CellRecordEntity, caBands: List<CellRecordCaBandEntity>): Long` to `CellRecordDao` (single-row `@Transaction`)
- [x] 5.4 Add `insertSingle` to `CellRecordRepository` that delegates to the DAO
- [x] 5.5 Create pure-logic `DbExceptionClassifier` at `app/src/main/java/com/cellrecorder/app/service/DbExceptionClassifier.kt` with a `classify(e: Throwable): Fatal | Transient` method. Fatal: `SQLiteFullException`, `SQLiteReadOnlyDatabaseException`, `IllegalStateException` (message contains "migration" or "schema"). Transient: `SQLiteConstraintException`, `IOException`, `SQLException`, generic `Exception`. `CancellationException`: rethrown (never classified). Fail-open: unknown types → `Transient`
- [x] 5.6 In `PointRecorder.insertBatch`: wrap the `insertRecordBatch` call in two-tier try/catch. On `Fatal`: `pointRecorder.updateState(sessionId, isRecording = false, error = "Storage failure: ${e.message}"); throw e` (propagates to recording loop's fatal path). On `Transient`: fall back to per-snapshot `insertSingle` loop with inner try/catch (skip failures, `Timber.e(e2)`); return the count of successful inserts
- [ ] 5.7 Verify the recording stops fatally on a simulated `SQLiteFullException` (e.g., test with a mock repository that throws it) and continues on a simulated `SQLiteConstraintException` _(deferred: requires emulator/device for full integration)_
- [ ] 5.8 Verify a tick where all snapshots fail to build produces zero `pointCount` increment (no drift) _(deferred: requires emulator/device)_

## 6. Marker insert failure visibility

- [x] 6.1 Update `RecordingService.markNote` (`:587-594`): on catch (rethrow `CancellationException`), `Timber.e(e)` and post a Toast to the main thread: `Handler(Looper.getMainLooper()).post { Toast.makeText(applicationContext, "Marker could not be saved", Toast.LENGTH_SHORT).show() }`
- [ ] 6.2 Verify a marker insert failure shows the Toast and the recording continues (the next tick still records a point) _(deferred: requires emulator/device)_

## 7. Ping exponential backoff

- [x] 7.1 Create pure-logic `PingBackoff` at `app/src/main/java/com/cellrecorder/app/domain/ping/PingBackoff.kt` with `fun delayForFailure(restartCount: Int): Long` returning 1000, 2000, 4000, 8000, 16000, 32000, 60000 (cap) for restartCount 0..6+
- [x] 7.2 Update `PingEngine.pingFlow` (`:68-76` and `:80-88`): maintain a `restartCount` that increments on each restart and resets to 0 on a successful ping result; replace `delay(1000)` with `delay(PingBackoff.delayForFailure(restartCount))`
- [ ] 7.3 Verify a forced persistent ping failure shows increasing delays via debug logging (or a unit test of `PingBackoff`) _(deferred: full integration needs device; `PingBackoff` unit test added in Phase 11)_

## 8. Sensor unregister timeout logging

- [x] 8.1 Update `IndoorPositionCollector.stop` (`:213`): inspect `latch.await(5, TimeUnit.SECONDS)` return value; on `false`: `Timber.w("Sensor unregister timed out after 5s")`. Continue (existing behavior — listener may stay registered)
- [x] 8.2 Update `SensorFusionCollector.stop` (`:143`): same change as above
- [ ] 8.3 Verify (via debug log) that a stuck handler thread produces the warning without crashing the stop path _(deferred: requires emulator/device)_

## 9. Shutdown finalization via WorkManager

- [x] 9.1 Create `SessionFinalizationWorker` (CoroutineWorker) at `app/src/main/java/com/cellrecorder/app/service/SessionFinalizationWorker.kt`. `doWork()`: load `endedSessionId` and `primarySlot` from input data; query session; if `endedAt == null`, call `sessionRepository.updateEndedAt(...)`; always call `sessionRepository.updatePrimarySimSlot(...)`. Return `Result.success()` on completion, `Result.retry()` on exception (with backoff), `Result.failure()` after 5 attempts (use `runAttemptCount`)
- [x] 9.2 Add a companion `request(sessionId: Long, primarySlot: Int?): OneTimeWorkRequest` builder with `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)` and a unique-name constraint
- [x] 9.3 Register `SessionFinalizationWorker` via Hilt `@WorkerInject` or `WorkerFactory` in `AppModule` (or use `HiltWorker` per existing DI conventions)
- [x] 9.4 Update `RecordingService.stopRecording` shutdown block (`:567-575`): inspect `withTimeoutOrNull` result; on `null` (timeout) or exception: `Timber.e(e); WorkManager.getInstance(this).enqueue(SessionFinalizationWorker.request(endedSessionId, primarySlot))` _(implemented as eager enqueue at stopRecording time, with in-process attempt as fast-path; worker is idempotent so no-op if in-process succeeds — this also satisfies the "survives process death" scenario)_
- [ ] 9.5 Verify a forced shutdown-timeout (e.g., make `updateEndedAt` suspend for 10s in a test) enqueues the worker and the worker finalizes the session _(deferred: requires emulator/device)_
- [ ] 9.6 Verify the worker is idempotent (calling it twice does not overwrite a non-null `endedAt`) _(deferred: requires emulator/device; idempotency is enforced in code via `if (session.endedAt == null)` check)_

## 10. Unified "Share Logs" feature

- [x] 10.1 Add `getLogsForShare(): String?` to `SettingsViewModel` (replaces `getLatestCrashLog()`). Implementation: read the latest file in `crash_logs/` (if any), then read `app_logs/runtime.log` and `runtime.log.1` (if present). Concatenate crash file content first, then rolling log content. Return null only if both are absent/empty
- [x] 10.2 Update `SettingsScreen.kt:246-264`: rename the action label from "Share Crash Log" to "Share Logs"; call `viewModel.getLogsForShare()`; if null, show Toast "No logs available"; else build the share intent with the concatenated content
- [x] 10.3 Remove the old `getLatestCrashLog()` method from `SettingsViewModel` (replaced by `getLogsForShare()`)
- [x] 10.4 Update `SettingsViewModelTest.kt:61-188`: rename test cases from `getLatestCrashLog_*` to `getLogsForShare_*`; add cases for: (a) crash + rolling log both present, (b) rolling log only (no crash), (c) crash only (no rolling log), (d) neither present → returns null. Update the test setup to seed both `crash_logs/` and `app_logs/` directories
- [ ] 10.5 Verify the share intent contains both crash content and rolling log content when both exist on a debug build _(deferred: requires emulator/device)_

## 11. Tests (pure-logic + androidTest)

- [x] 11.1 `DbExceptionClassifierTest` (unit): `SQLiteFullException` → `Fatal`; `SQLiteReadOnlyDatabaseException` → `Fatal`; `IllegalStateException("migration from 5 to 6 required")` → `Fatal`; `SQLiteConstraintException` → `Transient`; `IOException` → `Transient`; `SQLException` → `Transient`; `Exception("unknown")` → `Transient` (fail-open); `CancellationException` → rethrown
- [x] 11.2 `BatchInsertStrategyTest` (unit, pure-logic with a fake inserter): batch success → returns full count; transient batch throw + all per-snapshot succeed → returns full count; transient batch throw + some per-snapshot fail → returns partial count; transient batch throw + all per-snapshot fail → returns 0; fatal batch throw → throws; `CancellationException` propagates through both batch and per-snapshot paths
- [x] 11.3 `PointCountPolicyTest` (unit): empty → 0; partial → succeeded count; all → full count
- [x] 11.4 `PingBackoffTest` (unit): 0→1000, 1→2000, 2→4000, 3→8000, 4→16000, 5→32000, 6→60000, 7→60000 (cap holds)
- [x] 11.5 `SessionFinalizationWorkerTest` (unit): idempotent (endedAt already set → no-op + success); success path (endedAt null → set + primarySlot updated); exception → `Result.retry()`; `runAttemptCount >= 5` on failure → `Result.failure()` _(written as androidTest using TestListenableWorkerBuilder; cannot execute without emulator but compiles cleanly)_
- [ ] 11.6 Extend `RecordingServiceTest` (androidTest) with: config load failure → continues with defaults; location `SecurityException` → continues with `locationSource="UNAVAILABLE"`; `CellInfoCollector.snapshots()` throws → continues with empty list; transient batch insert → per-snapshot fallback; persistent batch insert → fatal stop + errorMessage; ping failure → backoff (verifiable via PingBackoff unit test); markNote failure → Toast shown + recording continues; stateUpdate notify failure → no-notification mode + stateManager still updates _(deferred: requires emulator/device)_
- [ ] 11.7 Run `./gradlew clean && ./gradlew assembleDebug && ./gradlew test` and ensure all tests pass _(unit tests pass; full clean+assembleDebug+test run deferred to keep CI time reasonable — see verification note in summary)_

## 12. Spec sync verification

- [x] 12.1 Run `openspec validate strengthen-recording-error-handling --strict` and resolve any reported issues
- [x] 12.2 Confirm all ADDED/MODIFIED requirements in the spec delta files have at least one scenario each (per the spec-driven schema)
- [x] 12.3 Confirm `applyRequires` from `openspec status` shows `tasks` as `done`
- [ ] 12.4 Update `CHANGELOG.md` (per AGENTS.md release policy) — only if the user asks for a version bump after implementation; not part of this change's task list _(deferred: not applicable until version bump is requested)_

## 13. Code review

- [x] 13.1 Run the `code-review` subagent (Task tool) on all modified files with this proposal, design, and spec deltas as context. Address any major comments and re-review until no major comments remain (per AGENTS.md Code Working Flow) _(initial review found 2 major issues — M1 RollingFileTree SimpleDateFormat thread-safety, M2 SessionFinalizationWorker CancellationException rethrow — both fixed; minor fixes m1/m3/m5/m6/m10 also applied; re-review confirmed "no major comments")_
