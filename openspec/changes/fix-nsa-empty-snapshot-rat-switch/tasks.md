## 1. Refactor `CellInfoCollector` — extract shared LTE helper

- [x] 1.1 Extract the existing `CellLte` branch of `buildSnapshot` (`CellInfoCollector.kt:109-134`) into a new private helper `buildLteSnapshot(subId: Int, lteCell: CellLte, subCells: List<ICell>, rat: String, networkTypeCode: Int): CellRecordSnapshot` preserving the exact field-population logic (identity split, band lookup, signal metrics, MCC/MNC, CA bands)
- [x] 1.2 Replace the `is CellLte ->` branch body in `buildSnapshot` with a call to `buildLteSnapshot(subId, serving as CellLte, subCells, rat = if (networkType is NetworkType.Lte && networkType.technology == NetworkType.LTE_CA) "4G_CA" else "4G", networkTypeCode = networkType.technology)`
- [x] 1.3 Confirm the non-NSA LTE path is byte-identical in behavior to the pre-refactor code (same fields, same `rat` labeling, same `networkTypeCode`)

## 2. Route NSA-no-NR fallback through the LTE helper

- [x] 2.1 In `buildNsaSnapshot`, when `nrCell == null` and `lteAnchor != null`, call `buildLteSnapshot(subId, lteAnchor, subCells, rat = if (caBands.isNotEmpty()) "4G_CA" else "4G", networkTypeCode = netMonster.getNetworkType(subId).technology)` where `caBands` is computed via `extractCaBands(lteAnchor, subCells)`
- [x] 2.2 In `buildNsaSnapshot`, when `nrCell == null` and `lteAnchor == null`, return `CellRecordSnapshot(subscriptionId = subId, rat = "UNKNOWN", networkTypeCode = netMonster.getNetworkType(subId).technology)`
- [x] 2.3 Remove the now-unused `lteRat` local variable and the inline `return CellRecordSnapshot(subscriptionId = subId, rat = lteRat, caBands = caBands)` block

## 3. Spec compliance verification

- [x] 3.1 Verify `buildNsaSnapshot` matches all four scenarios in the modified spec: NSA+NR+LTE (5G_NSA full), NSA+no-NR+LTE (full LTE fallback), NSA+no-NR+no-LTE (UNKNOWN with ntc), NSA+NR+no-LTE (5G_NSA with null anchors)
- [x] 3.2 Confirm `networkTypeCode` is populated on both no-NR fallback paths (LTE and UNKNOWN)
- [x] 3.3 Confirm the RAT label on the no-NR fallback is CA-based (`caBands.isNotEmpty()`), not `networkType.technology == LTE_CA`

## 4. Build and lint

- [x] 4.1 Run `./gradlew clean` to remove stale artifacts
- [x] 4.2 Run `./gradlew assembleDebug` and confirm a successful build
- [x] 4.3 Run lint checks and fix any new warnings introduced by the refactor

## 5. Code review

- [x] 5.1 Run the `code-review` subagent against the diff with this proposal, design, and spec delta as context; address any major comments and re-review until clean
