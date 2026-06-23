# User Interface Specification

## Purpose

Defines the user interface screens, navigation structure, and interactive behavior of the application.

## Scope

This spec covers screen layouts, controls, navigation, and visual feedback. It does not define:
- Recording lifecycle or triggers (see `recording/spec.md`).
- Foreground service mechanics (see `service/spec.md`).
- Cell identity processing (see `cell-info/spec.md`).
- Ping measurement (see `connectivity/spec.md`).
- Speedtest protocol (see `speedtest/spec.md`).
- Indoor positioning math (see `indoor/spec.md`).
- Permission UI logic (see `permission-flow/spec.md`).
- Post-session analytics (see `analytics/spec.md`).
- Data formats or persistence (see `data/spec.md`).
- Database concurrency (see `thread-safety/spec.md`, `db-write-safety/spec.md`).

## Related Specs

- `recording/spec.md` — what the recording screen monitors and controls.
- `service/spec.md` — how the notification interacts with the service.
- `sessions/spec.md` — session list, detail, replay, and export/import UI triggers.
- `analytics/spec.md` — analytics panels displayed in session detail and statistics.
- `data/spec.md` — export/import dialog behavior and format choices.
- `cell-info/spec.md` — how live cell data is rendered on screen.
- `connectivity/spec.md` — how ping status is displayed.
- `speedtest/spec.md` — how speedtest status and results are displayed.
- `indoor/spec.md` — indoor canvas, tracking confidence, drift radius, and origin reset UI.
- `permission-flow/spec.md` — how permission rationale and settings dialogs are rendered.
- `test-foundation/spec.md` — UI smoke test coverage requirements.
- `instrumented-test-coverage/spec.md` — Compose screen test coverage requirements.

## Requirements

### Requirement: Bottom Navigation

The system SHALL provide a bottom navigation bar with three primary destinations.

#### Scenario: Navigation tabs
- GIVEN the application is launched
- THEN a bottom navigation bar is displayed with Live Info, Sessions, and Statistics tabs
- AND Sessions is the default selected tab

#### Scenario: Bottom bar visibility
- GIVEN any of the three top-level screens
- THEN the bottom navigation bar is shown
- WHEN navigating to a detail screen
- THEN the bottom navigation bar is hidden

### Requirement: Live Info Screen

The system SHALL display real-time cell information for all active SIMs.

#### Scenario: Live info displayed
- GIVEN the Live Info tab is selected
- THEN a card is shown for each active SIM
- AND each card displays PLMN, RAT, Band, ARFCN, Cell ID, PCI, TAC, RSRP, RSRQ, and SINR
- AND sparkline charts show RSRP and SINR history per SIM

#### Scenario: No cell data
- GIVEN the Live Info tab is selected
- WHEN no SIM data is detected
- THEN a "No cell data available" message is displayed

### Requirement: Recording Screen

The system SHALL provide a screen for controlling and monitoring an active recording. The screen layout SHALL differ based on recording mode: outdoor mode shows an OSM map with GPS status; indoor mode shows a 2D canvas with tracking confidence and drift indicators. Recording lifecycle is defined in `recording/spec.md`; indoor positioning is defined in `indoor/spec.md`.

#### Scenario: Recording screen layout
- GIVEN a session has been created
- WHEN the user navigates to recording
- THEN the screen displays a top bar with session name, elapsed timer, and point counter
- AND if outdoor: an OSM map is shown with GPS status indicator and accuracy
- AND if indoor: a 2D path canvas is shown with tracking confidence indicator and drift radius
- AND a Start/Stop button is centered at the bottom
- AND a live stats panel shows per-SIM cell data and ping latency

#### Scenario: Map markers and path (outdoor)
- GIVEN an active outdoor recording
- THEN recorded points are shown as RAT-colored markers on the map
- AND a path polyline connects the markers

#### Scenario: Indoor canvas path (indoor)
- GIVEN an active indoor recording
- THEN recorded points are shown as a uniform-colored polyline on the 2D canvas
- AND discontinuity markers are placed at origin reset points (path segments on either side are not connected)

#### Scenario: GPS status indicator
- GIVEN an active recording
- THEN a GPS status indicator is shown with one of: "OK", "Searching...", or "EXTRAPOLATING"
- AND the current GPS accuracy is displayed
- AND for indoor mode the GPS status is NOT displayed

