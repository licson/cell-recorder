## Context

A code review of LTE CA band handling and 5G NSA handling identified 16 issues across the full data pipeline: collection (`CellInfoCollector`), persistence (`PointRecorder`, DAO), export/import (CSV, GeoJSON), analytics (`SessionAnalyticsEngine`), and UI (`RecordDetailSheet`, `CellInfoPanel`, `SimLiveStateMapper`, `RecordingScreen`). Two are critical bugs with user-visible impact, three are high-severity with spec violations, and the remainder are medium-to-low polish and data completeness issues.

The codebase has had prior changes addressing 5G NSA correctness (`improve-5g-nsa-recording`, `fix-nsa-empty-snapshot-rat-switch`) and UI improvements (`improve-5g-nsa-4g-ca-ui`). This change fixes issues that slipped through those efforts.

## Goals / Non-Goals

**Goals:**
- Fix all 16 identified issues from the code review
- Maintain backward compatibility for existing database records and exported files
- Add unit test coverage for `CellInfoCollector` to prevent regressions
- Ensure consistent band prefix rendering across all UI surfaces

**Non-Goals:**
- Refactoring the overall architecture of `CellInfoCollector` or `PointRecorder`
- Adding NR CA (NR secondary cells) support — only LTE secondary cells are supported as CA bands today
- Redesigning the export/import column schema beyond adding missing fields
- Adding instrumented (androidTest) tests — only JVM unit tests are in scope

## Decisions

### D1: Unify LTE CA RAT detection to use actual extracted CA bands

**Decision:** In `buildSnapshot()`, change the LTE branch to always use `caBands.isNotEmpty()` for the `4G_CA` label, matching the NSA fallback path. Remove the dependency on `networkType.technology == NetworkType.LTE_CA`.

**Rationale:** The modem's `NetworkType.LTE_CA` flag is unreliable — some modems don't report it even when secondary cells are active. The NSA fallback path already uses the correct approach (checking extracted CA bands). Since `buildLteSnapshot()` always calls `extractCaBands()` and attaches the results, the CA band list is the authoritative source.

**Alternatives considered:** Keep `NetworkType.LTE_CA` as an OR condition (`networkType == LTE_CA || caBands.isNotEmpty()`). Rejected because it adds complexity without benefit — `extractCaBands()` already captures all secondary cells including those the modem might report via `LTE_CA`.

### D2: Carry RAT context through CA band and anchor band data classes

**Decision:** Add a `rat` field to `CaBandInfo` and `AnchorCellInfo` (UI data classes in `CellInfoPanel.kt`), and use `BandResolver.formatBand()` at mapping time rather than hard-coding `"B"` prefix at render time.

**Rationale:** The hard-coded `"B"` prefix is correct for today's LTE-only CA bands and LTE anchors, but is fragile and incorrect for any future NR secondary cells. Passing the RAT at mapping time and formatting with `BandResolver` is the consistent pattern already used for primary bands.

**Implementation:** For CA bands on 5G NSA records, the RAT is `"4G"` (they're LTE secondaries from the anchor). For CA bands on 4G records, the RAT is `"4G"`. The anchor is always `"4G"`. Format via `BandResolver.formatBand(bandNumber, earfcn, rat)`.

### D3: Fix RecordDetailSheet color functions by delegating to existing correct functions

**Decision:** Replace all `rsrpColor(rsrq)` and `rsrpColor(sinr)` calls in `RecordDetailSheet.kt` with `rsrqColor(rsrq)` and `sinrColor(sinr)` respectively. These functions already exist in `SignalQualityColors.kt` and are correctly used in `CellInfoPanel.kt`.

**Rationale:** Direct fix — the correct functions exist and are already imported elsewhere. No new logic needed.

### D4: Add `bandwidthKhz` to `CellRecordCaBandEntity` via Room migration

**Decision:** Add a nullable `bandwidthKhz` column to the `cell_record_ca_bands` table. This requires a Room schema version bump and an `ALTER TABLE ... ADD COLUMN` migration.

**Rationale:** The column addition is safe — `ALTER TABLE ADD COLUMN` is non-destructive, the default is null, and existing rows are unaffected. No table rebuild is needed.

### D5: Prefer NR TAC, fall back to LTE anchor TAC

**Decision:** In `buildNsaSnapshot()`, change `tac = lteAnchor?.tac` to `tac = nrCell.tac ?: lteAnchor?.tac`. If the NR cell reports its own TAC, use it; otherwise fall back to the LTE anchor's TAC.

**Rationale:** In most NSA deployments, the NR cell doesn't report its own TAC and `nrCell.tac` is null, so behavior is unchanged. But if the modem does provide it, it's the more correct value for the primary NR record.

### D6: CellInfoCollector unit tests mock NetMonster's INetMonster interface

**Decision:** Create `CellInfoCollectorTest.kt` that mocks `INetMonster` (interface from NetMonster library) via Mockito, constructing test `CellLte`, `CellNr`, etc. instances with known values.

**Rationale:** `CellInfoCollector` depends only on `INetMonster` (constructor-injected). NetMonster exposes cell classes with public constructors, making them straightforward to instantiate in tests. This avoids needing an emulator.

### D7: Add missing import fields without breaking existing files

**Decision:** Add `isLocationEstimated` and `locationSource` to `CsvRecordParser.columnMap` and `GeoJsonRecordParser`. Add `bandwidthKhz` to CSV/GeoJSON export headers. All new fields are optional with existing defaults when absent.

**Rationale:** Import of files exported by the current app should round-trip these fields. Files from older versions (without these columns) will continue to import with defaults.

### D8: Analytics CA band RAT labeling — use LTE RAT for CA bands

**Decision:** In `computeBandDistribution()`, tag CA band entries with `"4G"` (or `"4G_CA"`) instead of inheriting the parent record's RAT. Since CA bands in the current data model are always LTE secondary cells, they should always be labeled as LTE for band distribution purposes.

**Rationale:** A 5G NSA record's CA bands come from the LTE anchor's secondary cells. Labeling them as `5G_NSA` distorts the band distribution chart.

### D9: Anchor-based handoff detection — lightweight approach

**Decision:** In the handoff detection loop, add a check for anchor field changes (`anchorEnbOrGnbId` or `anchorPci` change) on consecutive 5G NSA records. Classify as `ANCHOR_CHANGE` handoff type.

**Rationale:** This captures the most common NSA network change (LTE anchor switch) without requiring schema changes to the handoff event model — `ANCHOR_CHANGE` is a new constant in the existing handoff type enum/string.

## Risks / Trade-offs

- **[Room migration for CA band bandwidth column]** → Low risk. `ALTER TABLE ADD COLUMN` with nullable default is the simplest migration. A migration test should be added to `MigrationTest.kt` to verify.

- **[Changing LTE CA RAT detection from modem type to actual bands]** → Some recordings that previously showed `4G_CA` (modem reported CA but no secondary cells were detected) will now show `4G`. This is more correct but is technically a behavioral change. The reverse case (secondary cells present but modem didn't report CA) will now correctly show `4G_CA`.

- **[Adding ANCHOR_CHANGE handoff type]** → Existing analytics results will gain new handoff events on re-analysis. The `SessionAnalytics` data class and UI need to handle the new type. Since handoff types are strings, no schema change is needed.

- **[NR TAC preference over LTE TAC]** → On devices where the NR cell reports a TAC, the recorded TAC may differ from previous recordings of the same cell. This is more correct but could appear as a "change" in historical data comparisons.
