## ADDED Requirements

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
- AND the warning element exposes a "No steps" content description for accessibility

#### Scenario: Sensor health warning hidden
- GIVEN a sensor health warning is visible
- WHEN step events are received
- THEN the warning is dismissed

## MODIFIED Requirements

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