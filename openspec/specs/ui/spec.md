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

#### Scenario: Point tooltip
- GIVEN the recording screen map
- WHEN the user taps a point marker
- THEN a tooltip with all point attributes is displayed

### Requirement: Settings Screen

The system SHALL provide a settings screen for configuring recording and analytics parameters.

#### Scenario: Settings sections
- GIVEN the Settings screen
- THEN the following sections are displayed: Ping, Recording, Cell ID, GPS Loss Fallback, Analytics Thresholds

### Requirement: Global Statistics Screen

The system SHALL display aggregate statistics across all sessions.

#### Scenario: Statistics displayed
- GIVEN the Statistics tab is selected
- THEN summary cards show total sessions, total points, total duration, and on-network percentage
- AND RAT distribution per SIM is shown as stacked horizontal bars
- AND band distribution per SIM is shown as stacked bars