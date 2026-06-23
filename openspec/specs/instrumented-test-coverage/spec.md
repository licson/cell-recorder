# Instrumented Test Coverage Specification

## Purpose

Defines the coverage contract for the instrumented (androidTest) test layer: ViewModels, Compose screens, UseCases, DAOs, repositories, and migrations. Ensures integration regressions are caught during development instead of in the field.

## Requirements

### Requirement: Instrumented test coverage for ViewModels with DB-only dependencies

The system SHALL have an instrumented integration test (`@HiltAndroidTest` + `HiltAndroidRule` + real in-memory Room DB) for every ViewModel whose dependencies are entirely DB-backed or already-testable (no direct Android system service usage, no real network/sensor calls). Tests MUST verify the ViewModel's state flows, use case wiring, and repository interactions end-to-end through the Hilt graph.

#### Scenario: ViewModel with DB-only dependencies has an integration test

- **WHEN** a ViewModel's constructor parameters are all `@Inject`-provided by Hilt and resolve to repositories, use cases, or already-tested pure-logic components
- **THEN** an instrumented test file named `<ViewModelName>Test.kt` MUST exist in `app/src/androidTest/java/com/cellrecorder/app/`
- **AND** the test MUST use `@HiltAndroidTest`, `HiltAndroidRule`, `MainDispatcherRule`, and the real in-memory Room DB from `TestDatabaseModule`
- **AND** the test MUST construct the ViewModel manually (not via `hiltViewModel()`) to keep use case wiring explicit

#### Scenario: ViewModel requiring heavy fake scaffolding is deferred with rationale

- **WHEN** a ViewModel's constructor parameters include concrete Android system services (e.g., `SubscriptionManager`, `SensorManager`) or classes that spawn real subprocesses (e.g., `PingEngine` ICMP) or real sensors (e.g., `IndoorPositionCollector`, `SensorFusionCollector`)
- **THEN** the ViewModel's integration test MAY be deferred to a follow-up change
- **AND** the deferral MUST be documented in `design.md` with the prerequisite refactor (typically interface extraction) needed to enable testing

### Requirement: Compose UI smoke test for every screen

The system SHALL have a Compose UI smoke test for every screen-level `@Composable` in the app. A smoke test MUST verify the screen composes without crashing and that key UI elements are visible given a sample state. Smoke tests MUST NOT be required to assert deep state transitions or complex interactions.

#### Scenario: Screen has a smoke test

- **WHEN** a screen-level `@Composable` (e.g., `SessionListScreen`, `SettingsScreen`, `LiveInfoScreen`, `SessionDetailScreen`, `ReplayScreen`, `StatisticsScreen`, `RecordingScreen`) exists in production code
- **THEN** a `<ScreenName>Test.kt` file MUST exist in `app/src/androidTest/java/com/cellrecorder/app/ui/`
- **AND** the test MUST use `createAndroidComposeRule<ComponentActivity>` (or `createAndroidComposeRule<MainActivity>` for activity-bound screens) with manual ViewModel construction
- **AND** the test MUST assert at least one key UI element is rendered with sample state

#### Scenario: Screen test must not be @Ignore'd without a verified root cause

- **WHEN** a Compose UI test is annotated `@Ignore`
- **THEN** the @Ignore annotation's reason string MUST include a verified root-cause diagnosis (not speculation)
- **AND** the diagnosis MUST reference a captured stack trace, failing test output, or a documented framework limitation with a citation
- **AND** speculative diagnoses (e.g., "process isolation" without evidence of a separate OS process) MUST NOT be accepted

### Requirement: UseCase integration test for cross-component orchestrators

The system SHALL have an instrumented integration test for every UseCase that orchestrates multiple components (e.g., parsers + repositories + state updates). Pass-through UseCases (single delegation to a repository or already-tested component) do not require a dedicated test — they are exercised transitively.

#### Scenario: Orchestrating UseCase has an integration test

