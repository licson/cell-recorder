# User Interface Specification

## Purpose

Defines the user interface screens, navigation structure, and interactive behavior of the application.

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

The system SHALL provide a screen for controlling and monitoring an active recording. The screen layout SHALL differ based on recording mode: outdoor mode shows an OSM map with GPS status; indoor mode shows a 2D canvas with tracking confidence and drift indicators.

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
- THEN recorded points are shown as signal-colored segments on the 2D canvas
- AND a path polyline connects the points with discontinuity markers at origin resets

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

The system SHALL provide a settings screen for configuring recording and analytics parameters.

#### Scenario: Settings sections
- GIVEN the Settings screen
- THEN the following sections are displayed: Ping, Recording, Cell ID, GPS Loss Fallback, Analytics Thresholds

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

The system SHALL display a dynamic speedtest summary card during session replay that updates based on the current playback position.

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

The system SHALL display a conditional speedtest overview card on the global statistics screen.

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
- AND the card contains a step length slider (default 0.7m, range 0.3m–1.2m)
- AND an indoor recording interval picker (default 5000ms)

### Requirement: Indoor Path Canvas

The system SHALL display a 2D path canvas for indoor recording and indoor session detail/replay views.

#### Scenario: Indoor canvas layout
- GIVEN an active indoor recording or indoor session detail view
- THEN a 2D canvas is displayed showing the movement path as a polyline
- AND the path is color-coded by signal strength (RSRP)
- AND the current position is marked with a distinct marker
- AND the origin (0,0) is marked with a reference marker
- AND light grid lines are shown for spatial reference

#### Scenario: Canvas pan and zoom
- GIVEN the indoor path canvas
- WHEN the user drags or pinches on the canvas
- THEN the canvas pans and zooms accordingly
- AND the path, markers, and grid scale with the transformation

#### Scenario: Signal-colored path
- GIVEN an indoor recording with recorded points
- WHEN the path is rendered on the canvas
- THEN each path segment is colored based on the RSRP value at that point (excellent=green, good=blue, fair=yellow, poor=red)
- AND a signal color legend is displayed

### Requirement: Indoor Canvas Display Modes

The system SHALL support the existing `MapDisplayMode` options on the indoor path canvas, applying the same coloring and marker logic without geographic map tiles.

#### Scenario: SIGNAL_TRAILS mode on indoor canvas
- GIVEN an indoor session viewed on the indoor canvas
- WHEN the display mode is set to SIGNAL_TRAILS
- THEN the path polyline is color-coded by RSRP value per segment (green >-80, cyan -80~-90, orange -90~-100, red <-100)
- AND a signal color legend is displayed

#### Scenario: PACKET_LOSS mode on indoor canvas
- GIVEN an indoor session viewed on the indoor canvas
- WHEN the display mode is set to PACKET_LOSS
- THEN the path polyline is color-coded by packet loss percentage per segment (green 0%, cyan <=20%, orange <=40%, red >40%)
- AND a packet loss legend is displayed

#### Scenario: CELL_ID mode on indoor canvas
- GIVEN an indoor session viewed on the indoor canvas
- WHEN the display mode is set to CELL_ID
- THEN dot markers are placed at points where the cell identity changes
- AND each marker shows a RAT-colored dot icon with cell ID info snippet
- AND the path polyline is rendered in grey

#### Scenario: RAT mode on indoor canvas
- GIVEN an indoor session viewed on the indoor canvas
- WHEN the display mode is set to RAT
- THEN dot markers are placed at RAT change points
- AND each marker shows a RAT-colored dot icon

#### Scenario: BAND mode on indoor canvas
- GIVEN an indoor session viewed on the indoor canvas
- WHEN the display mode is set to BAND
- THEN dot markers are placed at band change points
- AND each marker shows a RAT-colored dot icon

#### Scenario: Display mode selector for indoor sessions
- GIVEN an indoor session detail or replay view
- THEN a `MapDisplayMode` dropdown is displayed with the same 5 options as outdoor sessions
- AND the selected mode applies to the indoor canvas rendering

#### Scenario: Discontinuity markers
- GIVEN an indoor recording with one or more origin resets
- WHEN the path is rendered on the canvas
- THEN a visible break/gap marker is shown at each origin reset point
- AND the path segments on either side of the discontinuity are not connected by a line

### Requirement: Tracking Confidence Indicator

The system SHALL display a tracking confidence indicator during indoor recording.

#### Scenario: Confidence state displayed
- GIVEN an active indoor recording
- THEN a tracking confidence indicator is shown with one of four states:
  - Confident (green): estimated drift < 3 meters
  - Degrading (yellow): estimated drift 3–10 meters
  - High drift (red): estimated drift > 10 meters
  - No steps (orange): no step events received for 10+ seconds
- AND the indicator shows the time elapsed since the last origin reset
- AND the indicator shows the current step count

#### Scenario: Confidence updates in real time
- GIVEN an active indoor recording
- WHEN the drift estimate changes
- THEN the confidence indicator updates to reflect the new drift state

### Requirement: Origin Reset Button

The system SHALL provide a "Reset Origin" button on the indoor recording screen.

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

The system SHALL display a drift radius circle on the indoor path canvas.

#### Scenario: Drift radius rendered
- GIVEN an active indoor recording
- WHEN the path is displayed on the canvas
- THEN a translucent circle is centered on the current position marker
- AND the circle's radius equals the estimated drift in meters
- AND the circle grows over time as drift increases

### Requirement: Indoor Recording Screen Layout

The system SHALL provide an indoor recording screen layout adapted from the outdoor recording screen.

#### Scenario: Indoor recording screen layout
- GIVEN an indoor recording session has been started
- WHEN the user navigates to the recording screen
- THEN the screen displays a top bar with session name, elapsed timer, and point counter
- AND a 2D path canvas is shown instead of an OSM map
- AND a tracking confidence indicator is displayed
- AND a drift radius circle is shown on the canvas
- AND a Start/Stop button is centered at the bottom
- AND a "Reset Origin" button is accessible
- AND a live stats panel shows per-SIM cell data AND current ping latency
- AND the current step count and estimated drift are displayed

#### Scenario: Indoor screen hides GPS status
- GIVEN an active indoor recording
- THEN the GPS status indicator is NOT displayed
- AND the GPS accuracy reading is NOT displayed
- AND a sensor health warning IS displayed when no steps are detected for 10+ seconds

### Requirement: Ping Latency Display in Indoor Recording

The system SHALL display current ping latency in the indoor recording screen's live stats bar.

#### Scenario: Ping latency shown in indoor mode
- GIVEN an active indoor recording
- THEN the live stats bar displays the current ping latency in milliseconds
- AND the format matches the outdoor ping display: "Ping: X.X ms"
- AND the GPS status, latitude, longitude, and altitude fields are NOT displayed

### Requirement: Sensor Health Warning in Indoor UI

The system SHALL display a sensor health warning when step detection is not receiving events.

#### Scenario: Sensor health warning visible
- GIVEN an active indoor recording with no step events for 10+ seconds
- THEN a warning message is displayed below the tracking confidence indicator
- AND the message reads: "No steps detected. Try moving the phone to your pocket."
- AND the warning is styled with a caution color (yellow/orange)

#### Scenario: Sensor health warning hidden
- GIVEN a sensor health warning is visible
- WHEN step events are received
- THEN the warning is dismissed