## Context

The prior `improve-pure-logic-test-foundation` change delivered 351 JVM unit tests for pure-logic components (total 443 unit tests passing), establishing fast feedback for the pure-logic surface. However, the instrumented test layer (`app/src/androidTest/`) — which validates DB wiring, ViewModel state flows, Compose UI, and Hilt graph composition — remains thin and partially broken:

- 3 Compose UI tests are `@Ignore`'d (`RecordingScreenPermissionTest`, `SessionListScreenTest`, `SettingsScreenTest`) under a vague "process isolation" reason. Investigation shows the diagnosis is wrong (see Decisions §1).
- `MainActivityTest` is a `noop()` placeholder — the test infrastructure loads but asserts nothing.
- 5 of 7 ViewModels have no integration test (`SessionDetailViewModel`, `StatisticsViewModel`, `ReplayViewModel`, `RecordingViewModel`, `LiveInfoViewModel`).
- `ImportSessionUseCase` — the orchestrator that runs parsers → 2 repos → state updates — has no test.
- 5 Compose screens have no UI test (`LiveInfoScreen`, `SessionDetailScreen`, `ReplayScreen`, `StatisticsScreen`, `RecordingScreen` UI body).
- Existing DAO/repository/ViewModel tests have documented internal gaps: only 3 of ~10 `AppConfigEntity` fields are round-tripped, `batchResplit` only asserts non-null, `MigrationTest` is schema-only (no row-seeding), `getVersionDisplay` asserts a hardcoded `"1.2.0"` literal that has drifted from the current `1.0.3`.
- 5 of 5 uncovered Compose screens are reachable via `MainActivity` nav.

Investigation during planning also surfaced a **production bug**: Room migrations `1→2` and `2→3` are missing from `AppDatabase.kt`. The schema JSONs for v1, v2, v3 are committed (proving those versions shipped), `addMigrations(...)` starts at `MIGRATION_3_4`, and `fallbackToDestructiveMigration()` is not set — so a user upgrading from v1 or v2 crashes with `IllegalStateException: A migration from X to Y was required but not found`.

This change addresses both the test gaps and the migration bug, since the bug fix and its tests belong in the same coherent unit.

## Goals / Non-Goals

**Goals:**

