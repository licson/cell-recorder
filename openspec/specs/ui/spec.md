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
- AND if tunnel: a tunnel placeholder is shown with no map, no canvas, and no GPS fields
- AND a Start/Stop button is centered at the bottom
- AND a live stats panel shows per-SIM cell data and, for outdoor/indoor, ping latency

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

The system SHALL provide a "Speed Test" settings section for configuring continuous throughput tests. The section also provides a manual "Launch Test" affordance for priming the mobile connection and a debug card for diagnosing engine behavior. Manual launch and diagnostics are defined in `speedtest-diagnostics/spec.md`.

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

#### Scenario: Launch Test button shown when enabled
- GIVEN the Settings screen
- WHEN `speedTestEnabled` is true
- THEN a "Launch Test" button is rendered inside the Speed Test card
- AND tapping the button triggers a manual speedtest launch (see `speedtest-diagnostics/spec.md`)

#### Scenario: Launch Test button hidden when disabled
- GIVEN the Settings screen
- WHEN `speedTestEnabled` is false
- THEN the "Launch Test" button is NOT rendered

#### Scenario: Debug card collapsed by default
- GIVEN the Settings screen and `speedTestEnabled` is true
- WHEN no manual launch is in progress
- THEN the debug card is collapsed or summary-only
- AND does not dominate the Settings page

#### Scenario: Debug card expands on launch
- GIVEN the "Launch Test" button is tapped
- WHEN the manual launch begins
- THEN the debug card expands to show the live event stream from the ring buffer (see `speedtest-diagnostics/spec.md`)
- AND the event list auto-scrolls to the newest event

#### Scenario: Share Debug Log action
- GIVEN the debug card is expanded
- WHEN the user taps the "Share Debug Log" action
- THEN the current ring buffer snapshot is serialized as plain text
- AND an `Intent.ACTION_SEND` chooser is displayed (mirrors the existing "Share Crash Log" pattern)

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

The system SHALL display structured Carrier Aggregation band information and 5G NSA anchor cell details on the Live Info screen. Band labels SHALL use RAT-appropriate prefixes (e.g., "B3", "n78"). The anchor cell's Cell ID SHALL be displayed formatted as `anchorEnbOrGnbId:anchorLcid`, matching the primary LTE Cell ID format, and SHALL render `---` when either identity component is missing.

#### Scenario: CA bands displayed as chips
- GIVEN the Live Info tab is selected and a SIM has active CA bands
- THEN each CA band is shown as a chip in a FlowRow layout
- AND each chip shows the RAT-appropriate band prefix and PCI
- AND the chip text is color-coded by the CA band's RSRP value

#### Scenario: Anchor cell displayed in structured rows
- GIVEN the Live Info tab is selected and a SIM is on 5G NSA
- THEN the anchor cell section shows a first row with Cell ID (`anchorEnbOrGnbId:anchorLcid`, or `---` if either is missing)
- AND a second row shows Band (with "B" prefix), ARFCN, PCI, TAC
- AND a final row shows RSRP, RSRQ, SINR with signal quality color coding

### Requirement: Recording Screen — Expandable SimCard

The system SHALL provide an expandable SimCard on the RecordingScreen that reveals full 5G NSA anchor and CA band details when expanded. The card's chevron icon SHALL respond to tap events to toggle expansion. Band labels SHALL use RAT-appropriate prefixes. The expanded anchor block SHALL include the anchor Cell ID formatted as `anchorEnbOrGnbId:anchorLcid` (rendering `---` when either component is missing). The collapsed compact anchor row SHALL NOT include the Cell ID, to preserve its one-line summary.

#### Scenario: SimCard collapsed state
- GIVEN the RecordingScreen shows the live stats panel
- THEN each SimCard shows a compact anchor row for 5G NSA (`LTE: B<band> PCI <pci> RSRP <rsrp>`)
- AND a CA band count badge (`<prefix><band>+<N>`) when CA bands are active
- AND the card is clickable when expandable data exists

