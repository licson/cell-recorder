## 1. Data Model & Migration

- [ ] 1.1 Add `recordingMode: String = "OUTDOOR"` field to `SessionEntity`
- [ ] 1.2 Add `relativeX: Double? = null` and `relativeY: Double? = null` fields to `CellRecordEntity`
- [ ] 1.3 Add `indoorStepLengthM: Float = 0.7f` and `indoorRecordingIntervalMs: Long = 5000L` fields to `AppConfigEntity`
- [ ] 1.4 Implement DB migration v10 → v11: add `recordingMode` column to `sessions` (TEXT DEFAULT "OUTDOOR"), add `relativeX` and `relativeY` columns to `cell_records` (REAL NULLABLE), add `indoorStepLengthM` and `indoorRecordingIntervalMs` columns to `app_config` (REAL/INTEGER with defaults)
- [ ] 1.5 Update `AppDatabase` version to 11 and register the migration

## 2. Indoor Position Collector

- [ ] 2.1 Create `IndoorPositionUpdate` data class with fields: `relativeX`, `relativeY`, `headingRad`, `stepCount`, `estimatedDriftM`, `timestamp`
- [ ] 2.2 Create `IndoorPositionCollector` class (Hilt `@Singleton`): subscribe to `TYPE_STEP_DETECTOR` and `TYPE_GAME_ROTATION_VECTOR` (fallback to `TYPE_ROTATION_VECTOR`)
- [ ] 2.3 Implement step-to-position conversion: each step event advances position by `indoorStepLengthM` meters in the current heading direction
- [ ] 2.4 Implement heading extraction from game rotation vector (yaw angle)
- [ ] 2.5 Implement drift estimation: `stepCount * indoorStepLengthM * driftRate`, where `driftRate` starts at 0.02 and grows linearly by 0.004/min (capped at 0.20)
- [ ] 2.6 Implement origin reset: reset (X,Y) to (0,0), heading to current device heading, step counter and drift to zero
- [ ] 2.7 Emit `Flow<IndoorPositionUpdate>` from `IndoorPositionCollector`
- [ ] 2.8 Implement sensor availability check: `hasStepDetector()` and `hasGameRotationVector()` / `hasRotationVector()` methods

## 3. Recording Service Modifications

- [ ] 3.1 Add `recordingMode` parameter to `RecordingService.start()` intent extras
- [ ] 3.2 Branch `startRecording()` on recording mode: indoor → use `IndoorPositionCollector`, outdoor → use existing `LocationCollector`
- [ ] 3.3 For indoor mode: launch a time-based trigger job instead of the GPS-based `recordingJob`
- [ ] 3.4 For indoor mode: do NOT launch `fallbackRecordingJob` (no GPS loss detection needed)
- [ ] 3.5 For indoor mode: ping job and speedtest job run unchanged

## 4. Point Recorder Modifications

- [ ] 4.1 Add indoor point recording method that accepts `IndoorPositionUpdate`
- [ ] 4.2 Populate `relativeX`, `relativeY` from `IndoorPositionUpdate` for indoor records
- [ ] 4.3 Set `latitude`, `longitude`, `altitude`, `accuracy` to null for indoor records
- [ ] 4.4 Set `locationSource = "INDOOR_IMU"` and `isLocationEstimated = false` for indoor records
- [ ] 4.5 Track discontinuity markers when origin reset occurs during indoor recording
- [ ] 4.6 Update `recordedPathSnapshot` to support both lat/lon pairs and relative X/Y pairs

## 5. Recording State & ViewModel

- [ ] 5.1 Add indoor-specific fields to `RecordingState`: `recordingMode`, `currentRelativeX`, `currentRelativeY`, `currentHeading`, `currentStepCount`, `estimatedDriftM`, `timeSinceOriginResetMs`
- [ ] 5.2 Update `RecordingStateManager` to populate indoor fields from `IndoorPositionCollector` updates
- [ ] 5.3 Add `resetOrigin()` function to `RecordingViewModel` that calls `IndoorPositionCollector.resetOrigin()`
- [ ] 5.4 Update notification content for indoor mode: show tracking confidence instead of GPS status

