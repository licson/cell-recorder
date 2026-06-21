## 1. Baseline Verification

- [x] 1.1 Run `./gradlew clean && ./gradlew test` and confirm all existing unit tests pass (green baseline before adding new tests)
- [x] 1.2 Run `./gradlew lint` and confirm no new warnings introduced by test infrastructure

## 2. New Unit Tests for Pure-Logic Components (No Source Changes)

- [x] 2.1 Create `app/src/test/java/com/cellrecorder/app/domain/model/BandResolverTest.kt`: test `formatBand` prefix selection (`n` for 5G with `earfcn >= 82000` or `null`, `B` otherwise), `resolveBandNumber` fallback to `mapEarfcn`, `fallbackNrBand` for all five EARFCN ranges plus out-of-range, `mapEarfcn` dispatch by RAT prefix, `---` return when band unresolvable
- [x] 2.2 Create `app/src/test/java/com/cellrecorder/app/domain/speedtest/SpeedTestConfigParserTest.kt`: parse a sample `speedtest-config.php` XML stream, assert `downloadThreads`, `uploadSizes`, `uploadCount`, `uploadMax` derived from `ratio`/`maxChunkCount`, handle malformed XML gracefully
- [x] 2.3 Create `app/src/test/java/com/cellrecorder/app/domain/speedtest/SpeedTestServerSelectorTest.kt`: test `haversineKm` (known distances, zero-distance, antipodal), `parseServerElement` (valid XML element, missing attributes, malformed coordinates). Pure helpers only — defer latency-ping tests to `androidTest`.
- [x] 2.4 Create `app/src/test/java/com/cellrecorder/app/domain/analytics/SpeedTestAnalyticsEngineTest.kt`: test download/upload averages, `percentile` (p50/p95 of known sample sets, even/odd count), RSRP/RAT/SIM correlation bins, download histogram 8-bin distribution
- [x] 2.5 Create `app/src/test/java/com/cellrecorder/app/domain/usecase/import_/CsvRecordParserTest.kt`: test column mapping for all 30+ fields, quote-aware line splitter (commas/quotes/newlines inside quoted fields), CA-band JSON parsing (valid/malformed/missing), row-level error collection (missing required columns, malformed rows), empty input
- [x] 2.6 Create `app/src/test/java/com/cellrecorder/app/domain/usecase/import_/GeoJsonRecordParserTest.kt`: test FeatureCollection parsing, Point geometry validation (non-Point rejected, missing geometry rejected), coordinates validation, dual-key support (`enbGnbId`/`enb_gnb_id`, `anchorBand`/`anchor_band`), timestamp validation, mixed valid/invalid features
- [x] 2.7 Create `app/src/test/java/com/cellrecorder/app/service/GpsStateMachineTest.kt`: test `isFixLost()` (true after `gpsLostAtMs` set, false after fresh fix, false during settling), `extrapolationAgeSec()` (zero when `gpsLostAtMs <= 0`, positive after), `isInSettling()` (true during window, false after), `estimatedAccuracy()` growth, thread safety via concurrent calls under `ReentrantLock`

## 3. Extend Existing Unit Tests to Close Documented Gaps

