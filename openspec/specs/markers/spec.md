# Markers Specification

## Purpose

User-stamped session markers as a first-class, mode-agnostic concept. Markers capture a moment on the session timeline with a type (waypoint, segment boundary, stop, note) and an optional label. Markers are temporal-only in v1 — they capture a moment, not a geographic position. They are creatable on any recording mode (OUTDOOR, INDOOR, TUNNEL).

## Requirements

## ADDED Requirements

### Requirement: Session Marker Entity

The system SHALL persist user-stamped markers along a session's timeline in a dedicated `session_markers` table, separate from `cell_records`. Each marker SHALL capture: a wall-clock `timestamp`, a per-session sequence number `seq`, a `type` from a fixed enum, and an optional free-text `label`. The schema SHALL be mode-agnostic: markers MAY be created on any recording mode (OUTDOOR, INDOOR, TUNNEL). Markers are temporal-only in v1 — they capture a moment on the timeline, not a geographic position.

#### Scenario: Session marker table schema

- GIVEN the database is on the current schema version
- THEN a `session_markers` table exists with columns: `id` (PK autoincrement), `sessionId` (FK to `sessions.id` with `ON DELETE CASCADE`), `timestamp` (INTEGER not null), `seq` (INTEGER not null), `type` (TEXT not null), `label` (TEXT, nullable)
- AND an index exists on `(sessionId, timestamp)` for replay and detail queries
- AND a foreign key links `sessionId` to `sessions.id` with `ON DELETE CASCADE` so markers are deleted alongside their parent session
- AND no `latitude`/`longitude`/`relativeX`/`relativeY` columns exist on this table (markers are temporal-only)

#### Scenario: Marker type enum

- GIVEN a marker is created
- THEN its `type` SHALL be one of: `WAYPOINT`, `SEGMENT_START`, `SEGMENT_END`, `STOP`, `NOTE`
- AND any other value SHALL be rejected at insert time by the repository layer
- AND `WAYPOINT` covers generic "point of interest" use (a station, a junction, a landmark)
- AND `SEGMENT_START`/`SEGMENT_END` cover boundary events (e.g., tunnel entry/exit, route segment endpoints)
- AND `STOP` covers a pause in movement (e.g., a signal stop, a station stop)
- AND `NOTE` is the free-form catch-all when nothing more specific applies
- AND `NOTE` is the default type for quick-tap and notification-action creation paths

#### Scenario: Per-session sequence counter

