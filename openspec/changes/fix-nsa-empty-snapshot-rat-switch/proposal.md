## Why

During 5G SA → 5G NSA or 4G LTE radio access technology (RAT) switches, the modem may briefly report `NetworkType.Nr.Nsa` while `getCells()` contains no `CellNr` (the NR secondary cell hasn't been added to the cell list yet, or some OEMs never emit it during NSA). The current `CellInfoCollector.buildNsaSnapshot()` returns a snapshot with only `subscriptionId`, `rat`, and `caBands` populated in that case — every other field (cell identity, band, ARFCN, PCI, TAC, all signal metrics, MCC/MNC) is null. The result is a run of mostly-empty `cell_records` rows that no UI can surface and no analytics can meaningfully consume. This violates the `cell-info` spec scenario "NSA mode with no NR cell found", which requires the LTE anchor to be recorded as the primary cell with full LTE data. The active `improve-5g-nsa-4g-ca-ui` change deliberately excludes data-collection logic, so this fix belongs in a separate change.

## What Changes

- Require an explicit check for an actual NR NSA connection (presence of a `CellNr` cell) before treating a tick as `5G_NSA`. The modem's `NetworkType.Nr.Nsa` flag alone is no longer sufficient.
- When `networkType` is `Nr.Nsa` but no `CellNr` cell is present, route the tick through the standard LTE snapshot path: populate full LTE identity (eNB ID + LCID split), band, ARFCN, PCI, TAC, bandwidth, all signal metrics, MCC/MNC, and CA bands — identical to a non-NSA LTE tick.
- Label the RAT for such ticks as `4G` or `4G_CA` based on whether LTE carrier aggregation bands are engaged (not based on `networkType.technology == LTE_CA`, which is always false when `networkType` is `Nr.Nsa`).
- Preserve `networkTypeCode = networkType.technology` on both the no-NR LTE fallback and the no-NR-no-LTE UNKNOWN fallback so analysts can distinguish "this was an NSA tick where NR wasn't reported" from a genuine LTE tick.
- No in-tick retry and no `getPhysicalChannelConfiguration` cross-check; rely on the next recording tick to pick up the NR cell once the modem emits it.
- Extract the existing `CellLte` snapshot logic from `buildSnapshot` into a shared private helper (`buildLteSnapshot`) so the non-NSA LTE path and the NSA-no-NR fallback share a single field-population code path (DRY), guaranteeing identical field coverage.

## Capabilities

### New Capabilities
<!-- No new capabilities introduced. -->

### Modified Capabilities
- `cell-info`: Strengthens the "NSA mode with no NR cell found" requirement to state explicitly that the LTE anchor cell is recorded as the primary cell with full LTE data (identity, band, signal, MCC/MNC), the RAT is labeled `4G` or `4G_CA` based on LTE CA engagement rather than the modem's network-type flag, and `networkTypeCode` preserves the NSA technology code for diagnostic visibility.

## Impact

- Code: `app/src/main/java/com/cellrecorder/app/service/CellInfoCollector.kt` — refactor of `buildNsaSnapshot()` no-NR branches and extraction of a shared `buildLteSnapshot` helper used by both the non-NSA LTE path and the NSA-no-NR fallback.
- Specs: `openspec/specs/cell-info/spec.md` — the "5G NSA Cell Detection" requirement's "NSA mode with no NR cell found" scenario gains explicit assertions about RAT labeling and field population.
- No data model changes: `CellRecordSnapshot`, `CellRecordEntity`, and the Room schema already carry every required field.
- No database migration; existing rows remain unchanged (this is forward-only for new recordings).
- No UI changes; downstream consumers (`PointRecorder`, `RecordingViewModel`, `LiveInfoViewModel`, `SessionDetailScreen`, analytics) receive fully-populated snapshots and work unchanged.
- Out of scope: the stale-primary issue where a `CellNr(Primary)` lingers in `getCells()` during SA→LTE while a fresh `CellLte(Primary)` is also present (can produce a populated-but-wrong-RAT record). Deferred to a follow-up change.
