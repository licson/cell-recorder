## Why

Current test coverage (~30%) is concentrated in the `data` layer and two `domain` hotspots. The `service` (~10%) and `ui` (~15%) layers are largely untested, and many pure-logic components with no Android dependencies have zero unit tests. This creates a fragile foundation for future development: regressions in band formatting, CSV/GeoJSON parsing, GPS state transitions, speed-test math, and indoor step detection are only caught at runtime (if at all). We need a fast, deterministic unit-test foundation that catches regressions at dev stage before they reach a device.

## What Changes

- Add JUnit 5 unit tests for pure-logic domain components that currently have no tests: `BandResolver`, `SpeedTestConfigParser`, `SpeedTestServerSelector` (pure helpers), `SpeedTestAnalyticsEngine`, `CsvRecordParser`, `GeoJsonRecordParser`, `GpsStateMachine`
- Extend existing unit tests to close documented gaps: `SessionAnalyticsEngine` (PCI insights, correlation bins, indoor mode, mobility classification, SINR histogram bin counts, jitter/stddev), `ExportSessionUseCase` (indoor mode branch, `csvField()` escaping edge cases), `PingEngine` (`pingFlow()` streaming behavior with mocked `Process`)
- Add unit tests for pure methods on `PermissionHelper` that do not require an `Activity` (`missingAllForMode`, `allGrantedForMode`, `allForegroundGranted`, `foregroundPermissions`, `indoorPermissions`, `requiredPermissions` and their `Build.VERSION` branching)
- Extract pure helpers from Android-coupled classes and unit-test the extracted logic:
  - `SimLiveStateMapper` — extract the `CellRecordSnapshot → SimLiveState` mapping currently duplicated in `RecordingViewModel` and `LiveInfoViewModel`
  - `PointRecorder` indoor discontinuity shift math
  - `SensorFusionCollector` heading-smoothing and speed-delta math
  - `IndoorPositionCollector` step-detection algorithm (sensor + accelerometer fallback)
- All new tests run on the JVM (no device, no Robolectric) and execute in milliseconds, fitting the established JUnit 5 + MockK + Turbine unit-test pattern already used by `SessionAnalyticsEngineTest` and `PingEngineTest`.

## Capabilities

### New Capabilities

- `test-foundation`: Defines the regression-test contract for pure-logic components — which components MUST have unit tests, the constraint that pure logic is testable in isolation without Android framework dependencies, and the behavioral guarantee that extracting pure helpers from Android-coupled classes MUST NOT change observable behavior.

### Modified Capabilities

None. The refactors in this change (extracting `SimLiveStateMapper` and math helpers) preserve observable behavior; no spec-level requirement of an existing capability changes.

## Impact

- **New test files** (~12): under `app/src/test/java/com/cellrecorder/app/...` covering `domain/model/`, `domain/speedtest/`, `domain/usecase/import_/`, `domain/analytics/`, `service/`, `ui/shared/`
- **Extended test files** (~3): `SessionAnalyticsEngineTest.kt`, `ExportSessionUseCaseTest.kt`, `PingEngineTest.kt`, `PermissionHelperTest.kt`
- **Source refactors** (behavior-preserving): extract `SimLiveStateMapper` from `RecordingViewModel` + `LiveInfoViewModel`; extract pure math helpers from `PointRecorder`, `SensorFusionCollector`, `IndoorPositionCollector`
- **No dependency changes**: JUnit 5, MockK, Turbine, and `kotlinx-coroutines-test` are already in `testImplementation`
- **No data model, API, permission, or UI behavior changes**
- **Build time impact**: negligible — JVM unit tests add seconds to `./gradlew test`