- [x] 3.1 Extend `app/src/test/java/com/cellrecorder/app/domain/analytics/SessionAnalyticsEngineTest.kt`: add `@Nested` class `GeneratePciInsights` asserting "Massive MIMO Candidate", "Load Balancing Detected", "Cross-Site Handoff Impact" cards for matching PCI patterns; add `@Nested` `ComputeCorrelation` asserting `correlationBins.rsrpPing`, `rsrpLoss`, `sinrPing`, `sinrLoss` bin contents; add `@Nested` `IndoorMode` asserting handoffs empty and single INDOOR mobility segment when `recordingMode = "INDOOR"`; add `@Nested` `MobilityClassification` asserting STATIONARY/WALKING/DRIVING/TUNNEL labels directly; add `@Nested` `SeverityLevels` asserting CRITICAL/WARNING/INFO on anomalies; assert `LatencyStats.jitterMs` and `stddev` non-zero on varied input; assert SINR histogram bin counts
- [x] 3.2 Extend `app/src/test/java/com/cellrecorder/app/domain/usecase/ExportSessionUseCaseTest.kt`: add tests for indoor CSV branch (`recordingMode = "INDOOR"` — `relativeX`/`relativeY` written, `indoorMode`/`coordinateReference` flags); indoor GeoJSON branch (fake lon/lat from relative coords, properties include indoor fields); `csvField()` escaping with embedded commas, embedded double-quotes (doubled), embedded newlines, embedded carriage returns; presence of `isLocationEstimated`, `locationSource`, `anchor*` fields in CSV/GeoJSON output
- [x] 3.3 Extend `app/src/test/java/com/cellrecorder/app/domain/ping/PingEngineTest.kt`: add `@Nested` `PingFlow` that uses `mockkStatic(Runtime::class)` to return a mocked `Process` whose `inputStream` is a `ByteArrayInputStream` of sample ping output; assert emitted `PingResult` items match parsed lines (SUCCESS with latency, TIMEOUT, HOST_UNREACHABLE, PROCESS_ERROR); assert process restart on `null` line; assert `awaitClose` cleanup cancels the flow. If `Runtime.exec` mocking proves unstable across CI environments, fall back to leaving a TODO and deferring the streaming test to `androidTest` (document in the test class KDoc).
- [x] 3.4 Extend `app/src/test/java/com/cellrecorder/app/ui/shared/PermissionHelperTest.kt`: add `@Nested` classes covering `foregroundPermissions()` (returns the fixed foreground array), `indoorPermissions()` (adds indoor sensors), `requiredPermissions()`, `backgroundPermissions()` (empty when `Build.VERSION.SDK_INT < 30`, contains `ACCESS_BACKGROUND_LOCATION` when `>= 30`, via `mockkStatic(Build.VERSION::class)`); `allGranted(List<String>)` (true on empty, false on any missing); `allGrantedForMode`, `allForegroundGranted`, `allBackgroundGranted`, `allIndoorGranted`; `missingForegroundPermissions`, `missingIndoorPermissions`, `missingBackgroundPermissions`, `missingPermissionsForMode`, `missingAllForMode` (returns complement of granted set). Leave `openAppSettings()` and the `Activity`-based `decidePermissionState` overload uncovered (requires Robolectric, out of scope).

## 4. Behavior-Preserving Extractions (Characterization Test First, Then Extract)

- [x] 4.1 **SimLiveStateMapper extraction (unblocks in-flight `improve-5g-nsa-4g-ca-ui` change — do this first)**
  - [x] 4.1.1 Write a characterization test in `app/src/test/java/com/cellrecorder/app/ui/recording/SimLiveStateMapperCharacterizationTest.kt` capturing current `RecordingViewModel.populate` output for: plain 4G record, 5G NSA record with anchor fields, 4G record with two CA bands, record with all null signal fields, record with `enbOrGnbId` + `lcid` vs record with only `fullCellIdentity`
  - [x] 4.1.2 Create `app/src/main/java/com/cellrecorder/app/ui/recording/SimLiveStateMapper.kt` as `object SimLiveStateMapper { fun map(snapshot: CellRecordSnapshot, simSlotIndex: Int, plmn: String): SimLiveState }` containing the pure mapping logic (band formatting via `BandResolver.formatBand`, anchor row construction, CA bands string, `formatCellId` logic, null-safe field mapping)
  - [x] 4.1.3 Refactor `RecordingViewModel.populate` to delegate to `SimLiveStateMapper.map(...)`, keeping the `SubscriptionManager` lookup in the ViewModel; verify characterization test still passes
  - [x] 4.1.4 Refactor `LiveInfoViewModel.populate` to delegate to the same `SimLiveStateMapper.map(...)`; verify both ViewModels produce identical `SimLiveState` for identical inputs
  - [x] 4.1.5 Replace the characterization test with permanent `SimLiveStateMapperTest.kt` covering all branches (5G NSA anchor, 4G CA, both, neither, null fields, `enbOrGnbId:lcid` vs `fullCellIdentity`)

