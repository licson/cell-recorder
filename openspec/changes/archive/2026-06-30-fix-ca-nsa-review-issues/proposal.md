## Why

A comprehensive code review of LTE Carrier Aggregation band handling and 5G NSA handling uncovered 16 issues across the full data pipeline — from collection through persistence, export/import, analytics, and UI display. Two are critical bugs with user-visible data corruption (CSV import silently drops primary band number; RecordDetailSheet uses wrong color thresholds for RSRQ and SINR). Three are high-severity issues including a spec violation (NR bandwidth never captured) and a RAT labeling inconsistency. The remaining issues range from missing data fields in UI/export to silent parse failures and missing test coverage.

## What Changes

### Critical Fixes
- Fix `CsvRecordParser.parseRow()` key mismatch: `int("band")` → `int("bandNumber")` so primary band number is actually imported from CSV files
- Fix `RecordDetailSheet` to use `rsrqColor()` for RSRQ and `sinrColor()` for SINR instead of `rsrpColor()` for all three metrics (9 call sites)

### High-Severity Fixes
- Capture NR `bandwidthKhz` in both `buildNsaSnapshot()` and the `CellNr` branch of `buildSnapshot()` (5G SA)
- Unify LTE CA RAT detection: use actual extracted CA bands (`caBands.isNotEmpty()`) instead of relying on `networkType.technology == NetworkType.LTE_CA` in `buildSnapshot()`
- Add unit tests for `CellInfoCollector` covering the 9+ spec scenarios for NSA and CA behavior

### Medium-Severity Fixes
- Replace hard-coded `"B"` prefix for CA and anchor bands with `BandResolver.formatBand()` across `SimLiveStateMapper`, `CellInfoPanel`, and `RecordDetailSheet`
- Fix CA band RAT labeling in `SessionAnalyticsEngine.computeBandDistribution()` — CA bands from an LTE anchor on a `5G_NSA` record should be labeled `4G`/`4G_CA`, not `5G_NSA`
- Add `earfcn` to `CaBandDetail` data class and map it in `SimLiveStateMapper` so live CA bands show EARFCN instead of `"---"`
- Preserve NR cell's own TAC in `buildNsaSnapshot()` as the primary TAC when available, falling back to the LTE anchor TAC

### Low-Severity / Informational Fixes
- Import `isLocationEstimated` and `locationSource` in both `CsvRecordParser` and `GeoJsonRecordParser`
- Export primary `bandwidthKhz` in CSV and GeoJSON formats
- Add `bandwidthKhz` field to `CaBandSnapshot` and `CellRecordCaBandEntity`; capture it in `extractCaBands()`
- Add anchor-based handoff detection for 5G NSA in `SessionAnalyticsEngine`
- Set `cellIdBitLength = 8` for LTE records in `buildLteSnapshot()`
- Log a warning on malformed CA JSON during CSV import instead of silently dropping
- Pass `onExpandToggle` callback from `SimCard` to `CellInfoPanel`

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `cell-info`: NR bandwidth capture, NR TAC sourcing in NSA, LTE CA RAT detection unification, LTE cellIdBitLength
- `data`: CSV/GeoJSON import of `isLocationEstimated`/`locationSource`, export of primary `bandwidthKhz`, CA band `bandwidthKhz` in schema, silent CA parse failure logging
- `ui`: RecordDetailSheet signal color functions, hard-coded band prefix elimination, live CA band EARFCN display, SimCard expand toggle passthrough
- `analytics`: CA band RAT labeling in band distribution, anchor-based NSA handoff detection
- `test-foundation`: CellInfoCollector unit test coverage

## Impact

- **Data collection** (`CellInfoCollector.kt`): 5 changes — NR bandwidth, NR TAC, CA RAT unification, LTE cellIdBitLength, CA band bandwidth
- **Data model** (`CaBandSnapshot.kt`, `CaBandEntity`, `CaBandDetail`): Add `bandwidthKhz` and `earfcn` fields
- **Import/Export** (`CsvRecordParser.kt`, `GeoJsonRecordParser.kt`, `ExportSessionUseCase.kt`): Fix band import, add missing columns
- **Analytics** (`SessionAnalyticsEngine.kt`): Fix CA RAT labeling, add anchor handoff detection
- **UI** (`RecordDetailSheet.kt`, `CellInfoPanel.kt`, `SimLiveStateMapper.kt`, `RecordingScreen.kt`): Fix colors, fix band prefixes, add earfcn, fix click target
- **Tests**: New `CellInfoCollectorTest.kt` with 9+ test cases
- **Database**: New column `bandwidthKhz` on `cell_record_ca_bands` table (requires migration)
