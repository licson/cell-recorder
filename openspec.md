# Cell Recorder — OpenSpec v4

## 1. Project Metadata

| Field | Value |
|---|---|
| **Name** | Cell Recorder |
| **Platform** | Android (min API 30 / Android 11) |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM + Clean Architecture (data/domain/presentation layers) |
| **Build** | Gradle (Kotlin DSL) |
| **Min SDK** | 30 |
| **Target SDK** | 34 |
| **Package** | `com.cellrecorder.app` |

## 2. Core Stack

| Concern | Choice |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation + Bottom Navigation Bar |
| DI | Hilt |
| Database | Room (SQLite) |
| Telephony | **NetMonster Core 1.2.0** (`app.netmonster:core`) |
| Maps | osmdroid (OpenStreetMap) |
| Background work | Foreground Service (persistent notification) |
| Location | Google Play Services FusedLocationProvider (falls back to GPS provider) |
| Ping | ICMP via `ProcessBuilder("ping")` |
| Async | Kotlin Coroutines + Flow |
| Serialization | Kotlinx Serialization JSON |
| Export | CSV + GeoJSON via `ActivityResultContracts.CreateDocument` |
| Import | CSV + GeoJSON via `ActivityResultContracts.OpenDocument` |
| Testing | JUnit 5, MockK, Turbine |

## 3. Data Model (Room Entities)

### 3.1 Session

```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,        // epoch millis
    val endedAt: Long?,         // null = recording in progress
    val pointCount: Int = 0,
    val primarySimSlot: Int? = null  // default data SIM at time of recording
)
```

### 3.2 CellRecord

```kotlin
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
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val rat: String,                // "2G", "3G", "4G", "4G_CA", "5G_NSA", "5G_SA"
    val networkTypeCode: Int?,      // raw TelephonyManager network type for auditing
    val fullCellIdentity: Long?,    // CellLte.eci (28-bit) or CellNr.nci (36-bit)
    val enbOrGnbId: Long?,          // NetMonster auto-split: CellLte.enb or CellNr.gnbId(bitLen)
    val lcid: Int?,                 // NetMonster auto-split: CellLte.cid or CellNr.clId(bitLen)
    val cellIdBitLength: Int?,      // 5G: configurable gNB bit-length (default 24)
    val pci: Int?,
    val tac: Int?,                  // Tracking Area Code
    val bandNumber: Int?,
    val earfcn: Int?,               // Frequency channel number
    val bandwidthKhz: Int?,
    val rsrp: Int?,                 // dBm
    val rsrq: Int?,                 // dB
    val sinr: Int?,                 // dB
    val rssi: Int?,                 // dBm
    val cqi: Int?,                  // 1-15 (LTE)
    val timingAdvance: Int?,
    val mcc: String?,
    val mnc: String?,
    val subscriptionId: Int?,       // Android subscription ID (SIM slot identity)
    val simSlotIndex: Int?,         // Physical SIM slot index (0, 1...)
    val avgLatencyMs: Double?,
    val packetLossPct: Double?,
    val isLocationEstimated: Boolean = false,  // true when GPS is extrapolated
    val locationSource: String = "GPS"         // "GPS" or "SENSOR_FUSION"
)
```

### 3.3 AppConfig (single-row table)

