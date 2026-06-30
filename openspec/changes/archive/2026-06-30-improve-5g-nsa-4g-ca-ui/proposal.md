## Why

5G NSA anchor info and 4G CA band data are collected and stored but largely invisible to users. The RecordingScreen SimCard ignores both fields, the SessionDetailScreen shows CA bands only as a loaded-but-unused relation, and the record detail tap does nothing visible. Users on 5G NSA have no way to see their LTE anchor cell during recording, and CA band count is hidden everywhere except the LiveInfoScreen flat text.

## What Changes

- Add expandable behavior to RecordingScreen `SimCard`: collapsed state shows a compact anchor row for 5G NSA and `B3+2` badge for 4G CA; expanded state reveals full anchor fields and structured CA band rows
- Extend `SimLiveState` with structured anchor and CA band detail fields (separate from the existing flat strings) to support richer display in both RecordingScreen and LiveInfoScreen
- Replace flat comma-separated CA band text in `LiveInfoScreen` with a structured `FlowRow` of chips showing per-band PCI and color-coded RSRP
- Expand `LiveInfoScreen` anchor section from a one-liner to structured rows showing band/ARFCN/PCI/TAC and signal metrics with quality coloring
- Add a `ModalBottomSheet` to `SessionDetailScreen` for record detail inspection when a record row is tapped, displaying full primary cell info, all CA bands with per-band signal, all anchor fields for 5G NSA, and location/connectivity details
- Add CA band count badge (`+N`) to `SimRecordRow` in SessionDetailScreen next to the band label
- Fix duplicate `"RSRP (dBm)"` column header in `ColumnHeadersRow` (second instance should be `"RSRQ (dBm)"`)
- Use `BandResolver.formatBand()` in analytics band distribution labels instead of raw `"Band N"`, requiring RAT context to be passed through the band distribution data model
- Group band distribution chart by RAT section (NR bands vs LTE primary vs LTE CA) with distinct color shading

## Capabilities

### New Capabilities

- `record-detail-sheet`: Bottom sheet for inspecting a single cell record's full data (primary cell, CA bands, anchor, location, connectivity) in SessionDetailScreen
- `expandable-sim-card`: Expandable SimCard on RecordingScreen that collapses to compact NSA/CA indicator and expands to full anchor and CA band details

### Modified Capabilities

- `ui`: RecordingScreen SimCard now expandable; LiveInfoScreen CA/anchor display restructured; SessionDetailScreen gains record detail sheet, CA badge, and fixed column header; analytics band labels use qualified names
- `analytics`: Band distribution data model gains RAT context; chart grouped by RAT section; labels use BandResolver.formatBand()

## Impact

- UI layer: `RecordingScreen`, `LiveInfoScreen`, `SessionDetailScreen`, `AnalyticsPanel`
- State models: `RecordingState.SimLiveState` (new fields), `RecordingViewModel`, `LiveInfoViewModel`
- Analytics: `SessionAnalyticsEngine`, `BandCountPerSim` model
- No data model or database changes (all required data is already collected and stored)
