## Why

The app computes rich analytics insights and collects structured signal data, but several UI surfaces either ignore the data entirely or display it inconsistently. The AnalyticsPanel renders a placeholder InsightCard while real insights are computed and discarded. ReplayScreen shows anchor and CA data as flat one-liners while the live RecordingScreen is being upgraded to an expandable, structured card. Signal quality colors (RSRP/RSRQ/SINR) are applied selectively despite helper functions existing in the codebase. And band labels in StatisticsScreen still show raw numbers like "Band 3" while AnalyticsPanel is being fixed to show "B3" / "n78". These gaps make the app feel unfinished and inconsistent across screens.

## What Changes

- Replace the hardcoded placeholder `InsightCard` composable with a data-driven version that renders real `insightCards` from `SessionAnalyticsEngine`, and wire it into `AnalyticsPanel`
- Extend `ReplayScreen.StatsPanel` to match the new expandable `RecordingScreen.SimCard`: collapsed state shows a compact anchor row for 5G NSA and a CA band count badge; expanded state reveals full anchor fields and structured CA band rows
- Add `CaBandDetail` and structured anchor fields to `ReplayViewModel` state population (reusing the same pattern as `RecordingViewModel`/`LiveInfoViewModel`)
- Apply signal quality color coding (rsrpColor, rsrqColor, sinrColor) to primary cell RSRP/RSRQ/SINR values on `RecordingScreen.SimCard`, `LiveInfoScreen.LiveSimCard`, `ReplayScreen.StatsPanel`, and `RecordDetailSheet` (within the scope of `improve-5g-nsa-4g-ca-ui`)
- Fix `StatisticsScreen` band distribution labels to use `BandResolver.formatBand()` with RAT context, keeping parity with the `AnalyticsPanel` fix in `improve-5g-nsa-4g-ca-ui`
- Extract shared signal-quality color and formatting helpers to avoid duplication across `RecordingScreen`, `LiveInfoScreen`, `ReplayScreen`, `SessionDetailScreen`, and `SimLiveStateMapper`

## Capabilities

### New Capabilities

- `insight-card`: Data-driven InsightCard composable that renders a list of insight titles and bodies in the analytics panel

### Modified Capabilities

- `ui`: RecordingScreen SimCard signal values gain quality color coding; LiveInfoScreen LiveSimCard gains quality color coding; ReplayScreen StatsPanel becomes expandable and gains structured CA/anchor display plus quality color coding; SessionDetailScreen record rows gain quality color coding on signal values (through the existing detail sheet work)
- `analytics`: AnalyticsPanel InsightCard renders real computed insights instead of a placeholder; StatisticsScreen band distribution labels use qualified band names with RAT context

## Impact

- UI layer: `RecordingScreen`, `LiveInfoScreen`, `ReplayScreen`, `StatisticsScreen`, `InsightCard`, `AnalyticsPanel`
- State models: `ReplayViewModel` (new structured CA/anchor mapping), `SimLiveState` already has the fields from the previous change
- No data model or database changes (all data already collected and stored)
- Affects shared composables: signal quality colors will be extracted to a common location
