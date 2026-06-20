## ADDED Requirements

### Requirement: Recording Mode Selection

The system SHALL allow the user to select a recording mode (Outdoor or Indoor) when creating a new session.

#### Scenario: Recording mode selector in session creation
- GIVEN the user taps the FAB to create a new session
- WHEN the session creation dialog is shown
- THEN a recording mode selector is displayed with "Outdoor" and "Indoor" options
- AND "Outdoor" is the default selection

#### Scenario: Indoor mode guidance note
- GIVEN the session creation dialog
- WHEN the user selects "Indoor" mode
- THEN a guidance note is displayed: "Indoor mode uses step detection instead of GPS. Best for sessions under 5 minutes."

#### Scenario: Indoor session created
- GIVEN the session creation dialog
- WHEN the user selects "Indoor" mode and enters a session name
- THEN a new session is created with `recordingMode = "INDOOR"`

#### Scenario: Outdoor session created
- GIVEN the session creation dialog
- WHEN the user selects "Outdoor" mode and enters a session name
- THEN a new session is created with `recordingMode = "OUTDOOR"`

### Requirement: Indoor Session Detail

The system SHALL display an indoor session's detail view with a 2D path canvas instead of a map, preserving all non-geographic functionality from the outdoor detail view.

#### Scenario: Indoor session detail loaded
- GIVEN the user taps an indoor session in the list
- THEN a 2D path canvas is displayed showing the recorded path with signal-colored polyline
- AND a scrollable data table shows all records grouped by timestamp
- AND the data table includes `relativeX` and `relativeY` columns in place of `latitude`, `longitude`, `altitude`, and `accuracy` columns

#### Scenario: Indoor session display mode selector
- GIVEN the indoor session detail view
- THEN a `MapDisplayMode` dropdown is displayed with the same 5 options as outdoor sessions (SIGNAL_TRAILS, PACKET_LOSS, CELL_ID, RAT, BAND)
- AND the selected mode applies to the indoor canvas rendering

#### Scenario: Indoor session SIM filter
- GIVEN an indoor session detail view with records from multiple SIM slots
- THEN a SIM filter dropdown is displayed
- AND the user can filter the canvas and data table by SIM slot

#### Scenario: Indoor session analytics panel
- GIVEN the indoor session detail view
- WHEN the user toggles analytics mode
- THEN non-geographic analytics are displayed (RAT coverage, band distribution, signal histograms, correlations, latency stats, anomaly detection, timeline segments, insight cards, speedtest analytics)
- AND geographic-dependent analytics are NOT displayed (coverage maps, geographic handoff detection)

#### Scenario: Point selection on indoor canvas
- GIVEN the indoor session detail view
- WHEN the user taps a record in the data table
- THEN the corresponding point is highlighted on the 2D canvas

### Requirement: Indoor Session Replay

The system SHALL replay an indoor session's recorded path with animated playback on a 2D canvas, preserving the same time-based controls and panels as outdoor replay.

#### Scenario: Indoor replay started
- GIVEN an indoor session with recorded points
- WHEN the user taps the Replay button
- THEN the 2D canvas shows all points (faded) with an animated marker along the path

#### Scenario: Indoor replay playback controls
- GIVEN indoor replay mode is active
- THEN the user can play, pause, scrub with a time slider, and select speed (1x, 2x, 5x, 10x)

#### Scenario: Indoor replay stats panel
- GIVEN indoor replay mode is active
- WHEN the marker reaches a point
- THEN the current point's RAT, PCI, RSRP, RSRQ, SINR, ping, and packet loss are shown in a stats panel
- AND for `5G_NSA` records, the LTE anchor's band, PCI, and RSRP are also displayed

#### Scenario: Indoor replay timeline and charts
- GIVEN indoor replay mode is active
- THEN a `RatTimelineBar` is displayed showing RAT segments with a position cursor
- AND a `ChartGrid` is displayed with RSRP, SINR, ping, and packet loss curves over time
- AND a vertical cursor on each chart is synced to the replay position

#### Scenario: Indoor replay speedtest markers
- GIVEN indoor replay mode is active and the session has speedtest records
- THEN speedtest markers are displayed on the `RatTimelineBar`
- AND a `SpeedTestSummaryCard` updates based on the current playback position

#### Scenario: Indoor replay SIM filter
- GIVEN indoor replay mode is active with records from multiple SIM slots
- THEN a `SimFilterRow` is displayed allowing the user to filter by All/SIM1/SIM2

#### Scenario: Indoor replay landscape layout
- GIVEN the device is in landscape orientation during indoor replay
- THEN the left half displays the indoor path canvas
- AND the right half displays the scrollable column of stats panel, timeline, charts, controls

## MODIFIED Requirements

### Requirement: Session Creation

The system SHALL allow the user to create a new session from the session list. The session SHALL include a recording mode (Outdoor or Indoor, default Outdoor).

#### Scenario: Create new session
- GIVEN the Session List screen
- WHEN the user taps the FAB
- THEN a dialog prompts for a session name and recording mode selection
- AND a new session is created upon confirmation with the selected recording mode

### Requirement: Session Detail — Data Mode

The system SHALL display a session detail view with a map (outdoor) or 2D canvas (indoor) and a scrollable data table.

#### Scenario: Session detail loaded
- GIVEN the user taps a session in the list
- THEN if the session is outdoor, a map is displayed with all recorded points (RAT-colored) and a path polyline
- AND if the session is indoor, a 2D path canvas is displayed with all recorded points (signal-colored) and a path polyline
- AND a scrollable data table shows all records grouped by timestamp
- AND if the session is outdoor, the data table includes `latitude`, `longitude`, `altitude`, `accuracy` columns
- AND if the session is indoor, the data table includes `relativeX`, `relativeY` columns in place of lat/lon/alt/accuracy

#### Scenario: Point selection on map or canvas
- GIVEN the session detail view
- WHEN the user taps a record in the data table
- THEN the corresponding point is highlighted on the map (outdoor) or canvas (indoor)