#### Scenario: SimCard expanded state
- GIVEN the user taps a SimCard with anchor or CA data (or its chevron icon)
- THEN the card expands to show full anchor details (Cell ID as `anchorEnbOrGnbId:anchorLcid` or `---`, Band, ARFCN, PCI, TAC, RSRP, RSRQ, SINR)
- AND structured CA band rows (Band, PCI, EARFCN, RSRP, RSRQ, SINR per band)
- AND CA bands that have EARFCN data display the actual EARFCN value (not "---")
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

The system SHALL display a bottom sheet when a user taps a record in the session detail list, showing the full record data. Band labels SHALL use RAT-appropriate prefixes.

#### Scenario: Bottom sheet opens on tap
- GIVEN the SessionDetailScreen record list is visible
- WHEN a user taps a record row
- THEN a ModalBottomSheet opens showing the record's full details

#### Scenario: Primary cell section
- GIVEN the record detail bottom sheet is open
- THEN the Primary Cell section shows RAT, PLMN, Cell ID, PCI, TAC, Band, ARFCN, BW, RSRP, RSRQ, SINR, RSSI, CQI, TA
- AND all signal values are color-coded by quality using the appropriate color function (RSRP uses rsrpColor, RSRQ uses rsrqColor, SINR uses sinrColor)

#### Scenario: CA Bands section
- GIVEN the record detail bottom sheet is open and the record has CA bands
- THEN the CA Bands section shows a card per band with band (using RAT-appropriate prefix), EARFCN, PCI, RSRP, RSRQ, SINR
- AND each signal value is color-coded using the appropriate color function
- AND the section is hidden when no CA bands exist

#### Scenario: Anchor Cell section
- GIVEN the record detail bottom sheet is open and the record is 5G NSA with anchor data
- THEN the Anchor Cell section shows Cell ID (`anchorEnbOrGnbId:anchorLcid`, or `---` if either is missing), Band (with "B" prefix), EARFCN, PCI, TAC, RSRP, RSRQ, SINR
- AND each signal value is color-coded using the appropriate color function
- AND the section is hidden for non-5G_NSA records or when anchor data is missing

#### Scenario: Location and Connectivity sections
- GIVEN the record detail bottom sheet is open
- THEN the Location section shows lat/lon/alt/accuracy/source for outdoor, or relX/relY for indoor
- AND the Connectivity section shows avgLatencyMs and packetLossPct

#### Scenario: Dismiss bottom sheet
- GIVEN the record detail bottom sheet is open
- WHEN the user taps outside the sheet or swipes down
- THEN the sheet dismisses and the selected record is cleared

### Requirement: Session Detail Record Row CA Badge

The system SHALL display a CA band count badge on session detail record rows when a record has carrier aggregation bands.

#### Scenario: Record row with CA bands
- GIVEN a session detail record row for a record with CA bands
- WHEN the row is rendered
- THEN the Band column shows `<formatted_band> +<N>` where N is the number of CA bands

#### Scenario: Record row without CA bands
- GIVEN a session detail record row for a record without CA bands
- WHEN the row is rendered
- THEN the Band column shows `<formatted_band>` with no count suffix

### Requirement: Session Detail Column Headers

The system SHALL display correct column headers in the session detail records list.

#### Scenario: Column headers match data
- GIVEN the SessionDetailScreen records list
- WHEN the column header row is rendered
- THEN the columns are: #, SIM, PLMN, Band, RSRP (dBm), RSRQ (dB), and either relX/relY (indoor) or Ping (ms) (outdoor)
- AND no duplicate header labels exist

### Requirement: Session Detail Markers Section

The system SHALL display a collapsible markers section on the Session Detail screen.

#### Scenario: Markers section visible
- GIVEN a session with markers
- WHEN the user views the Session Detail screen
- THEN a collapsible "Markers" section is displayed above the records list
- AND the section shows the count of markers
- AND each marker displays its sequence number, type, label, and timestamp
- AND tapping a marker opens the marker dialog for editing
- AND tapping the delete icon removes the marker

