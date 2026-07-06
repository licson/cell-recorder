# Data Import and Export Specification

## Purpose

Defines the formats and behavior for exporting and importing session data via CSV and GeoJSON files.

## Scope

This spec covers data persistence formats, export/import, and schema evolution. It does not define:
- Cell identity processing (see `cell-info/spec.md`).
- Session management (see `sessions/spec.md`).
- Analytics computation (see `analytics/spec.md`).
- Speedtest protocol (see `speedtest/spec.md`).
- Indoor positioning (see `indoor/spec.md`).
- UI dialogs (see `ui/spec.md`).

## Related Specs

- `cell-info/spec.md` — anchor cell and CA band semantics in exported data.
- `sessions/spec.md` — when export/import is triggered from the UI.
- `recording/spec.md` — what data is produced during recording.
- `speedtest/spec.md` — speedtest data export format.
- `indoor/spec.md` — indoor coordinate handling in export/import.
- `analytics/spec.md` — database indices used by analytics queries.
- `instrumented-test-coverage/spec.md` — DAO and migration test requirements.

## Requirements

### Requirement: CSV Export

The system SHALL allow exporting a session's data as a CSV file. For `5G_NSA` records, the CSV SHALL include anchor cell columns. Anchor cell semantics are defined in `cell-info/spec.md`. The CSV export SHALL include the primary cell's `bandwidthKhz` as the `bandwidth` column.

#### Scenario: Export to CSV
- GIVEN a session with recorded points
- WHEN the user selects CSV export from the session menu
- THEN the system opens a document save dialog
- AND the generated CSV includes columns for timestamp, coordinates, signal metrics, cell identity, primary bandwidth, carrier aggregation bands, and anchor cell fields for NSA records
- AND the `ca_bands` column contains a JSON array string
- AND anchor columns are prefixed with `anchor_` (e.g., `anchor_pci`, `anchor_rsrp`)

### Requirement: GeoJSON Export

The system SHALL allow exporting a session's data as a GeoJSON FeatureCollection. For `5G_NSA` records, the Feature properties SHALL include anchor cell fields. Anchor cell semantics are defined in `cell-info/spec.md`. The GeoJSON export SHALL include the primary cell's `bandwidthKhz` as the `bandwidth` property.

#### Scenario: Export to GeoJSON
- GIVEN a session with recorded points
- WHEN the user selects GeoJSON export from the session menu
- THEN the system opens a document save dialog
- AND the generated GeoJSON follows the FeatureCollection schema with one Feature per recorded point
- AND anchor properties are prefixed with `anchor_` for NSA records

#### Scenario: GeoJSON feature properties
- GIVEN a GeoJSON export
- WHEN the file is generated
- THEN each Feature's geometry contains `[lon, lat, alt]` coordinates
- AND each Feature's properties include all cell, bandwidth, and signal attributes
- AND for `5G_NSA` records, anchor cell properties are included

### Requirement: Marker CSV Export

The system SHALL allow exporting a session's markers as a separate CSV file.

#### Scenario: Export markers CSV
- GIVEN a session with one or more markers
- WHEN the user exports the session
- THEN a separate `session_name_markers.csv` file is generated alongside the cell record export
- AND the marker CSV includes columns: `timestamp`, `seq`, `type`, `label`
- AND each row represents one marker
- AND labels containing commas or quotes are escaped according to the same CSV rules as the cell record export

#### Scenario: No marker export when empty
- GIVEN a session without markers
- WHEN the user exports the session
- THEN no marker CSV file is generated

### Requirement: Marker GeoJSON Export

The system SHALL include marker features in the GeoJSON export.

#### Scenario: GeoJSON marker features
- GIVEN a session with markers
- WHEN the user exports the session as GeoJSON
- THEN each marker is emitted as a separate `Point` Feature
- AND each marker Feature's properties include `markerType`, `seq`, `label`, and `timestamp`
- AND marker Features are distinguishable from cell record Features by the presence of `markerType`

### Requirement: Tunnel GeoJSON Export