- GIVEN a session with N existing markers
- WHEN a new marker is created for that session
- THEN the new marker's `seq` is N+1 (1-indexed, monotonically increasing within the session)
- AND the counter is scoped per session (a second session's first marker has `seq = 1`)

#### Scenario: Marker cascade delete with session

- GIVEN a session with markers
- WHEN the session is deleted
- THEN all markers with that `sessionId` are deleted by the foreign-key `ON DELETE CASCADE` rule
- (Session deletion: `sessions/spec.md`)

### Requirement: Marker Creation UX

The system SHALL provide a "Mark" button on the RecordingScreen that is visible whenever recording is active, regardless of recording mode (OUTDOOR, INDOOR, or TUNNEL). The button SHALL support two interaction paths: a quick-tap path that records a marker with no dialog, and a long-press path that opens a small dialog with type chips and an editable label. Additionally, the system SHALL provide a "Mark Note" action button on the foreground service notification for every recording mode, allowing the user to drop a `NOTE` marker directly from the lock screen. The marker dialog SHALL be type-aware: the label field is hidden for `SEGMENT_START` and `SEGMENT_END` (the type is the entire semantic content), and shown for `WAYPOINT`, `STOP`, and `NOTE`. The dialog SHALL also offer recent-label suggestions (see "Recent Label Suggestions" requirement below). Screen rendering details are defined in `ui/spec.md`; notification details in `service/spec.md`.

#### Scenario: Quick-tap creates a NOTE marker

- GIVEN an active recording (any mode)
- WHEN the user taps the Mark button
- THEN a marker is created with `type = "NOTE"`
- AND the marker's `label` is auto-generated as `"NOTE #<seq> HH:MM:SS"` (type-prefixed, 24-hour wall-clock time at insert)
- AND no dialog is shown
- AND the marker is persisted immediately via `SessionMarkerRepository.insert(...)`
- AND a brief visual confirmation is shown (e.g., a snackbar: "Marked #N")

#### Scenario: Notification action creates a NOTE marker

- GIVEN an active recording (any mode)
- WHEN the user taps the "Mark Note" action on the foreground notification
- THEN a marker is created with `type = "NOTE"`
- AND the marker's `label` is auto-generated as `"NOTE #<seq> HH:MM:SS"`
- AND the marker is persisted immediately via `SessionMarkerRepository.insert(...)`
- AND the user is not required to open the app or unlock the screen

#### Scenario: Long-press opens the type-aware marker dialog

- GIVEN an active recording (any mode)
- WHEN the user long-presses the Mark button
- THEN a small dialog opens with the title "New Marker"
- AND the dialog displays a row of type chips (`WAYPOINT`, `SEGMENT_START`, `SEGMENT_END`, `STOP`, `NOTE`) with `NOTE` selected by default
- AND when the selected type is `WAYPOINT`, `STOP`, or `NOTE`, an optional single-line text field for the label is displayed and auto-focused (the user opened the dialog to type)
- AND when the selected type is `SEGMENT_START` or `SEGMENT_END`, the label field is NOT displayed (the type is the entire semantic content)
- AND a "Save" button confirms the marker with the selected type and label (or NULL if the field is blank or the field is hidden)
- (Recent-label suggestions: "Recent Label Suggestions" requirement below)

#### Scenario: Mark button visible whenever recording is active

- GIVEN the RecordingScreen
- WHEN the recording mode is `OUTDOOR`, `INDOOR`, or `TUNNEL`
- AND the recording is active
- THEN the Mark button IS displayed alongside the Start/Stop button in the action row
- WHEN the recording is not active
- THEN the Mark button is NOT displayed
- (Recording screen layout: `ui/spec.md`)

#### Scenario: Marker creation serialized with recording tick

- GIVEN an active recording (any mode)
- WHEN a marker insert and a recording tick race
- THEN both writes are serialized through the recording mutex
- AND the marker's `timestamp` reflects the wall-clock at lock acquisition
- AND the cell record's `timestamp` reflects the wall-clock at sample time
- (Concurrency rules: `thread-safety/spec.md`)

### Requirement: Marker Editing

The system SHALL allow the user to edit any marker's `type` and `label` after recording. Editing updates the existing row in place; `id`, `sessionId`, `timestamp`, and `seq` are immutable. The same `MarkerDialog` used for creation is reused for editing, with a title of "Edit Marker" and an additional "Delete" button shown in edit mode.

#### Scenario: Edit marker from replay detail card

- GIVEN a marker pin is displayed on the replay timeline
- WHEN the user taps the pin
- THEN a detail card opens showing the marker's `seq`, `type`, `label`, and timestamp
- AND the detail card offers an "Edit" action that opens the `MarkerDialog` in edit mode, pre-populated with the current `type` and `label`
- AND the "Save" button updates the existing row with the new `type` and `label` (or NULL if the label field is blank or hidden for the selected type)
- AND the `id`, `sessionId`, `timestamp`, and `seq` are unchanged
- AND the timeline pin re-renders with the updated `type` and `label`

#### Scenario: Edit marker from session detail markers section

- GIVEN the markers section is expanded on session detail
- WHEN the user taps the edit affordance on a marker row
- THEN the `MarkerDialog` opens in edit mode, pre-populated with the current `type` and `label`
- AND saving updates the existing row in place
- AND the markers section re-renders with the updated `type` and `label`
- AND the `seq` value of the edited marker is unchanged (no renumbering)

#### Scenario: Editing a label updates recent-label suggestions

- GIVEN the user saves an edit that changes a marker's `label` to a non-null, non-empty value
- WHEN the repository persists the update
- THEN the `(type, label)` pair is upserted into the `recent_marker_labels` table (incrementing `useCount` and updating `lastUsed` if it already exists, or inserting a new row with `useCount = 1`)
- (Recent-labels schema: "Recent Label Suggestions" requirement below)
- AND the previous label (if any) is NOT decremented in the recents table

#### Scenario: Editing preserves timeline ordering

- GIVEN a marker is edited
- THEN its `timestamp` and `seq` are unchanged
- AND the marker's position on the replay timeline and in the session detail markers section does not change
- AND no other markers' `seq` values change

### Requirement: Recent Label Suggestions

The system SHALL persist recently-used marker labels globally across sessions in a dedicated `recent_marker_labels` table and surface the top-N most-recently-used labels for the selected type as chips above the label field in the `MarkerDialog`. The recents table is app-local user state; it is NOT included in session CSV or GeoJSON exports. This feature caters for both repeat users (who benefit from one-tap chips for labels they've used before) and one-shot users (who see an unobtrusive empty chip row on their first session).

#### Scenario: Recent marker labels table schema

- GIVEN the database is on the current schema version
- THEN a `recent_marker_labels` table exists with columns: `type` (TEXT not null), `label` (TEXT not null), `useCount` (INTEGER not null, default 1), `lastUsed` (INTEGER not null, wall clock)
- AND the primary key is `(type, label)`
- AND no foreign key is declared (the table is app-local user state, not tied to any session)

#### Scenario: Label inserted into recents on marker creation

- GIVEN a marker is created with a non-null, non-empty `label`
- WHEN the repository persists the marker
- THEN the `(type, label)` pair is upserted into `recent_marker_labels`
- AND if the pair already exists, `useCount` is incremented and `lastUsed` is updated to the current wall clock
- AND if the pair does not exist, a new row is inserted with `useCount = 1` and `lastUsed` = now

#### Scenario: Label not inserted into recents when marker has no label

- GIVEN a marker is created with `label = null` or `label = ""` (blank)
- WHEN the repository persists the marker
- THEN no row is upserted into `recent_marker_labels`

#### Scenario: Label chips displayed in marker dialog

- GIVEN the `MarkerDialog` is open with a type selected that shows the label field (`WAYPOINT`, `STOP`, or `NOTE` per "Marker Creation UX" requirement above)
- AND the `recent_marker_labels` table has one or more rows for that type
- THEN a `FlowRow` of chips is rendered above the label field
- AND the chips are ordered by `lastUsed` descending
- AND the chips are capped at the top 20 most-recently-used labels for the selected type
- AND each chip displays the `label` text
- AND when the table has zero rows for the selected type, no chips are rendered (the row is unobtrusive)

#### Scenario: Chip tap fills the label field

- GIVEN the `MarkerDialog` is open with label chips displayed
- WHEN the user taps a chip
- THEN the label field is filled with the chip's `label` text
- AND the dialog stays open (the user can tweak the label before saving)
- AND the user must tap "Save" to confirm

#### Scenario: Type change refreshes label chips

- GIVEN the `MarkerDialog` is open
- WHEN the user selects a different type that shows the label field
- THEN the label chips re-render with the recents for the newly-selected type
- AND if the previously-selected type's label is no longer relevant, the field is cleared (or kept — defer to implementation; the recommendation is to clear when switching between boundary and non-boundary types, and keep when switching between two non-boundary types)

#### Scenario: Recents are global across sessions

- GIVEN a marker was created with `label = "King's Cross"` in session A
- WHEN the user opens the `MarkerDialog` for a new marker in session B with the same `type`
- THEN the "King's Cross" chip is displayed as a recent label
- (The recents table is not session-scoped)

#### Scenario: Recents are not exported with sessions

- GIVEN a session is exported to CSV or GeoJSON
- THEN the `recent_marker_labels` table is NOT included in the export
- AND the export contains only session-scoped data (cell records, markers, speedtest records)

### Requirement: Marker Visibility in Replay

The system SHALL display session markers as vertical pins on the replay timeline for any session that has markers, regardless of recording mode. Pins are tappable to open a detail card with edit and delete actions. Replay rendering is defined in `ui/spec.md` and `sessions/spec.md`.

#### Scenario: Markers rendered as timeline pins

- GIVEN a session with markers and replay mode is active
- WHEN the replay timeline is displayed
- THEN a vertical pin is drawn at each marker's `timestamp`
- AND each pin is tappable
- (Replay timeline rendering: `ui/spec.md`, `sessions/spec.md`)

#### Scenario: Marker detail on tap

- GIVEN a replay timeline with marker pins
- WHEN the user taps a marker pin
- THEN a small detail card is shown with the marker's `seq`, `type`, `label`, and timestamp
- AND the card offers an "Edit" action that opens the `MarkerDialog` in edit mode (per "Marker Editing" requirement above)
- AND the card offers a "Delete" action (per "Marker Deletion" requirement below)

### Requirement: Marker Visibility in Session Detail

The system SHALL display a collapsible "Markers (N)" section on the Session Detail screen for any session that has markers, regardless of recording mode. Session detail rendering is defined in `sessions/spec.md` and `ui/spec.md`.

#### Scenario: Markers section collapsed by default

- GIVEN a session with N markers (N > 0)
- WHEN the session detail screen is loaded
- THEN a "Markers (N)" section header is displayed above the cell-records table
- AND the section is collapsed by default
- AND the header is tap-to-expand

#### Scenario: Markers section expanded

- GIVEN the "Markers (N)" section is collapsed
- WHEN the user taps the header
- THEN the section expands to show one row per marker
- AND each row displays `#<seq>`, `type` (with chip-style coloring), `label` (or "—" if NULL), and timestamp
- AND each row offers an edit affordance (opens the `MarkerDialog` in edit mode per "Marker Editing" requirement above)
- AND each row offers a delete affordance

#### Scenario: Empty markers section hidden

- GIVEN a session with zero markers (any mode)
- WHEN the session detail screen is loaded
- THEN the "Markers (N)" section is NOT displayed

### Requirement: Marker Deletion

The system SHALL allow the user to delete a marker from either the replay timeline detail card or the session detail markers section. Deletion SHALL be a hard delete (no undo, no soft-delete).

#### Scenario: Delete marker from session detail

- GIVEN the markers section is expanded on session detail
- WHEN the user taps the delete affordance on a marker row
- THEN the marker is removed from the database
- AND the markers section re-renders with the remaining markers
- AND the `seq` values of remaining markers are NOT renumbered (the original gaps are preserved)

#### Scenario: Delete marker from replay

- GIVEN a marker pin is displayed on the replay timeline
- WHEN the user taps the pin and then the delete action in the detail card
- THEN the marker is removed from the database
- AND the replay timeline re-renders without the deleted pin

#### Scenario: Deletion preserves session integrity

- GIVEN a marker is deleted
- THEN the session's `cell_records` rows are unaffected
- AND no other markers' `seq` values change

### Requirement: Marker Export and Import

The system SHALL export and import markers alongside cell records for any session that has markers, regardless of recording mode. Markers are emitted as a companion `markers_<session>.csv` file and as `Point` features in the existing GeoJSON FeatureCollection. The `recent_marker_labels` table is NOT included in any session export. Export formats are defined in `data/spec.md`.

#### Scenario: Markers CSV export

- GIVEN a session with markers (any mode)
- WHEN the user exports the session to CSV
- THEN a `markers_<session>.csv` file is generated alongside the cell-records CSV
- AND the markers CSV contains columns: `timestamp,seq,type,label`
- AND one row is emitted per marker, sorted by `seq` ascending

#### Scenario: Markers GeoJSON export

- GIVEN a session with markers (any mode)
- WHEN the user exports the session to GeoJSON
- THEN each marker is emitted as a `Point` feature in the FeatureCollection
- AND the feature's geometry coordinates are `[0, 0]` (markers are temporal, not spatial, in v1)
- AND the feature's properties include `markerType`, `label`, and `seq`

#### Scenario: Markers CSV import restores markers

- GIVEN the import dialog is open
- WHEN the user selects a `markers_*.csv` companion file alongside a cell-records CSV
- THEN markers are parsed and persisted to the `session_markers` table
- AND the original `seq` values from the file are preserved

#### Scenario: Markers GeoJSON import restores markers

- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file with any features carrying a `markerType` property
- THEN those features are parsed as markers and persisted to the `session_markers` table
- AND the original `seq` values from the file are preserved (or assigned sequentially if absent)

#### Scenario: Empty markers file omitted on export

- GIVEN a session with zero markers (any mode)
- WHEN the user exports the session
- THEN no `markers_<session>.csv` file is emitted
- AND the cell-records CSV is emitted as usual

#### Scenario: Session with markers round-trips losslessly

- GIVEN a session with cell records and markers (any mode)
- WHEN the session is exported and re-imported
- THEN the re-imported session has the same number of cell records
- AND the re-imported session has the same number of markers
- AND each marker's `type`, `label`, and `seq` are preserved