- **WHEN** a UseCase's `invoke()` calls more than one repository, parser, or state holder
- **THEN** an instrumented test file named `<UseCaseName>Test.kt` MUST exist in `app/src/androidTest/java/com/cellrecorder/app/domain/usecase/`
- **AND** the test MUST exercise the end-to-end pipeline (e.g., for `ImportSessionUseCase`: parse CSV/GeoJSON → persist to repos → refresh point count → set endedAt) against a real in-memory Room DB
- **AND** the test MUST cover at least one happy path and one error path per supported format

### Requirement: DAO tests round-trip every entity field

The system SHALL have DAO tests that round-trip every field of each Room entity through insert and query. Tests that only verify default values or a subset of fields are insufficient.

#### Scenario: Every entity field is round-tripped through the DAO

- **WHEN** a Room entity (e.g., `AppConfigEntity`, `SessionEntity`, `CellRecordEntity`, `SpeedTestRecordEntity`) has N persistent fields
- **THEN** the corresponding `<Entity>DaoTest.kt` MUST contain at least one test that inserts an entity with non-default values for all N fields
- **AND** the test MUST query the entity back and assert every field's value matches what was inserted

### Requirement: Repository tests cover all public methods through the repository layer

The system SHALL have repository tests that exercise every public method of each repository through the repository interface (not just at the DAO level). Methods tested only at the DAO level are not sufficient for repository coverage.

#### Scenario: Every public repository method has a repository-layer test

- **WHEN** a Repository class exposes M public methods
- **THEN** the corresponding `<Name>RepositoryTest.kt` MUST contain at least one test per public method that invokes the method through the repository and asserts the observable outcome (return value, DB state, or flow emission)
- **AND** aggregate methods (e.g., `getBandDistribution`, `getSuccessRate`, `batchResplit`) MUST assert specific values, not just non-null

### Requirement: Migration tests verify row-level transformations and cover the full version chain

The system SHALL have migration tests that seed rows in the source-version database, run the migration, and assert the rows are present (with transformed values where applicable) in the destination-version database. Schema-only validation is necessary but not sufficient. The migration test chain MUST cover every released schema version to the current version.

#### Scenario: Migration test seeds rows and verifies transformations

- **WHEN** a `Migration(startVersion, endVersion)` is registered in `DatabaseModule.addMigrations(...)`
- **THEN** `MigrationTest.kt` MUST contain a test that creates a v`startVersion` database, seeds a representative row in each affected table, runs the migration, and asserts the row's data round-trips to v`endVersion` with the expected transformations applied

#### Scenario: Migration test chain covers every released schema version

- **WHEN** schema JSONs exist in `app/schemas/com.cellrecorder.app.data.local.AppDatabase/` for versions 1 through N
- **THEN** `MigrationTest.kt` MUST contain tests for each step migration `i → i+1` for `i` from 1 to `N-1`
- **AND** `MigrationTest.kt` MUST contain a full-chain test from version 1 to version N
- **AND** any gap in the chain (a missing `Migration` in production code) MUST be fixed before the test chain is considered complete

#### Scenario: Destructive fallback must not be used

- **WHEN** the app is built with `Room.databaseBuilder(...)`
- **THEN** `fallbackToDestructiveMigration()` MUST NOT be called
- **AND** `addMigrations(...)` MUST include a `Migration` object for every version step from the earliest released version to the current `@Database(version = N)`

### Requirement: Version-dependent test assertions use dynamic values, not hardcoded literals

The system SHALL use dynamic build-config values (e.g., `BuildConfig.VERSION_NAME`) for assertions that depend on the app version, version code, or any value that changes with release. Hardcoded literals in such assertions are prohibited because they drift on release.

#### Scenario: Version assertion uses BuildConfig

- **WHEN** a test asserts that a string contains the app version (e.g., `getVersionDisplay()` returns a string containing the version name)
- **THEN** the assertion MUST reference `BuildConfig.VERSION_NAME` (or equivalent dynamic value)
- **AND** the assertion MUST NOT use a hardcoded literal like `"1.0.3"` or `"1.2.0"`
