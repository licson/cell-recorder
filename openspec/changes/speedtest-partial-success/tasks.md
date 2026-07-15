## 1. Data Model & Migration

- [x] 1.1 Add `downloadSucceeded: Boolean` and `uploadSucceeded: Boolean?` fields to `SpeedTestRecordEntity`; remove the `succeeded` field
- [x] 1.2 Bump `AppDatabase` `@Database(version = 16)` with `exportSchema = true`
- [x] 1.3 Implement `Migration(15, 16)` in `DatabaseModule.addMigrations(...)` using the table-rebuild pattern (add new columns, backfill from `downloadBps`/`uploadBps`, drop `succeeded`)
- [x] 1.4 Add row-seeded instrumented test verifying: legacy `succeeded=false, downloadBps!=null` → `downloadSucceeded=true`; legacy `SKIPPED_WIFI` row → `downloadSucceeded=false, uploadSucceeded=null`; surviving columns round-trip
- [x] 1.5 Generate and commit the v16 schema JSON in `app/schemas/`

## 2. Engine: Per-Phase Success & Cache Policy

- [x] 2.1 Replace `succeeded: Boolean` with `downloadSucceeded: Boolean` and `uploadSucceeded: Boolean?` on `SpeedTestResult`
- [x] 2.2 Update `SpeedTestEngine.runTest()` to set per-phase flags at every return path: success, partial (download ok / upload fail), download fail, instant bail-out (WiFi, config, selection), exception
- [x] 2.3 Gate `invalidateCache()` on download failure or escaping exception; remove the upload-only-failure invalidation path
- [x] 2.4 Update unit/instrumented tests under `SpeedTestEngineRealNetworkTest` and engine unit tests to assert per-phase flags and cache-preserved-on-upload-failure behavior
- [x] 2.5 Update `SettingsViewModel` manual launch path to consume per-phase result fields

## 3. Engine: Pre-Upload Probe

- [x] 3.1 Implement `SpeedTestMeasurer.probeUpload(serverUrl, httpClient)` issuing a ~1 KB POST with 5-second timeout; return success/failure with reason
- [x] 3.2 Wire probe call in `SpeedTestEngine.runTest()` before `measureUpload()`: on probe failure, skip `measureUpload`, set `uploadSucceeded=false`, `uploadBps=null`, record `"Upload probe failed: <reason>"`, do not invalidate cache
- [x] 3.3 Emit a `SpeedTestDebugEvent` with phase `probe`, status `info`/`ok`/`fail` (add `probe` to the phase constant set in `SpeedTestDebugEvent`)
- [x] 3.4 Update debug card UI (Settings) to render the new `probe` phase events
- [x] 3.5 Add engine test for probe-skip path (mock/seed a failing probe, assert upload measurement is not invoked, cache retained)

## 4. Recording Service & Persistence

- [x] 4.1 Update `RecordingService` speedtest insert (around line 491) to populate `downloadSucceeded` and `uploadSucceeded` from `SpeedTestResult`; remove `succeeded` field
- [x] 4.2 Update `RecordingServiceTest` instrumented tests that build `SpeedTestRecordEntity` rows and call `runTest()` to use per-phase fields
- [x] 4.3 Update `TestDataFactory.speedTestRecord(...)` helper to use per-phase fields

## 5. Analytics

- [x] 5.1 Update `SpeedTestAnalyticsEngine.analyze()` to filter download samples by `downloadBps != null` (not `succeeded`); filter upload samples by `uploadBps != null`
- [x] 5.2 Replace `successRate` computation: `downloadSucceeded=true` count divided by non-WiFi-skipped records
- [x] 5.3 Update correlation computations (`computeRsrpCorrelation`, `computeRatCorrelation`, `computeSimCorrelation`, upload variant) to filter by per-phase non-null bps instead of `succeeded`
- [x] 5.4 Update `SpeedTestAnalyticsEngineTest` to cover: partial-success row contributes to download stats only; legacy `succeeded=false, downloadBps!=null` row is retroactively included; upload-disabled rows contribute to download stats only
- [x] 5.5 Update global statistics path (Statistics screen / `StatisticsViewModel`) to consume per-phase fields; update `StatisticsViewModelTest` and `StatisticsScreenTest` row factories

## 6. CSV Export

- [x] 6.1 Update `ExportSpeedTestUseCase.exportCsv()` to emit `download_succeeded` and `upload_succeeded` columns in place of `succeeded`; order per spec
- [x] 6.2 Update `ExportSpeedTestUseCaseTest` to assert new column headers and values (true/false/empty for upload_succeeded)

## 7. UI Consumers

- [x] 7.1 Update `SessionDetailViewModel` and any UI rendering of speedtest `succeeded` to use per-phase fields (session detail analytics panel)
- [x] 7.2 Update replay marker rendering (`ReplayScreen.kt:549` and related) to derive "succeeded" from `downloadSucceeded || (uploadSucceeded == true)` or per-phase visual encoding (download color, upload color)
- [x] 7.3 Update any speedtest status strings / badges in recording live UI to reflect partial-success ("Download only", "Upload skipped", etc.)

## 8. Documentation & Release

- [x] 8.1 Update `CHANGELOG.md` under a new `## [Unreleased]` section with `Changed` (per-phase success, retroactive re-include), `Fixed` (cache no longer invalidated on upload-only failure), and `Breaking` (CSV `succeeded` column replaced) entries
- [x] 8.2 Update `openspec/design.md` architecture notes for the speedtest engine (replace `succeeded` references with per-phase fields; mention the pre-upload probe phase)
- [x] 8.3 Run `openspec validate speedtest-partial-success` and resolve any reported issues
- [x] 8.4 Run `./gradlew clean` and `./gradlew assembleDebug` to verify a clean build
- [x] 8.5 Run unit tests: `./gradlew test` (or the project's test command)
- [x] 8.6 Run instrumented tests covering the migration and DAO: `./gradlew connectedAndroidTest` for the migration test (or note device availability)