- [x] 4.2 **PointRecorder indoor discontinuity shift extraction**
  - [x] 4.2.1 Write a characterization test capturing current `_discontinuities` deque state for: path under `MAX_PATH_SIZE` (no shift), path overflowing with no discontinuities, path overflowing with discontinuity at index 0 (removed), path overflowing with discontinuities at indices 2 and 5 (shifted to 1 and 4)
  - [x] 4.2.2 Create `app/src/main/java/com/cellrecorder/app/service/IndoorDiscontinuityShifter.kt` as a pure `object` or top-level `fun shiftDiscontinuities(path: ArrayDeque<*>, discontinuities: ArrayDeque<Int>, maxSize: Int): ArrayDeque<Int>`
  - [x] 4.2.3 Refactor `PointRecorder` to call the extracted helper inside the `synchronized` block; verify characterization test still passes
  - [x] 4.2.4 Add `IndoorDiscontinuityShifterTest.kt` covering all branches plus edge cases (empty deque, single discontinuity at 0, discontinuity at `MAX_PATH_SIZE - 1`)

- [x] 4.3 **SensorFusionCollector math extraction**
  - [x] 4.3.1 Write a characterization test capturing current heading-delta smoothing for: raw delta of 5° (smoothed from 0°), raw delta of 350° (360° wrap from 10°), raw delta of 0° (no change), and current speed-delta decay for: dtSec = 0.1/1.0/10.0s, clamp behavior at `±0.5×initialSpeedMps`
  - [x] 4.3.2 Create `app/src/main/java/com/cellrecorder/app/service/SensorFusionMath.kt` as `object SensorFusionMath` with `fun smoothHeadingDelta(smoothed: Float, raw: Float): Float` and `fun decaySpeedDelta(initialSpeedMps: Float, currentDeltaMps: Float, dtSec: Float): Float`
  - [x] 4.3.3 Refactor `SensorFusionCollector` to call the extracted helpers; verify characterization test still passes
  - [x] 4.3.4 Add `SensorFusionMathTest.kt` covering all smoothing/decay/clamp branches

- [x] 4.4 **IndoorPositionCollector step detection extraction**
  - [x] 4.4.1 Write a characterization test capturing current step-detection decisions for: magnitude below `1.15×gravity` (no step), magnitude above threshold within cooldown (no step), magnitude above threshold after cooldown (step), baseline calibration (first 20 samples accumulate), drift rate at 0/5/50 minutes (0.02, 0.04, 0.20 capped)
  - [x] 4.4.2 Create `app/src/main/java/com/cellrecorder/app/service/IndoorStepDetector.kt` as `object IndoorStepDetector` with `fun isStep(filteredMagnitude: Float, gravityBaseline: Float, threshold: Float = 1.15f): Boolean`, `fun calibrateBaseline(currentBaseline: Float, sampleMagnitude: Float, sampleCount: Int): Float`, `fun driftRateForElapsedMinutes(elapsedMin: Float, base: Double = 0.02, slope: Double = 0.004, max: Double = 0.20): Double`
  - [x] 4.4.3 Refactor `IndoorPositionCollector` to call the extracted helpers; verify characterization test still passes
  - [x] 4.4.4 Add `IndoorStepDetectorTest.kt` covering all step/no-step/cooldown/baseline/drift branches

- [x] 4.5 **RecordingService `movePoint` + `calculateDistance` extraction**
  - [x] 4.5.1 Write a characterization test capturing current `movePoint` output for known (lat, lon, bearing, distance) tuples (zero distance, short distance, long distance, cardinal bearings) and `calculateDistance` for known coordinate pairs
  - [x] 4.5.2 Create `app/src/main/java/com/cellrecorder/app/service/GeoExtrapolation.kt` as `object GeoExtrapolation` with `fun movePoint(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double>` and `fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double`
  - [x] 4.5.3 Refactor `RecordingService` to call the extracted helpers; verify characterization test still passes
  - [x] 4.5.4 Add `GeoExtrapolationTest.kt` covering all branches plus edge cases (antipodal points, same point, equator, prime meridian, 90°/180°/270°/360° bearings)

## 5. Verification

- [ ] 5.1 Run `./gradlew clean && ./gradlew test` and confirm all unit tests (existing + new) pass
- [ ] 5.2 Run `./gradlew lint` and confirm no new warnings introduced by source refactors
- [ ] 5.3 Run `./gradlew assembleDebug` and confirm the app still builds (refactors did not break compilation or Hilt graph)
- [ ] 5.4 Manually verify on a device or emulator that recording, indoor mode, and live info display behave identically to before the refactor (sanity check, since no behavior change is expected)
- [ ] 5.5 Commit the change locally with a descriptive message (e.g., `Add pure-logic unit test foundation and extract testable helpers`)
