## Context

The 5G NSA LTE anchor cell is already fully captured end-to-end: `CellInfoCollector` splits `lteAnchor.eci` into `anchorEnbOrGnbId` (eci shr 8) and `anchorLcid` (eci and 0xFF) at `CellInfoCollector.kt:93-94`; these are persisted on `CellRecordEntity`, exported as CSV columns `anchor_enb_gnb_id` / `anchor_lcid`, exported as GeoJSON properties `anchorEnbGnbId` / `anchorLcid`, and round-tripped on import. The `cell-info/spec.md` "LTE anchor fields populated" scenario already enumerates both fields.

The gap is purely presentational. Every screen that shows an anchor section enumerates its fields (Band, ARFCN, PCI, TAC, RSRP/RSRQ/SINR) but omits Cell ID, while the primary cell always shows Cell ID formatted as `enbOrGnbId:lcid`. Two parallel display paths feed the UI:

- **Live path** (`RecordingScreen`, `LiveInfoScreen`, `ReplayScreen` StatsPanel): `CellRecordSnapshot` → `SimLiveStateMapper` → `SimLiveState` → `CellInfoPanel` (via `SimLiveState.toCellInfoData()` → `CellInfoData.anchorCell: AnchorCellInfo`).
- **Recorded path** (`SessionDetailScreen` bottom sheet): `CellRecordWithCaBands` → `CellInfoData` via `CellRecordWithCaBands.toCellInfoData()` (also feeds `CellInfoPanel`); and `RecordDetailSheet` reads `CellRecordEntity` directly.

`SimLiveState` currently carries individual anchor string fields (band, pci, arfcn, tac, rsrp, rsrq, sinr) and a pre-formatted `anchorInfo` compact summary, but **not** the anchor's split identity. `AnchorCellInfo` (in `CellInfoPanel.kt`) likewise has no `cellId` field. So the identity is dropped during the snapshot→live-state mapping.

## Goals / Non-Goals

**Goals:**
- Show the anchor cell's Cell ID (`anchorEnbOrGnbId:anchorLcid`) in every expanded anchor section and in the record detail bottom sheet's Anchor Cell section, formatted identically to the primary LTE Cell ID.
- Keep the collapsed compact anchor rows on Recording/Replay unchanged (`LTE: B<band> PCI <pci> RSRP <rsrp>`) — they are deliberately one-line summaries.
- Render `---` when either anchor identity component is null, consistent with how missing primary Cell ID is handled.
- No persistence, schema, export, import, or collector changes.

**Non-Goals:**
- Storing or displaying the raw 28-bit anchor ECI. The split form already matches how LTE primaries are shown; adding a raw ECI field would require a schema migration for no display value.
- Surfacing `anchorRssi` / `anchorCqi` / `anchorTimingAdvance` / `anchorBandwidthKhz` on the live `CellInfoPanel` (a pre-existing spec/impl gap unrelated to Cell ID). The `record-detail-sheet` spec already lists bandwidth/RSSI/CQI/TA for the anchor; that is out of scope here.
- Changing the compact anchor summary string.

## Decisions

### Decision 1: Format anchor Cell ID as `anchorEnbOrGnbId:anchorLcid`

Reuse the existing primary-cell format (`SimLiveStateMapper.formatCellId` produces `"${enbOrGnbId}:${lcid}"`). Applying the same format to the anchor makes the two LTE cells visually parallel and requires no new formatting logic — only a new field carrying the joined string.

**Alternatives considered:**
- *Raw ECI display*: rejected because the collector discards `eci` after splitting (only the split components are persisted), and showing a raw 28-bit integer would be inconsistent with how every other LTE cell is presented.
- *Separate `eNB` and `lcid` rows*: rejected; the primary cell uses a single `Cell ID` row, and matching that keeps the anchor block the same height.

### Decision 2: Thread a single formatted `anchorCellId: String` through `SimLiveState`

Add one field `anchorCellId: String = "---"` to `SimLiveState`. `SimLiveStateMapper.map()` formats it from `snapshot.anchorEnbOrGnbId` / `snapshot.anchorLcid` (falling back to `"---"` when either is null), mirroring how `formatCellId` handles the primary. This avoids threading two raw fields and keeps `SimLiveState` a string-only view model (consistent with its existing anchor fields, which are all pre-formatted strings).

