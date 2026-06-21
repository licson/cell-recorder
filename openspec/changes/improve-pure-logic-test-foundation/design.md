## Context

Test coverage analysis shows ~30% overall coverage, concentrated in the `data` layer (~80%) and two `domain` hotspots (`SessionAnalyticsEngine`, `PingEngine` helpers). The `service` (~10%) and `ui` (~15%) layers are largely untested. Critically, many pure-logic components with **zero Android dependencies** have no unit tests at all, and several Android-coupled classes embed pure-logic math that cannot be tested without extraction.

The project already establishes a JUnit 5 + MockK + Turbine pattern in `app/src/test/` (see `SessionAnalyticsEngineTest`, `PingEngineTest`, `PingSlidingWindowTest`, `LocationCollectorTest`). These dependencies are present in `testImplementation`. The JVM unit tests run in milliseconds via `./gradlew test`, versus the multi-minute device/emulator round-trip for `./gradlew connectedCheck`.

The `data` layer's instrumented tests (Room DAOs + Hilt repositories) are healthy and out of scope. The `@Ignore`'d Compose UI tests (`SessionListScreenTest`, `SettingsScreenTest`, `RecordingScreenPermissionTest`) and the `noop()` `MainActivityTest` are blocked by an unresolved test-runner process-isolation issue and are explicitly **out of scope** for this change.

A separate in-flight change (`improve-5g-nsa-4g-ca-ui`) will touch `RecordingViewModel`, `LiveInfoViewModel`, `SessionAnalyticsEngine.computeBandDistribution`, and `BandResolver.formatBand` usage. Those components are currently untested or under-tested, so landing a regression net before that work proceeds is the immediate motivation.

## Goals / Non-Goals

**Goals:**
- Establish a fast, deterministic JVM unit-test foundation for pure-logic components across `domain/`, `service/`, and `ui/shared/`
- Close documented gaps in existing test files (`SessionAnalyticsEngine`, `ExportSessionUseCase`, `PingEngine`, `PermissionHelper`)
- Extract pure helpers from Android-coupled classes so that math, parsing, and state-machine logic become testable in isolation, **without changing observable behavior**
- Specifically protect the components touched by `improve-5g-nsa-4g-ca-ui` (`BandResolver`, `SessionAnalyticsEngine.computeBandDistribution`, the `CellRecordSnapshot → SimLiveState` mapping) with tests before that change proceeds

**Non-Goals:**
- Adding Compose UI tests or fixing the `@Ignore`'d test infrastructure (separate change)
- Adding Robolectric or unit-testing `Activity`/`Context`-dependent code (would require a new test dependency and a philosophical shift)
- Directly testing Hilt `@HiltViewModel` classes as JVM unit tests (their constructors take `Context`/`CellInfoCollector`; these remain `androidTest` territory — but their pure-logic helpers become unit-testable via extraction)
- Testing `RecordingService` (582 LOC, the most complex class) end-to-end — only its extractable pure helpers (`movePoint`, `calculateDistance`) are in scope
- Hitting a specific coverage percentage metric — the goal is regression-catching nets, not a number
- Modifying any user-facing behavior

## Decisions

### Decision 1: Reuse the established JUnit 5 + MockK + Turbine test pattern

**Choice:** All new unit tests live under `app/src/test/` using JUnit 5 (`org.junit.jupiter.api.Test`), MockK for mocking, and Turbine for `Flow` assertions, exactly as `SessionAnalyticsEngineTest` and `PingEngineTest` do.

**Rationale:** These dependencies are already wired. The pattern is proven by the highest-quality existing test (`SessionAnalyticsEngineTest`, 510 LOC, exhaustive `@Nested` classes). Introducing a different framework (e.g., kotest, mockito-kotlin) would fragment the test codebase.

**Alternatives considered:**
- *Robolectric* — would let us test `Context`/`Activity`-dependent code on the JVM, but adds a heavy dependency, requires AndroidManifest awareness, and conflicts with the established JUnit 5 setup. Defer to a separate decision.
- *Expanding `androidTest`* — instrumented tests are slow (minutes vs milliseconds) and require a device/emulator, defeating the "fast feedback at dev stage" goal.

### Decision 2: Extract pure helpers as `object` singletons or top-level functions, not new classes

**Choice:** Pure-logic extraction follows one of two minimal patterns:
- **`object` with pure functions** — for stateless logic (e.g., `SimLiveStateMapper`, `BandResolver`-style helpers). Matches the existing `BandResolver` and `SpeedTestAnalyticsEngine` style.
- **Top-level `fun`** — for small, single-purpose transforms (e.g., `movePoint()`, `calculateDistance()` extracted from `RecordingService`).

The original Android-coupled class delegates to the extracted helper; its public API and observable behavior do not change.

**Rationale:** Minimizes API-surface change, keeps Hilt/DI wiring untouched, and makes the extracted logic trivially importable into tests.

**Alternatives considered:**
- *Extract into a new `@Singleton` Hilt class* — adds DI ceremony for stateless logic; not worth it.
- *Keep logic inline and test via reflection* — fragile and doesn't document the testability contract.

### Decision 3: `SimLiveStateMapper` is a new `object` shared by both ViewModels

**Choice:** Create `object SimLiveStateMapper { fun map(snapshot: CellRecordSnapshot, simSlotIndex: Int, plmn: String): SimLiveState }`. Both `RecordingViewModel.populate` and `LiveInfoViewModel.populate` call it, removing the current duplication.

**Rationale:** The upcoming `improve-5g-nsa-4g-ca-ui` change (tasks 1.3 + 1.4) explicitly duplicates new field-population logic across both ViewModels. Extracting the mapper **before** that change lands means the new fields are populated in one place and tested once. This is a prerequisite refactor, not a coincidental one.

