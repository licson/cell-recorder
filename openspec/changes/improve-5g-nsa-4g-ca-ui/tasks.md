## 1. Data Model Extensions

- [ ] 1.1 Add `CaBandDetail(band: String, pci: String, rsrp: String, rsrq: String, sinr: String)` data class to `RecordingState.kt`
- [ ] 1.2 Extend `SimLiveState` with new fields: `anchorBand: String`, `anchorPci: String`, `anchorArfcn: String`, `anchorTac: String`, `anchorRsrp: String`, `anchorRsrq: String`, `anchorSinr: String`, `caBandDetails: List<CaBandDetail>`
- [ ] 1.3 Update `RecordingViewModel` to populate the new `SimLiveState` fields from `CellRecordSnapshot` (anchor fields from snapshot, CA band details from `snapshot.caBands`)
- [ ] 1.4 Update `LiveInfoViewModel` with the same population logic as RecordingViewModel

## 2. RecordingScreen Expandable SimCard

- [ ] 2.1 Add `expanded` boolean state to `SimCard` composable (remember + mutableStateOf)
- [ ] 2.2 Add collapsible anchor row for 5G NSA: `LTE: B<band> PCI <pci> RSRP <rsrp>` with RSRP color-coded by `rsrpColor()`
- [ ] 2.3 Add CA band count badge for 4G CA: modify Band `StatItem` to show `B<band>+<N>` when `caBandDetails.isNotEmpty()`
- [ ] 2.4 Add expand indicator chevron icon on the right edge of the card (visible only when expandable data exists)
- [ ] 2.5 Add expanded content section: Anchor details (Band, ARFCN, PCI, TAC, RSRP, RSRQ, SINR) for 5G NSA
- [ ] 2.6 Add expanded CA Bands section: structured rows showing `B<band> PCI <pci> RSRP <rsrp> RSRQ <rsrq> SINR <sinr>` for each CA band
- [ ] 2.7 Make card clickable to toggle expanded state

## 3. LiveInfoScreen Structured Display

- [ ] 3.1 Replace flat comma-separated `caBands.joinToString(", ")` with `FlowRow` of chips, each showing `B<band> PCI <pci>` with RSRP color-coded text
- [ ] 3.2 Expand anchor section from one-liner to structured rows: Row 1 (Band, ARFCN, PCI, TAC), Row 2 (RSRP, RSRQ, SINR color-coded), Row 3 (TA, CQI, RSSI, BW if available)
- [ ] 3.3 Use the new `SimLiveState` structured fields instead of the legacy `caBands` and `anchorInfo` strings

## 4. SessionDetailScreen Improvements

- [ ] 4.1 Fix duplicate column header: change second `"RSRP (dBm)"` to `"RSRQ (dBm)"` in `ColumnHeadersRow`
- [ ] 4.2 Add CA band count badge to `SimRecordRow`: append `+<N>` to the Band text when `wrapper.caBands.isNotEmpty()`
- [ ] 4.3 Create `RecordDetailSheet` composable with sections: Primary Cell, CA Bands, Anchor Cell, Location, Connectivity
- [ ] 4.4 Implement Primary Cell section with all fields (RAT, PLMN, Cell ID, PCI, TAC, Band, ARFCN, BW, RSRP, RSRQ, SINR, RSSI, CQI, TA) with signal quality coloring
- [ ] 4.5 Implement CA Bands section as a list of rows (band, EARFCN, PCI, RSRP, RSRQ, SINR per band) — hidden when no CA bands
- [ ] 4.6 Implement Anchor Cell section with all anchor fields — shown only for `5G_NSA` records with anchor data
- [ ] 4.7 Implement Location section (lat/lon/alt/accuracy/source, or relX/relY for indoor)
- [ ] 4.8 Implement Connectivity section (avgLatencyMs, packetLossPct)
- [ ] 4.9 Wire `selectedRecord` state to show `ModalBottomSheet` when non-null, dismiss on `selectRecord(null)`
- [ ] 4.10 Add RSRQ column data to `SimRecordRow` to match the fixed header (currently the second "RSRP" column renders `avgLatencyMs` or `relativeX` — restructure to show RSRQ in its own weighted column)

## 5. Analytics Band Labels

- [ ] 5.1 Add `rat: String` field to `BandCountPerSim` data class (or wrapper) in `SessionAnalyticsEngine`
- [ ] 5.2 Update `SessionAnalyticsEngine.computeBandDistribution()` to tag each band entry with its source RAT from the record
- [ ] 5.3 Update `AnalyticsPanel` `SimBarCard` to use `BandResolver.formatBand(bandNumber, earfcn=null, rat=rat)` for legend labels instead of raw `"Band N"`
- [ ] 5.4 Group band distribution items by RAT before rendering in the stacked bar chart
- [ ] 5.5 Apply distinct color ranges: NR bands in cool tones (cyan/teal range), LTE bands in warm tones (blue/indigo range)

## 6. Verification

- [ ] 6.1 Run `./gradlew assembleDebug` to verify clean build
- [ ] 6.2 Run lint checks and fix any new warnings
- [ ] 6.3 Verify RecordingScreen SimCard expand/collapse on 5G NSA and 4G CA SIMs
- [ ] 6.4 Verify LiveInfoScreen CA chips and anchor rows render correctly
- [ ] 6.5 Verify SessionDetailScreen bottom sheet opens on tap and shows all sections
- [ ] 6.6 Verify analytics band labels show "n78"/"B3" format and chart groups by RAT