**Alternatives considered:**
- *Add `anchorEnbOrGnbId` + `anchorLcid` as separate string fields*: rejected — would duplicate the primary's pattern of formatting in the mapper, and `SimLiveState` already commits to pre-formatted strings for anchor fields.
- *Format inside `CellInfoPanel`*: rejected; `CellInfoPanel` is documented as presentation-only ("Carries pre-formatted strings so the panel is presentation-only"), so formatting belongs in the mapper.

### Decision 3: Add `cellId: String` to `AnchorCellInfo` and populate from both mappers

`AnchorCellInfo` gains a `cellId` field. Both `SimLiveState.toCellInfoData()` and `CellRecordWithCaBands.toCellInfoData()` populate it — the former from `SimLiveState.anchorCellId`, the latter by formatting `record.anchorEnbOrGnbId` / `record.anchorLcid` inline (the recorded path bypasses `SimLiveState`). `CellInfoPanel`'s expanded anchor block renders a `Cell ID` StatItem as the first anchor row, weighted to match the primary's `Cell ID` column (weight `1.2f`, monospace font).

**Alternatives considered:**
- *Render the Cell ID row only in `RecordDetailSheet`, not `CellInfoPanel`*: rejected per the proposal (all 3 surfaces).
- *Reuse the primary `formatCellId` helper for the anchor*: this is what the recorded-path mapper will do (the helper already lives in the detail package); for the live path, formatting happens in `SimLiveStateMapper` which has its own `formatCellId`. Both produce identical `x:y` output, so no consolidation is required for this change.

### Decision 4: `RecordDetailSheet` adds a `DetailRow("Cell ID", ...)` as the first anchor row

`RecordDetailSheet` reads `CellRecordEntity` directly and already calls a `formatCellId(record)` helper for the primary. Add an analogous anchor formatting (either a small inline helper or extend the existing `formatCellId` to accept the anchor fields) and emit `DetailRow("Cell ID", anchorCellId)` at the top of the Anchor Cell block, before Band. Place Cell ID first to mirror the Primary Cell section ordering (Cell ID precedes PCI/TAC/Band there too, after RAT/PLMN).

### Decision 5: Collapsed compact rows unchanged

The `SimLiveStateMapper`-produced `anchorInfo` compact string (`"B{band} PCI {pci} RSRP {rsrp}"`) and the `CellInfoPanel` collapsed `Anchor` StatItem stay as-is. They are intentionally brief summaries; adding a cell ID would force truncation or wrap. Only the expanded anchor block and the detail sheet gain the row.

## Risks / Trade-offs

- **[Risk] Expanded anchor block grows by one row on already-dense SimCards** → Mitigation: the row reuses the existing weight/font scheme; anchor blocks only appear for 5G NSA ticks, so non-NSA cards are unaffected. Layout weights already flex; a 1.2f Cell ID column fits the existing Row.
- **[Risk] `anchorEnbOrGnbId` present but `anchorLcid` null (or vice versa) yields a half-populated ID** → Mitigation: treat the pair as atomic — if either is null, render `---`. This matches `SimLiveStateMapper.formatCellId`'s existing null-handling for the primary cell.
- **[Risk] Divergence between the live mapper's `formatCellId` and the detail package's `formatCellId`** → Mitigation: both produce `"$id:$lcid"`; no consolidation is forced, but if a future change centralizes cell-ID formatting, both call sites should be updated together. Noted as an open question, not addressed here.
- **[Trade-off] Anchor Cell ID shown as split `eNB:lcid` only, never raw ECI** → acceptable; matches primary-cell convention and avoids a schema migration.

## Migration Plan

No data migration. The change is display-only and the underlying fields already exist. Rollout is a single build; rollback is reverting the UI additions (no persistence format is touched, so old recordings continue to display correctly with `---` for any record that genuinely lacks anchor identity).

## Open Questions

- Should the two `formatCellId` helpers (one in `SimLiveStateMapper`, one in the detail package) be consolidated into a single shared formatter? Out of scope for this change, but worth a follow-up cleanup if it surfaces as a maintenance friction point.