- Un-@Ignore the 3 misdiagnosed Compose UI tests (or, if they genuinely fail, leave them @Ignore'd with a corrected, evidence-backed explanation).
- Convert `MainActivityTest` from `noop()` to a real smoke test.
- Fill documented internal coverage gaps in 6 existing instrumented test files.
- Fix the missing `MIGRATION_1_2` and `MIGRATION_2_3` migrations in production and add row-seeded tests for the full 1→12 chain.
- Add integration tests for the 3 ViewModels whose dependencies are all DB-backed (`SessionDetailViewModel`, `StatisticsViewModel`, `ReplayViewModel`).
- Add `ImportSessionUseCaseTest` covering the end-to-end import pipeline against a real Room DB.
- Add Compose UI smoke tests for the 5 currently-uncovered screens.
- Establish a spec (`instrumented-test-coverage`) that codifies the coverage contract so future regressions are caught at review time.

**Non-Goals:**

- Add integration tests for `RecordingViewModel`, `LiveInfoViewModel`, `RecordingService`, `PointRecorder`, `CellInfoCollector`, `IndoorPositionCollector`, `SensorFusionCollector`. These require heavy fake scaffolding (concrete `@Singleton` classes — not interfaces — plus `SubscriptionManager`, `PingEngine` real ICMP, real sensors) or a prerequisite interface-extraction refactor. Deferred to a follow-up change.
- Add Compose UI tests beyond smoke-level (no deep state assertions, screenshot tests, or accessibility audits).
- Refactor production code to extract interfaces for testability (except the migration additions, which are a bug fix).
- Fix the latent bugs discovered and documented by the prior change (`CsvRecordParser` band column mapping, dead `takeIf` in `SpeedTestAnalyticsEngine.uploadByRsrp`, unreachable `INTRA_SITE_PCI_CHANGE` branch). Those are out of scope here.

## Decisions

### 1. The "process isolation" diagnosis in the @Ignore'd UI tests was partially correct — the real cause is a package resolution mismatch

**Finding:** The original @Ignore reason cited "process isolation" — `com.cellrecorder.app.test` was assumed to be a separate OS process. The investigation concluded this was wrong (it's the test APK's `applicationId`, not an OS process). However, the actual root cause is a **package resolution mismatch** that produces a similar symptom:

> `Intent in process com.cellrecorder.app resolved to different process com.cellrecorder.app.test: Intent { ... cmp=com.cellrecorder.app.test/androidx.activity.ComponentActivity }`

`ActivityScenario.launch()` checks that the resolved activity's package matches `instrumentation.getTargetContext().getPackageName()` (which is `com.cellrecorder.app`). When the test activity is registered in the test APK's manifest (whose `applicationId` is `com.cellrecorder.app.test`), the resolved component's package is `com.cellrecorder.app.test`, causing the mismatch.

**Fix applied:**
1. `RecordingScreenPermissionTest` — added `@HiltAndroidTest` + `HiltAndroidRule` (required for `@AndroidEntryPoint` activities). Launched `HiltTestActivity` (moved to the app's `debug` source set so it's registered in `com.cellrecorder.app` package, not the test package).
2. `SessionListScreenTest` and `SettingsScreenTest` — switched from `createAndroidComposeRule<ComponentActivity>()` (in test package) to `createAndroidComposeRule<HiltTestActivity>()` (in app's debug source set). Added `@HiltAndroidTest` + `HiltAndroidRule` since `HiltTestActivity` is `@AndroidEntryPoint`.
3. `MainActivityTest` — replaced `noop()` with `@HiltAndroidTest` + `createAndroidComposeRule<MainActivity>` + `GrantPermissionRule` (including `POST_NOTIFICATIONS` for API 33+) + `onNodeWithText("No sessions yet.", substring = true).assertIsDisplayed()`.
4. `HiltTestActivity` moved from `androidTest/` source set to `debug/` source set (only included in debug builds, registered in the app's package). Added `app/src/debug/AndroidManifest.xml` registering the activity. Removed it from `androidTest/AndroidManifest.xml`.

**Additional fixes during execution:**
- `SessionListScreenTest` assertions changed from `onNodeWithText` to `onNodeWithContentDescription` for icon buttons (FAB "New Session", Settings, Import, Select) since these are `contentDescription`s, not visible `Text` nodes.
- `SettingsScreenTest` section assertions changed from `assertIsDisplayed()` to `performScrollTo().assertIsDisplayed()` since the settings screen uses `verticalScroll` and sections below the fold are not initially visible.

**Alternatives considered:**
- Register `HiltTestActivity` in the app's main manifest (not debug) — rejected because it would appear in release builds.
- Use `MainActivity` without overriding `setContent` — rejected because `MainActivity.onCreate` calls `setContent`, which prevents the test from injecting its own Composable via `composeTestRule.setContent {}`.
- Use `createComposeRule()` (no Activity) — rejected because `SessionListScreen` uses `rememberLauncherForActivityResult` which requires a `ComponentActivity` context.

### 2. Defer integration tests that need heavy fake scaffolding

**Decision:** Out of scope: `RecordingViewModel`, `LiveInfoViewModel`, `RecordingService`, `PointRecorder`, `CellInfoCollector`, `IndoorPositionCollector`, `SensorFusionCollector`. Per-dependency method counts are small (≤2 each), but every injected `@Singleton` is a concrete Kotlin class (final by default — needs MockK, not hand-rolled fakes), and both `RecordingViewModel` and `LiveInfoViewModel` directly use 3 concrete Android framework classes (`Context`, `SubscriptionManager`, `SubscriptionInfo`).

The prerequisite refactor (extract interfaces for `SessionRepository`, `ConfigRepository`, `CellInfoCollector`, `RecordingStateManager`, `IndoorPositionCollector`, `PingEngine`, plus introduce a `SubscriptionInfoProvider` interface) is ~150 LoC of production change and deserves its own change.

**Alternatives considered:**
- Include both VMs with MockK + Robolectric: rejected — ~400-500 LoC of test scaffolding, adds Robolectric as a new test dependency, mixes testing paradigms.
- Extract interfaces now, add tests in this change: rejected — interface extraction is behavior-change-adjacent and doubles the scope.

### 3. Reuse the existing instrumented-test pattern, not introduce a new one

**Decision:** All new ViewModel tests use `@HiltAndroidTest` + `HiltAndroidRule` + `MainDispatcherRule` + manual ViewModel construction + Hilt-injected real in-memory Room DB. This matches `SessionListViewModelTest.kt:32-38` and `SettingsViewModelTest.kt` exactly. All new Compose UI tests use `createAndroidComposeRule<ComponentActivity>` + manual ViewModel construction, matching `SessionListScreenTest.kt`.

**Rationale:** Consistency with the existing test stack keeps the cognitive load low and lets us reuse `TestDatabaseModule`, `TestAppModule`, `MainDispatcherRule`, and `TestDataFactory`.

**Alternatives considered:**
- Inject ViewModels via Hilt (`hiltViewModel()` + `@HiltViewModel` already used in production): rejected — manual construction gives explicit control over use case wiring and matches the proven pattern.
- Use `composeTestRule` instead of `createAndroidComposeRule`: rejected — `createAndroidComposeRule` is what the existing @Ignore'd tests use; switching would force rewriting the existing test bodies.

### 4. Migration strategy: write the missing migrations, don't use destructive fallback

**Decision:** Add `MIGRATION_1_2` (ALTER TABLE `cell_records` ADD COLUMN `subscriptionId INTEGER` + `simSlotIndex INTEGER`) and `MIGRATION_2_3` (table-rebuild: CREATE `app_config_new` without `lteEnbSplitShift` and with `cellInfoRefreshIntervalSec`, INSERT SELECT, DROP old, RENAME). Prepend both to `DatabaseModule.addMigrations(...)`. Do not add `fallbackToDestructiveMigration()`.

**Rationale:** The v2→v3 migration drops a column, which SQLite on API 30 cannot do directly — the create-new/copy/drop/rename pattern is required. Using `fallbackToDestructiveMigration()` would silently wipe user data on upgrade; preserving user data is the project's existing contract (no destructive fallback is set today).

**Alternatives considered:**
- Add `fallbackToDestructiveMigration()` instead: rejected — would lose user recording history for v1/v2 users; violates the implicit data-preservation contract.
- Document the bug, defer the fix to a separate change: rejected — the fix and its tests are tightly coupled; splitting them invites the bug to regress before tests land.

### 5. `BuildConfig.VERSION_NAME` for the version assertion, not hardcoded literal

**Decision:** Replace `SettingsViewModelTest.getVersionDisplay`'s assertion `contains("1.2.0")` with `contains(BuildConfig.VERSION_NAME)`. No change to `SettingsViewModel.getVersionDisplay` production code.

**Rationale:** The literal `"1.2.0"` has already drifted from the current `1.0.3` (`app/build.gradle.kts`). A dynamic assertion is future-proof and costs nothing.

**Alternatives considered:**
- Update the literal to `"1.0.3"`: rejected — drifts again on the next release.
- Delete the version assertion: rejected — loses the format regression guard.

### 6. `MigrationTest` row-seeding approach

**Decision:** For each existing migration test (3→4 through 11→12), seed a representative row in the source-version table before running the migration, then assert the row is present (with transformed values where applicable) in the destination-version table. The schema-validation call (`runMigrationsAndValidate(..., true, ...)`) stays.

**Rationale:** Schema-only tests catch column-presence bugs but miss data-transformation bugs (e.g., a migration that drops a column the user needs, or fails to copy a column). Row-seeding catches both.

### 7. Compose UI tests are smoke tests, not deep behavioral tests

**Decision:** New Compose UI tests assert that the screen composes with a sample state and that key UI elements are visible (e.g., the session list shows when the screen loads, the playback button exists on the replay screen). They do not assert deep state transitions or complex interactions.

**Rationale:** Smoke tests catch the most common regression (screen crashes or fails to render) at low cost. Deep behavioral UI tests are expensive to write and maintain; the corresponding ViewModels have integration tests for behavior.

## Risks / Trade-offs

- **[Risk] Un-@Ignore'd UI tests may fail on the emulator for reasons other than the hypothesized Hilt setup** → Mitigation: Run them locally on the emulator first. If they fail, capture the stack trace. If the failure is a genuine Hilt/ActivityScenario blocker, update the @Ignore with the real root cause and a link to the captured trace. If the failure is a Compose test issue (text matching, threading), fix it directly. Document whichever path is taken in `design.md` after execution.

- **[Risk] `MIGRATION_2_3` (table-rebuild) is non-trivial and could itself introduce a bug** → Mitigation: Write the migration to mirror the v3 schema JSON exactly. Add a row-seeded test that asserts every surviving column's data round-trips. Cross-check the rebuilt table's columns against `app/schemas/com.cellrecorder.app.data.local.AppDatabase/3.json`.

- **[Risk] Compose UI tests for screens with heavy state (e.g., `ReplayScreen` map placeholder, `RecordingScreen` live data) may be flaky** → Mitigation: Use manual ViewModel construction with deterministic fake state (matches `SessionListScreenTest` pattern). Avoid `waitUntil` with tight timeouts; prefer `onNodeWithText` assertions on stable content.

- **[Risk] `ImportSessionUseCaseTest` may be slow because it parses real CSV/GeoJSON files** → Mitigation: Use small inline test fixtures (a few lines of CSV, a tiny GeoJSON FeatureCollection). No large file I/O.

- **[Risk] Touching `MigrationTest.kt` may break the existing schema-only tests** → Mitigation: Keep schema validation as the first assertion in each test, then add row-level assertions. Add new tests rather than modifying existing ones where possible.

- **[Trade-off] Defer `RecordingViewModel`/`LiveInfoViewModel` tests** → Accepted. The prerequisite interface-extraction refactor is the clean path; mocking 5+ concrete `@Singleton`s per VM would lock in a brittle test setup that the refactor would invalidate.

- **[Trade-off] Smoke-level UI tests miss deep regressions** → Accepted. The corresponding ViewModels have integration tests; UI smoke tests catch composition crashes, which is the highest-frequency regression class for Compose screens.

## Migration Plan

This change ships one production code migration (the two missing Room migrations) and one test infrastructure migration (un-@Ignore'ing 3 UI tests).

**Rollout:**
1. Land `MIGRATION_1_2` and `MIGRATION_2_3` + `DatabaseModule` registration. Users on v1/v2 now upgrade cleanly to v12 instead of crashing. Users on v3+ are unaffected (their migrations already run).
2. Land the un-@Ignore'd UI tests. CI already runs `connectedDebugAndroidTest` on API 30 + 34 emulators (`.github/workflows/build.yml:69-81`); the un-ignored tests will now execute. If any fail on CI, the test owner gets immediate feedback.

**Rollback:**
- If a migration is broken: revert `AppDatabase.kt` and `DatabaseModule.kt`. Users on v1/v2 return to the crash state (no worse than today); users on v3+ are unaffected. The migration test in this change would have caught the breakage before merge.
- If a UI test is flaky: re-@Ignore it with the captured failure trace. No production impact.

## Open Questions

- **Do migrations 1→2 and 2→3 have any users in the wild?** The schema JSONs for v1, v2, v3 are committed, so those versions shipped — but to how many users is unknown. The fix is cheap and protects whoever is on v1/v2; not fixing it leaves a known crash. Decision: fix regardless of user count, because the cost of the fix is small and the cost of a crash is large.
- **Should the new `instrumented-test-coverage` spec live under `ui/`, `service/`, or as a top-level capability?** Decision: top-level (`instrumented-test-coverage`). It cuts across `ui`, `data`, `service`, and `sessions`; a dedicated capability makes the coverage contract findable without hunting through domain specs.
- **Are the 5 new Compose UI tests worth the maintenance cost?** Yes — Compose screens are the most regression-prone surface (composition crashes, missing arguments, theme issues), and the existing 3 @Ignore'd tests prove the team intended to have UI coverage. Smoke tests are the lowest-cost way to deliver it.
