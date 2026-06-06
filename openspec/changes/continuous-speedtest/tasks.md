## 1. Data Layer

- [x] 1.1 Create `SpeedTestRecordEntity` Room entity with all fields per spec
- [x] 1.2 Create `SpeedTestRecordDao` with CRUD operations, `getBySessionId()`, `getBySessionIdAndTimestampRange()`
- [x] 1.3 Create `SpeedTestRecordRepository` wrapping the DAO
- [x] 1.4 Add speedtest config fields to `AppConfigEntity` (`speedTestEnabled`, `speedTestIntervalMs`, `speedTestUploadEnabled`, `speedTestSecure`, `speedTestServerId`)
- [x] 1.5 Create database migration 9→10 (CREATE TABLE speed_test_records + ALTER TABLE app_config)
- [x] 1.6 Update `AppDatabase.CALLBACK.onCreate` to seed new config columns
- [x] 1.7 Register `SpeedTestRecordDao` in `DatabaseModule` Hilt DI

## 2. Speedtest Engine — Core Protocol

- [x] 2.1 Create `SpeedTestConfigParser` — parse `speedtest-config.php` XML using `XmlPullParser` into config data class (client lat/lon, download/upload thread counts, test durations, chunk sizes)
- [x] 2.2 Create `SpeedTestServerInfo` data class (id, name, host, url, lat, lon, sponsor, latencyMs)
- [x] 2.3 Create `SpeedTestServerSelector` — fetch server list XML from primary URL (with fallbacks), compute Haversine distance, sort top 5 closest, HTTP ping each ×3, select best, cache per session
- [x] 2.4 Create `SpeedTestMeasurer` — download measurement via HTTP streaming GET with coroutine semaphore concurrency; upload measurement via HTTP streaming POST with `content1=` payload
- [x] 2.5 Create `SpeedTestResult` data class (downloadBps, uploadBps, serverId, serverName, serverHost, serverLocation, succeeded, errorMessage)
- [x] 2.6 Create `SpeedTestEngine` — orchestrates full protocol: config fetch → server select (cached) → download → upload (if enabled) → result; handles WiFi skip, single-test guarantee, and failure invalidation

## 3. Service Integration

- [x] 3.1 Integrate `SpeedTestEngine` as a coroutine job in `RecordingService` alongside pingJob; launch when config.speedTestEnabled is true, cancel on stop
- [x] 3.2 Capture cell snapshot (`cellInfoCollector.snapshots()`) at start of each test for correlation fields
- [x] 3.3 Write `SpeedTestRecordEntity` to DB after each test completes
- [x] 3.4 Update `RecordingState` with speedtest status fields (`speedTestStatus`, `lastSpeedTestDownloadBps`, `lastSpeedTestUploadBps`)

## 4. Recording Screen UI

- [x] 4.1 Update `LiveStatsBar` to display speedtest status: idle/running/completed/failed/skipped WiFi with download/upload speeds
- [x] 4.2 Observe new `RecordingState` speedtest fields in `RecordingScreen`

## 5. Settings UI

- [x] 5.1 Create `SpeedTestEulaDialog` composable with Speedtest.net terms links, data usage warning, Accept/Decline buttons
- [x] 5.2 Add "Speed Test" card to `SettingsScreen` with toggle, EULA dialog flow, upload toggle, interval picker, server ID input
- [x] 5.3 Add speedtest config update methods to `SettingsViewModel`

## 6. Session Analytics

- [x] 6.1 Create `SpeedTestSessionAnalytics` data class (avgDownloadBps, p95DownloadBps, avgUploadBps, p95UploadBps, successRate, downloadByRsrp, downloadByRat, downloadBySim, downloadHistogram)
- [x] 6.2 Create `SpeedTestAnalyticsEngine` — pure computation over `List<SpeedTestRecordEntity>` producing `SpeedTestSessionAnalytics`; group by RSRP bins, RAT, and SIM
- [ ] 6.3 Add `SpeedTestSessionAnalytics?` to `SessionDetailViewModel` — load speedtest records per session and compute analytics alongside cell record analytics
- [ ] 6.4 Add conditional "Speed Test" section to `AnalyticsPanel` showing summary card + correlation bars + histogram
- [x] 6.5 Add `rsrpDownload`, `ratDownload`, `simDownload`, `rsrpUpload` fields to `CorrelationBins` data class
- [x] 6.6 Add speedtest global stats (`StateFlow<SpeedTestGlobalStats?>`) to `StatisticsViewModel` with conditional "Speed Test Overview" card in `StatisticsScreen`

## 7. Replay Screen

- [x] 7.1 Add speedtest records loading to `ReplayViewModel` — load `SpeedTestRecordEntity` list per session, compute marker positions mapped to cell record timeline indices
- [x] 7.2 Add colored speedtest markers to `RatTimelineBar` in `ReplayScreen` — color-coded by download speed (red→yellow→green gradient)
- [x] 7.3 Add speedtest summary card composable showing stats from nearest markers

## 8. Export

- [x] 8.1 Create `ExportSpeedTestUseCase` — generate `session_name_speedtest.csv` with columns per spec
- [ ] 8.2 Integrate into session export flow — include speedtest CSV when records exist

## 9. Spec Sync

- [x] 9.1 Update `openspec/specs/recording/spec.md` with speedtest lifecycle requirements
- [x] 9.2 Update `openspec/specs/connectivity/spec.md` with speedtest protocol requirements
- [x] 9.3 Update `openspec/specs/analytics/spec.md` with speedtest correlation requirements
- [x] 9.4 Update `openspec/specs/sessions/spec.md` with replay marker requirements
- [x] 9.5 Update `openspec/specs/service/spec.md` with speedtest notification requirements
- [x] 9.6 Update `openspec/specs/ui/spec.md` with speedtest settings and live stats requirements
- [x] 9.7 Update `openspec/specs/data/spec.md` with speedtest entity and export requirements
- [ ] 9.8 Update `openspec/design.md` with speedtest architecture and data model