```kotlin
@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val pingDestination: String = "8.8.8.8",
    val pingIntervalMs: Long = 1000,
    val pingTimeoutMs: Long = 3000,
    val recordingIntervalMs: Long = 5000,
    val locationChangeThresholdM: Float = 10f,
    val gpsAccuracyThresholdM: Float = 50f,
    val maxRecordingDurationMin: Int = 120,
    val nrGnbBitLength: Int = 24,                // bits for gNB portion of NCI (default 24)
    val cellInfoRefreshIntervalSec: Int = 5,     // cell info sampling interval (default 5s)
    val maxGpsLossExtrapolationSec: Int = 120,   // max seconds to extrapolate GPS position
    val handoffTimeWindowMs: Long = 5000,        // max interval between consecutive points to consider as handoff
    val rsrpDropThresholdDbm: Int = 15,          // min dBm drop to flag as anomaly
    val rsrpDropTimeWindowMs: Long = 10000,      // time window for detecting RSRP drops
    val latencySpikeSigma: Double = 3.0,         // sigma multiplier for latency spike detection
    val pciFlapWindowMs: Long = 30000,           // time window for PCI flapping detection
    val pciFlapCountThreshold: Int = 3,          // distinct PCI count to trigger flap flag
    val coverageGapThresholdMs: Long = 30000,    // minimum duration of UNKNOWN RAT to flag as gap
    val mobilityStationaryKmh: Float = 5f,       // speed threshold: stationary vs walking
    val mobilityWalkingKmh: Float = 15f,         // speed threshold: walking vs driving
    val indoorAccuracyThresholdM: Float = 30f,   // accuracy threshold for indoor classification
    val tunnelSignalLossThresholdMs: Long = 10000 // minimum signal loss duration for tunnel detection
)
```

## 4. Architecture & Module Layout

```
app/
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   │   ├── SessionDao.kt
│   │   │   ├── CellRecordDao.kt
│   │   │   └── ConfigDao.kt
│   │   └── entity/
│   │       ├── SessionEntity.kt
│   │       ├── CellRecordEntity.kt
│   │       └── AppConfigEntity.kt
│   └── repository/
│       ├── SessionRepository.kt
│       ├── CellRecordRepository.kt
│       └── ConfigRepository.kt
├── domain/
│   ├── model/
│   │   ├── SessionSummary.kt
│   │   ├── CellRecordSnapshot.kt
│   │   ├── PingResult.kt
│   │   └── StatisticsModels.kt       // RatDistribution, BandDistribution, SimSlotDistribution, Sim5GTime, ...
│   ├── usecase/
│   │   ├── CreateSessionUseCase.kt
│   │   ├── StartRecordingUseCase.kt
│   │   ├── StopRecordingUseCase.kt
│   │   ├── GetSessionsUseCase.kt
│   │   ├── GetSessionPointsUseCase.kt
│   │   ├── GetConfigUseCase.kt
│   │   ├── UpdateConfigUseCase.kt
│   │   ├── ExportSessionUseCase.kt
│   │   ├── BatchResplitUseCase.kt
│   │   └── import_/
│   │       ├── CsvRecordParser.kt
│   │       ├── GeoJsonRecordParser.kt
│   │       └── ImportSessionUseCase.kt
│   ├── ping/
│   │   ├── PingEngine.kt
│   │   └── PingSlidingWindow.kt
│   └── analytics/
│       ├── SessionAnalyticsEngine.kt
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
│           └── InsightCard.kt
├── service/
│   ├── RecordingService.kt        (Foreground Service)
│   ├── RecordingState.kt          (SimLiveState, RecordingState data classes)
│   ├── LocationCollector.kt       (FusedLocation + GPS fallback + distance check)
│   ├── CellInfoCollector.kt       (NetMonster Core wrapper)
│   └── SensorFusionCollector.kt   (Game Rotation Vector heading tracking)
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
│   │   └── SettingsViewModel.kt
│   ├── map/
│   │   └── SessionMapView.kt
│   ├── shared/
│   │   ├── TooltipIconButton.kt
│   │   └── PermissionRationaleDialog.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── di/
    ├── AppModule.kt
    ├── DatabaseModule.kt
    └── NetMonsterModule.kt
```

## 5. Feature Details

### 5.1 Recording Lifecycle

1. User creates/names a session → session inserted with `endedAt = null`
2. User taps "Start Recording" → `RecordingService` starts as foreground service with `NOTIFICATION_ID = 1001`
3. Service runs continuously until user taps "Stop" or `maxRecordingDuration` is reached
4. On stop: session updated with `endedAt = now()` and `primarySimSlot = defaultDataSubSlot`, service stops itself