#### Scenario: Point tooltip
- GIVEN the recording screen map
- WHEN the user taps a point marker
- THEN a tooltip with all point attributes is displayed

#### Scenario: Speedtest status in live stats
- GIVEN an active recording with speedtest enabled
- THEN the live stats bar displays speedtest status in the format: idle (`Speed: ---`), discovering (`Speed: Selecting server...`), downloading (`Speed: Testing ↓...`), uploading (`Speed: Testing ↑...`), completed (`Speed: ↓156 ↑42 Mbps`), failed (`Speed: Failed`), or skipped on WiFi (`Speed: (WiFi)`)

### Requirement: Settings Screen

The system SHALL provide a settings screen for configuring recording and analytics parameters, including indoor-specific settings. Configuration defaults and ranges are defined in `indoor/spec.md` and `recording/spec.md`.

#### Scenario: Settings sections
- GIVEN the Settings screen
- THEN the following sections are displayed: Ping, Recording, Indoor Recording, Cell ID, GPS Loss Fallback, Analytics Thresholds

#### Scenario: Indoor recording settings
- GIVEN the Settings screen
- WHEN the "Indoor Recording" section is displayed
- THEN the section contains a step length text input (default 0.7m, range 0.1m–2.0m, validated in `SettingsViewModel`)
- AND an indoor recording interval picker (default 5000ms)

### Requirement: Settings Screen — Speed Test Section

The system SHALL provide a "Speed Test" settings section for configuring continuous throughput tests.

#### Scenario: Speed test settings displayed
- GIVEN the Settings screen
- THEN a "Speed Test" card is displayed
- AND the card contains: a master enable toggle, a "Download Speed" label indicating the feature provides throughput measurement, an upload test toggle (default ON), an interval picker (default 60s), and an optional server ID input (blank for auto-select)

#### Scenario: EULA dialog on enable
- GIVEN the user toggles "Enable Speed Test" to ON
- WHEN the speedtest binary or feature has not been previously accepted
- THEN a dialog is displayed informing the user of Speedtest.net's Terms of Use and Privacy Policy
- AND a data usage warning: "Each test uses approximately 5-15 MB of cellular data (download only) or 10-30 MB (with upload). With a 60-second interval, expect ~300-1800 MB per hour of recording."
- AND the dialog provides a link to open Speedtest.net Terms in a browser
- AND a link to open Speedtest.net Privacy Policy in a browser
- AND the user can either Accept (toggle stays ON) or Decline (toggle reverts to OFF)

#### Scenario: EULA re-prompt on binary absence
- GIVEN the user has previously accepted the EULA
- WHEN the user toggles speed tests OFF and ON again
- THEN the EULA dialog is not shown again (toggle activates immediately)

### Requirement: Global Statistics Screen

The system SHALL display aggregate statistics across all sessions.

#### Scenario: Statistics displayed
- GIVEN the Statistics tab is selected
- THEN summary cards show total sessions, total points, total duration, and on-network percentage
- AND RAT distribution per SIM is shown as stacked horizontal bars
- AND band distribution per SIM is shown as stacked bars

### Requirement: Replay Screen — Speedtest Summary

The system SHALL display a dynamic speedtest summary card during session replay that updates based on the current playback position. Speedtest marker behavior is defined in `speedtest/spec.md` and `sessions/spec.md`.

#### Scenario: Auto-updating speedtest summary
- GIVEN replay mode is active
- WHEN the current playback position passes a speedtest marker
- THEN the speedtest summary card shows the most recent speedtest result before the current position
- AND the card shows "Position" as the label for auto-updated markers

#### Scenario: Manual marker selection
- GIVEN the replay timeline with speedtest markers
- WHEN a user taps a marker
- THEN the tapped marker's result is shown in the summary card
- AND the card shows "Selected" as the label for manually tapped markers
- AND the card uses the primary color to distinguish selected from auto-updated markers

#### Scenario: No speedtest at position
- GIVEN replay mode is active
- WHEN the current position is before the first speedtest marker
- THEN the speedtest summary card shows "No test at this position"

### Requirement: Statistics Screen — Speedtest Overview

