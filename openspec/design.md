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
- **Speedtest engine**: Custom Kotlin implementation of Speedtest.net HTTP protocol (not Ookla binary — binary fails on Android due to `SO_BINDTODEVICE` syscall restriction). Uses `java.net.HttpURLConnection` (no external HTTP dependencies). Coroutine-based with `Semaphore` for concurrency limiting. Pure Kotlin, no native code, works on all Android ABIs
- **Server selection**: Fetched from `speedtest-servers-static.php` XML, Haversine-sorted by distance, top 5 candidates pinged via HTTP `latency.txt`, lowest latency selected. Cached per recording session; invalidated on test failure
- **WiFi skip**: Tests skip when WiFi is active (`ConnectivityManager.getActiveNetwork()` has `TRANSPORT_WIFI`). Records `SKIPPED_WIFI` error so analytics can distinguish skipped vs failed tests
- **Correlation snapshot**: `CellInfoCollector.snapshots()` called at test start to capture `ratAtTest`, `rsrpAtTest`, `bandAtTest`, `dataSimSlotIndex`. No temporal join needed — snapshot is deterministic point-in-time
- **Replay display**: Speedtest markers are colored dots on the RAT timeline bar (green=fast, yellow=moderate, red=slow). A summary card shows total/completed tests and latest result. No sparse line chart (cell records are dense, speedtests are sparse — markers avoid visual degradation)

## Gradle Dependencies

| Dependency | Version |
|---|---|
| AGP | 8.4.1 |
| Kotlin | 2.0.0 |
| Gradle | 8.7 |
| Compose BOM | 2024.10.00 |
| Navigation | 2.7.7 |
| Hilt | 2.51.1 |
| Room | 2.6.1 |
| NetMonster Core | 1.2.0 (`app.netmonster:core`) |
| osmdroid | 6.1.18 |
| Play Services Location | 21.2.0 |
| `kotlinx-coroutines-play-services` | 1.8.0 |
| Kotlinx Serialization JSON | 1.6.3 |
| JUnit 5 (bom) | 5.10.2 |
| MockK | 1.13.10 |
| Turbine | 1.1.0 |