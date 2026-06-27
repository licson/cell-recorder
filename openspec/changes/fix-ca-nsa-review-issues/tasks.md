## 1. Data Model & Database Migration

- [x] 1.1 Update `CaBandSnapshot` domain model to include `val bandwidthKhz: Int? = null`
- [x] 1.2 Update `CellRecordCaBandEntity` to include `@ColumnInfo(name = "bandwidthKhz") val bandwidthKhz: Int? = null`
- [x] 1.3 Add Room migration in `Migration.kt` to add `bandwidthKhz` column to `cell_record_ca_bands` table
- [x] 1.4 Increment database version in `AppDatabase.kt` and register the new migration
- [x] 1.5 Write migration test in `MigrationTest.kt` verifying `cell_record_ca_bands` preserves data after adding `bandwidthKhz`

## 2. Cell Info Collection Fixes

- [x] 2.1 Update `CellInfoCollector.extractCaBands` to map `cell.bandwidth` to `CaBandSnapshot.bandwidthKhz`
- [x] 2.2 Update `CellInfoCollector.buildNsaSnapshot` to capture `bandwidthKhz = nrCell.band?.bandwidth`
- [x] 2.3 Update `CellInfoCollector.buildNsaSnapshot` to source primary TAC from `nrCell.tac ?: lteAnchor?.tac`
- [x] 2.4 Update `CellInfoCollector.buildSnapshot` (SA path) to capture `bandwidthKhz = serving.band?.bandwidth` for `CellNr`
- [x] 2.5 Update `CellInfoCollector.buildLteSnapshot` to explicitly set `cellIdBitLength = 8`
- [x] 2.6 Update `CellInfoCollector.buildSnapshot` LTE RAT detection to use `if (caBands.isNotEmpty()) "4G_CA" else "4G"` instead of checking `networkType.technology`

> Note on 2.2 & 2.4: netmonster 1.3.0's `CellNr`/`BandNr` expose no bandwidth property, so NR `bandwidthKhz` is left `null` (see updated `specs/cell-info/spec.md`).

## 3. CellInfoCollector Unit Tests

- [x] 3.1 Create `CellInfoCollectorTest.kt` under `app/src/test/.../service/`
- [x] 3.2 Add test: NSA mode with NR and LTE anchor produces `5G_NSA` record with anchor fields
- [x] 3.3 Add test: NSA mode with NO NR cell but LTE anchor falls back to `4G`/`4G_CA` with full LTE fields
- [x] 3.4 Add test: NSA mode with NO NR and NO LTE produces `UNKNOWN` with `networkTypeCode`
- [x] 3.5 Add test: LTE with secondary cells correctly extracts CA bands (including bandwidth) and sets `4G_CA`
- [x] 3.6 Add test: 5G SA and NSA correctly capture `bandwidthKhz` when available

## 4. Export & Import Fixes

- [x] 4.1 Update `CsvRecordParser.kt` `columnMap` to map `"band"` to `"bandNumber"` instead of looking up `"band"` in `parseRow`
- [x] 4.2 Update `CsvRecordParser.parseRow` to correctly import `bandNumber = int("bandNumber")`
- [x] 4.3 Add `isLocationEstimated`, `locationSource`, and `bandwidthKhz` to CSV and GeoJSON export headers/properties in `ExportSessionUseCase.kt`
- [x] 4.4 Add `isLocationEstimated` and `locationSource` to `columnMap` and `parseRow` in `CsvRecordParser.kt`
- [x] 4.5 Add `isLocationEstimated` and `locationSource` parsing to `GeoJsonRecordParser.kt`
- [x] 4.6 Update `CsvRecordParser.parseCaBands` to log a warning and return `emptyList()` instead of `null` on malformed JSON

## 5. Analytics Fixes

- [x] 5.1 Update `SessionAnalyticsEngine.computeBandDistribution` to tag CA bands with `"4G"` (or `"4G_CA"`) RAT instead of inheriting parent RAT
- [x] 5.2 Update `SessionAnalyticsEngine` handoff loop to detect `ANCHOR_CHANGE` when consecutive `5G_NSA` records have different `anchorEnbOrGnbId` or `anchorPci`
- [x] 5.3 Update `HandoffType` enum/string-constants to include `ANCHOR_CHANGE` (and map in UI if necessary)

> Note on 5.3: the existing enum value `NSA_ANCHOR_CHANGE` is reused (per decision) rather than adding a redundant `ANCHOR_CHANGE`; the handoff loop emits `HandoffType.NSA_ANCHOR_CHANGE`, and `HandoffTimeline.kt` already maps it to a color. See updated `specs/analytics/spec.md`.

## 6. UI & Mapping Fixes

- [x] 6.1 Update `CaBandDetail` in `SimLiveStateMapper.kt` to include `val earfcn: Int? = null` and map it from snapshot
- [x] 6.2 Update `AnchorCellInfo` and `CaBandInfo` in `CellInfoPanel.kt` to accept a `rat: String` property
- [x] 6.3 Update `SimLiveState.toCellInfoData` to pass correct RAT to `CaBandInfo` ("4G") and format bands using `BandResolver.formatBand`
- [x] 6.4 Update `CellRecordWithCaBands.toCellInfoData` to pass correct RAT and use `BandResolver.formatBand` instead of hardcoded `"B"` prefix
- [x] 6.5 Update `RecordDetailSheet.kt` to use `rsrqColor()` for RSRQ rows and `sinrColor()` for SINR rows (9 occurrences)
- [x] 6.6 Update `RecordDetailSheet.kt` to use `BandResolver.formatBand` with `"4G"` RAT for CA bands and anchor bands
- [x] 6.7 Update `RecordingScreen.kt` `SimCard` composable to pass `onExpandToggle` down to `CellInfoPanel`