### 5.2 Recording Trigger Logic (LocationCollector + SensorFusion Fallback)

**Normal operation:**
- Every 1 second the service evaluates whether to record a point
- While moving (GPS distance since last point >= `locationChangeThresholdM`): record immediately
- While stationary (GPS distance < threshold): record every `recordingIntervalMs` (default 5s)
- GPS accuracy checked against `gpsAccuracyThresholdM`; readings above threshold discarded

**GPS Loss Extrapolation:**
- If no accurate GPS fix for > 3s and a fix was previously acquired, service enters extrapolation mode
- `SensorFusionCollector` uses `TYPE_GAME_ROTATION_VECTOR` to track heading changes relative to baseline
- Estimated position calculated from last known speed, bearing, and heading delta: `movePoint(lat, lon, bearing + headingDelta, speed * elapsed)`
- Estimated accuracy degrades over time (`50m + 3m * elapsedSec`)
- Extrapolation stops after `maxGpsLossExtrapolationSec` (default 120s) or when GPS fix returns
- On GPS recovery, a 5s settling delay is applied before resuming normal recording
- Extrapolated points saved with `isLocationEstimated = true`, `locationSource = "SENSOR_FUSION"`

### 5.3 Multi-SIM Recording

The app records **separate data points for every active SIM slot** simultaneously.

- `netMonster.getCells()` returns all visible cells from all subscriptions
- Each `ICell` carries `subscriptionId` — the collector groups cells by subscription and finds the primary serving cell per SIM
- `getNetworkType(subId)` is queried per subscription (not just the default)
- One location trigger produces **N `CellRecordEntity` rows** (one per SIM with visible cells)
- Session `pointCount` increments by **1 per location** (not per SIM)

**SIM labels:** The UI labels each row as "SIM 1", "SIM 2", etc. derived from `SubscriptionInfo.simSlotIndex`.

**Ping attribution:** ICMP ping is always routed through the device's **default data SIM** (OS-level behavior, cannot be overridden without root). The UI acknowledges this by labeling the ping value as `Ping (via SIM {n})`.

### 5.4 CellInfoCollector — NetMonster Core Integration

> **API note (v1.2.0)**: `CellNr` does not have `gnbId()`/`clId()` helper methods in v1.2.0 — implemented manually via `nci shr (36 - bitLen)`. `SignalLte.rsrp`/`rsrq`/`snr` are `Double?` (not `Int?`). `SignalNr` has no `timingAdvance`. `BandLte.earfcn` is `downlinkEarfcn`, `BandNr.arfcn` is `downlinkArfcn`. LTE-CA detected via `networkType.technology == NetworkType.LTE_CA` since `LteCa` is not a subclass.

