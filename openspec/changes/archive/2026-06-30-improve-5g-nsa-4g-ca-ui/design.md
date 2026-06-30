## Context

The app collects 5G NSA anchor and 4G CA band data during recording but makes most of it invisible to users. The RecordingScreen `SimCard` has the data in `SimLiveState` but doesn't render it. The SessionDetailScreen loads CA bands via `CellRecordWithCaBands` but `SimRecordRow` only uses `wrapper.record`, discarding the CA bands. Tapping a record row sets `selectedRecord` state but nothing renders — there's no way to inspect a single record's full details. The LiveInfoScreen shows anchor/CA as flat text strings. Analytics band distribution uses raw numbers like "Band 3" without RAT-qualified prefixes like "B3" or "n78".

All the required data already exists in the data model — no database changes, no new collection logic. The change is purely about surfacing existing data in the UI.

## Goals / Non-Goals

**Goals:**
- Make 5G NSA anchor info and 4G CA bands visible on the RecordingScreen during active recording
- Show structured CA band and anchor details in LiveInfoScreen
- Add a record detail bottom sheet to SessionDetailScreen for inspecting full record data
- Add CA band count badge to session detail rows
- Fix duplicate column header in SessionDetailScreen
- Use qualified band names (B3, n78) in analytics charts
- Group analytics band distribution by RAT type

**Non-Goals:**
- Adding new data fields or database columns
- Changing data collection logic in CellInfoCollector
- Modifying export/import formats (already include all data)
- Changing RAT color scheme (keeping uniform: 5G*=cyan, 4G*=blue)

## Decisions

### D1: SimCard expandable via local state — not a separate screen

The RecordingScreen SimCard will use a local `expanded` boolean state. Tapping the card toggles expansion. This avoids navigation complexity and keeps the card inline with the map visible above. The collapsed state adds only one row (anchor summary for 5G NSA, band+count badge for 4G CA), keeping the card compact for map viewing.

**Alternative considered:** A dedicated "Cell Details" overlay/bottom sheet on the recording screen — rejected because it occludes the map and adds navigation flow complexity for data that's already in the card.

### D2: Extend SimLiveState with structured fields instead of replacing strings

Add `anchorBand`, `anchorPci`, `anchorArfcn`, `anchorTac`, `anchorRsrp`, `anchorRsrq`, `anchorSinr` as individual String fields, and `caBandDetails: List<CaBandDetail>` as a structured list. Keep the existing `caBands: List<String>` and `anchorInfo: String` for backward compatibility during migration. The new fields support the expandable card and structured LiveInfo display.

**Alternative considered:** Replacing the flat strings entirely — rejected because it would break the LiveInfoScreen during incremental development. Keeping both allows progressive migration.

### D3: CaBandDetail as a data class in RecordingState

Add `CaBandDetail(band: String, pci: String, rsrp: String, rsrq: String, sinr: String)` to `RecordingState.kt`. This keeps all live state models in one file and is consumed by both RecordingScreen and LiveInfoScreen.

### D4: ModalBottomSheet for record detail in SessionDetailScreen

When `selectedRecord != null`, show a `ModalBottomSheet` with the record's full data. The sheet has three sections: Primary Cell (all fields), CA Bands (list per band), and Anchor Cell (5G NSA only). This reuses the existing `selectedRecord` state flow and `selectRecord()` ViewModel method which are already wired but have no UI.

**Alternative considered:** Navigating to a separate RecordDetailScreen — rejected because a bottom sheet is lighter weight and doesn't disrupt the session detail scroll position.

### D5: CA count badge format — `n78 +3`

The band column in `SimRecordRow` currently shows `BandResolver.formatBand()` output (e.g., "n78"). Append `+N` when CA bands exist (e.g., "n78 +3"). This is compact and immediately signals CA activity without taking extra column width.

### D6: BandResolver.formatBand() in analytics — Pass RAT context

The `BandDistributionPerSim` model currently stores `bandNumber: Int` without RAT. Add `rat: String` to the `BandCountPerSim` entry (or a new wrapper). In `SessionAnalyticsEngine.computeBandDistribution()`, tag each band with its source RAT. In `AnalyticsPanel`, use `BandResolver.formatBand(bandNumber, earfcn=null, rat=rat)` for display labels.

**Note:** When `earfcn` is null, `formatBand` falls back to the prefix based on `rat.startsWith("5G")`. This gives "n78" for 5G and "B3" for 4G, which is sufficient for the chart.

### D7: Band distribution grouped by RAT in analytics chart

The existing `StackedDistributionBar` renders all bands in one stacked bar. Group the items by RAT before rendering: show NR bands first, then LTE primary bands, then CA bands (if separable). Use distinct color ranges: warm colors for LTE bands, cool colors for NR bands, within the same chart.

This is a rendering-only change — the data model already has `rat` per band after D6. The `SimBarCard` composable will receive grouped items and render them with section labels.

## Risks / Trade-offs

- [Expanded SimCard increases vertical space during recording, reducing map view] → Collapsed state adds only ~1 row; expansion is user-initiated only
- [CaBandDetail list on SimLiveState increases state size] → Typically 1-4 CA bands per SIM; negligible memory impact
- [Bottom sheet may obscure record list on small screens] → Standard ModalBottomSheet behavior; swipe-to-dismiss is available
- [formatBand with null earfcn relies on RAT prefix only] → Acceptable; the analytics context always has RAT available, and the prefix (B/n) is the key differentiator for users
- [Grouping bands by RAT in chart adds complexity to AnalyticsPanel] → Low risk; grouping is done in the composable, no model changes beyond adding `rat` to band entries