The system SHALL export tunnel sessions as GeoJSON with a tunnel mode flag.

#### Scenario: Tunnel session GeoJSON export
- GIVEN a tunnel session with recorded points
- WHEN the user selects GeoJSON export
- THEN the FeatureCollection includes `"tunnelMode": true`
- AND cell record Features use the sentinel `[0, 0]` coordinates because tunnel mode has no geographic location
- AND the original `locationSource` property for each record is `"TUNNEL"`

### Requirement: Indoor CSV Export

The system SHALL include relative coordinate columns in CSV export for indoor sessions.

#### Scenario: Indoor session CSV export
- GIVEN an indoor session with recorded points
- WHEN the user selects CSV export from the session menu
- THEN the CSV includes `relativeX` and `relativeY` columns
- AND the `latitude` and `longitude` columns are empty (null)

#### Scenario: Outdoor session CSV unchanged
- GIVEN an outdoor session with recorded points
- WHEN the user selects CSV export from the session menu
- THEN the CSV includes `latitude` and `longitude` columns as before
- AND `relativeX` and `relativeY` columns are empty (null)

### Requirement: Indoor GeoJSON Export

The system SHALL export indoor sessions as GeoJSON with approximate coordinates and metadata indicating indoor mode.

#### Scenario: Indoor session GeoJSON export
- GIVEN an indoor session with recorded points
- WHEN the user selects GeoJSON export from the session menu
- THEN each Feature's geometry coordinates are computed as `[0 + relativeX / 111320, 0 + relativeY / 111320]`
- AND the FeatureCollection includes `"indoorMode": true` property
- AND the FeatureCollection includes `"coordinateReference": "relative"` property

#### Scenario: Outdoor GeoJSON unchanged
- GIVEN an outdoor session with recorded points
- WHEN the user selects GeoJSON export from the session menu
- THEN the GeoJSON follows the existing format with real geographic coordinates
- AND no indoor metadata properties are included

### Requirement: Indoor CSV Import

The system SHALL import CSV files containing indoor relative coordinates.

#### Scenario: Import indoor CSV with relative coordinates
- GIVEN the import dialog is open
- WHEN the user selects a CSV file that includes `relativeX` and `relativeY` columns
- THEN relative coordinates are parsed and stored on the corresponding records
- AND the session `recordingMode` is set to `"INDOOR"`
- AND `latitude` and `longitude` fields default to null

#### Scenario: Import outdoor CSV (existing behavior)
- GIVEN the import dialog is open
- WHEN the user selects a CSV file without `relativeX` and `relativeY` columns
- THEN `relativeX` and `relativeY` default to null and the import succeeds
- AND the session `recordingMode` is set to `"OUTDOOR"`

### Requirement: CSV Import

The system SHALL allow importing cell records from a CSV file by parsing the file once and assigning the session ID to all parsed records. Anchor cell columns are optional and nullable. Indoor sessions with `relativeX` and `relativeY` columns are supported. The parser SHALL correctly map the CSV `band` header to the internal `bandNumber` field. The parser SHALL support importing `isLocationEstimated` and `locationSource` fields. The parser SHALL log a warning but continue if the `ca_bands` JSON is malformed.

#### Scenario: Import from CSV with anchor columns
- GIVEN the import dialog is open
- WHEN the user selects a CSV file that includes anchor columns
- THEN anchor fields are parsed and stored on the corresponding records
- AND the CSV is parsed exactly once (not twice)

#### Scenario: Import from CSV without anchor columns
- GIVEN the import dialog is open
- WHEN the user selects a CSV file that does not include anchor columns
- THEN anchor fields default to null and the import succeeds without error

#### Scenario: Malformed lines skipped during import
- GIVEN the import dialog is open
- WHEN the user selects a CSV file
- THEN malformed lines are skipped
- AND a new session is created containing the successfully parsed records

#### Scenario: Import indoor CSV with relative coordinates
- GIVEN the import dialog is open
- WHEN the user selects a CSV file that includes `relativeX` and `relativeY` columns
- THEN relative coordinates are parsed and stored on the corresponding records
- AND the session `recordingMode` is set to `"INDOOR"`
- AND `latitude` and `longitude` fields default to null

