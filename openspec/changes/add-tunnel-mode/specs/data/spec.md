## ADDED Requirements

### Requirement: Session Marker Entity

The system SHALL store user-stamped markers in a separate Room entity, `SessionMarkerEntity`, not mixed with cell records. The schema is defined in `tunnel/spec.md`.

#### Scenario: SessionMarkerEntity structure

- GIVEN a marker is created
- WHEN the marker is persisted
- THEN a `SessionMarkerEntity` is created with: id, sessionId, timestamp, seq, type, label
- AND the entity is stored in the `session_markers` table
- AND a foreign key links `sessionId` to `sessions.id` with `ON DELETE CASCADE`
- AND an index on `(sessionId, timestamp)` supports replay and detail queries
- (Marker schema and enum: `tunnel/spec.md`)

### Requirement: Recent Marker Labels Entity

The system SHALL store recently-used marker labels in a separate app-local Room entity, `RecentMarkerLabelEntity`, not tied to any session. The table is global user state used for label suggestions in the `MarkerDialog`. The schema is defined in `tunnel/spec.md` under the "Recent Label Suggestions" requirement.

#### Scenario: RecentMarkerLabelEntity structure

- GIVEN the database is on the current schema version
- THEN a `recent_marker_labels` table exists with columns: `type` (TEXT NOT NULL), `label` (TEXT NOT NULL), `useCount` (INTEGER NOT NULL), `lastUsed` (INTEGER NOT NULL)
- AND the primary key is `(type, label)`
- AND no foreign key is declared (the table is app-local user state, not tied to any session)
- AND the entity is stored in the `recent_marker_labels` table

#### Scenario: Recent labels are not exported with sessions

- GIVEN a session is exported to CSV or GeoJSON
- THEN the `recent_marker_labels` table is NOT included in the export
- AND the export contains only session-scoped data (cell records, markers, speedtest records)

#### Scenario: Recent labels survive session deletion

- GIVEN a session is deleted
- THEN any labels that were previously stored in `recent_marker_labels` for that session's markers remain in the table (the table is not session-scoped and does not cascade with session deletion)

### Requirement: Markers CSV Export

The system SHALL include markers in session export as a separate `markers_<session>.csv` file when the session has any markers. This follows the same multi-file export pattern as speedtest records. Tunnel mode behavior is defined in `tunnel/spec.md`.

#### Scenario: Markers CSV export

- GIVEN a session with markers (typically a tunnel session, but the schema is generic)
- WHEN the user exports the session to CSV
- THEN a `markers_<session>.csv` file is generated alongside the cell-records CSV
- AND the markers CSV contains columns: `timestamp,seq,type,label`
- AND one row is emitted per marker, sorted by `seq` ascending

#### Scenario: No markers file when session has no markers

- GIVEN a session with zero markers
- WHEN the user exports the session
- THEN no `markers_<session>.csv` file is emitted
- AND the cell-records CSV is emitted as usual

### Requirement: Markers GeoJSON Export

The system SHALL export markers as `Point` features in the GeoJSON FeatureCollection and add a session-level `"tunnelMode": true` flag for tunnel sessions. This mirrors the existing `"indoorMode": true` convention. Tunnel mode behavior is defined in `tunnel/spec.md`.

#### Scenario: Tunnel session GeoJSON export

- GIVEN a tunnel session with recorded points and markers
- WHEN the user selects GeoJSON export from the session menu
- THEN each cell record Feature's geometry coordinates are sentinel `[0, 0]` (since tunnel records have `latitude = 0, longitude = 0`)
- AND each marker is emitted as a separate `Point` Feature with geometry coordinates `[0, 0]` (markers are temporal, not spatial)
- AND each marker Feature's properties include `markerType`, `label`, and `seq`
- AND the FeatureCollection includes a `"tunnelMode": true` session-level property
- AND the FeatureCollection does NOT include `"indoorMode": true`

#### Scenario: Outdoor GeoJSON unchanged

- GIVEN an outdoor session with recorded points
- WHEN the user selects GeoJSON export from the session menu
- THEN the GeoJSON follows the existing format with real geographic coordinates
- AND no `"tunnelMode"` property is included
- (Indoor GeoJSON format: existing `data/spec.md` requirement)

### Requirement: Markers CSV Import

The system SHALL import markers from a `markers_*.csv` companion file alongside the cell-records CSV. The parser SHALL restore markers with their original `seq` values. Tunnel mode behavior is defined in `tunnel/spec.md`.

#### Scenario: Import tunnel CSV with markers companion

- GIVEN the import dialog is open
- WHEN the user selects a CSV file that includes a `markers_*.csv` companion
- THEN markers are parsed and stored on the `session_markers` table
- AND the session `recordingMode` is set to `"TUNNEL"`
- AND the original `seq` values from the file are preserved
- AND malformed lines in the markers file are skipped (matching the existing cell-records CSV behavior)

#### Scenario: Import CSV without markers companion unchanged

- GIVEN the import dialog is open
- WHEN the user selects a CSV file without a markers companion
- THEN the import proceeds as before
- AND the session `recordingMode` is set per the existing detection rules (`"INDOOR"` if `relativeX`/`relativeY` are present, else `"OUTDOOR"`)

### Requirement: Markers GeoJSON Import

The system SHALL import markers from a GeoJSON FeatureCollection. The parser SHALL recognize features with a `markerType` property as markers. Tunnel mode behavior is defined in `tunnel/spec.md`.

#### Scenario: Import tunnel GeoJSON with markers

- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file with `"tunnelMode": true`
- THEN any features with a `markerType` property are parsed as markers and stored on the `session_markers` table
- AND the session `recordingMode` is set to `"TUNNEL"`
- AND the original `seq` values from the file are preserved (or assigned sequentially on insert if absent)

#### Scenario: Import GeoJSON without markers unchanged

- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file without `"tunnelMode": true` and without any `markerType` features
- THEN the import proceeds as before
- AND the session `recordingMode` is set per the existing detection rules (`"INDOOR"` if `"indoorMode": true`, else `"OUTDOOR"`)
