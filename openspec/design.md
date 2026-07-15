# Design: Cell Recorder

## Data Model (Room Entities)

```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val endedAt: Long?,
    val pointCount: Int = 0,
    val primarySimSlot: Int? = null
)

@Entity(
    tableName = "cell_records",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("timestamp")]
)
data class CellRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val latitude: Double, val longitude: Double, val altitude: Double, val accuracy: Float,
    val rat: String,
    val networkTypeCode: Int?,
    val fullCellIdentity: Long?,
    val enbOrGnbId: Long?,
    val lcid: Int?,
    val cellIdBitLength: Int?,
    val pci: Int?, val tac: Int?,
    val bandNumber: Int?, val earfcn: Int?, val bandwidthKhz: Int?,
    val rsrp: Int?, val rsrq: Int?, val sinr: Int?, val rssi: Int?, val cqi: Int?,
    val timingAdvance: Int?,
    val mcc: String?, val mnc: String?,
    val subscriptionId: Int?, val simSlotIndex: Int?,
    val avgLatencyMs: Double?, val packetLossPct: Double?,
    val isLocationEstimated: Boolean = false,
    val locationSource: String = "GPS"
)

@Entity(
    tableName = "cell_record_ca_bands",
    foreignKeys = [ForeignKey(
        entity = CellRecordEntity::class,
        parentColumns = ["id"],
        childColumns = ["cellRecordId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("cellRecordId"), Index("bandNumber")]
)
data class CellRecordCaBandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cellRecordId: Long,
    val bandNumber: Int?, val earfcn: Int?, val pci: Int?,
    val rsrp: Int?, val rsrq: Int?, val sinr: Int?, val rssi: Int?, val cqi: Int?,
    val timingAdvance: Int?
)

@Entity(
    tableName = "speed_test_records",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("timestamp")]
)
data class SpeedTestRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val downloadBps: Long?,
    val uploadBps: Long?,
    val serverName: String?,
    val serverHost: String?,
    val serverLocation: String?,
    val serverId: Long?,
    val dataSimSlotIndex: Int?,
    val ratAtTest: String?,
    val rsrpAtTest: Int?,
    val bandAtTest: Int?,
    val succeeded: Boolean,
    val errorMessage: String?,
    val networkType: String?
)

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val id: Int = 1,
    val pingDestination: String = "8.8.8.8",
    val pingIntervalMs: Long = 1000,
    val pingTimeoutMs: Long = 3000,
    val recordingIntervalMs: Long = 5000,
    val locationChangeThresholdM: Float = 10f,
    val gpsAccuracyThresholdM: Float = 50f,
    val maxRecordingDurationMin: Int = 120,
    val nrGnbBitLength: Int = 24,
    val cellInfoRefreshIntervalSec: Int = 5,
    val maxGpsLossExtrapolationSec: Int = 120,
    val handoffTimeWindowMs: Long = 5000,
    val rsrpDropThresholdDbm: Int = 15,
    val rsrpDropTimeWindowMs: Long = 10000,
    val latencySpikeSigma: Double = 3.0,
    val pciFlapWindowMs: Long = 30000,
    val pciFlapCountThreshold: Int = 3,
    val coverageGapThresholdMs: Long = 30000,
    val mobilityStationaryKmh: Float = 5f,
    val mobilityWalkingKmh: Float = 15f,
    val indoorAccuracyThresholdM: Float = 30f,
    val tunnelSignalLossThresholdMs: Long = 10000,
    val speedTestEnabled: Boolean = false,
    val speedTestIntervalMs: Long = 60000L,
    val speedTestUploadEnabled: Boolean = true,
    val speedTestSecure: Boolean = true,
    val speedTestServerId: String? = null
)
```

## Architecture & Module Layout