```kotlin
class CellInfoCollector @Inject constructor(
    private val netMonster: INetMonster
) {
    fun snapshots(config: AppConfigEntity): List<CellRecordSnapshot> {
        val cells = netMonster.getCells()
        return cells.groupBy { it.subscriptionId }.map { (subId, subCells) ->
            val serving = subCells.firstOrNull { it.connectionStatus is PrimaryConnection }
            val networkType = netMonster.getNetworkType(subId)
            buildSnapshot(subId, serving, networkType, config)
        }
    }

    private fun buildSnapshot(
        subId: Int,
        serving: ICell?,
        networkType: NetworkType,
        config: AppConfigEntity
    ): CellRecordSnapshot {
        return when (serving) {
            is CellLte -> {
                val fullId = serving.eci?.toLong()
                CellRecordSnapshot(
                    subscriptionId = subId,
                    rat = if (networkType is NetworkType.Lte && networkType.technology == NetworkType.LTE_CA) "4G_CA" else "4G",
                    fullCellIdentity = fullId,
                    enbOrGnbId = fullId?.shr(8),
                    lcid = fullId?.and(0xFF)?.toInt(),
                    pci = serving.pci, tac = serving.tac,
                    bandNumber = serving.band?.number,
                    earfcn = serving.band?.downlinkEarfcn,
                    bandwidthKhz = serving.bandwidth,
                    rsrp = serving.signal?.rsrp?.toInt(),
                    rsrq = serving.signal?.rsrq?.toInt(),
                    sinr = serving.signal?.snr?.toInt(),
                    rssi = serving.signal?.rssi,
                    cqi = serving.signal?.cqi,
                    timingAdvance = serving.signal?.timingAdvance,
                    mcc = serving.network?.mcc, mnc = serving.network?.mnc
                )
            }
            is CellNr -> {
                val fullId = serving.nci
                val shift = 36 - config.nrGnbBitLength
                CellRecordSnapshot(
                    subscriptionId = subId,
                    rat = if (networkType is NetworkType.Nr.Nsa) "5G_NSA" else "5G_SA",
                    fullCellIdentity = fullId,
                    enbOrGnbId = fullId?.shr(shift),
                    lcid = fullId?.and((1L shl shift) - 1)?.toInt(),
                    cellIdBitLength = config.nrGnbBitLength,
                    pci = serving.pci, tac = serving.tac,
                    bandNumber = serving.band?.number,
                    earfcn = serving.band?.downlinkArfcn,
                    rsrp = serving.signal?.ssRsrp,
                    rsrq = serving.signal?.ssRsrq,
                    sinr = serving.signal?.ssSinr,
                    mcc = serving.network?.mcc, mnc = serving.network?.mnc
                )
            }
            is CellWcdma -> CellRecordSnapshot(
                subscriptionId = subId,
                rat = "3G", fullCellIdentity = serving.ci?.toLong(),
                pci = serving.psc, mcc = serving.network?.mcc, mnc = serving.network?.mnc
            )
            is CellGsm -> CellRecordSnapshot(
                subscriptionId = subId,
                rat = "2G", fullCellIdentity = serving.cid?.toLong(),
                pci = serving.bsic, mcc = serving.network?.mcc, mnc = serving.network?.mnc
            )
            else -> CellRecordSnapshot(
                subscriptionId = subId,
                rat = "UNKNOWN", networkTypeCode = networkType.technology
            )
        }
    }
}
```

### 5.5 Ping Engine

- On recording start, a **continuous ping loop** begins in a coroutine
- Pings fire every `pingIntervalMs` (default 1s) with `pingTimeoutMs` timeout
- Results pushed into `PingSlidingWindow` (last 5)
- When recording point is triggered:
  - `avgLatencyMs` = mean of non-null values in window
  - `packetLossPct` = (count of null / N) * 100

### 5.6 Cell ID Split — Post-Processing

- NetMonster Core provides automatic split values (`CellLte.enb`/`.cid`, `CellNr.gnbId(bitLen)`/`.clId(bitLen)`)
- User can override the split formula in **Settings** (NR gNB bit-length)
- **Batch re-split** action in Session Detail re-applies the current formula to all points:
  - 4G/4G_CA: `enb = fullId shr 8`, `cid = fullId and 0xFF`
  - 5G_SA: `gnb = fullId shr (36 - nrBitLen)`, `clId = fullId and ((1L shl (36 - nrBitLen)) - 1)`

### 5.7 Session Analytics Engine

The `SessionAnalyticsEngine` performs comprehensive post-hoc analysis on a session's records:

- **RAT Coverage**: percentage of time per RAT, broken into signal quality buckets (excellent/good/fair/poor)
- **Band Distribution**: frequency bands used per SIM, sorted by occurrence
- **Signal Histograms**: distribution of RSRP, SINR, and ping latency values
- **Correlation Analysis**: RSRP↔Ping, RSRP↔Packet Loss, SINR↔Ping, SINR↔Loss, per SIM
- **Latency Stats**: mean, p50, p95, p99, jitter (standard deviation)
- **Handoff Detection**: detects cell/site changes within `handoffTimeWindowMs`, classifies as intra-site PCI change vs inter-site handoff, tracks latency impact
- **Anomaly Detection** (grouped by consecutive occurrence with duration):
  - RSRP drops exceeding `rsrpDropThresholdDbm` within `rsrpDropTimeWindowMs`
  - Latency spikes exceeding mean + `latencySpikeSigma` * standard deviation — consecutive spikes grouped into one anomaly with peak latency
  - PCI flapping: rapid PCI changes within `pciFlapWindowMs` exceeding `pciFlapCountThreshold` — overlapping windows collapsed into one anomaly per episode
  - Missing ping clusters: 3+ consecutive samples without ping data
  - Each anomaly includes `endTimestamp` for duration display in the UI
