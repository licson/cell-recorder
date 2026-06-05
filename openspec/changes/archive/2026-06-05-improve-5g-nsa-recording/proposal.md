## Why

5G NSA (Non-Standalone) recordings are fundamentally broken: `CellInfoCollector` always picks the LTE anchor cell (which has `PrimaryConnection`), so the `CellNr` branch that sets `rat = "5G_NSA"` is never reached. No `5G_NSA` record has ever been created. The NR cell's band, PCI, and signal metrics are completely lost. Additionally, the analytics engine's "Massive MIMO Candidate" insight only checks for `5G_SA`, excluding NSA intra-site PCI changes.

## What Changes

- Detect 5G NSA mode via `NetworkType.Nr.Nsa` and build the record from the NR cell (typically `SecondaryConnection`), not the LTE anchor
- Capture the LTE anchor cell's identity, band, and signal metrics as anchor fields on the same `5G_NSA` record (single record per GPS point per SIM — avoids replay jitter)
- Extract CA bands from the LTE anchor's secondary cells and attach to the `5G_NSA` record
- Populate `bandwidthKhz` for NR cells from `CellNr.band.bandwidth` (nullable — modem may not report it)
- Fix `SessionAnalyticsEngine.generatePciInsights()` to include `5G_NSA` alongside `5G_SA`
- Add ~13 nullable anchor columns to `CellRecordEntity` with Room schema migration
- Update CSV/GeoJSON export and import to handle anchor fields
- Update UI (replay stats panel, session detail, live info) to display anchor data for NSA records

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

- `cell-info`: Add NSA dual-cell detection — when `NetworkType.Nr.Nsa`, find NR cell and LTE anchor separately, record NR cell as primary with anchor fields populated from LTE cell
- `analytics`: Clarify that 5G insight cards (Massive MIMO Candidate, etc.) apply to both `5G_NSA` and `5G_SA`
- `sessions`: NSA records carry anchor cell data; replay and session detail screens must display anchor info
- `data`: Add anchor fields to CSV and GeoJSON export/import formats

## Impact

- **Database**: Schema version bump + migration adding ~13 nullable columns to `cell_records` table
- **CellInfoCollector.kt**: Core logic change — NSA detection and dual-cell extraction
- **CellRecordSnapshot.kt / CellRecordEntity.kt**: New anchor fields
- **SessionAnalyticsEngine.kt**: One-line fix for RAT check
- **UI**: Replay stats panel, session detail rows, live info screen need anchor data display
- **Export/Import**: CSV and GeoJSON format changes (additive — new optional columns)
- **Backward compatibility**: Existing records without anchor fields continue to work (nullable columns)
