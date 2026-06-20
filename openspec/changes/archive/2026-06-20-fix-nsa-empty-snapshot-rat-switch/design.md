## Context

`CellInfoCollector.snapshots()` routes each subscription's cells through one of two paths based on `netMonster.getNetworkType(subId)`:

- If `NetworkType.Nr.Nsa` → `buildNsaSnapshot()`
- Otherwise → `buildSnapshot()`, which switches on the primary cell's type (`CellLte`, `CellNr`, `CellWcdma`, `CellGsm`).

`getNetworkType()` and `getCells()` are two independent modem queries with no transactional guarantee. During a 5G SA → 5G NSA / 4G LTE handover, the modem flips `networkType` to `Nr.Nsa` immediately while the NR secondary cell has not yet been added to `getAllCellInfo` (or, on some OEMs/modem builds, is never emitted during NSA at all). The result: `buildNsaSnapshot()` sees `nrCell == null` and falls into its empty-fallback branch (`CellInfoCollector.kt:49-58`), which returns a snapshot with only `subscriptionId`, `rat`, and `caBands` populated. Every other field defaults to null and is persisted verbatim by `PointRecorder.recordPoint()`.

The active change `improve-5g-nsa-4g-ca-ui` explicitly excludes data-collection logic (design.md Non-Goal: "Changing data collection logic in CellInfoCollector"), so this fix belongs in a separate change.

The `cell-info` spec already mandates the correct behavior in the "NSA mode with no NR cell found" scenario — "the LTE anchor cell is recorded as the primary cell (same as non-NSA LTE behavior)" — but the implementation does not honor it. This change closes that gap and tightens the spec to make the expected RAT label and field coverage explicit.

## Goals / Non-Goals

**Goals:**
- Eliminate mostly-empty `cell_records` rows during 5G SA → 5G NSA / 4G LTE RAT switches.
- When the modem reports `Nr.Nsa` but no `CellNr` is present, produce a fully-populated LTE snapshot (identity, band, ARFCN, PCI, TAC, bandwidth, signal metrics, MCC/MNC, CA bands) identical to a non-NSA LTE tick.
- Preserve `networkTypeCode = networkType.technology` on the no-NR fallbacks so analysts can distinguish "NSA tick where NR wasn't reported" from a genuine LTE tick.
- Keep LTE field-population logic in a single shared code path (DRY) so the non-NSA LTE branch and the NSA-no-NR fallback cannot drift apart.

**Non-Goals:**
- In-tick retry or `getPhysicalChannelConfiguration()` cross-check. The next recording tick is expected to pick up the NR cell once the modem emits it.
- Fixing the stale-primary issue where a lingering `CellNr(Primary)` from a previous SA state coexists with a fresh `CellLte(Primary)` during SA→LTE — that can produce a populated-but-wrong-RAT record. Deferred to a follow-up change.
- Touching `CellRecordSnapshot`, `CellRecordEntity`, the Room schema, exports, or UI. All required fields already exist.
- Backfilling historical empty rows. The fix is forward-only for new recordings.

## Decisions

### D1: Explicit NR-cell presence check before treating a tick as 5G_NSA

`networkType == NetworkType.Nr.Nsa` alone is not sufficient to label a tick `5G_NSA`. The system SHALL additionally require a `CellNr` cell to be present in `getCells()` for that subscription. If no `CellNr` is present, the tick is treated as LTE (or UNKNOWN if no LTE primary is present either), regardless of the modem's `networkType` flag.

**Rationale:** The modem's `networkType` reflects the *preferred/configured* RAT, not the *currently-connected* RAN. During handover the configured type flips before the NR SCell is actually added to the cell list. Treating `networkType` as authoritative is what produces the empty rows.

**Alternative considered:** Cross-checking against `getPhysicalChannelConfiguration(subId)` to see whether any physical channel is currently mapped to NR — rejected as over-engineering; the simpler presence check in `getCells()` is sufficient and avoids an extra modem IPC per tick.

### D2: Route the NSA-no-NR fallback through the standard LTE path

