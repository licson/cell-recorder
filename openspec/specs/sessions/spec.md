# Sessions Specification

## Purpose

Defines how the system manages recording sessions: listing, viewing details, replaying, and deleting sessions.

## Requirements

### Requirement: Session List

The system SHALL display past sessions sorted by creation date descending.

#### Scenario: Session list displayed
- GIVEN the user navigates to the Sessions tab
- THEN a list of past sessions is shown
- AND each entry displays the session name, date, and point count
- AND sessions are sorted by date descending

### Requirement: Session Creation

The system SHALL allow the user to create a new session from the session list.

#### Scenario: Create new session
- GIVEN the Session List screen
- WHEN the user taps the FAB
- THEN a dialog prompts for a session name
- AND a new session is created upon confirmation

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

The system SHALL display a session detail view with a map and a scrollable data table.

#### Scenario: Session detail loaded
- GIVEN the user taps a session in the list
- THEN a map is displayed with all recorded points (RAT-colored) and a path polyline
- AND a scrollable data table shows all records grouped by timestamp

#### Scenario: Point selection on map
- GIVEN the session detail view
- WHEN the user taps a record in the data table
- THEN the corresponding point is highlighted on the map

### Requirement: Session Detail — Analytics Mode

The system SHALL display an analytics panel alongside the map.

#### Scenario: Analytics toggle
- GIVEN the session detail view in data mode
- WHEN the user toggles analytics mode
- THEN the map expands
- AND a full analytics panel is displayed below

### Requirement: Session Replay

The system SHALL replay a session's recorded path with animated playback. For `5G_NSA` records, the replay stats panel SHALL display both the NR cell data and the LTE anchor cell data.

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
- AND a vertical cursor is synced to the replay position# Sessions Specification (Delta)

## ADDED Requirements

### Requirement: Session Replay — Speedtest Markers

The system SHALL display speedtest markers on the replay timeline when speedtest records exist for the session.

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