The system SHALL display a conditional speedtest overview card on the global statistics screen. Speedtest data semantics are defined in `speedtest/spec.md` and `analytics/spec.md`.

#### Scenario: Speedtest global stats
- GIVEN the Statistics tab is selected
- WHEN speedtest records exist across all sessions
- THEN a "Speed Test Overview" card shows total tests, average download speed, average upload speed, and success rate
- AND if no speedtest records exist, the card is hidden

### Requirement: Settings Screen — Indoor Recording

The system SHALL provide an indoor recording settings section for configuring step length and recording interval.

#### Scenario: Indoor recording settings displayed
- GIVEN the Settings screen
- THEN an "Indoor Recording" card is displayed
- AND the card contains a step length text input (default 0.7m, range 0.1m–2.0m, validated in `SettingsViewModel`)
- AND an indoor recording interval picker (default 5000ms)

### Requirement: Indoor Path Canvas

The system SHALL display a 2D path canvas for indoor recording and indoor session detail/replay views. Indoor positioning logic is defined in `indoor/spec.md`; indoor session replay is defined in `sessions/spec.md`.

#### Scenario: Indoor canvas layout
- GIVEN an active indoor recording or indoor session detail view
- THEN a 2D canvas is displayed showing the movement path as a polyline
- AND the path polyline is rendered in a single uniform color (per-segment RSRP coloring is not supported because the recorded path stores only `(x, y)` pairs without per-point RSRP)
- AND the current position is marked with a distinct marker
- AND the origin (0,0) is marked with a reference marker
- AND light grid lines are shown for spatial reference

#### Scenario: Canvas pan and zoom
- GIVEN the indoor path canvas
- WHEN the user drags or pinches on the canvas
- THEN the canvas pans and zooms accordingly
- AND the path, markers, and grid scale with the transformation

#### Scenario: Signal color legend
- GIVEN an indoor recording or indoor session detail/replay view
- THEN a signal color legend is displayed below the canvas
- AND the legend explains the RSRP ranges that map to each color (excellent=green >-80 dBm, good=cyan -80~-90 dBm, fair=orange -90~-100 dBm, poor=red <-100 dBm)
- AND the legend is informational only (the path itself uses a uniform color)

### Requirement: Indoor Canvas Display Modes

The system SHALL support the existing `MapDisplayMode` options on the indoor path canvas. Note: the indoor canvas does not store per-point RSRP on the recorded path, so display modes that rely on per-point signal coloring fall back to the uniform path color.

#### Scenario: Display mode selector for indoor sessions
- GIVEN an indoor session detail or replay view
- THEN a `MapDisplayMode` dropdown is displayed with the same 5 options as outdoor sessions
- AND the selected mode applies to the indoor canvas rendering (dot markers are placed for cell-ID/RAT/band change points; per-segment color coding is not supported and falls back to uniform color)

#### Scenario: Discontinuity markers
- GIVEN an indoor recording with one or more origin resets
- WHEN the path is rendered on the canvas
- THEN a visible break/gap marker is shown at each origin reset point
- AND the path segments on either side of the discontinuity are not connected by a line

### Requirement: Tracking Confidence Indicator

The system SHALL display a tracking confidence indicator during indoor recording. Drift estimation is defined in `indoor/spec.md`.

#### Scenario: Confidence state displayed
- GIVEN an active indoor recording
- THEN a tracking confidence indicator is shown with one of three states:
  - Confident (green): estimated drift < 3 meters
  - Degrading (yellow): estimated drift 3–10 meters
  - High drift (red): estimated drift > 10 meters
- AND the indicator shows the time elapsed since the last origin reset

#### Scenario: Confidence updates in real time
- GIVEN an active indoor recording
- WHEN the drift estimate changes
- THEN the confidence indicator updates to reflect the new drift state

### Requirement: Origin Reset Button

The system SHALL provide a "Reset Origin" button on the indoor recording screen. Origin reset logic is defined in `indoor/spec.md`.

#### Scenario: Reset origin button visible
- GIVEN an active indoor recording
- THEN a "Reset Origin" button is displayed on the recording screen