- **Mobility Classification**: segments the session into stationary, walking, driving, indoor, tunnel based on speed and signal characteristics
- **Coverage Gaps**: detects periods of UNKNOWN RAT exceeding `coverageGapThresholdMs`
- **Timeline Segments**: groups contiguous points by RAT
- **Insight Cards**: generated from handoff analysis (e.g., "Massive MIMO Candidate", "Load Balancing Detected", "Cross-Site Handoff Impact")

## 6. UI Screens

### 6.1 Bottom Navigation

The app uses a bottom navigation bar with three primary destinations:
- **Live Info** (`Sensors` icon) — real-time cell info display per SIM
- **Sessions** (`List` icon) — list of past sessions (default start destination)
- **Statistics** (`BarChart` icon) — global aggregate statistics across all sessions

The bottom bar is only shown on these three top-level destinations.

### 6.2 Live Info (home tab)

- Dedicated screen showing real-time cell data for all active SIMs
- One `Card` per SIM displaying: PLMN, RAT, Band, ARFCN, Cell ID, PCI, TAC, RSRP, RSRQ, SINR
- Live mini sparkline charts for RSRP and SINR history per SIM (using `MetricChart` component)
- Data refreshes based on `cellInfoRefreshIntervalSec`
- Shows "No cell data available" when no SIM data is detected

### 6.3 Session List

- List of past sessions sorted by date descending
- Each row: session name, date, point count
- FAB to create new session (dialog prompts for name)
- Tap to open session detail; long-press context menu for delete and export
- Settings gear icon in top bar

### 6.4 Recording Screen (live)

- Top bar: session name, elapsed timer (`MM:SS`), point counter
- OSM map with real-time RAT-colored markers and recorded path overlay
- Start/Stop button (large, centered at bottom)
- Live stats panel: one row per active SIM showing RAT, PCI, RSRP, RSRQ, SINR, Cell ID
- Ping row labeled with active data SIM slot (`Ping (via SIM {n})`)
- Tap point marker to see tooltip with all attributes
- GPS status indicator: "OK", "Searching...", or "EXTRAPOLATING" with GPS accuracy

### 6.5 Session Detail & Replay

**Data mode (default):**
- OSM map with all recorded points (RAT-colored) and recorded path polyline
- Bottom action bar: "Analytics" toggle, "Replay" button, "More" menu (Re-split, Export CSV, Export GeoJSON, Delete)
- Scrollable data table grouped by timestamp (all SIMs for the same timestamp in one group)
  - Lazy viewport rendering: only visible rows are composed, off-screen rows use measured-height placeholders
  - Columns: `#`, `SIM`, `PLMN`, `Band`, `RSRP (dBm)`, `Ping (ms)`
  - Tapping a record selects it and highlights it on the map

**Analytics mode (toggled from data mode):**
- Map expands to larger viewport with map display mode selector and SIM filter
- Full-height scrollable analytics panel including:
  - **Coverage Gaps** list
  - **RAT Coverage** bar chart with signal quality breakdown
  - **Signal Histograms** — RSRP and SINR distribution
  - **Ping Histogram** — latency distribution
  - **Correlation Charts** — expandable sections for each correlation type per SIM
  - **Latency Stats** card (mean, p50, p95, p99, jitter)
  - **Handoff Timeline** — annotated event list
  - **Anomaly List** — severity-coded anomaly flags
  - **Mobility Segments** — visual timeline of movement type with badges
  - **Insight Cards** — generated network insights

