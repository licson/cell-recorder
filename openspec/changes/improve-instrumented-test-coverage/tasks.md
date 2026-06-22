## 1. Baseline verification

- [x] 1.1 Run `./gradlew clean && ./gradlew testDebugUnitTest` and confirm all 443 existing unit tests pass (no regression from prior change)
- [x] 1.2 Run `./gradlew :app:lintDebug` and confirm no new lint warnings
- [x] 1.3 Run `./gradlew :app:assembleDebug` and confirm the debug APK builds
- [x] 1.4 Run `./gradlew :app:assembleDebugAndroidTest` and confirm the androidTest APK builds (existing test infra compiles)

## 2. Fix missing Room migrations 1→3 (production bug fix)

- [x] 2.1 Read `app/schemas/com.cellrecorder.app.data.local.AppDatabase/1.json` and `2.json` and `3.json` to confirm the exact column diffs: v1→v2 adds `subscriptionId` and `simSlotIndex` to `cell_records`; v2→v3 drops `lteEnbSplitShift` and adds `cellInfoRefreshIntervalSec` to `app_config`
- [x] 2.2 Add `MIGRATION_1_2` to `AppDatabase.kt` companion object: `ALTER TABLE cell_records ADD COLUMN subscriptionId INTEGER` and `ALTER TABLE cell_records ADD COLUMN simSlotIndex INTEGER`
- [x] 2.3 Add `MIGRATION_2_3` to `AppDatabase.kt` companion object: table-rebuild pattern for `app_config` (CREATE app_config_new without lteEnbSplitShift and with cellInfoRefreshIntervalSec; INSERT SELECT surviving columns; DROP app_config; RENAME app_config_new TO app_config)
- [x] 2.4 Prepend `MIGRATION_1_2` and `MIGRATION_2_3` to the `addMigrations(...)` call in `DatabaseModule.kt`
- [x] 2.5 Run `./gradlew :app:assembleDebug` and confirm the app builds with the new migrations registered

## 3. Repair @Ignore'd Compose UI tests + MainActivityTest smoke test

- [x] 3.1 Read `RecordingScreenPermissionTest.kt`, `SessionListScreenTest.kt`, `SettingsScreenTest.kt`, and `MainActivityTest.kt` to confirm current state and the @Ignore'd reasons
- [x] 3.2 Add `@HiltAndroidTest` and `@get:Rule val hiltRule = HiltAndroidRule(this)` to `RecordingScreenPermissionTest.kt`; call `hiltRule.inject()` in `@Before`; remove `@Ignore`
- [x] 3.3 Remove `@Ignore` from `SessionListScreenTest.kt` (no Hilt changes needed — uses `createAndroidComposeRule<ComponentActivity>` with manual VM construction)
- [x] 3.4 Remove `@Ignore` from `SettingsScreenTest.kt` (same as 3.3)
- [x] 3.5 Replace `MainActivityTest.noop()` with a real `@HiltAndroidTest` + `createAndroidComposeRule<MainActivity>` smoke test asserting the session-list screen renders (e.g., the toolbar title or empty-list placeholder is visible)
- [x] 3.6 Run `./gradlew :app:connectedDebugAndroidTest` on the local emulator and confirm all 4 un-ignored tests pass. If any fail, capture the stack trace; if the failure is Hilt-related, fix the Hilt setup; if Compose-test-related, fix the assertion; if a genuine framework blocker, re-@Ignore with the captured trace as evidence and document in design.md
- [x] 3.7 If any test required a behavior change beyond annotations, update `design.md` "Decisions §1" with the actual root cause and fix applied

## 4. Fill existing test internal gaps

- [x] 4.1 `ConfigDaoTest.kt`: add round-trip tests for `latencySpikeSigma`, `indoorStepLengthM`, `speedTestEnabled`, `nrGnbBitLength`; add an empty-DB `get()` test
- [x] 4.2 `ConfigRepositoryTest.kt`: add round-trip tests for all `AppConfigEntity` fields through the repository (currently only 3 fields are exercised)
- [x] 4.3 `SpeedTestRecordDaoTest.kt`: add null-average test (rows with `downloadBps`/`uploadBps` = null), success-rate extremes (0% and 100%), timestamp range boundaries (start, end, just outside)
- [x] 4.4 `CellRecordRepositoryTest.kt`: add repository-layer tests for `getBandDistribution`, `getSimSlotDistribution`, `getOnNetworkCount`; replace the `batchResplit` non-null assertion with specific split-value assertions
- [x] 4.5 `SessionListViewModelTest.kt`: add tests for the export flow, import flow, and multi-select operations (bulk delete via `selectedIds`)
- [x] 4.6 `SettingsViewModelTest.kt`: add validation tests for `updateRecordingInterval`, `updateLocationChangeThreshold`, `updateNrGnbBitLength` (invalid + boundary values); add `getLatestCrashLog` test with a seeded crash log file; replace the hardcoded `"1.2.0"` assertion with `BuildConfig.VERSION_NAME`
- [x] 4.7 Run `./gradlew :app:connectedDebugAndroidTest` and confirm the new repository/ViewModel tests pass

## 5. Add row-seeded migration tests (covers full 1→12 chain)

