# Data Import and Export Specification

## Purpose

Defines the formats and behavior for exporting and importing session data via CSV and GeoJSON files.

## Requirements

### Requirement: CSV Export

The system SHALL allow exporting a session's data as a CSV file. For `5G_NSA` records, the CSV SHALL include anchor cell columns.

#### Scenario: Export to CSV
- GIVEN a session with recorded points
- WHEN the user selects CSV export from the session menu
- THEN the system opens a document save dialog
- AND the generated CSV includes columns for timestamp, coordinates, signal metrics, cell identity, carrier aggregation bands, and anchor cell fields for NSA records
- AND the `ca_bands` column contains a JSON array string
- AND anchor columns are prefixed with `anchor_` (e.g., `anchor_pci`, `anchor_rsrp`)

### Requirement: GeoJSON Export

The system SHALL allow exporting a session's data as a GeoJSON FeatureCollection. For `5G_NSA` records, the Feature properties SHALL include anchor cell fields.

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
- AND each Feature's properties include all cell and signal attributes
- AND for `5G_NSA` records, anchor cell properties are included

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

The system SHALL allow importing cell records from a CSV file by parsing the file once and assigning the session ID to all parsed records. Anchor cell columns are optional and nullable. Indoor sessions with `relativeX` and `relativeY` columns are supported.

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

### Requirement: Batch Re-Split

The system SHALL allow the user to re-apply the cell ID split formula to all points in an existing session, including 5G NSA records.

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

The system SHALL allow importing cell records from a GeoJSON FeatureCollection file. Anchor cell properties are optional and nullable. Indoor sessions with `"indoorMode"` property are supported.

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

### Requirement: Speedtest Record Entity

The system SHALL store speedtest results in a separate Room entity, not mixed with cell records.

#### Scenario: SpeedTestRecordEntity structure
- GIVEN a speed test completes
- WHEN the result is persisted
- THEN a `SpeedTestRecordEntity` is created with: sessionId, timestamp, downloadBps, uploadBps, serverName, serverHost, serverLocation, serverId, dataSimSlotIndex, ratAtTest, rsrpAtTest, bandAtTest, succeeded, errorMessage, networkType
- AND the entity is stored in the `speed_test_records` table
- AND a foreign key links `sessionId` to `sessions.id` with ON DELETE CASCADE

### Requirement: Speedtest CSV Export

The system SHALL include speedtest data in session export when speedtest records exist.

#### Scenario: Speedtest CSV export
- GIVEN a session with speedtest records
- WHEN the user exports the session
- THEN a separate `session_name_speedtest.csv` file is generated alongside the cell record CSV
- AND the speedtest CSV contains columns: timestamp, download_bps, upload_bps, server_name, server_host, server_location, succeeded, error_message, data_sim_slot, rat_at_test, rsrp_at_test, band_at_test, network_type

#### Scenario: No speedtest export when empty
- GIVEN a session without speedtest records
- WHEN the user exports the session
- THEN only the cell record CSV is generated (no speedtest file)