#### Scenario: Import location metadata
- GIVEN the import dialog is open
- WHEN the user selects a CSV file that includes `is_location_estimated` and `location_source` columns
- THEN these fields are parsed and stored on the corresponding records

### Requirement: Marker CSV Import

The system SHALL allow importing markers from a companion CSV file when importing a cell record CSV.

#### Scenario: Import markers CSV
- GIVEN the user has selected a cell record CSV for import
- WHEN the user selects a marker CSV file with the same session name
- THEN the markers are parsed and stored in the `session_markers` table linked to the new session
- AND the marker CSV includes columns: `timestamp`, `seq`, `type`, `label`
- AND the session `recordingMode` is set to `"TUNNEL"` when markers are present

#### Scenario: Import markers CSV with escaped labels
- GIVEN a marker CSV with quoted labels containing commas or quotes
- WHEN the file is imported
- THEN the labels are unescaped and stored correctly

### Requirement: Marker GeoJSON Import

The system SHALL import marker features embedded in a GeoJSON FeatureCollection.

#### Scenario: Import GeoJSON markers
- GIVEN a GeoJSON file containing marker Point Features
- WHEN the file is imported
- THEN each marker is parsed and stored in the `session_markers` table linked to the new session
- AND marker Features are identified by the presence of `markerType` in their properties
- AND the session `recordingMode` is set to `"TUNNEL"` when the FeatureCollection has `"tunnelMode": true` or contains marker features

### Requirement: Tunnel Import Detection

The system SHALL detect tunnel sessions during import.

#### Scenario: Detect tunnel from CSV location source
- GIVEN a CSV file where any record has `location_source = "TUNNEL"`
- WHEN the file is imported
- THEN the session `recordingMode` is set to `"TUNNEL"`

#### Scenario: Detect tunnel from GeoJSON flag
- GIVEN a GeoJSON file with `"tunnelMode": true`
- WHEN the file is imported
- THEN the session `recordingMode` is set to `"TUNNEL"`

#### Scenario: Primary band number imported correctly
- GIVEN the import dialog is open
- WHEN the user selects a CSV file with a `band` column
- THEN the value is correctly assigned to the `bandNumber` field of the record

#### Scenario: Malformed CA bands JSON
- GIVEN the import dialog is open
- WHEN the user selects a CSV file with a malformed JSON string in the `ca_bands` column
- THEN the record is successfully parsed
- AND the `caBands` list for that record is empty
- AND a warning is logged

### Requirement: Batch Re-Split

The system SHALL allow the user to re-apply the cell ID split formula to all points in an existing session, including 5G NSA records. Cell ID split logic is defined in `cell-info/spec.md`.

#### Scenario: Batch re-split
- GIVEN a session with recorded points
- WHEN the user initiates a batch re-split action
- THEN every point in the session has its `enbOrGnbId` and `lcid` recalculated using the current bit length
- AND for `5G_NSA` records, the NR cell identity is re-split using the configurable NR gNB bit length
- AND for `5G_NSA` records, the anchor `anchorEnbOrGnbId` and `anchorLcid` are re-split using the LTE formula (shr 8 / and 0xFF)

### Requirement: Composite database index for analytics queries

The system SHALL provide a composite index on `(sessionId, timestamp)` in the `cell_records` table to optimize analytics queries that filter by session and order by timestamp.

#### Scenario: Analytics query performance
- GIVEN a session with many recorded points
- WHEN analytics queries execute `ORDER BY timestamp ASC` with a `sessionId` filter
- THEN the composite index is used for query optimization

### Requirement: GeoJSON Import

The system SHALL allow importing cell records from a GeoJSON FeatureCollection file. Anchor cell properties are optional and nullable. Indoor sessions with `"indoorMode"` property are supported. The parser SHALL support importing `isLocationEstimated` and `locationSource` fields.

