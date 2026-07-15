## Why

The 5G NSA LTE anchor cell's identity is captured (`anchorEnbOrGnbId` + `anchorLcid`), persisted, and exported to CSV/GeoJSON, but never shown on any screen. Every other anchor attribute (band, PCI, ARFCN, TAC, RSRP/RSRQ/SINR) is displayed, and the primary cell always shows its `eNB:lcid` Cell ID — so the anchor's Cell ID is a conspicuous, inconsistent omission that leaves users unable to identify which LTE eNB is anchoring their 5G NSA session.

## What Changes

- Add the anchor cell's Cell ID (formatted as `anchorEnbOrGnbId:anchorLcid`, matching the primary LTE Cell ID format) to the anchor section of every screen that displays anchor details:
  - Live Info screen anchor section
  - Recording screen expandable SimCard (expanded anchor block; the collapsed compact row is unchanged to preserve its one-line summary)
  - Replay screen expandable StatsPanel (expanded anchor block; collapsed compact row unchanged)
  - Session detail record bottom sheet Anchor Cell section
- Treat a missing anchor ID the same way as other missing anchor fields: render `---` so the row stays aligned with siblings.
- No data-layer, persistence, export, or import changes — the fields already exist end-to-end.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `ui`: Live Info, Recording SimCard, Session Detail bottom sheet, and Replay StatsPanel anchor display requirements gain an explicit Cell ID row.
- `record-detail-sheet`: Anchor Cell section content list gains Cell ID.
- `sessions`: Replay stats panel anchor summary gains Cell ID.

## Impact

- **Code:** `SimLiveState` / `SimLiveStateMapper`, `CellInfoPanel` (`AnchorCellInfo` + expanded anchor rows + compact row decision), `RecordDetailSheet`, and the shared `CellRecordWithCaBands.toCellInfoData()` / `SimLiveState.toCellInfoData()` mappers. No DAO, entity, migration, collector, or export/import changes.
- **Data:** No schema or persistence impact. `anchorEnbOrGnbId` and `anchorLcid` are already stored and round-tripped.
- **Specs:** Display requirements in `ui`, `record-detail-sheet`, and `sessions` are tightened to enumerate Cell ID alongside the existing anchor fields. `cell-info` is unaffected (its anchor field list is already correct).
- **Tests:** Existing instrumented UI tests for Recording/LiveInfo/Replay/SessionDetail screens and the RecordDetailSheet will need assertions for the new row; unit coverage for `SimLiveStateMapper` and the `toCellInfoData()` mappers should assert the anchor cell ID formatting.
