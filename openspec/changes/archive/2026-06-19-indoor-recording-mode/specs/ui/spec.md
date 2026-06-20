## ADDED Requirements

### Requirement: Indoor Path Canvas

The system SHALL display a 2D path canvas for indoor recording and indoor session detail/replay views.

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

The system SHALL display a tracking confidence indicator during indoor recording.

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

The system SHALL provide an "Reset Origin" button on the indoor recording screen.

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
- AND a live stats panel shows per-SIM cell data
- AND the current step count and estimated drift are displayed
- AND a signal color legend is displayed below the canvas

#### Scenario: Indoor screen hides GPS status
- GIVEN an active indoor recording
- THEN the GPS status indicator is NOT displayed
- AND the GPS accuracy reading is NOT displayed

## MODIFIED Requirements

### Requirement: Recording Screen

The system SHALL provide a screen for controlling and monitoring an active recording. The screen layout SHALL differ based on recording mode: outdoor mode shows an OSM map with GPS status; indoor mode shows a 2D canvas with tracking confidence and drift indicators.

#### Scenario: Recording screen layout
- GIVEN a session has been created
- WHEN the user navigates to recording
- THEN the screen displays a top bar with session name, elapsed timer, and point counter
- AND if outdoor: an OSM map is shown with GPS status indicator and accuracy
- AND if indoor: a 2D path canvas is shown with tracking confidence indicator and drift radius
- AND a Start/Stop button is centered at the bottom
- AND a live stats panel shows per-SIM cell data

#### Scenario: Map markers and path (outdoor)
- GIVEN an active outdoor recording
- THEN recorded points are shown as RAT-colored markers on the map
- AND a path polyline connects the markers

#### Scenario: Indoor canvas path (indoor)
- GIVEN an active indoor recording
- THEN recorded points are shown as a uniform-colored polyline on the 2D canvas
- AND discontinuity markers are placed at origin reset points (path segments on either side are not connected)

### Requirement: Settings Screen

The system SHALL provide a settings screen for configuring recording and analytics parameters, including indoor-specific settings.

#### Scenario: Settings sections
- GIVEN the Settings screen
- THEN the following sections are displayed: Ping, Recording, Indoor Recording, Cell ID, GPS Loss Fallback, Analytics Thresholds

#### Scenario: Indoor recording settings
- GIVEN the Settings screen
- WHEN the "Indoor Recording" section is displayed
- THEN the section contains a step length text input (default 0.7m, range 0.1m–2.0m, validated in `SettingsViewModel`)
- AND an indoor recording interval picker (default 5000ms)