When `networkType` is `Nr.Nsa` but no `CellNr` is present, the LTE anchor cell (the `CellLte` with `PrimaryConnection`) is recorded as the primary cell using the **same** field-population logic as the non-NSA LTE branch. Concretely, the existing `CellLte` branch of `buildSnapshot` (`CellInfoCollector.kt:109-134`) is extracted into a shared private helper `buildLteSnapshot(subId, lteCell, subCells, rat, networkTypeCode)` and called from both paths.

**Rationale:** DRY — guarantees the fallback produces identical field coverage to a real LTE tick, eliminating the risk of the two paths drifting. Also matches the spec text "same as non-NSA LTE behavior".

**Alternative considered:** Inlining a parallel LTE field-population block inside `buildNsaSnapshot()` — rejected as duplication; any future LTE field addition would need to be made in two places.

### D3: RAT label on the fallback is CA-based, not networkType-based

For the NSA-no-NR fallback, the RAT is labeled `"4G_CA"` if `caBands.isNotEmpty()`, else `"4G"`. This intentionally does **not** use the `networkType.technology == NetworkType.LTE_CA` check that the non-NSA LTE path uses, because when `networkType` is `Nr.Nsa` that check is always false (the technology code is an NR code, not an LTE one) and would mislabel CA-engaged ticks as `"4G"`.

**Rationale:** CA engagement is observable directly from the cells list (presence of `SecondaryConnection` LTE cells). Using that observable signal is more reliable than inferring it from a network-type code that describes a different RAT than the one actually connected.

### D4: Preserve `networkTypeCode` on both no-NR fallbacks

Both the no-NR-with-LTE fallback and the no-NR-no-LTE UNKNOWN fallback SHALL set `networkTypeCode = netMonster.getNetworkType(subId).technology`. Today the LTE fallback drops it (defaults to null) and the UNKNOWN fallback also drops it.

**Rationale:** The `networkTypeCode` is the only diagnostic signal that distinguishes "this was an NSA tick where NR wasn't reported yet" from a genuine LTE tick. Without it, analysts cannot tell whether a `4G`-labeled row during a handover was real LTE or an NSA-mode transient. The cost is zero (one field assignment); the analytical value is significant.

**Alternative considered:** Adding a separate `wasNsaTransient` boolean column — rejected as a schema change for marginal value; `networkTypeCode` already encodes the same information.

## Risks / Trade-offs

- **[A real LTE tick during a brief NSA blip could be mislabeled `4G` instead of `5G_NSA`]** → Acceptable. The device was, in fact, not connected to NR at that instant, so `4G` is the truthful label. The preserved `networkTypeCode` lets analysts see the modem *thought* it was in NSA. The next tick with a `CellNr` present will restore the `5G_NSA` label.
- **[A device that never emits `CellNr` during NSA would now record every tick as `4G`/`4G_CA` instead of `5G_NSA`]** → This is the correct behavior for that device: if the modem never reports an NR cell, the device is not connected to NR RAN from the app's observable perspective. The previous behavior (empty `5G_NSA` rows) was strictly worse — it claimed 5G NSA while carrying no NR data.
- **[Analytics that bucket by `rat` will see a `4G`/`4G_CA` row where the user expected `5G_NSA` during a handover]** → True, but the row is now populated and usable, where previously it was empty and unusable. Analytics can additionally bucket by `networkTypeCode` if it needs to detect the NSA transient.
- **[DRY refactor touches both the non-NSA LTE path and the NSA fallback]** → Slightly larger blast radius than a minimal patch. Mitigated by extracting the helper verbatim from the existing branch (no behavior change for the non-NSA path) and running `code-review` + clean build.
- **[No unit test for `CellInfoCollector` exists today]**
  → Manual verification only. A unit test would require building a fake `INetMonster`; deferred unless requested. The behavior change is small enough that build + lint + code-review is the agreed verification bar.

## Migration Plan

- Forward-only; no data migration. Existing empty rows in `cell_records` remain as-is.
- No database schema change, no version bump required.
- Rollback is a single-file revert of `CellInfoCollector.kt`; no downstream consumers depend on the new behavior because they already tolerate null fields.