#### Scenario: Empty markers section
- GIVEN a session without markers
- WHEN the user views the Session Detail screen
- THEN no markers section is displayed

### Requirement: Marker Dialog

The system SHALL provide a shared dialog for creating and editing markers.

#### Scenario: Marker dialog fields
- GIVEN the marker dialog is open
- THEN it shows a type selector with NOTE, WAYPOINT, SEGMENT_START, SEGMENT_END, and STOP
- AND it shows a text field for the optional label
- AND it surfaces recently used labels for the selected type

#### Scenario: Save marker
- GIVEN the user selects a type and optionally enters a label
- WHEN the user taps Save
- THEN the marker is persisted with the selected type and label
- AND the label is added to the recent labels for that type

#### Scenario: Delete marker
- GIVEN the user is editing an existing marker
- WHEN the user taps Delete
- THEN the marker is removed from the session

### Requirement: Replay Screen Marker Pins

The system SHALL display marker pins on the replay timeline.

#### Scenario: Marker pins on replay timeline
- GIVEN a session with markers
- WHEN the user opens the Replay screen
- THEN marker pins are drawn on the RAT timeline bar at the marker timestamps
- AND each pin is colored by marker type
- AND tapping a pin shows a detail card with the marker type, label, and timestamp
- AND the detail card includes Edit and Delete actions that open the marker dialog

### Requirement: Session List Import Markers

The system SHALL support importing markers from the session list import flow.

#### Scenario: Import CSV with markers
- GIVEN the user chooses CSV import
- WHEN the user selects the main cell record CSV
- THEN a second file picker is shown for the optional markers CSV file
- AND selecting a markers file imports the markers along with the cell records
- AND canceling the second picker imports only the cell records

#### Scenario: Import GeoJSON with markers
- GIVEN the user chooses GeoJSON import
- WHEN the user selects a GeoJSON file containing marker Features
- THEN the markers are imported along with the cell records
- AND no second picker is shown

### Requirement: Replay Screen — Expandable StatsPanel

The system SHALL provide an expandable StatsPanel in the ReplayScreen that matches the live RecordingScreen SimCard behavior. The expanded anchor block SHALL include the anchor Cell ID formatted as `anchorEnbOrGnbId:anchorLcid` (rendering `---` when either component is missing). The collapsed compact anchor row SHALL NOT include the Cell ID.

#### Scenario: StatsPanel expandable
- GIVEN the ReplayScreen is active and a record is selected
- THEN the StatsPanel is expandable when anchor or CA data exists
- AND the collapsed state shows a compact anchor row and CA band count badge
- AND the expanded state shows full anchor details (including anchor Cell ID as `anchorEnbOrGnbId:anchorLcid` or `---`) and structured CA band rows
- AND CA bands that have EARFCN data display the actual EARFCN value
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
- AND the chart is sorted by count within each RAT group## ADDED Requirements

### Requirement: Tunnel Mode Selector in New Session Dialog

The system SHALL provide a Tunnel mode chip in the New Session dialog, alongside the existing Outdoor and Indoor chips. Selecting Tunnel creates a session with `recordingMode = "TUNNEL"`. Recording mode selection is defined in `sessions/spec.md`.

#### Scenario: Tunnel chip displayed

- GIVEN the New Session dialog
- WHEN the dialog is shown
- THEN three FilterChips are displayed: "Outdoor", "Indoor", "Tunnel"
- AND "Outdoor" is the default selected chip

#### Scenario: Tunnel mode guidance note

- GIVEN the New Session dialog
- WHEN the user selects "Tunnel" mode
- THEN a guidance note is displayed: "Tunnel mode samples on a fixed time cadence and uses manual markers for landmarks. Best for mapping coverage inside metro tunnels."

#### Scenario: Tunnel session created