#### Scenario: Import from GeoJSON with anchor properties
- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file that includes anchor properties
- THEN anchor fields are parsed and stored on the corresponding records

#### Scenario: Import from GeoJSON without anchor properties
- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file that does not include anchor properties
- THEN anchor fields default to null and the import succeeds without error

#### Scenario: Import indoor GeoJSON
- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file with `"indoorMode"` property set to true
- THEN the session `recordingMode` is set to `"INDOOR"`
- AND `relativeX` and `relativeY` are parsed from the geometry coordinates using the inverse approximate conversion
- AND `latitude` and `longitude` default to null

#### Scenario: Import location metadata
- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file that includes `isLocationEstimated` and `locationSource` properties
- THEN these properties are parsed and stored on the corresponding records

### Requirement: Speedtest Record Entity

The system SHALL store speedtest results in a separate Room entity, not mixed with cell records.

#### Scenario: SpeedTestRecordEntity structure
- GIVEN a speed test completes
- WHEN the result is persisted
- THEN a `SpeedTestRecordEntity` is created with: sessionId, timestamp, downloadBps, uploadBps, serverName, serverHost, serverLocation, serverId, dataSimSlotIndex, ratAtTest, rsrpAtTest, bandAtTest, succeeded, errorMessage, networkType
- AND the entity is stored in the `speed_test_records` table
- AND a foreign key links `sessionId` to `sessions.id` with ON DELETE CASCADE

### Requirement: Speedtest CSV Export

The system SHALL include speedtest data in session export when speedtest records exist. Speedtest data semantics are defined in `speedtest/spec.md`.

#### Scenario: Speedtest CSV export
- GIVEN a session with speedtest records
- WHEN the user exports the session
- THEN a separate `session_name_speedtest.csv` file is generated alongside the cell record CSV
- AND the speedtest CSV contains columns: timestamp, download_bps, upload_bps, server_name, server_host, server_location, succeeded, error_message, data_sim_slot, rat_at_test, rsrp_at_test, band_at_test, network_type

#### Scenario: No speedtest export when empty
- GIVEN a session without speedtest records
- WHEN the user exports the session
- THEN only the cell record CSV is generated (no speedtest file)

### Requirement: Database upgrade path coverage from all released schema versions

The system SHALL support upgrade paths from every previously-released Room schema version to the current `@Database(version = N)` version via registered `Migration` objects in `DatabaseModule.addMigrations(...)`. A user on any released schema version MUST be able to upgrade to the current version without data loss and without an `IllegalStateException: A migration from X to Y was required but not found`.

#### Scenario: User upgrades from earliest released schema version

- **GIVEN** a user is running an app build whose database is on the earliest released schema version (v1)
- **WHEN** the user upgrades to a build whose `@Database(version = N)` is the current version
- **THEN** the database MUST migrate cleanly from v1 to vN through the chain of registered `Migration` objects
- **AND** the user's existing data MUST be preserved (no destructive fallback)
- **AND** the app MUST NOT crash with `IllegalStateException: A migration from X to Y was required but not found`

#### Scenario: Every version step has a registered migration

- **WHEN** the app is built with `@Database(version = N)` and `exportSchema = true`
- **AND** schema JSONs exist in `app/schemas/com.cellrecorder.app.data.local.AppDatabase/` for versions 1 through N
- **THEN** `DatabaseModule.addMigrations(...)` MUST include a `Migration` object for each step `i → i+1` for `i` from 1 to `N-1`
- **AND** `fallbackToDestructiveMigration()` MUST NOT be called on the `Room.databaseBuilder`

#### Scenario: Column-dropping migration uses table rebuild pattern

- **WHEN** a migration needs to drop a column from a SQLite table on API 30 (where SQLite cannot `ALTER TABLE ... DROP COLUMN` directly)
- **THEN** the migration MUST use the create-new-table / copy-data / drop-old-table / rename pattern
- **AND** the migration MUST preserve all surviving columns' data through the copy step
- **AND** a row-seeded migration test MUST verify the surviving data round-trips through the migration