**Replay mode (toggled by "Replay" button):**
- OSM map with all points visible (faded)
- Animated marker moves along recorded path
- Time slider below map for scrubbing
- Play/Pause button
- Speed selector (1x, 2x, 5x, 10x)
- Stats panel (bottom sheet): current point's RAT, PCI, RSRP, RSRQ, SINR, ping, packet loss
- Timeline chart: RSRP & latency curves over time, vertical cursor synced to replay position
- Playback animates first to last point; speed multiplies real time deltas

### 6.6 Statistics (global)

- Aggregate statistics computed via Room queries across **all sessions**
- Summary cards: total sessions, total points, total duration, on-network percentage
- RAT distribution per SIM (stacked horizontal bar charts with legend)
- Band distribution per SIM (stacked bar)
- Records per SIM (horizontal bars)
- Time on network per SIM
- 5G time breakdown (SA vs NSA) per SIM with percentage bar

### 6.7 Settings

| Section | Settings |
|---|---|
| **Ping** | Destination, Interval (ms), Timeout (ms) |
| **Recording** | Interval (ms), Location Change Threshold (m), GPS Accuracy Threshold (m), Max Duration (min) |
| **Cell ID** | NR gNB Bit-Length, Cell Info Refresh Interval (s) |
| **GPS Loss Fallback** | Max Extrapolation Time (s) |
| **Analytics Thresholds** | Handoff Time Window (ms), RSRP Drop Threshold (dBm), RSRP Drop Time Window (ms), Latency Spike Sigma, PCI Flap Window (ms), PCI Flap Count Threshold, Coverage Gap Threshold (ms), Mobility Stationary (km/h), Mobility Walking (km/h), Indoor Accuracy (m), Tunnel Signal Loss (ms) |

## 7. Background Service (RecordingService)

- Type: **Foreground Service** (required for Android 11+) with `FOREGROUND_SERVICE_TYPE_LOCATION`
- Notification: persistent channel `cell_recorder_channel` — content shows elapsed time, point count, GPS status
- Notification includes "Stop" action and opens MainActivity on tap
- State shared via `companion object` `_currentState: MutableStateFlow<RecordingState?>`
- Auto-stops on: user "Stop", max duration reached, or system kill (`START_STICKY` restarts but checks `isRecording` flag)
- GPS extrapolation fallback via `SensorFusionCollector` when fix is lost

## 8. Permissions

| Permission | Type |
|---|---|
| `ACCESS_FINE_LOCATION` | runtime |
| `ACCESS_BACKGROUND_LOCATION` | runtime (API 29+) |
| `READ_PHONE_STATE` | runtime |
| `FOREGROUND_SERVICE` | manifest |
| `FOREGROUND_SERVICE_LOCATION` | manifest |
| `POST_NOTIFICATIONS` | runtime (API 33+) |
| `INTERNET` | manifest |

Optional hardware feature: `android.hardware.sensor.gyroscope` (`required="false"`) — used by SensorFusionCollector when available.

## 9. Import & Export

### CSV Export

```
timestamp,lat,lon,alt,accuracy,subscription_id,sim_slot_index,rat,pci,rsrp,rsrq,sinr,enb_gnb_id,lcid,avg_latency_ms,packet_loss_pct,mcc,mnc,band,earfcn,tac,is_location_estimated,location_source
```

### GeoJSON Export

```json
{
  "type": "FeatureCollection",
  "features": [{
    "type": "Feature",
    "geometry": { "type": "Point", "coordinates": [lon, lat, alt] },
    "properties": {
      "timestamp": 1234567890, "subscriptionId": 1, "simSlotIndex": 0,
      "rat": "4G", "pci": 123,
      "rsrp": -110, "rsrq": -12, "sinr": 15,
      "enbGnbId": 12345, "lcid": 7,
      "avgLatencyMs": 25.3, "packetLossPct": 0.0,
      "mcc": "310", "mnc": "410", "band": 4, "earfcn": 2000, "tac": 1234,
      "isLocationEstimated": false, "locationSource": "GPS"
    }
  }]
}
```