- GIVEN the New Session dialog
- WHEN the user selects "Tunnel" mode and enters a session name
- THEN a new session is created with `recordingMode = "TUNNEL"`

### Requirement: Tunnel Recording Screen Layout

The system SHALL provide a tunnel recording screen layout adapted from the outdoor recording screen. Tunnel mode does not use a map, an indoor path canvas, or a tracking confidence indicator. Recording lifecycle is defined in `recording/spec.md`; tunnel mode behavior in `tunnel/spec.md`; marker UX in `markers/spec.md`.

#### Scenario: Tunnel recording screen layout

- GIVEN a tunnel recording session has been started
- WHEN the user navigates to the recording screen
- THEN the screen displays a top bar with session name, elapsed timer, and point count
- AND a placeholder area is shown where the outdoor map or indoor canvas would be (a "Tunnel recording in progress" panel with the elapsed time and marker count, or an empty area)
- AND a Start/Stop button is centered at the bottom
- AND a Mark button is displayed in the action row (per `markers/spec.md` — visible whenever recording is active)
- AND a live stats panel shows per-SIM cell data and ping latency
- AND the GPS status indicator, latitude/longitude/altitude, indoor canvas, tracking confidence, drift radius, and sensor health warning are NOT displayed

#### Scenario: Tunnel screen hides GPS and indoor elements

- GIVEN an active tunnel recording
- THEN the GPS status indicator is NOT displayed
- AND the GPS accuracy reading is NOT displayed
- AND the indoor path canvas is NOT displayed
- AND the tracking confidence indicator is NOT displayed
- AND the drift radius circle is NOT displayed
- AND the sensor health warning is NOT displayed
- AND the "Reset Origin" button is NOT displayed

#### Scenario: Tunnel live stats bar

- GIVEN an active tunnel recording
- THEN the live stats bar displays the current ping latency in milliseconds
- AND the format matches the indoor ping display: "Ping: X.X ms $dataSimLabel"
- AND the GPS status, latitude, longitude, and altitude fields are NOT displayed

### Requirement: Mark Button on RecordingScreen

The system SHALL provide a "Mark" button on the RecordingScreen action row, visible whenever recording is active (regardless of recording mode). The button supports quick-tap (one-tap NOTE marker with auto-label) and long-press (opens the type-aware `MarkerDialog` with type chips, conditional label field, and recent-label chips). Mark creation semantics are defined in `markers/spec.md`.

#### Scenario: Mark button visible whenever recording is active

- GIVEN the RecordingScreen
- WHEN the recording mode is `OUTDOOR`, `INDOOR`, or `TUNNEL`
- AND the recording is active
- THEN the Mark button IS displayed alongside the Start/Stop button in the action row
- WHEN the recording is not active
- THEN the Mark button is NOT displayed

#### Scenario: Mark button quick-tap feedback

- GIVEN an active recording (any mode)
- WHEN the user taps the Mark button
- THEN a marker is created immediately with `type = "NOTE"` and an auto-generated label `"NOTE #<seq> HH:MM:SS"` (type-prefixed per `markers/spec.md`)
- AND a brief visual confirmation is shown (e.g., a snackbar: "Marked #N")
- AND no dialog is shown
- (Marker creation semantics: `markers/spec.md`)

#### Scenario: Mark button long-press opens type-aware dialog

- GIVEN an active recording (any mode)
- WHEN the user long-presses the Mark button
- THEN a small dialog opens titled "New Marker"
- AND the dialog displays a row of type chips (`WAYPOINT`, `SEGMENT_START`, `SEGMENT_END`, `STOP`, `NOTE`) with `NOTE` selected by default
- AND when the selected type is `WAYPOINT`, `STOP`, or `NOTE`, an optional single-line `OutlinedTextField` for the label is displayed and auto-focused (the user opened the dialog to type)
- AND when the selected type is `SEGMENT_START` or `SEGMENT_END`, the label field is NOT displayed (the type is the entire semantic content)
- AND a "Save" button confirms the marker with the selected type and label (or NULL if the field is blank or hidden)
- (Recent-label chips: scenario below; Marker creation semantics: `markers/spec.md`)