#### Scenario: Reset origin tapped
- GIVEN an active indoor recording
- WHEN the user taps "Reset Origin"
- THEN the indoor position is reset to (0,0)
- AND the tracking confidence indicator resets to Confident (green)
- AND a discontinuity marker is placed on the path

### Requirement: Drift Radius Visualization

The system SHALL display a drift radius circle on the indoor path canvas. Drift estimation is defined in `indoor/spec.md`.

#### Scenario: Drift radius rendered
- GIVEN an active indoor recording
- WHEN the path is displayed on the canvas
- THEN a translucent circle is centered on the current position marker
- AND the circle's radius equals the estimated drift in meters
- AND the circle grows over time as drift increases

### Requirement: Indoor Recording Screen Layout

The system SHALL provide an indoor recording screen layout adapted from the outdoor recording screen. Indoor positioning and service integration are defined in `indoor/spec.md` and `service/spec.md`.

#### Scenario: Indoor recording screen layout
- GIVEN an indoor recording session has been started
- WHEN the user navigates to the recording screen
- THEN the screen displays a top bar with session name, elapsed timer, and point counter
- AND a 2D path canvas is shown instead of an OSM map
- AND a tracking confidence indicator is displayed
- AND a drift radius circle is shown on the canvas
- AND a Start/Stop button is centered at the bottom
- AND a "Reset Origin" button is accessible
- AND a live stats panel shows per-SIM cell data
- AND the current step count and estimated drift are displayed
- AND a signal color legend is displayed below the canvas

#### Scenario: Indoor screen hides GPS status
- GIVEN an active indoor recording
- THEN the GPS status indicator is NOT displayed
- AND the GPS accuracy reading is NOT displayed

### Requirement: Ping Latency Display in Indoor Recording

The system SHALL display current ping latency in the indoor recording screen's live stats bar. Ping measurement is defined in `connectivity/spec.md`.

#### Scenario: Ping latency shown in indoor mode
- GIVEN an active indoor recording
- THEN the live stats bar displays the current ping latency in milliseconds
- AND the format matches the outdoor ping display: "Ping: X.X ms"
- AND the GPS status, latitude, longitude, and altitude fields are NOT displayed

### Requirement: Sensor Health Warning in Indoor UI

The system SHALL display a sensor health warning when step detection is not receiving events. Step detection logic is defined in `indoor/spec.md`.

#### Scenario: Sensor health warning visible
- GIVEN an active indoor recording with no step events for 10+ seconds
- THEN a warning message is displayed below the tracking confidence indicator
- AND the message reads: "No steps detected. Try moving the phone to your pocket."
- AND the warning is styled with a caution color (yellow/orange)
- AND the warning element exposes a "No steps" content description for accessibility

#### Scenario: Sensor health warning hidden
- GIVEN a sensor health warning is visible
- WHEN step events are received
- THEN the warning is dismissed

### Requirement: Live Info Screen — Structured CA Bands and Anchor

The system SHALL display structured Carrier Aggregation band information and 5G NSA anchor cell details on the Live Info screen.

#### Scenario: CA bands displayed as chips
- GIVEN the Live Info tab is selected and a SIM has active CA bands
- THEN each CA band is shown as a chip in a FlowRow layout
- AND each chip shows the band and PCI
- AND the chip text is color-coded by the CA band's RSRP value

#### Scenario: Anchor cell displayed in structured rows
- GIVEN the Live Info tab is selected and a SIM is on 5G NSA
- THEN the anchor cell section shows structured rows: Band, ARFCN, PCI, TAC
- AND a second row shows RSRP, RSRQ, SINR with signal quality color coding

### Requirement: Recording Screen — Expandable SimCard

The system SHALL provide an expandable SimCard on the RecordingScreen that reveals full 5G NSA anchor and CA band details when expanded.

#### Scenario: SimCard collapsed state
- GIVEN the RecordingScreen shows the live stats panel
- THEN each SimCard shows a compact anchor row for 5G NSA (`LTE: B<band> PCI <pci> RSRP <rsrp>`)
- AND a CA band count badge (`B<band>+<N>`) when CA bands are active
- AND the card is clickable when expandable data exists

#### Scenario: SimCard expanded state
- GIVEN the user taps a SimCard with anchor or CA data
- THEN the card expands to show full anchor details (Band, ARFCN, PCI, TAC, RSRP, RSRQ, SINR)
- AND structured CA band rows (Band, PCI, EARFCN, RSRP, RSRQ, SINR per band)
- AND all signal values are color-coded by quality