### Import

- `ImportSessionUseCase` supports importing both CSV and GeoJSON files
- `CsvRecordParser` parses CSV content into `CellRecordEntity` list, skipping malformed lines
- `GeoJsonRecordParser` parses GeoJSON FeatureCollection into `CellRecordEntity` list
- Each import creates a new `SessionEntity`, inserts all parsed records, and sets `endedAt`

---

## 10. Implementation Notes (v1.2)

### 10.1 NetMonster Core v1.2.0 API corrections

| Spec assumption | Actual API |
|---|---|
| `CellLte.signal.rsrp / rsrq` is `Int?` | Actually `Double?` (convert with `.toInt()`) |
| `CellLte.signal.snr` is `Int?` | Actually `Double?` (mapped to `sinr`) |
| `CellNr` has `gnbId(bitLen)` / `clId(bitLen)` helpers | Not in v1.2.0 — implement manually: `nci shr (36 - bitLen)` / `nci and mask` |
| `CellNr.signal.timingAdvance` exists | Not in v1.2.0 `SignalNr` |
| `BandLte.earfcn` property | Accessible as `band.downlinkEarfcn` |
| `BandNr.arfcn` property | Accessible as `band.downlinkArfcn` |
| `networkType is LteCa` | Use `networkType is NetworkType.Lte && networkType.technology == NetworkType.LTE_CA` (value 19) |
| `networkType is Nsa` | Correct: `networkType is NetworkType.Nr.Nsa` |

### 10.2 Key Architecture Decisions

- **Single module** with clean architecture packages (`data/domain/service/ui/di`)
- **Hilt** for DI; `PingEngine` and other services annotated with `@Inject constructor()` to satisfy Hilt
- **osmdroid maps** wrapped in Compose `AndroidView` (View interop)
- **Recording state** shared via `StateFlow` companion object in `RecordingService`
- **Export** delegates to SAF `CreateDocument` contract; use case generates content strings
- **Import** uses `ActivityResultContracts.OpenDocument`; `CsvRecordParser` and `GeoJsonRecordParser` handle parsing
- **JUnit 5** configured via `tasks.withType<Test> { useJUnitPlatform() }` in `build.gradle.kts`
- **`kotlinx-coroutines-play-services`** dependency for `Task.await()` on `FusedLocationProviderClient`
- **Room schema**: version 6 with explicit migrations (3→4, 4→5, 5→6); `fallbackToDestructiveMigration()` NOT used; single-row `AppConfig` table seeded via `RoomDatabase.Callback.onCreate`
- **Multi-SIM**: `SubscriptionManager` used to enumerate active subscriptions and identify the default data SIM for ping attribution; `READ_PHONE_STATE` permission is sufficient for API 30+
- **Viewport rendering**: Session Detail data table renders only visible rows; off-screen rows replaced by measured-height `Spacer` boxes for scroll state preservation
- **Sensor fusion**: `SensorFusionCollector` uses `TYPE_GAME_ROTATION_VECTOR` (no magnetometer needed); fallback heading is 0 when sensor unavailable. GPS extrapolation uses `movePoint(lat, lon, bearing, distance)` Haversine calculation.

### 10.3 Gradle Dependencies

- AGP `8.4.1`, Kotlin `2.0.0`, Gradle `8.7`
- Compose BOM `2024.10.00`, Navigation `2.7.7`, Hilt `2.51.1`, Room `2.6.1`
- NetMonster Core `1.2.0` (`app.netmonster:core`), osmdroid `6.1.18`
- Play Services Location `21.2.0`, `kotlinx-coroutines-play-services:1.8.0`
- Kotlinx Serialization JSON `1.6.3`
- JUnit 5 (`org.junit:junit-bom:5.10.2`), MockK `1.13.10`, Turbine `1.1.0`