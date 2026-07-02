## ADDED Requirements

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

The system SHALL provide a tunnel recording screen layout adapted from the outdoor recording screen. Tunnel mode does not use a map, an indoor path canvas, or a tracking confidence indicator. Recording lifecycle is defined in `recording/spec.md`; tunnel mode behavior in `tunnel/spec.md`.

#### Scenario: Tunnel recording screen layout

- GIVEN a tunnel recording session has been started
- WHEN the user navigates to the recording screen
- THEN the screen displays a top bar with session name, elapsed timer, and point count
- AND a placeholder area is shown where the outdoor map or indoor canvas would be (a "Tunnel recording in progress" panel with the elapsed time and marker count, or an empty area)
- AND a Start/Stop button is centered at the bottom
- AND a Mark button is displayed in the action row (only when recording is active)
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

The system SHALL provide a "Mark" button on the RecordingScreen action row, visible only when `isTunnel && isRecording`. The button supports quick-tap (one-tap NOTE marker with auto-label) and long-press (opens the type-aware `MarkerDialog` with type chips, conditional label field, and recent-label chips). Mark creation semantics are defined in `tunnel/spec.md`.

#### Scenario: Mark button visible only in active tunnel recording

- GIVEN the RecordingScreen
- WHEN the recording mode is `OUTDOOR` or `INDOOR`
- THEN the Mark button is NOT displayed
- WHEN the recording mode is `TUNNEL` AND the recording is active
- THEN the Mark button IS displayed alongside the Start/Stop button in the action row

#### Scenario: Mark button quick-tap feedback

- GIVEN an active tunnel recording
- WHEN the user taps the Mark button
- THEN a marker is created immediately with `type = "NOTE"` and an auto-generated label `"NOTE #<seq> HH:MM:SS"` (type-prefixed per `tunnel/spec.md`)
- AND a brief visual confirmation is shown (e.g., a snackbar or transient toast: "Marked #N")
- AND no dialog is shown
- (Marker creation semantics: `tunnel/spec.md`)

#### Scenario: Mark button long-press opens type-aware dialog

- GIVEN an active tunnel recording
- WHEN the user long-presses the Mark button
- THEN a small dialog opens titled "New Marker"
- AND the dialog displays a row of type chips (`STATION`, `TUNNEL_ENTRY`, `TUNNEL_EXIT`, `STOP`, `NOTE`) with `NOTE` selected by default
- AND when the selected type is `STATION`, `STOP`, or `NOTE`, an optional single-line `OutlinedTextField` for the label is displayed and auto-focused (the user opened the dialog to type)
- AND when the selected type is `TUNNEL_ENTRY` or `TUNNEL_EXIT`, the label field is NOT displayed (the type is the entire semantic content)
- AND a "Save" button confirms the marker with the selected type and label (or NULL if the field is blank or hidden)
- (Recent-label chips: scenario below; Marker creation semantics: `tunnel/spec.md`)

#### Scenario: Recent-label chips displayed in the marker dialog

- GIVEN the `MarkerDialog` is open with a type selected that shows the label field (`STATION`, `STOP`, or `NOTE`)
- AND the `recent_marker_labels` table has one or more rows for that type
- THEN a `FlowRow` of chips is rendered above the label field
- AND the chips are ordered by `lastUsed` descending and capped at the top 20
- AND when the table has zero rows for the selected type, no chips are rendered (the row is unobtrusive)
- (Recent-labels semantics: `tunnel/spec.md`)

#### Scenario: Tapping a recent-label chip fills the field

- GIVEN the `MarkerDialog` is open with label chips displayed
- WHEN the user taps a chip
- THEN the label field is filled with the chip's `label` text
- AND the dialog stays open (the user can tweak the label before saving)
- AND the user must tap "Save" to confirm
- (Recent-labels semantics: `tunnel/spec.md`)

### Requirement: Marker Pins on Replay Timeline

The system SHALL display session markers as vertical pins on the replay timeline at the position corresponding to each marker's `timestamp`. Each pin is tappable to open a detail card with edit and delete actions. Replay rendering is defined in `sessions/spec.md`.

#### Scenario: Marker pins rendered on tunnel session replay

- GIVEN a tunnel session with markers and replay mode is active
- WHEN the replay timeline is displayed
- THEN a vertical pin is drawn at each marker's `timestamp` position on the timeline
- AND pins are visually distinct from speedtest markers (different color or shape)
- AND each pin is tappable to open a detail card

#### Scenario: Marker detail card with edit and delete actions

- GIVEN a replay timeline with marker pins
- WHEN the user taps a marker pin
- THEN a small detail card is shown with the marker's `seq`, `type`, `label`, and timestamp
- AND the card offers an "Edit" action that opens the `MarkerDialog` in edit mode (title "Edit Marker", pre-populated `type` and `label`, an additional "Delete" button)
- AND the card offers a "Delete" action that removes the marker immediately (per `tunnel/spec.md`)
- AND after either action completes, the timeline re-renders

### Requirement: Markers Section on Session Detail Screen

The system SHALL display a collapsible "Markers (N)" section on the Session Detail screen for sessions that have any markers. Each expanded row offers edit and delete affordances. Session detail rendering is defined in `sessions/spec.md`.

#### Scenario: Markers section header collapsed by default

- GIVEN a session with N markers (N > 0)
- WHEN the session detail screen is loaded
- THEN a "Markers (N)" section header is displayed above the cell-records table
- AND the section is collapsed by default
- AND the header is tap-to-expand

#### Scenario: Markers section expanded rows with edit and delete affordances

- GIVEN the "Markers (N)" section is collapsed
- WHEN the user taps the header
- THEN the section expands to show one row per marker
- AND each row displays `#<seq>`, the `type` with chip-style coloring (e.g., STATION = blue, TUNNEL_ENTRY = green, TUNNEL_EXIT = red, STOP = orange, NOTE = grey), the `label` (or "—" if NULL), and the timestamp
- AND each row offers an edit affordance (e.g., a pencil icon button) that opens the `MarkerDialog` in edit mode (per `tunnel/spec.md`)
- AND each row offers a delete affordance (e.g., a trash icon button) that removes the marker immediately (per `tunnel/spec.md`)
- AND after either action completes, the markers section re-renders with the updated row(s)

#### Scenario: Empty markers section hidden

- GIVEN a session with zero markers (any mode)
- WHEN the session detail screen is loaded
- THEN the "Markers (N)" section is NOT displayed