### Requirement: Signal Quality Color Coding

The system SHALL apply signal quality color coding to RSRP, RSRQ, and SINR values on all live and replay screens.

#### Scenario: Primary cell signal colors
- GIVEN any screen displaying primary cell RSRP, RSRQ, or SINR
- THEN values are colored green for excellent, cyan for good, orange for fair, and red for poor
- AND the thresholds match the map display mode legend (excellent > -80 dBm, good -80 to -90, fair -90 to -100, poor < -100 for RSRP)

#### Scenario: Anchor and CA band signal colors
- GIVEN any screen displaying anchor cell or CA band signal metrics
- THEN RSRP, RSRQ, and SINR values are color-coded using the same quality thresholds as the primary cell

### Requirement: Session Detail Screen — Record Detail Bottom Sheet

The system SHALL display a bottom sheet when a user taps a record in the session detail list, showing the full record data.

#### Scenario: Bottom sheet opens on tap
- GIVEN the SessionDetailScreen record list is visible
- WHEN a user taps a record row
- THEN a ModalBottomSheet opens showing the record's full details

#### Scenario: Primary cell section
- GIVEN the record detail bottom sheet is open
- THEN the Primary Cell section shows RAT, PLMN, Cell ID, PCI, TAC, Band, ARFCN, BW, RSRP, RSRQ, SINR, RSSI, CQI, TA
- AND all signal values are color-coded by quality

#### Scenario: CA Bands section
- GIVEN the record detail bottom sheet is open and the record has CA bands
- THEN the CA Bands section shows a card per band with band, EARFCN, PCI, RSRP, RSRQ, SINR
- AND each signal value is color-coded
- AND the section is hidden when no CA bands exist

#### Scenario: Anchor Cell section
- GIVEN the record detail bottom sheet is open and the record is 5G NSA with anchor data
- THEN the Anchor Cell section shows Band, EARFCN, PCI, TAC, RSRP, RSRQ, SINR
- AND the section is hidden for non-5G_NSA records or when anchor data is missing

#### Scenario: Location and Connectivity sections
- GIVEN the record detail bottom sheet is open
- THEN the Location section shows lat/lon/alt/accuracy/source for outdoor, or relX/relY for indoor
- AND the Connectivity section shows avgLatencyMs and packetLossPct

#### Scenario: Dismiss bottom sheet
- GIVEN the record detail bottom sheet is open
- WHEN the user taps outside the sheet or swipes down
- THEN the sheet dismisses and the selected record is cleared

### Requirement: Replay Screen — Expandable StatsPanel

The system SHALL provide an expandable StatsPanel in the ReplayScreen that matches the live RecordingScreen SimCard behavior.

#### Scenario: StatsPanel expandable
- GIVEN the ReplayScreen is active and a record is selected
- THEN the StatsPanel is expandable when anchor or CA data exists
- AND the collapsed state shows a compact anchor row and CA band count badge
- AND the expanded state shows full anchor details and structured CA band rows
- AND all signal values are color-coded by quality

### Requirement: Insight Cards

The system SHALL display computed analytics insights in the session analytics panel instead of a placeholder.

#### Scenario: Real insights displayed
- GIVEN the AnalyticsPanel is rendered for a session with computed insights
- THEN each insight card is displayed with its title and body
- AND cards are stacked vertically

#### Scenario: No insights empty state
- GIVEN the AnalyticsPanel is rendered for a session with no insights
- THEN a compact "No insights for this session" message is shown
- AND the placeholder robot emoji is NOT displayed

### Requirement: Global Statistics Screen — Band Distribution Labels

The system SHALL display qualified band names in the global StatisticsScreen band distribution chart.

#### Scenario: Qualified band labels
- GIVEN the Statistics tab is selected and band distribution data exists
- THEN the chart legend shows qualified band names (e.g., "B3" for LTE, "n78" for 5G)
- AND NR bands are grouped with cool tone colors (cyan/teal range)
- AND LTE bands are grouped with warm tone colors (blue/indigo range)
- AND the chart is sorted by count within each RAT group