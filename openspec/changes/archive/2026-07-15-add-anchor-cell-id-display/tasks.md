## 1. Live-path data threading

- [x] 1.1 Add `anchorCellId: String = "---"` field to `SimLiveState` (`app/src/main/java/com/cellrecorder/app/service/RecordingState.kt`), positioned alongside the existing anchor string fields.
- [x] 1.2 In `SimLiveStateMapper.map()` (`app/src/main/java/com/cellrecorder/app/ui/recording/SimLiveStateMapper.kt`), populate `anchorCellId` from `snapshot.anchorEnbOrGnbId` and `snapshot.anchorLcid`: format as `"${enbOrGnbId}:${lcid}"` when both are non-null, otherwise `"---"`. Mirror the null-handling of the existing `formatCellId` for the primary cell.

## 2. CellInfoPanel — AnchorCellInfo + expanded anchor row

- [x] 2.1 Add `cellId: String` to the `AnchorCellInfo` data class in `app/src/main/java/com/cellrecorder/app/ui/shared/CellInfoPanel.kt`.
- [x] 2.2 In `SimLiveState.toCellInfoData()`, set `AnchorCellInfo.cellId` from `anchorCellId`.
- [x] 2.3 In `CellRecordWithCaBands.toCellInfoData()`, format `AnchorCellInfo.cellId` inline from `record.anchorEnbOrGnbId` / `record.anchorLcid` (same `"$id:$lcid"` rule, `"---"` if either is null), reusing the detail-package `formatCellId` helper or an equivalent inline format.
- [x] 2.4 In `CellInfoPanel`'s expanded anchor block, add a `Cell ID` `StatItem` as the FIRST anchor row (before Band), using `weight = 1.2f` and `FontFamily.Monospace` to match the primary Cell ID column. Leave the collapsed compact `Anchor` StatItem unchanged.

## 3. RecordDetailSheet — Anchor Cell ID row

- [x] 3.1 In `app/src/main/java/com/cellrecorder/app/ui/detail/RecordDetailSheet.kt`, add a `DetailRow("Cell ID", anchorCellId)` as the first row of the Anchor Cell block (before Band), where `anchorCellId` is `"${anchorEnbOrGnbId}:${anchorLcid}"` when both are non-null, otherwise `"---"`. Reuse or extend the existing `formatCellId` helper for the anchor fields.

## 4. Tests

- [x] 4.1 Add/update unit tests for `SimLiveStateMapper` asserting `anchorCellId` is `"${enb}:${lcid}"` when both anchor identity components are present, and `"---"` when either is null (locate the existing mapper test file; if none exists, add one under `app/src/test/java/com/cellrecorder/app/ui/recording/`).
- [x] 4.2 Add/update unit tests for the `toCellInfoData()` mappers (`SimLiveState.toCellInfoData()` and `CellRecordWithCaBands.toCellInfoData()`) asserting `AnchorCellInfo.cellId` formatting and the `---` fallback.
- [x] 4.3 Update instrumented UI tests for the Recording screen, Live Info screen, Replay screen, and Session Detail screen (under `app/src/androidTest/java/com/cellrecorder/app/ui/`) to assert the anchor Cell ID row/text appears in the expanded anchor block for 5G NSA records and renders `---` when anchor identity is missing.

## 5. Verification

- [x] 5.1 Run `./gradlew clean` then `./gradlew assembleDebug` to confirm a clean build.
- [x] 5.2 Run `./gradlew lint` (or the project's lint command) and resolve any new warnings introduced by the change.
- [x] 5.3 Run the unit and instrumented test suites affected by the change and confirm they pass.
- [x] 5.4 After implementation, run the `code-review` subagent against the modified files (per `AGENTS.md` "Code Working Flow — Review") and address any major comments.
