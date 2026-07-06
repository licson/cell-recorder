# Sessions Specification

## Purpose

Defines how the system manages recording sessions: listing, viewing details, replaying, and deleting sessions.

## Scope

This spec covers session CRUD, replay, and detail views. It does not define:
- Recording lifecycle or triggers (see `recording/spec.md`).
- Cell identity processing (see `cell-info/spec.md`).
- Analytics computation (see `analytics/spec.md`).
- Speedtest protocol (see `speedtest/spec.md`).
- Indoor positioning (see `indoor/spec.md`).
- Data export/import formats (see `data/spec.md`).
- UI screen rendering (see `ui/spec.md`).

## Related Specs

- `recording/spec.md` — session creation and recording lifecycle.
- `analytics/spec.md` — analytics panels displayed in session detail.
- `data/spec.md` — export/import formats triggered from session list.
- `cell-info/spec.md` — cell identity and RAT definitions used in replay.
- `speedtest/spec.md` — speedtest data displayed in replay.
- `ui/spec.md` — screen layouts and controls for session views.
- `indoor/spec.md` — indoor session positioning and canvas behavior.

## Requirements

### Requirement: Session List

The system SHALL display past sessions sorted by creation date descending.

#### Scenario: Session list displayed
- GIVEN the user navigates to the Sessions tab
- THEN a list of past sessions is shown
- AND each entry displays the session name, date, and point count
- AND sessions are sorted by date descending

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

#### Scenario: Tunnel session created
- GIVEN the session creation dialog
- WHEN the user selects "Tunnel" mode and enters a session name
- THEN a new session is created with `recordingMode = "TUNNEL"`

### Requirement: Session Context Menu

The system SHALL provide a context menu on session list items.

#### Scenario: Long-press context menu
- GIVEN the Session List screen
- WHEN the user long-presses a session row
- THEN a context menu is shown with delete and export options

### Requirement: Session Deletion

The system SHALL delete a session and all its associated data.

#### Scenario: Delete session
- GIVEN a session with recorded points
- WHEN the user confirms deletion
- THEN the session and all its cell records are permanently removed

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

### Requirement: Session Detail — Analytics Mode

The system SHALL display an analytics panel alongside the map.

#### Scenario: Analytics toggle
- GIVEN the session detail view in data mode
- WHEN the user toggles analytics mode
- THEN the map expands
- AND a full analytics panel is displayed below

### Requirement: Session Replay

The system SHALL replay a session's recorded path with animated playback. For `5G_NSA` records, the replay stats panel SHALL display both the NR cell data and the LTE anchor cell data. 5G NSA anchor semantics are defined in `cell-info/spec.md`.

#### Scenario: Replay started
- GIVEN a session with recorded points
- WHEN the user taps the Replay button
- THEN the map shows all points (faded) with an animated marker along the path

#### Scenario: Playback controls
- GIVEN replay mode is active
- THEN the user can play, pause, scrub with a time slider, and select speed (1x, 2x, 5x, 10x)

#### Scenario: Replay stats panel
- GIVEN replay mode is active
- WHEN the marker reaches a point
- THEN the current point's RAT, PCI, RSRP, RSRQ, SINR, ping, and packet loss are shown in a stats panel
- AND for `5G_NSA` records, the LTE anchor's band, PCI, and RSRP are also displayed

#### Scenario: Timeline chart in replay
- GIVEN replay mode is active
- THEN RSRP and latency curves are displayed over time
- AND a vertical cursor is synced to the replay position

### Requirement: Session Replay — Speedtest Markers

The system SHALL display speedtest markers on the replay timeline when speedtest records exist for the session. Speedtest data semantics are defined in `speedtest/spec.md`.

#### Scenario: Speedtest markers on timeline
- GIVEN a session with speedtest records and replay mode is active
- WHEN the replay timeline is displayed
- THEN colored markers are shown on the RAT timeline at positions corresponding to the nearest cell record timestamps
- AND markers are color-coded by download speed (red for slow, yellow for moderate, green for fast)

#### Scenario: Speedtest detail card
- GIVEN the replay timeline with speedtest markers
- WHEN a user taps a marker
- THEN a detail card is shown below the timeline
- AND the card displays: timestamp, download speed, upload speed, server name/ID, RSRP at test, RAT at test, and band at test

#### Scenario: Speedtest card auto-updates during playback
- GIVEN replay mode is active and the user is scrubbing or playing
- WHEN the current position passes a speedtest marker
- THEN the speedtest detail card updates to show the last speedtest result at or before the current position
- AND the card continues showing that result until the next marker is passed
- AND the card shows "Position" label for auto-updated markers and "Selected" label for manually tapped markers

### Requirement: Indoor Session Detail

The system SHALL display an indoor session's detail view with a 2D path canvas instead of a map, preserving all non-geographic functionality from the outdoor detail view. Indoor positioning is defined in `indoor/spec.md`.

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

The system SHALL replay an indoor session's recorded path with animated playback on a 2D canvas, preserving the same time-based controls and panels as outdoor replay. Indoor positioning is defined in `indoor/spec.md`.

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

### Requirement: Session Markers

The system SHALL store markers as first-class entities linked to a session.

#### Scenario: Markers linked to session
- GIVEN a session with markers
- THEN the markers are retrieved via `sessionMarkerRepository.getMarkersForSession(sessionId)`
- AND each marker contains a `sessionId`, `timestamp`, `seq`, `type`, and optional `label`

#### Scenario: Marker CRUD
- GIVEN a session with markers
- WHEN the user creates, edits, or deletes a marker
- THEN the change is persisted to the `session_markers` table
- AND the markers are immediately reflected in the Session Detail and Replay screens

#### Scenario: Marker export
- GIVEN a session with markers
- WHEN the user exports the session
- THEN markers are exported as a separate CSV and embedded in GeoJSON
- AND the marker export is only generated when at least one marker exists

#### Scenario: Marker import
- GIVEN a CSV or GeoJSON file containing markers
- WHEN the user imports the file
- THEN the markers are linked to the newly created session
- AND the session recording mode is set to "TUNNEL" when markers are present or tunnel metadata is set