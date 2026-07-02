## MODIFIED Requirements

### Requirement: Session Creation

The system SHALL allow the user to create a new session from the session list. The session SHALL include a recording mode (Outdoor, Indoor, or Tunnel, default Outdoor).

#### Scenario: Create new session

- GIVEN the Session List screen
- WHEN the user taps the FAB
- THEN a dialog prompts for a session name and recording mode selection
- AND a new session is created upon confirmation with the selected recording mode

### Requirement: Recording Mode Selection

The system SHALL allow the user to select a recording mode (Outdoor, Indoor, or Tunnel) when creating a new session.

#### Scenario: Recording mode selector in session creation

- GIVEN the user taps the FAB to create a new session
- WHEN the session creation dialog is shown
- THEN a recording mode selector is displayed with "Outdoor", "Indoor", and "Tunnel" options
- AND "Outdoor" is the default selection

#### Scenario: Indoor mode guidance note

- GIVEN the session creation dialog
- WHEN the user selects "Indoor" mode
- THEN a guidance note is displayed: "Indoor mode uses step detection instead of GPS. Best for sessions under 5 minutes."

#### Scenario: Tunnel mode guidance note

- GIVEN the session creation dialog
- WHEN the user selects "Tunnel" mode
- THEN a guidance note is displayed: "Tunnel mode samples on a fixed time cadence and uses manual markers for landmarks. Best for mapping coverage inside metro tunnels."

#### Scenario: Indoor session created

- GIVEN the session creation dialog
- WHEN the user selects "Indoor" mode and enters a session name
- THEN a new session is created with `recordingMode = "INDOOR"`

#### Scenario: Tunnel session created

- GIVEN the session creation dialog
- WHEN the user selects "Tunnel" mode and enters a session name
- THEN a new session is created with `recordingMode = "TUNNEL"`

#### Scenario: Outdoor session created

- GIVEN the session creation dialog
- WHEN the user selects "Outdoor" mode and enters a session name
- THEN a new session is created with `recordingMode = "OUTDOOR"`

## ADDED Requirements

### Requirement: Tunnel Session Detail

The system SHALL display a tunnel session's detail view without a map or indoor canvas, preserving all non-geographic functionality from the outdoor/indoor detail views. Tunnel mode behavior is defined in `tunnel/spec.md`.

#### Scenario: Tunnel session detail loaded

- GIVEN the user taps a tunnel session in the list
- THEN a "Tunnel recording" placeholder panel is displayed in place of the map or indoor canvas
- AND the panel shows the session name, total point count, total duration, and marker count
- AND a scrollable data table shows all records grouped by timestamp
- AND the data table does NOT include `latitude`, `longitude`, `altitude`, `accuracy`, `relativeX`, or `relativeY` columns (tunnel records have sentinel/null values for these)
- AND the data table DOES include a `src` (location source) column showing `"TUNNEL"` for every row

#### Scenario: Tunnel session SIM filter

- GIVEN a tunnel session detail view with records from multiple SIM slots
- THEN a SIM filter dropdown is displayed
- AND the user can filter the data table by SIM slot

#### Scenario: Tunnel session analytics panel

- GIVEN the tunnel session detail view
- WHEN the user toggles analytics mode
- THEN non-geographic analytics are displayed (RAT coverage, band distribution, signal histograms, correlations, latency stats, anomaly detection, timeline segments, insight cards, speedtest analytics)
- AND geographic-dependent analytics are NOT displayed (coverage maps, geographic handoff detection)

#### Scenario: Tunnel session point selection

- GIVEN the tunnel session detail view
- WHEN the user taps a record in the data table
- THEN the record's bottom sheet opens with the full record data
- AND the Location section of the bottom sheet shows `"Tunnel record (no GPS coordinates)"` instead of lat/lon/alt/accuracy or relX/relY
- (Record detail bottom sheet: existing `ui/spec.md` requirement)

### Requirement: Tunnel Session Replay

The system SHALL replay a tunnel session's recorded data with the same time-based controls and panels as outdoor/indoor replay, without a map or indoor canvas. Tunnel mode behavior is defined in `tunnel/spec.md`.