```
app/
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   │   ├── SessionDao.kt
│   │   │   ├── CellRecordDao.kt
│   │   │   ├── ConfigDao.kt
│   │   │   └── SpeedTestRecordDao.kt
│   │   └── entity/
│   │       ├── SessionEntity.kt
│   │       ├── CellRecordEntity.kt
│   │       ├── CellRecordCaBandEntity.kt
│   │       ├── AppConfigEntity.kt
│   │       └── SpeedTestRecordEntity.kt
│   └── repository/
│       ├── SessionRepository.kt
│       ├── CellRecordRepository.kt
│       ├── ConfigRepository.kt
│       └── SpeedTestRecordRepository.kt
├── domain/
│   ├── model/
│   │   ├── SessionSummary.kt
│   │   ├── CellRecordSnapshot.kt
│   │   ├── PingResult.kt
│   │   └── StatisticsModels.kt
│   ├── usecase/
│   │   ├── CreateSessionUseCase.kt
│   │   ├── StartRecordingUseCase.kt
│   │   ├── StopRecordingUseCase.kt
│   │   ├── GetSessionsUseCase.kt
│   │   ├── GetSessionPointsUseCase.kt
│   │   ├── GetConfigUseCase.kt
│   │   ├── UpdateConfigUseCase.kt
│   │   ├── ExportSessionUseCase.kt
│   │   ├── ExportSpeedTestUseCase.kt
│   │   ├── BatchResplitUseCase.kt
│   │   └── import_/
│   │       ├── CsvRecordParser.kt
│   │       ├── GeoJsonRecordParser.kt
│   │       └── ImportSessionUseCase.kt
│   ├── speedtest/
│   │   ├── SpeedTestEngine.kt
│   │   ├── SpeedTestConfigParser.kt
│   │   ├── SpeedTestServerSelector.kt
│   │   ├── SpeedTestMeasurer.kt
│   │   └── model/
│   │       ├── SpeedTestResult.kt
│   │       └── SpeedTestServerInfo.kt
│   ├── ping/
│   │   ├── PingEngine.kt
│   │   └── PingSlidingWindow.kt
│   └── analytics/
│       ├── SessionAnalyticsEngine.kt
│       ├── SpeedTestAnalyticsEngine.kt
│       └── model/
│           ├── SessionAnalytics.kt
│           ├── RatCoverage.kt
│           ├── BandDistItem.kt
│           ├── HistogramBin.kt
│           ├── CorrelationBin.kt
│           ├── CorrelationBins.kt
│           ├── LatencyStats.kt
│           ├── HandoffEvent.kt
│           ├── AnomalyFlag.kt
│           ├── MobilitySegment.kt
│           ├── CoverageGap.kt
│           ├── TimelineSegment.kt
│           ├── RatDistItem.kt
│           ├── InsightCard.kt
│           └── SpeedTestSessionAnalytics.kt
├── service/
│   ├── RecordingService.kt
│   ├── RecordingState.kt
│   ├── RecordingStateManager.kt
│   ├── RecordingNotificationHelper.kt
│   ├── GpsStateMachine.kt
│   ├── PointRecorder.kt
│   ├── LocationCollector.kt
│   ├── CellInfoCollector.kt
│   └── SensorFusionCollector.kt
├── ui/
│   ├── MainActivity.kt
│   ├── CellRecorderApp.kt
│   ├── navigation/
│   │   ├── Routes.kt
│   │   ├── AppNavGraph.kt
│   │   └── BottomNavBar.kt
│   ├── liveinfo/
│   │   ├── LiveInfoScreen.kt
│   │   └── LiveInfoViewModel.kt
│   ├── sessionlist/
│   │   ├── SessionListScreen.kt
│   │   └── SessionListViewModel.kt
│   ├── recording/
│   │   ├── RecordingScreen.kt
│   │   └── RecordingViewModel.kt
│   ├── detail/
│   │   ├── SessionDetailScreen.kt
│   │   ├── SessionDetailViewModel.kt
│   │   ├── MapDisplayMode.kt
│   │   ├── replay/
│   │   │   ├── ReplayScreen.kt
│   │   │   ├── ReplayViewModel.kt
│   │   │   └── MetricChart.kt
│   │   └── analytics/
│   │       ├── AnalyticsPanel.kt
│   │       ├── SignalHistogram.kt
│   │       └── CorrelationChart.kt
│   ├── statistics/
│   │   ├── StatisticsScreen.kt
│   │   └── StatisticsViewModel.kt
│   ├── analytics/components/
│   │   ├── CoverageGapList.kt
│   │   ├── AnomalyList.kt
│   │   ├── LatencyStatsCard.kt
│   │   ├── HandoffTimeline.kt
│   │   ├── ExpandableCorrelationSection.kt
│   │   ├── MetricGrid.kt
│   │   ├── MobilityBadge.kt
│   │   ├── InsightCard.kt
│   │   └── CoverageBar.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   ├── SettingsViewModel.kt
│   │   └── SpeedTestEulaDialog.kt
│   ├── map/
│   │   └── SessionMapView.kt
│   ├── shared/
│   │   ├── TooltipIconButton.kt
│   │   ├── PermissionRationaleDialog.kt
│   │   └── FormatUtils.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── di/
    ├── AppModule.kt
    ├── DatabaseModule.kt
    └── NetMonsterModule.kt
```

## NetMonster Core v1.2.0 API Corrections

