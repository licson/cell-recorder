## 1. Shared Signal Quality Helpers

- [x] 1.1 Create `SignalQualityColors.kt` in `ui/shared/` with `rsrpColor(value: Int?): Color`, `rsrqColor(value: Int?): Color`, and `sinrColor(value: Int?): Color` using the existing thresholds from `MapDisplayMode.kt`
- [x] 1.2 Create `CellFormatUtils.kt` in `ui/shared/` with shared `formatPlmn()`, `formatCellId()`, and `formatBandLabel()` helpers to eliminate duplication across `RecordingScreen`, `LiveInfoScreen`, `ReplayScreen`, `SessionDetailScreen`, and `SimLiveStateMapper`
- [x] 1.3 Update `MapDisplayMode.kt` to delegate to the new shared helpers instead of defining its own `rsrpColor()` and `rsrpColorArgb()`
- [x] 1.4 Update `SessionDetailScreen.kt` to delegate to the new shared helpers instead of defining its own `ratColor()`, `formatPlmn()`, and `formatCellId()`
- [x] 1.5 Update `SimLiveStateMapper.kt` to delegate to the new shared helpers instead of defining its own `formatPlmn()` and `formatCellId()`
- [x] 1.6 Verify no duplicate `ratColor`, `rsrpColor`, `formatPlmn`, or `formatCellId` functions remain in the codebase (grep and clean up any stragglers in `ReplayScreen.kt`)

## 2. InsightCard Fix

- [x] 2.1 Modify `InsightCard` composable in `ui/analytics/components/InsightCard.kt` to accept `insights: List<InsightCardData>` (or reuse the existing `InsightCard` model from `domain/analytics/model/InsightCard.kt`) and render a list of cards with title and body
- [x] 2.2 Update `AnalyticsPanel.kt` to pass `analytics.insightCards` to the `InsightCard()` composable instead of calling it with no arguments
- [x] 2.3 Add empty state handling: when `insights.isEmpty()`, show a compact "No insights for this session" text instead of the robot placeholder
- [x] 2.4 Run the app and verify that a session with handoff events generates and displays real insight cards (e.g., "Massive MIMO Candidate")

## 3. ReplayScreen StatsPanel Parity

- [x] 3.1 Add `CaBandDetail` and structured anchor fields to `ReplayViewModel` by reusing or adapting the `SimLiveStateMapper` logic to build a `SimLiveState`-like object from the current `CellRecordEntity`
- [x] 3.2 Modify `ReplayScreen.StatsPanel` to be expandable: add an `expanded` boolean state, a clickable surface, and a chevron indicator
- [x] 3.3 Add collapsed state content: compact anchor row for 5G NSA (`LTE: B<band> PCI <pci> RSRP <rsrp>`) and CA band count badge (`B<band>+<N>`) for 4G CA
- [x] 3.4 Add expanded state content: full anchor details (Band, ARFCN, PCI, TAC, RSRP, RSRQ, SINR) and structured CA band rows (band, PCI, EARFCN, RSRP, RSRQ, SINR per band)
- [x] 3.5 Apply signal quality color coding to all signal values in the StatsPanel (primary cell, anchor, and CA bands) using the shared helpers from task 1

## 4. Signal Color Coding on Existing Screens

- [x] 4.1 Update `RecordingScreen.SimCard` to apply `rsrpColor()`, `rsrqColor()`, and `sinrColor()` to the primary cell RSRP, RSRQ, and SINR `StatItem` values
- [x] 4.2 Update `LiveInfoScreen.LiveSimCard` to apply signal quality colors to RSRP, RSRQ, and SINR values in the primary cell row
- [x] 4.3 Update `LiveInfoScreen.LiveSimCard` to apply signal quality colors to CA band chips and anchor metrics (using the structured fields from `improve-5g-nsa-4g-ca-ui`)
- [x] 4.4 Update `RecordDetailSheet` (from `improve-5g-nsa-4g-ca-ui`) to apply signal quality colors to all signal metrics in the Primary Cell, CA Bands, and Anchor Cell sections

## 5. StatisticsScreen Band Labels

- [x] 5.1 Update `StatisticsViewModel` to pass RAT context through the band distribution data model (reusing the `rat` field added to `BandCountPerSim` by `improve-5g-nsa-4g-ca-ui`)
- [x] 5.2 Update `StatisticsScreen` `SimBarCard` to use `BandResolver.formatBand(bandNumber, earfcn=null, rat=rat)` for legend labels instead of raw `"Band ${it.bandNumber}"`
- [x] 5.3 Apply the same RAT color grouping (cool tones for NR, warm tones for LTE) to the `StatisticsScreen` band distribution chart, matching the `AnalyticsPanel` fix from `improve-5g-nsa-4g-ca-ui`

## 6. Verification

- [x] 6.1 Run `./gradlew clean assembleDebug` to verify a clean build with no errors
- [x] 6.2 Run lint checks (`./gradlew lint`) and fix any new warnings
- [x] 6.3 Verify InsightCard renders real insights on a session with 3+ intra-site PCI changes or 3+ cross-site handoffs
- [x] 6.4 Verify ReplayScreen StatsPanel expand/collapse works and shows structured anchor/CA data
- [x] 6.5 Verify RecordingScreen SimCard signal values are color-coded (test with a SIM showing RSRP > -80, -85, -95, -105)
- [x] 6.6 Verify LiveInfoScreen LiveSimCard signal values are color-coded
- [x] 6.7 Verify RecordDetailSheet signal values are color-coded
- [x] 6.8 Verify StatisticsScreen band labels show "B3"/"n78" format and chart groups by RAT
- [x] 6.9 Run existing instrumented tests to ensure no regressions (`./gradlew connectedAndroidTest` or at least `./gradlew test`)