#### Scenario: Recent-label chips displayed in the marker dialog

- GIVEN the `MarkerDialog` is open with a type selected that shows the label field (`WAYPOINT`, `STOP`, or `NOTE`)
- AND the `recent_marker_labels` table has one or more rows for that type
- THEN a `FlowRow` of chips is rendered above the label field
- AND the chips are ordered by `lastUsed` descending and capped at the top 20
- AND when the table has zero rows for the selected type, no chips are rendered (the row is unobtrusive)
- (Recent-labels semantics: `markers/spec.md`)

#### Scenario: Tapping a recent-label chip fills the field

- GIVEN the `MarkerDialog` is open with label chips displayed
- WHEN the user taps a chip
- THEN the label field is filled with the chip's `label` text
- AND the dialog stays open (the user can tweak the label before saving)
- AND the user must tap "Save" to confirm
- (Recent-labels semantics: `markers/spec.md`)

### Requirement: Marker Pins on Replay Timeline

The system SHALL display session markers as vertical pins on the replay timeline at the position corresponding to each marker's `timestamp`, for any session with markers (any mode). Each pin is tappable to open a detail card with edit and delete actions. Replay rendering is defined in `sessions/spec.md`; marker semantics in `markers/spec.md`.

#### Scenario: Marker pins rendered on replay timeline

- GIVEN a session with markers and replay mode is active
- WHEN the replay timeline is displayed
- THEN a vertical pin is drawn at each marker's `timestamp` position on the timeline
- AND pins are visually distinct from speedtest markers (different color or shape)
- AND each pin is tappable to open a detail card

#### Scenario: Marker detail card with edit and delete actions

- GIVEN a replay timeline with marker pins
- WHEN the user taps a marker pin
- THEN a small detail card is shown with the marker's `seq`, `type`, `label`, and timestamp
- AND the card offers an "Edit" action that opens the `MarkerDialog` in edit mode (title "Edit Marker", pre-populated `type` and `label`, an additional "Delete" button)
- AND the card offers a "Delete" action that removes the marker immediately (per `markers/spec.md`)
- AND after either action completes, the timeline re-renders

### Requirement: Markers Section on Session Detail Screen

The system SHALL display a collapsible "Markers (N)" section on the Session Detail screen for any session that has markers (any mode). Each expanded row offers edit and delete affordances. Session detail rendering is defined in `sessions/spec.md`; marker semantics in `markers/spec.md`.

#### Scenario: Markers section header collapsed by default

- GIVEN a session with N markers (N > 0, any mode)
- WHEN the session detail screen is loaded
- THEN a "Markers (N)" section header is displayed above the cell-records table
- AND the section is collapsed by default
- AND the header is tap-to-expand

#### Scenario: Markers section expanded rows with edit and delete affordances

- GIVEN the "Markers (N)" section is collapsed
- WHEN the user taps the header
- THEN the section expands to show one row per marker
- AND each row displays `#<seq>`, the `type` with chip-style coloring (e.g., `WAYPOINT` = blue, `SEGMENT_START` = green, `SEGMENT_END` = red, `STOP` = orange, `NOTE` = grey), the `label` (or "—" if NULL), and the timestamp
- AND each row offers an edit affordance (e.g., a pencil icon button) that opens the `MarkerDialog` in edit mode (per `markers/spec.md`)
- AND each row offers a delete affordance (e.g., a trash icon button) that removes the marker immediately (per `markers/spec.md`)
- AND after either action completes, the markers section re-renders with the updated row(s)

#### Scenario: Empty markers section hidden

- GIVEN a session with zero markers (any mode)
- WHEN the session detail screen is loaded
- THEN the "Markers (N)" section is NOT displayed