| Spec assumption | Actual API |
|---|---|
| `CellLte.signal.rsrp / rsrq` is `Int?` | `Double?` (convert with `.toInt()`) |
| `CellLte.signal.snr` is `Int?` | `Double?` (mapped to `sinr`) |
| `CellNr` has `gnbId(bitLen)` / `clId(bitLen)` helpers | Not in v1.2.0 — implement manually: `nci shr (36 - bitLen)` / `nci and mask` |
| `CellNr.signal.timingAdvance` exists | Not in v1.2.0 `SignalNr` |
| `BandLte.earfcn` property | Accessed as `band.downlinkEarfcn` |
| `BandNr.arfcn` property | Accessed as `band.downlinkArfcn` |
| `networkType is LteCa` | Use `networkType is NetworkType.Lte && networkType.technology == NetworkType.LTE_CA` (value 19) |
| `networkType is Nsa` | Correct: `networkType is NetworkType.Nr.Nsa` |

## Key Architecture Decisions

- **Single module** with clean architecture packages (`data/domain/service/ui/di`)
- **Hilt** for DI; services annotated with `@Inject constructor()` to satisfy Hilt
- **osmdroid maps** wrapped in Compose `AndroidView` (View interop) with `DisposableEffect` for lifecycle management and `onRelease` for `onDetach` to prevent tile-loading thread leaks
- **Recording state** shared via `RecordingStateManager` (Hilt `@Singleton`) injected into both `RecordingService` and `RecordingViewModel`
- **Export** delegates to SAF `CreateDocument` contract; use case generates content strings
- **Import** uses `ActivityResultContracts.OpenDocument`; `CsvRecordParser` and `GeoJsonRecordParser` handle parsing
- **JUnit 5** configured via `tasks.withType<Test> { useJUnitPlatform() }` in `build.gradle.kts`
- **`kotlinx-coroutines-play-services`** dependency for `Task.await()` on `FusedLocationProviderClient`
- **Room schema**: version 10 with explicit migrations (3→4, 4→5, 5→6, 6→7, 7→8, 8→9, 9→10); `fallbackToDestructiveMigration()` NOT used; single-row `AppConfig` table seeded via `RoomDatabase.Callback.onCreate`
- **Foreign key constraints**: Enabled via `PRAGMA foreign_keys = ON` in `AppDatabase.CALLBACK.onOpen()`. All FK declarations with `ON DELETE CASCADE` are enforced at runtime
- **Multi-SIM**: `SubscriptionManager` used to enumerate active subscriptions and identify the default data SIM for ping attribution
- **Viewport rendering**: Session Detail data table renders only visible rows; off-screen rows replaced by measured-height `Spacer` boxes for scroll state preservation
- **Sensor fusion**: `SensorFusionCollector` uses `TYPE_GAME_ROTATION_VECTOR` for heading delta tracking and `TYPE_LINEAR_ACCELERATION` for speed adjustment during GPS loss. Linear acceleration transformed from device frame to world frame (ENU) via manual 3×3 matrix multiplication, projected onto current heading, integrated into speed delta with exponential decay (τ=10s) and ±50% clamping. GPS extrapolation uses `movePoint` Haversine calculation
- **RecordingService idempotency**: `startRecording()` cancels existing jobs before creating new ones; `onStartCommand` guards against calling `startRecording()` when `recordingJob.isActive == true`; session ID guard uses `> 0` to reject default of 0
- **Notification content intent**: Uses `FLAG_ACTIVITY_CLEAR_TOP \| FLAG_ACTIVITY_SINGLE_TOP`; `MainActivity` uses `launchMode="singleTop"` to prevent duplicate Activity instances
- **Point recording resilience**: `PointRecorder.recordPoint()` wraps per-snapshot database operations in try-catch — a single failed insert skips that snapshot and continues
- **Speedtest engine**: Custom Kotlin implementation of Speedtest.net HTTP protocol (not Ookla binary — binary fails on Android due to `SO_BINDTODEVICE` syscall restriction). Uses OkHttp with a shared `OkHttpClient` (connection pool: 8 idle, 30s keep-alive). Coroutine-based with `Semaphore` for concurrency limiting. Features: gauge phase for adaptive file sizing, 1.5s/3s warmup grace periods to overcome TCP slow start, slice-based throughput calculation (discards fastest 10% and slowest 30% of samples), 1.06× overhead compensation, 64 KB read/write buffer, pre-allocated upload payload, and server-ACK'd upload byte counting. Per-phase success semantics: each `SpeedTestResult` carries `downloadSucceeded: Boolean` and `uploadSucceeded: Boolean?` (null when upload was not run — WiFi skip, instant bail-out, upload disabled, or pre-upload probe skipped the upload phase).
- **Pre-upload probe**: Before the full upload measurement (3-second warmup cost), the engine issues a single ~1 KB POST to the speedtest server with a 5-second timeout. On probe failure, the upload phase is skipped for that cycle (`uploadSucceeded = false`, `uploadBps = null`, `errorMessage = "Upload probe failed: <reason>"`) without burning the warmup bytes. A `probe` phase `SpeedTestDebugEvent` is emitted for instrumentation parity.
- **Server selection**: Fetched from `speedtest-servers-static.php` XML, Haversine-sorted by distance, top 5 candidates pinged via HTTP `latency.txt`, lowest latency selected. Cached per recording session; invalidated only on download-phase failure or escaping exception (upload-only failures keep the cache warm — the server is by construction reachable since download just succeeded)
- **WiFi skip**: Tests skip when WiFi is active (`ConnectivityManager.getActiveNetwork()` has `TRANSPORT_WIFI`). Records `SKIPPED_WIFI` error with `downloadSucceeded = false`, `uploadSucceeded = null` so analytics can distinguish skipped vs failed tests
- **Correlation snapshot**: `CellInfoCollector.snapshots()` called at test start to capture `ratAtTest`, `rsrpAtTest`, `bandAtTest`, `dataSimSlotIndex`. No temporal join needed — snapshot is deterministic point-in-time
- **Replay display**: Speedtest markers are colored dots on the RAT timeline bar (green=fast, yellow=moderate, red=slow). A summary card shows the most recent speedtest result before the current playback position (auto-updates during scrubbing/playback). Tapping a marker explicitly selects it with a "Selected" label in primary color. Markers span the RAT timeline canvas and are clickable via `onMarkerClick` callback. Marker "succeeded" count is derived from `downloadSucceeded` (download is the headline metric).
- **Session detail analytics**: `SpeedTestAnalyticsEngine` is called from `SessionDetailViewModel` when speedtest records load; results exposed as `StateFlow<SpeedTestSessionAnalytics?>`. `AnalyticsPanel` shows a "Speed Tests" section with summary metrics, download histogram, RSRP/RAT/SIM throughput correlations, and upload RSRP correlation. Download statistics are computed from records where `downloadBps != null`; upload statistics from records where `uploadBps != null`. This retroactively re-includes legacy rows where the old whole-test `succeeded = false` but `downloadBps` was set.
- **Export integration**: Cell CSV export triggers a follow-up speedtest CSV export via `ExportSpeedTestUseCase.exportCsv()`. After the user saves the cell CSV, a second SAF document picker opens for `session_name_speedtest.csv`. The speedtest export columns match the spec: timestamp, finished_at, download_bps, upload_bps, server_name, server_host, server_location, download_succeeded, upload_succeeded, error_message, data_sim_slot, rat_at_test, rsrp_at_test, band_at_test, network_type

