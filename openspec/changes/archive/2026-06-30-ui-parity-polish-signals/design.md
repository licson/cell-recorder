## Context

The `improve-5g-nsa-4g-ca-ui` change adds structured NSA anchor and CA band data to the RecordingScreen SimCard, LiveInfoScreen, and SessionDetailScreen bottom sheet. After that change lands, the app will still have three categories of inconsistency:

1. **InsightCard is a hardcoded placeholder** — `SessionAnalyticsEngine.generatePciInsights()` produces real insight cards (Massive MIMO Candidate, Load Balancing Detected, Cross-Site Handoff Impact), but `AnalyticsPanel.kt` passes no data to the `InsightCard()` composable, which renders a robot emoji and "AI-generated insights will appear here in a future update." The computed insights are never seen by users.

2. **ReplayScreen StatsPanel is a stripped-down version of the live SimCard** — It shows anchor data as a flat one-liner (`B3 PCI 123 RSRP -85`) and ignores CA bands entirely. Users replaying a session to inspect history get less detail than during live recording.

3. **Signal quality colors are not applied consistently** — `rsrpColor()`, `rsrqColor()`, and `sinrColor()` exist in the codebase but are used in only a few places. Primary cell RSRP/RSRQ/SINR values on RecordingScreen, LiveInfoScreen, ReplayScreen, and the detail sheet are all plain text, making it hard to visually scan signal quality.

4. **StatisticsScreen band labels use raw numbers** — `StatisticsScreen` shows "Band 3" while `AnalyticsPanel` is being fixed to show "B3" / "n78". This creates a global inconsistency in how bands are labeled.

## Goals / Non-Goals

**Goals:**
- Render real `SessionAnalytics.insightCards` in the AnalyticsPanel instead of the placeholder
- Bring ReplayScreen StatsPanel to parity with the new RecordingScreen SimCard (expandable, structured anchor, structured CA bands, signal quality colors)
- Apply signal quality color coding to primary cell RSRP/RSRQ/SINR on all screens: RecordingScreen, LiveInfoScreen, ReplayScreen, and RecordDetailSheet
- Fix StatisticsScreen band distribution labels to use `BandResolver.formatBand()` with RAT context, matching AnalyticsPanel
- Extract shared signal-quality color and formatting helpers to eliminate duplication across screens

**Non-Goals:**
- Adding new data fields or database columns (all data already exists)
- Changing data collection logic
- Modifying export/import formats
- Adding hidden fields (RSSI, CQI, TA, BW) to the UI
- Changing the RAT color scheme
- Implementing map point tooltips

## Decisions

### D1: InsightCard accepts a list of insights — no empty state redesign

The `InsightCard` composable will be changed to accept `insights: List<InsightCardData>` (a new data class or the existing `InsightCard` model from `SessionAnalytics`). When the list is empty, it will show a compact "No insights for this session" message instead of the robot placeholder. `AnalyticsPanel` will pass `analytics.insightCards` directly.

**Alternative considered:** A dedicated insights screen — rejected because insights are session-scoped and naturally belong in the analytics panel. A separate screen would fragment the analytics experience.

### D2: ReplayViewModel populates structured CA/anchor fields using the same mapper as RecordingViewModel

The `ReplayViewModel` currently maps `CellRecordEntity` to `CellRecordEntity` directly (no transformation). To support the expandable StatsPanel, it will need to populate the same `CaBandDetail` and anchor fields that `RecordingViewModel` and `LiveInfoViewModel` use. The cleanest approach is to reuse the existing `SimLiveStateMapper` logic (or a new `ReplaySimStateMapper`) to build a `SimLiveState`-like object from the current replay record.

**Alternative considered:** Duplicating the UI logic directly in `ReplayScreen` — rejected because it would duplicate the string formatting and color coding that already exists in the mapper.

### D3: Signal quality colors as shared composable helpers

`rsrpColor()`, `rsrqColor()`, and `sinrColor()` will be extracted from `MapDisplayMode.kt`, `SessionDetailScreen.kt`, and `SimLiveStateMapper.kt` into a new `SignalQualityColors.kt` file in the `ui/shared` package. Each function will accept a raw numeric value and return a `Color`. The composables (`RecordingScreen`, `LiveInfoScreen`, `ReplayScreen`, `RecordDetailSheet`) will call these helpers directly on the raw values.

**Alternative considered:** Pre-coloring strings in the mapper — rejected because the mapper should produce raw values, and the UI should own presentation. This also allows color to be applied selectively (e.g., on the detail sheet but not in CSV export).

### D4: StatisticsScreen uses the same band model change as AnalyticsPanel

The `improve-5g-nsa-4g-ca-ui` change adds `rat: String` to `BandCountPerSim` (or a wrapper) in `SessionAnalyticsEngine`. `StatisticsScreen` uses the same `BandDistribution` model from the domain layer. The fix is to pass RAT context through the `StatisticsViewModel` and use `BandResolver.formatBand(bandNumber, earfcn=null, rat=rat)` in the labels. This is a rendering-only change in `StatisticsScreen`.

**Alternative considered:** Leaving StatisticsScreen as-is — rejected because users will see "Band 3" in one place and "B3" in another, creating confusion.

## Risks / Trade-offs

- [InsightCard list may be long for sessions with many handoffs] → Insights are rare (generated only when thresholds are exceeded: 3+ intra-site PCI changes, 3+ cross-site latency increases). Most sessions will show 0–2 cards. The composable will scroll if needed.
- [ReplayViewModel state size increases with CA band details] → Typically 1–4 CA bands per record; negligible impact.
- [Shared color helpers may change existing behavior if thresholds differ] → The existing `rsrpColor()` in `MapDisplayMode.kt` uses thresholds: excellent > -80, good -80 to -90, fair -90 to -100, poor < -100. The new shared helper will use the exact same thresholds to avoid any visual change in existing consumers.
- [StatisticsScreen band model change depends on the analytics engine model change] → This change is sequential: the `improve-5g-nsa-4g-ca-ui` change must add RAT to the band model first. This proposal assumes that model change is already in place.

## Open Questions

- Should the InsightCard render an icon per insight type (e.g., a signal icon for "Massive MIMO Candidate", a balance icon for "Load Balancing")? For now, a uniform card style with title and body is sufficient.
- Should the ReplayScreen StatsPanel expand/collapse animation match the RecordingScreen SimCard exactly? Yes, for visual consistency.
