## Context

`CellInfoCollector.snapshots()` picks the first cell with `PrimaryConnection` per subscription. In 5G NSA (EN-DC) mode, NetMonster reports the LTE anchor as `PrimaryConnection` and the NR secondary cell as `SecondaryConnection`. This means the `CellNr` branch (which sets `rat = "5G_NSA"`) is never reached. The NR cell's band, PCI, SS-RSRP, SS-RSRQ, SS-SINR, and NCI are completely lost. Every NSA recording is incorrectly labeled `"4G"`.

The analytics engine's "Massive MIMO Candidate" insight only checks `it.rat == "5G_SA"`, excluding NSA intra-site PCI changes from the insight card.

A dual-record approach (separate LTE and NR records per NSA point) was considered but rejected because the replay screen uses a flat record list — two records at the same timestamp would cause slider jitter, chart discontinuity between NR and LTE signal scales, and misleading RAT timeline alternation.

## Goals / Non-Goals

**Goals:**

- Correctly detect 5G NSA mode and record the NR cell as the primary record with `rat = "5G_NSA"`
- Capture LTE anchor cell identity, band, and signal metrics as anchor fields on the same NSA record
- Attach LTE CA bands to the NSA record (from the anchor's secondary cells)
- Populate `bandwidthKhz` for NR cells when the modem reports it
- Fix analytics insight to include `5G_NSA` alongside `5G_SA`
- Update export/import formats for anchor fields
- Update UI to display anchor data for NSA records

**Non-Goals:**

- Changing `batchResplit()` for `5G_NSA` — NCI is not reliably reported in NSA mode
- Capturing NR CA/SCC bands — Android telephony API does not report NR-CA combos
- Refactoring stringly-typed RAT constants into an enum — out of scope
- Adding NSA-specific insight cards — existing insights should handle NSA after the fix
- Changing the replay or session detail architecture (e.g., grouping by timestamp)

## Decisions

### D1: Single NR record with anchor fields (not dual-record)

**Decision**: Store LTE anchor data as nullable anchor columns on the `5G_NSA` record. One record per GPS point per SIM.

**Alternatives considered**:
- **Dual-record**: Emit separate LTE and NR records per NSA point. Rejected because the replay screen's flat list iteration causes jitter, chart discontinuity, and misleading RAT timeline alternation when two records share the same timestamp.
- **Anchor fields on LTE record**: Store NR data as "NR" fields on the `4G` record. Rejected because the primary RAT should be `5G_NSA` (the user's network experience is 5G), and the LTE anchor is a supporting role.

**Rationale**: Single record preserves clean replay behavior, avoids schema duplication, and correctly represents the user's 5G NSA experience. Anchor fields are only populated for `5G_NSA` records — null for all other RATs.

### D2: Anchor column naming convention

**Decision**: Prefix anchor columns with `anchor` (e.g., `anchorPci`, `anchorRsrp`, `anchorBandNumber`).

**Rationale**: Clear semantic separation from primary cell fields. Follows the existing column naming pattern. Makes queries and UI code self-documenting.

### D3: NSA cell selection in CellInfoCollector

**Decision**: When `networkType is NetworkType.Nr.Nsa`:
1. Find NR cell: `subCells.firstOrNull { it is CellNr }` (any CellNr, regardless of connection status — handles Pixel 7 edge case where NetMonster demotes null-NCI NR cells to SecondaryConnection)
2. Find LTE anchor: `subCells.firstOrNull { it is CellLte && it.connectionStatus is PrimaryConnection }`
3. Build `5G_NSA` record from NR cell with anchor fields from LTE cell
4. Extract CA bands from LTE anchor's secondary cells

**Fallback**: If no NR cell is found, fall back to LTE anchor as the primary record (current behavior). If no LTE anchor is found, NR record has null anchor fields.

**Rationale**: NetMonster's NSA detection (`NetworkType.Nr.Nsa`) is reliable. The NR cell may have `SecondaryConnection` or (rarely, due to Pixel 7 workaround) `PrimaryConnection` — searching by type handles both. The LTE anchor reliably has `PrimaryConnection`.

### D4: CA bands attached to NSA record

**Decision**: CA bands from the LTE anchor's `SecondaryConnection` LTE cells attach to the `5G_NSA` record via the existing `cell_record_ca_bands` FK.

**Rationale**: Reuses existing infrastructure — no schema changes to `CellRecordCaBandEntity`. The CA bands semantically belong to the LTE anchor, but since the anchor is part of the NSA connection, attaching them to the NSA record is correct and keeps the data together.

### D5: Room schema migration

**Decision**: Add ~13 nullable columns via `ALTER TABLE ADD COLUMN` in a migration. Bump schema version.

**Rationale**: Adding nullable columns is a simple migration with no data loss. All existing records have null anchor fields — backward compatible.

### D6: Analytics insight fix scope

**Decision**: Change `it.rat == "5G_SA"` to `it.rat.startsWith("5G")` in `generatePciInsights()`.

**Rationale**: Minimal fix. Both `5G_NSA` and `5G_SA` can exhibit Massive MIMO behavior (frequent intra-site PCI changes). The `startsWith("5G")` pattern is already used elsewhere in the codebase (e.g., `BandResolver.kt`, `MapDisplayMode.kt`).

## Risks / Trade-offs

- **[13 new nullable columns]** → Acceptable trade-off for clean single-record model. Columns are only populated for `5G_NSA` records. Alternative (dual-record) has worse UX impact.
- **[Anchor fields may be null]** → Not all NSA deployments provide complete LTE anchor info from NetMonster. UI must handle null anchor fields gracefully (hide or show "---").
- **[NCI may be null in NSA]** → Some modems don't report NCI in NSA mode. `fullCellIdentity`, `enbOrGnbId`, `lcid` will be null — same as current behavior for NR cells. No regression.
- **[Export format change]** → Adding anchor columns to CSV/GeoJSON is additive. Import must handle files without anchor columns (backward compatible via null defaults).
- **[No test coverage for NSA]** → No instrumented tests exist. Unit tests for `CellInfoCollector` would need mocking of NetMonster's `INetMonster` interface, which is non-trivial. Manual testing on a 5G NSA device is recommended.