## Gradle Dependencies

| Dependency | Version |
|---|---|
| Gradle | 9.6.0 |
| AGP | 9.2.1 |
| Kotlin | 2.1.21 |
| KSP | 2.1.21-2.0.2 |
| compileSdk | 37 |
| targetSdk | 34 (intentionally held; bump requires runtime audit) |
| minSdk | 30 |
| Compose BOM | 2026.06.00 |
| Activity Compose | 1.13.0 |
| Lifecycle (runtime-compose, viewmodel-compose) | 2.11.0 |
| Navigation | 2.9.8 |
| Hilt | 2.59.2 |
| Hilt Navigation Compose | 1.3.0 |
| Room | 2.8.4 |
| NetMonster Core | 1.3.0 (`app.netmonster:core`) |
| osmdroid | 6.1.20 |
| Play Services Location | 21.3.0 |
| `kotlinx-coroutines-*` (core, android, play-services, test) | 1.11.0 |
| OkHttp | 5.4.0 (`com.squareup.okhttp3:okhttp`) |
| Kotlinx Serialization JSON | 1.11.0 |
| JUnit BOM | 6.1.0 (+ `junit-platform-launcher` required by Gradle 9.x) |
| MockK | 1.14.11 |
| Turbine | 1.2.1 |
| AndroidX Test (ext:junit, rules, core, runner) | 1.7.0 / 1.3.0 |
| Espresso | 3.7.0 |

> AGP 9.0 enables built-in Kotlin by default; this project opts out (`android.builtInKotlin=false`, `android.newDsl=false` in `gradle.properties`) because KSP's source-set registration is incompatible with built-in Kotlin. Migrate to built-in Kotlin before bumping to AGP 10.