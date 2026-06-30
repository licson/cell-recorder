## 1. Data Model Extensions

- [x] 1.1 Add `CaBandDetail(band: String, pci: String, rsrp: String, rsrq: String, sinr: String)` data class to `RecordingState.kt`
- [x] 1.2 Extend `SimLiveState` with new fields: `anchorBand: String`, `anchorPci: String`, `anchorArfcn: String`, `anchorTac: String`, `anchorRsrp: String`, `anchorRsrq: String`, `anchorSinr: String`, `caBandDetails: List<CaBandDetail>`
- [x] 1.3 Update `RecordingViewModel` to populate the new `SimLiveState` fields from `CellRecordSnapshot` (anchor fields from snapshot, CA band details from `snapshot.caBands`)
- [x] 1.4 Update `LiveInfoViewModel` with the same population logic as RecordingViewModel

## 2. RecordingScreen Expandable SimCard

- [x] 2.1 Add `expanded` boolean state to `SimCard` composable (remember + mutableStateOf)
- [x] 2.2 Add collapsible anchor row for 5G NSA: `LTE: B<band> PCI <pci> RSRP <rsrp>` with RSRP color-coded by `rsrpColor()`
- [x] 2.3 Add CA band count badge for 4G CA: modify Band `StatItem` to show `B<band>+<N>` when `caBandDetails.isNotEmpty()`
- [x] 2.4 Add expand indicator chevron icon on the right edge of the card (visible only when expandable data exists)
- [x] 2.5 Add expanded content section: Anchor details (Band, ARFCN, PCI, TAC, RSRP, RSRQ, SINR) for 5G NSA
- [x] 2.6 Add expanded CA Bands section: structured rows showing `B<band> PCI <pci> RSRP <rsrp> RSRQ <rsrq> SINR <sinr>` for each CA band
- [x] 2.7 Make card clickable to toggle expanded state

## 3. LiveInfoScreen Structured Display

- [x] 3.1 Replace flat comma-separated `caBands.joinToString(", ")` with `FlowRow` of chips, each showing `B<band> PCI <pci>` with RSRP color-coded text
- [x] 3.2 Expand anchor section from one-liner to structured rows: Row 1 (Band, ARFCN, PCI, TAC), Row 2 (RSRP, RSRQ, SINR color-coded), Row 3 (TA, CQI, RSSI, BW if available)
- [x] 3.3 Use the new `SimLiveState` structured fields instead of the legacy `caBands` and `anchorInfo` strings

## 4. SessionDetailScreen Improvements

- [x] 4.1 Fix duplicate column header: change second `"RSRP (dBm)"` to `"RSRQ (dBm)"` in `ColumnHeadersRow`
- [x] 4.2 Add CA band count badge to `SimRecordRow`: append `+<N>` to the Band text when `wrapper.caBands.isNotEmpty()`
- [x] 4.3 Create `RecordDetailSheet` composable with sections: Primary Cell, CA Bands, Anchor Cell, Location, Connectivity
- [x] 4.4 Implement Primary Cell section with all fields (RAT, PLMN, Cell ID, PCI, TAC, Band, ARFCN, BW, RSRP, RSRQ, SINR, RSSI, CQI, TA) with signal quality coloring
- [x] 4.5 Implement CA Bands section as a list of rows (band, EARFCN, PCI, RSRP, RSRQ, SINR per band) — hidden when no CA bands
- [x] 4.6 Implement Anchor Cell section with all anchor fields — shown only for `5G_NSA` records with anchor data
- [x] 4.7 Implement Location section (lat/lon/alt/accuracy/source, or relX/relY for indoor)
- [x] 4.8 Implement Connectivity section (avgLatencyMs, packetLossPct)
- [x] 4.9 Wire `selectedRecord` state to show `ModalBottomSheet` when non-null, dismiss on `selectRecord(null)`
- [x] 4.10 Add RSRQ column data to `SimRecordRow` to match the fixed header (currently the second "RSRP" column renders `avgLatencyMs` or `relativeX` — restructure to show RSRQ in its own weighted column)

## 5. Analytics Band Labels

- [x] 5.1 Add `rat: String` field to `BandCountPerSim` data class (or wrapper) in `SessionAnalyticsEngine`
- [x] 5.2 Update `SessionAnalyticsEngine.computeBandDistribution()` to tag each band entry with its source RAT from the record
- [x] 5.3 Update `AnalyticsPanel` `SimBarCard` to use `BandResolver.formatBand(bandNumber, earfcn=null, rat=rat)` for legend labels instead of raw `"Band N"`
- [x] 5.4 Group band distribution items by RAT before rendering in the stacked bar chart
- [x] 5.5 Apply distinct color ranges: NR bands in cool tones (cyan/teal range), LTE bands in warm tones (blue/indigo range)

## 6. Verification

- [x] 6.1 Run `./gradlew assembleDebug` to verify clean build
- [x] 6.2 Run lint checks and fix any new warnings
- [x] 6.3 Verify RecordingScreen SimCard expand/collapse on 5G NSA and 4G CA SIMs
- [x] 6.4 Verify LiveInfoScreen CA chips and anchor rows render correctly
- [x] 6.5 Verify SessionDetailScreen bottom sheet opens on tap and shows all sections
- [x] 6.6 Verify analytics band labels show "n78"/"B3" format and chart groups by RAT

## 7. Shared CellInfoPanel Refactoring

- [x] 7.1 Create `CellInfoData`, `CaBandInfo`, `AnchorCellInfo` data classes in `ui/shared/CellInfoPanel.kt`
- [x] 7.2 Add `SimLiveState.toCellInfoData()` conversion function with CA combo notation (`B3+2`)
- [x] 7.3 Add `CellRecordWithCaBands.toCellInfoData()` conversion function with RAT-aware band formatting
- [x] 7.4 Implement `CellInfoPanel` composable with `isExpandable`, `expanded`, `onExpandToggle` parameters
- [x] 7.5 Support compact anchor row in collapsed state, full structured anchor/CA rows in expanded state
- [x] 7.6 Apply signal quality colors (rsrpColor, rsrqColor, sinrColor) consistently across all fields
- [x] 7.7 Refactor `RecordingScreen.SimCard` to use `CellInfoPanel(isExpandable = true)` — remove ~120 lines of duplicated anchor/CA logic
- [x] 7.8 Refactor `ReplayScreen.StatsPanel` to use `CellInfoPanel(isExpandable = true)` — remove ~120 lines of duplicated anchor/CA logic
- [x] 7.9 Refactor `LiveInfoScreen.LiveSimCard` to use `CellInfoPanel(isExpandable = false)` — replaces FlowRow chips with structured CA band rows, adds CA combo notation
- [x] 7.10 Delete duplicated `StatItem`, `LiveStatItem`, `SuggestionChip`, `FlowRow` composables from all three screens
- [x] 7.11 Verify `./gradlew assembleDebug` builds successfully after refactoring
