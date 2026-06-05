## 1. Data Model

- [x] 1.1 Add 13 nullable anchor columns to `CellRecordEntity` (`anchorEnbOrGnbId`, `anchorLcid`, `anchorPci`, `anchorTac`, `anchorBandNumber`, `anchorEarfcn`, `anchorBandwidthKhz`, `anchorRsrp`, `anchorRsrq`, `anchorSinr`, `anchorRssi`, `anchorCqi`, `anchorTimingAdvance`)
- [x] 1.2 Add 13 anchor fields to `CellRecordSnapshot` domain model
- [x] 1.3 Bump Room schema version and add migration with `ALTER TABLE cell_records ADD COLUMN` for each anchor column
- [x] 1.4 Update `AppDatabase.kt` with new version number and migration class

## 2. Cell Info Collection

- [x] 2.1 Refactor `CellInfoCollector.snapshots()` to detect NSA mode via `NetworkType.Nr.Nsa` and handle dual-cell extraction
- [x] 2.2 In NSA mode, find NR cell (`subCells.firstOrNull { it is CellNr }`) and LTE anchor (`subCells.firstOrNull { it is CellLte && it.connectionStatus is PrimaryConnection }`)
- [x] 2.3 Build `5G_NSA` record from NR cell with anchor fields populated from LTE anchor
- [x] 2.4 Extract CA bands from LTE anchor's secondary cells and attach to `5G_NSA` record
- [x] 2.5 Populate `bandwidthKhz` from `CellNr.band.bandwidth` (nullable)
- [x] 2.6 Handle fallback: if no NR cell found in NSA mode, record LTE anchor as primary (current behavior)
- [x] 2.7 Handle edge case: if no LTE anchor found, record NR cell with null anchor fields

## 3. Point Recording

- [x] 3.1 Update `PointRecorder.recordPoint()` to pass anchor fields from `CellRecordSnapshot` to `CellRecordEntity`
- [x] 3.2 Insert anchor CA bands alongside existing CA bands using the same `cell_record_ca_bands` FK

## 4. Analytics Fix

- [x] 4.1 Change `SessionAnalyticsEngine.generatePciInsights()` line 606 from `it.rat == "5G_SA"` to `it.rat.startsWith("5G")`

## 5. Export and Import

- [x] 5.1 Add anchor field columns to CSV export in `ExportSessionUseCase` (prefixed with `anchor_`)
- [x] 5.2 Add anchor properties to GeoJSON export in `ExportSessionUseCase` (prefixed with `anchor_`)
- [x] 5.3 Parse anchor columns in CSV import (`CsvRecordParser`) — gracefully handle missing columns (null defaults)
- [x] 5.4 Parse anchor properties in GeoJSON import (`GeoJsonRecordParser`) — gracefully handle missing properties (null defaults)

## 6. UI Updates

- [x] 6.1 Update replay stats panel (`ReplayScreen.kt` StatsPanel) to display anchor band, PCI, and RSRP for `5G_NSA` records
- [x] 6.2 Update session detail screen (`SessionDetailScreen.kt`) to display anchor info in record rows for `5G_NSA` records
- [x] 6.3 Update live info screen (`LiveInfoScreen.kt`) to display anchor info for NSA SIMs
- [x] 6.4 Update live info ViewModel (`LiveInfoViewModel.kt`) to map anchor fields to display state
- [x] 6.5 Update recording ViewModel (`RecordingViewModel.kt`) to map anchor fields to display state

## 7. Verification

- [x] 7.1 Run `./gradlew assembleDebug` and verify clean build
- [x] 7.2 Run existing unit tests (`./gradlew test`) and verify all pass
- [x] 7.3 Verify Room migration by running `./gradlew assembleDebug` with new schema version