## 6. UI — Indoor Path Canvas

- [ ] 6.1 Create `IndoorPathCanvas` composable: render path polyline with signal-colored segments (RSRP-mapped colors)
- [ ] 6.2 Add pan and zoom gesture support (transformable + draggable)
- [ ] 6.3 Add grid lines for spatial reference
- [ ] 6.4 Add current position marker and origin (0,0) marker
- [ ] 6.5 Add drift radius circle (translucent, growing with drift estimate)
- [ ] 6.6 Add discontinuity markers at origin reset points (visible gap in path)
- [ ] 6.7 Add signal color legend

## 7. UI — Tracking Confidence & Controls

- [ ] 7.1 Create `TrackingConfidenceIndicator` composable: 3-state (Confident=green, Degrading=yellow, High drift=red) with time since last reset
- [ ] 7.2 Add "Reset Origin" button to indoor recording screen
- [ ] 7.3 Show step count and drift estimate on indoor recording screen
- [ ] 7.4 Hide GPS status indicator and accuracy on indoor recording screen

## 8. UI — Recording Screen Mode Branch

- [ ] 8.1 Conditionally render `IndoorPathCanvas` vs. OSM map based on `RecordingState.recordingMode`
- [ ] 8.2 Show `TrackingConfidenceIndicator` when indoor
- [ ] 8.3 Show GPS status when outdoor (existing behavior)
- [ ] 8.4 Add "Reset Origin" FAB/action button when indoor

## 9. UI — Session Creation

- [ ] 9.1 Add recording mode selector (Outdoor / Indoor radio buttons or dropdown) to session creation dialog
- [ ] 9.2 Pass selected `recordingMode` to `CreateSessionUseCase`
- [ ] 9.3 Update `CreateSessionUseCase` to persist `recordingMode` on `SessionEntity`

## 10. UI — Session Detail & Replay (Indoor)

- [ ] 10.1 In `SessionDetailScreen`: conditionally render `IndoorPathCanvas` for indoor sessions instead of map
- [ ] 10.2 In `SessionDetailScreen`: show `relativeX`/`relativeY` columns in data table for indoor sessions
- [ ] 10.3 In `ReplayScreen`: render indoor path on `IndoorPathCanvas` for indoor sessions instead of map
- [ ] 10.4 In `ReplayScreen`: animate marker along indoor path for indoor sessions

## 11. Settings

- [ ] 11.1 Add "Indoor Recording" settings section to `SettingsScreen`
- [ ] 11.2 Add step length slider (default 0.7m, range 0.3m–1.2m)
- [ ] 11.3 Add indoor recording interval picker (default 5000ms)
- [ ] 11.4 Add session time guidance note ("Indoor sessions < 5 min give best accuracy")

## 12. Export & Import

- [ ] 12.1 Update CSV export to include `relativeX`, `relativeY` columns (null for outdoor sessions)
- [ ] 12.2 Update GeoJSON export for indoor sessions: compute approximate coordinates as `[0 + relativeX / 111320, 0 + relativeY / 111320]`
- [ ] 12.3 Add `"indoorMode": true` and `"coordinateReference": "relative"` properties to indoor GeoJSON FeatureCollection
- [ ] 12.4 Update CSV import to parse `relativeX`, `relativeY` columns and set `recordingMode = "INDOOR"` when present
- [ ] 12.5 Update GeoJSON import to detect `"indoorMode"` property and parse relative coordinates

## 13. Analytics

- [ ] 13.1 Exclude indoor sessions from geographic-dependent analytics (coverage maps, geographic handoff)
- [ ] 13.2 Classify all points in indoor sessions as "indoor" mobility segment regardless of speed
- [ ] 13.3 Ensure non-geographic analytics (RAT coverage, band distribution, signal histograms, correlations, latency, anomalies, timeline, insights) work for indoor sessions

## 14. Build & Verify

- [ ] 14.1 Run clean build (`./gradlew clean assembleDebug`) to verify no compilation errors
- [ ] 14.2 Run unit tests (`./gradlew test`) to verify existing tests pass
- [ ] 14.3 Verify DB migration v10 → v11 works correctly (existing data preserved, new columns have correct defaults)
