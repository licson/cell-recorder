# User Interface Specification (Delta)

## MODIFIED Requirements

### Requirement: Recording Screen

The system SHALL provide a screen for controlling and monitoring an active recording.

#### Scenario: Recording screen layout
- GIVEN a session has been created
- WHEN the user navigates to recording
- THEN the screen displays a top bar with session name, elapsed timer, and point counter
- AND an OSM map is shown
- AND a Start/Stop button is centered at the bottom
- AND a live stats panel shows per-SIM cell data

#### Scenario: Map markers and path
- GIVEN an active recording
- THEN recorded points are shown as RAT-colored markers on the map
- AND a path polyline connects the markers

#### Scenario: GPS status indicator
- GIVEN an active recording
- THEN a GPS status indicator is shown with one of: "OK", "Searching...", or "EXTRAPOLATING"
- AND the current GPS accuracy is displayed

#### Scenario: Speedtest status in live stats
- GIVEN an active recording with speedtest enabled
- THEN the live stats bar displays speedtest status in the format: idle (`Speed: ---`), discovering (`Speed: Selecting server...`), downloading (`Speed: Testing ↓...`), uploading (`Speed: Testing ↑...`), completed (`Speed: ↓156 ↑42 Mbps`), failed (`Speed: Failed`), or skipped on WiFi (`Speed: (WiFi)`)

## ADDED Requirements

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