- [x] 5.1 For each existing migration test (3→4, 4→5, 5→6, 6→7, 7→8, 8→9, 9→10, 10→11, 11→12): seed a representative row in each affected table before running the migration; after the migration, assert the row is present (with transformed values where applicable) in the destination schema
- [x] 5.2 Add `migrateFrom1To2` test: create v1 DB, seed a `cell_records` row, run `MIGRATION_1_2`, verify the row survives and the new `subscriptionId`/`simSlotIndex` columns exist (default null)
- [x] 5.3 Add `migrateFrom2To3` test: create v2 DB, seed an `app_config` row with `lteEnbSplitShift` set, run `MIGRATION_2_3`, verify the row survives, `lteEnbSplitShift` is gone, `cellInfoRefreshIntervalSec` is present, and all surviving columns round-trip
- [x] 5.4 Add `migrateFullChain1To12` test: create v1 DB, seed representative rows in all v1 tables, run the full migration chain to v12, verify all rows survive with expected transformations
- [x] 5.5 Run `./gradlew :app:connectedDebugAndroidTest` and confirm all migration tests pass

## 6. Add new ViewModel integration tests

- [x] 6.1 Create `SessionDetailViewModelTest.kt` with `@HiltAndroidTest` + `HiltAndroidRule` + `MainDispatcherRule`; seed a session + cell records + speedtest records; test session summary emission, points flow, analytics insights generation, batch resplit, export invocation. Target 12-15 tests.
- [x] 6.2 Create `StatisticsViewModelTest.kt` with the same setup; seed sessions across multiple days/RATs/SIMs; test aggregate stats emission (total sessions, total duration, RAT distribution, band distribution, SIM distribution, on-network count, speedtest averages, success rate). Target 10-12 tests.
- [x] 6.3 Create `ReplayViewModelTest.kt` with the same setup; seed a session with ordered cell records + speedtest records; test session load, points flow ordering, speedtest markers flow, playback state transitions (idle/playing/paused), filter application. Target 8-10 tests.
- [x] 6.4 Run `./gradlew :app:connectedDebugAndroidTest` and confirm all new ViewModel tests pass

## 7. Add ImportSessionUseCaseTest

- [x] 7.1 Create `ImportSessionUseCaseTest.kt` (`app/src/androidTest/java/com/cellrecorder/app/domain/usecase/import_/`) with `@HiltAndroidTest` + `HiltAndroidRule` + real in-memory Room DB
- [x] 7.2 Add CSV happy-path test: small inline CSV (3 rows with CA bands JSON), invoke import, verify session created, records + CA bands persisted, point count refreshed, endedAt set
- [x] 7.3 Add CSV error-path tests: malformed line skipped, missing required fields, empty file
- [x] 7.4 Add GeoJSON happy-path test: small inline FeatureCollection (3 features), invoke import, verify session + records persisted
- [x] 7.5 Add GeoJSON error-path tests: invalid JSON, feature with missing geometry, FeatureCollection with zero features
- [x] 7.6 Add indoor-mode import test: CSV with `relativeX`/`relativeY` columns, verify session `recordingMode` = `"INDOOR"` and lat/lon default to null
- [x] 7.7 Run `./gradlew :app:connectedDebugAndroidTest` and confirm all ImportSessionUseCase tests pass

## 8. Add Compose UI smoke tests for uncovered screens

- [x] 8.1 Create `LiveInfoScreenTest.kt` using `createAndroidComposeRule<ComponentActivity>` + manual ViewModel construction with sample state; assert key UI elements render (cells, ping latency, SIM info). Target 6-8 tests.
- [x] 8.2 Create `SessionDetailScreenTest.kt` with sample session + analytics state; assert summary card, analytics insights, export menu actions render. Target 8-10 tests.
- [x] 8.3 Create `ReplayScreenTest.kt` with sample points + speedtest markers; assert map placeholder, playback controls, filter UI render. Target 6-8 tests.
- [x] 8.4 Create `StatisticsScreenTest.kt` with sample aggregates; assert aggregate cards, charts, and empty state render. Target 5-7 tests.
- [x] 8.5 Create `RecordingScreenTest.kt` (UI body, not permissions — that's covered by `RecordingScreenPermissionTest`) with sample live cell info + GPS status + recording state; assert key elements render. Target 6-8 tests.
- [x] 8.6 Run `./gradlew :app:connectedDebugAndroidTest` and confirm all new UI tests pass

## 9. Final verification

- [x] 9.1 Run `./gradlew clean && ./gradlew :app:testDebugUnitTest` and confirm the 443 existing unit tests still pass (no regression)
- [x] 9.2 Run `./gradlew :app:connectedDebugAndroidTest` and confirm all instrumented tests (existing + new) pass
- [x] 9.3 Run `./gradlew :app:lintDebug` and confirm no new lint warnings
- [x] 9.4 Run `./gradlew :app:assembleDebug` and confirm the debug APK builds (migrations registered correctly)
- [x] 9.5 Manually verify on the emulator that launching the app, navigating to each screen (SessionList, SessionDetail, Replay, Statistics, LiveInfo, Recording, Settings), starting + stopping a recording, and importing + exporting a session all behave identically to before (sanity check, since the only production behavior change is the migration bug fix which affects v1/v2 upgraders only)
- [ ] 9.6 Commit the change locally with a descriptive message (e.g., `Add instrumented test coverage and fix missing migrations 1→3`)