#### Scenario: Tunnel replay started

- GIVEN a tunnel session with recorded points
- WHEN the user taps the Replay button
- THEN a "Tunnel recording" placeholder panel is displayed in place of the map or indoor canvas
- AND an animated cursor on the timeline advances as the playback progresses

#### Scenario: Tunnel replay playback controls

- GIVEN tunnel replay mode is active
- THEN the user can play, pause, scrub with a time slider, and select speed (1x, 2x, 5x, 10x)

#### Scenario: Tunnel replay stats panel

- GIVEN tunnel replay mode is active
- WHEN the cursor reaches a point
- THEN the current point's RAT, PCI, RSRP, RSRQ, SINR, ping, and packet loss are shown in a stats panel
- AND for `5G_NSA` records, the LTE anchor's band, PCI, and RSRP are also displayed

#### Scenario: Tunnel replay timeline and charts

- GIVEN tunnel replay mode is active
- THEN a `RatTimelineBar` is displayed showing RAT segments with a position cursor
- AND a `ChartGrid` is displayed with RSRP, SINR, ping, and packet loss curves over time
- AND a vertical cursor on each chart is synced to the replay position
- AND marker pins are rendered on the timeline per `tunnel/spec.md` and `ui/spec.md`

#### Scenario: Tunnel replay speedtest markers

- GIVEN tunnel replay mode is active and the session has speedtest records
- THEN speedtest markers are displayed on the `RatTimelineBar` (visually distinct from marker pins)
- AND a `SpeedTestSummaryCard` updates based on the current playback position

#### Scenario: Tunnel replay SIM filter

- GIVEN tunnel replay mode is active with records from multiple SIM slots
- THEN a `SimFilterRow` is displayed allowing the user to filter by All/SIM1/SIM2

### Requirement: Session Detail Markers Section

The system SHALL display a collapsible "Markers (N)" section on the Session Detail screen for any session that has any markers. The section is not mode-specific (the schema supports markers on any mode), but in v1 markers are only creatable in `TUNNEL` mode. Each expanded row offers edit and delete affordances. Rendering is defined in `ui/spec.md`; the marker data model is defined in `tunnel/spec.md`; marker editing is defined in `tunnel/spec.md`.

#### Scenario: Markers section visible for sessions with markers

- GIVEN a session with N markers (N > 0), regardless of `recordingMode`
- WHEN the session detail screen is loaded
- THEN a "Markers (N)" section header is displayed above the cell-records table
- AND the section is collapsed by default
- (Section rendering: `ui/spec.md`)

#### Scenario: Markers section hidden for sessions without markers

- GIVEN a session with zero markers
- WHEN the session detail screen is loaded
- THEN the "Markers (N)" section is NOT displayed

#### Scenario: Marker row edit affordance

- GIVEN the markers section is expanded
- WHEN the user taps the edit affordance (e.g., a pencil icon) on a marker row
- THEN the `MarkerDialog` opens in edit mode, pre-populated with the current `type` and `label` (per `tunnel/spec.md`)
- AND saving updates the row in place
- AND the markers section re-renders with the updated row
- AND the marker's `seq` and `timestamp` are unchanged (the row's position in the list does not move)

### Requirement: Session Marker Pins on Replay

The system SHALL display session markers as vertical pins on the replay timeline for any session that has markers. In v1 markers are only creatable in `TUNNEL` mode, but the replay rendering is mode-agnostic. Each pin is tappable to open a detail card with edit and delete actions. Rendering is defined in `ui/spec.md`; the marker data model is defined in `tunnel/spec.md`; marker editing is defined in `tunnel/spec.md`.

#### Scenario: Marker pins rendered on replay timeline

- GIVEN a session with markers and replay mode is active
- WHEN the replay timeline is displayed
- THEN a vertical pin is drawn at each marker's `timestamp` position on the timeline
- AND pins are visually distinct from speedtest markers
- AND each pin is tappable to open a marker detail card with edit and delete actions
- (Marker detail card: `ui/spec.md`)