**Migration:** The mapper takes the snapshot + the `simSlotIndex`/`plmn` (which still come from `SubscriptionManager` in each ViewModel). The Android-dependent `SubscriptionManager` lookup stays in the ViewModel; only the pure mapping is extracted.

**Alternatives considered:**
- *Test each ViewModel via `androidTest`* — slower, doesn't dedup, and the upcoming change would still need to duplicate the new field logic in two places.
- *Leave duplication, add no mapper* — the test gap remains, and the upcoming change doubles down on the duplication.

### Decision 4: Test `PingEngine.pingFlow()` by mocking `Process` and `Runtime.exec`

**Choice:** Use MockK to `mockkStatic(Runtime::class)` and return a mocked `Process` whose `inputStream` is a `ByteArrayInputStream` of sample ping output lines. Assert that `pingFlow()` emits `PingResult` items matching the parsed lines, handles `null` lines (process restart), and completes on `awaitClose`.

**Rationale:** `pingFlow()` is the production streaming entry point; the existing `PingEngineTest` only tests internal helpers. The streaming/restart/cleanup behavior is exactly what a regression net should cover.

**Risks:** `Runtime.exec` mocking is fiddly; if it proves unstable, fall back to testing only the parse helpers + a thin integration smoke test via `androidTest`. Document the fallback in `tasks.md`.

### Decision 5: `PermissionHelper` pure methods get direct unit tests; `Activity`-dependent methods stay `@Ignore`'d

**Choice:** Add unit tests for the pure-logic `PermissionHelper` methods: `foregroundPermissions()`, `backgroundPermissions()`, `indoorPermissions()`, `requiredPermissions()`, `allGranted()`, `allGrantedForMode()`, `allForegroundGranted()`, `allBackgroundGranted()`, `allIndoorGranted()`, `missingForegroundPermissions()`, `missingIndoorPermissions()`, `missingBackgroundPermissions()`, `missingPermissionsForMode()`, `missingAllForMode()`. Use `mockkStatic(Build.VERSION::class)` to test the `SDK_INT` branching for background-location permissions.

`openAppSettings()` and the `Activity`-based `decidePermissionState` overload remain covered (when they run) by the `@Ignore`'d `RecordingScreenPermissionTest`. Adding Robolectric to unit-test them is out of scope.

**Rationale:** These pure methods underpin the entire app's permission gating; a regression in `missingAllForMode` would silently break recording startup. They are trivially JVM-testable today.

### Decision 6: Scope the service-class math extraction to four named targets

**Choice:** Extract pure helpers from exactly four service classes:
1. `PointRecorder` — indoor discontinuity shift logic (the `ArrayDeque<Int>` shift when `_recordedPath` overflows `MAX_PATH_SIZE`)
2. `SensorFusionCollector` — heading delta smoothing (`0.85 * old + 0.15 * raw` + 360° wrap) and speed delta exponential decay (`tau = 10s`, clamp to ±0.5×initialSpeed)
3. `IndoorPositionCollector` — step detection (accelerometer fallback `STEP_THRESHOLD = 1.15×gravity`, 20-sample baseline calibration, 350ms cooldown) and drift rate (`0.02 + elapsedMin * 0.004`, max 0.20)
4. `RecordingService` — `movePoint()` (haversine extrapolation) and `calculateDistance()` (haversine)

**Rationale:** These are the only service classes with non-trivial pure math. `CellInfoCollector` builds `CellRecordSnapshot` from NetMonster `INetMonster` calls — testable but heavy on mocking; defer. `RecordingNotificationHelper`, `CallbackHandlerThread`, `RecordingStateManager`, `RecordingState` are trivial.

**Alternatives considered:**
- *Mock `INetMonster` to test `CellInfoCollector`* — feasible but high-effort for moderate value; defer to a follow-up.
- *Full `RecordingService` integration test* — out of scope (Non-Goal); only the two pure helpers are extractable.

## Risks / Trade-offs

- **[Refactor changes behavior]** → Mitigation: write the new unit tests *against the extracted helper* first (characterization tests capturing current output), then extract, then re-run. Any divergence is caught before the refactor lands. Keep extraction purely mechanical (move code, add `object` wrapper, delegate from original).
- **[`PingEngine.pingFlow` mocking proves unstable]** → Mitigation: Decision 4 documents a fallback (test parse helpers only, defer streaming test to `androidTest`). The fallback is acceptable because the parse helpers are the high-value logic.
- **[Test maintenance burden grows]** → Mitigation: tests assert on public API and observable outputs, not implementation details. Refactors that preserve behavior should not require test edits. Use `@Nested` classes to group by method (matches `SessionAnalyticsEngineTest` style).
- **[`Build.VERSION` mocking via `mockkStatic` is brittle across SDK upgrades]** → Mitigation: pin the mocked `SDK_INT` explicitly per test; document the two branches (below API 30 vs API 30+) in the test class doc. min API is 30, so the `< 30` branch is dead code in production but still worth pinning for safety.
- **[Scope creep into `RecordingService`]** → Mitigation: Non-Goal explicitly limits service extraction to `movePoint` + `calculateDistance`. If the implementer finds more pure helpers worth extracting, raise it as a follow-up rather than expanding this change.
- **[Interaction with the in-flight `improve-5g-nsa-4g-ca-ui` change]** → Mitigation: this change should land **first**. The `SimLiveStateMapper` extraction (Decision 3) and `BandResolver`/`SessionAnalyticsEngine` tests directly de-risk the other change's tasks 1.3, 1.4, 5.1-5.3. If both changes are in flight simultaneously, the mapper extraction must be reconciled manually (the other change's task 1.3/1.4 should call the new mapper rather than re-duplicating).
