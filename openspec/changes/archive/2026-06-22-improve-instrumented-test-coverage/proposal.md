## Why

The prior `improve-pure-logic-test-foundation` change added 351 JVM unit tests for pure-logic components, but the instrumented test layer (`app/src/androidTest/`) is thin and partially broken: 3 Compose UI tests are `@Ignore`'d under a misdiagnosed "process isolation" reason, the only `MainActivityTest` is a `noop()`, 5 of 7 ViewModels have no integration test, several DAO/repository tests cover only a fraction of their surface area, and migrations 1→2 and 2→3 are missing entirely from production code (a real upgrade-path crash for users on v1/v2 schemas). This change fills those gaps so the test suite catches integration regressions during development instead of in the field.

## What Changes

- **Repair the 3 `@Ignore`'d Compose UI tests** (`RecordingScreenPermissionTest`, `SessionListScreenTest`, `SettingsScreenTest`) by adding the missing Hilt annotations (`@HiltAndroidTest` + `HiltAndroidRule`) where required and verifying on the emulator. If a test genuinely fails after the fix, document the real root cause in `design.md` and leave it `@Ignore`'d with a corrected explanation.
- **Convert `MainActivityTest` from `noop()` to a real smoke test** (`@HiltAndroidTest` + `createAndroidComposeRule<MainActivity>`) asserting the session-list screen renders.
- **Fill internal coverage gaps** in existing DAO, repository, migration, and ViewModel tests (round-trip every `AppConfigEntity` field, success-rate extremes, time-range boundaries, `batchResplit` value assertions, export/import/multi-select ViewModel flows, validation tests for `SettingsViewModel`).
- **Fix missing Room migrations 1→2 and 2→3** in production code (`AppDatabase.kt` + `DatabaseModule.kt`): `MIGRATION_1_2` adds `subscriptionId` + `simSlotIndex` to `cell_records`; `MIGRATION_2_3` rebuilds `app_config` to drop `lteEnbSplitShift` and add `cellInfoRefreshIntervalSec` (SQLite on API 30 cannot `DROP COLUMN`, so use the create-new/copy/drop/rename pattern). Add row-seeded tests for the full 1→12 migration chain.
- **Replace the hardcoded `"1.2.0"` literal** in `SettingsViewModelTest.getVersionDisplay` with a `BuildConfig.VERSION_NAME` assertion so the test does not drift on version bumps.
- **Add new ViewModel integration tests** for `SessionDetailViewModel`, `StatisticsViewModel`, and `ReplayViewModel` — all three have DB-only dependencies and match the existing `SessionListViewModelTest` pattern (`@HiltAndroidTest` + `HiltAndroidRule` + `MainDispatcherRule` + real in-memory Room DB + manual VM construction).
- **Add `ImportSessionUseCaseTest`** — an instrumented test verifying the CSV/GeoJSON import pipeline end-to-end against a real Room DB (session created, records + CA bands persisted, point count refreshed, `endedAt` set, error paths).
- **Add new Compose UI smoke tests** for the 5 currently-uncovered screens: `LiveInfoScreen`, `SessionDetailScreen`, `ReplayScreen`, `StatisticsScreen`, `RecordingScreen` (UI body, not permissions).

## Capabilities

### New Capabilities

- `instrumented-test-coverage`: Requirements for instrumented (androidTest) test coverage of ViewModels, Compose screens, UseCases, DAOs, repositories, and migrations — the coverage contract for the integration test layer.

### Modified Capabilities

- `data`: Adds the requirement that the database must support upgrade paths from every previously-released schema version (currently 1 through 12) to the current version, and that `fallbackToDestructiveMigration()` must not be used (preserves user data on upgrade).

## Impact

- **Production code (bug fix):** `app/src/main/java/com/cellrecorder/app/data/local/AppDatabase.kt` (add `MIGRATION_1_2` and `MIGRATION_2_3`), `app/src/main/java/com/cellrecorder/app/di/DatabaseModule.kt` (prepend the two migrations to `addMigrations(...)`). No user-visible behavior change for users on v3+; fixes a crash for users upgrading from v1 or v2.
- **Test files modified:** `RecordingScreenPermissionTest.kt`, `SessionListScreenTest.kt`, `SettingsScreenTest.kt`, `MainActivityTest.kt`, `ConfigDaoTest.kt`, `ConfigRepositoryTest.kt`, `SpeedTestRecordDaoTest.kt`, `CellRecordRepositoryTest.kt`, `MigrationTest.kt`, `SessionListViewModelTest.kt`, `SettingsViewModelTest.kt`.
- **Test files added:** `SessionDetailViewModelTest.kt`, `StatisticsViewModelTest.kt`, `ReplayViewModelTest.kt`, `ImportSessionUseCaseTest.kt`, `LiveInfoScreenTest.kt`, `SessionDetailScreenTest.kt`, `ReplayScreenTest.kt`, `StatisticsScreenTest.kt`, `RecordingScreenTest.kt`.
- **Spec deltas:** `specs/instrumented-test-coverage/spec.md` (new), `specs/data/spec.md` (delta).
- **No new runtime dependencies.** Reuses existing JUnit4, MockK, Hilt testing, Compose UI test, and Room testing infra already in `app/build.gradle.kts`.
- **Out of scope:** `RecordingService`, `PointRecorder`, `CellInfoCollector`, `RecordingViewModel`, `LiveInfoViewModel`, `IndoorPositionCollector`, `SensorFusionCollector` — these need heavy fake/refactor scaffolding and are deferred to a follow-up change.
- **Estimated scale:** ~115-135 new tests, ~9 new test files, ~10 modified test files, ~30-50 lines of production code (the two migrations), ~1800-2600 total LoC.
