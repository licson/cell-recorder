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

### Requirement: CSV Import

The system SHALL allow importing cell records from a CSV file. Anchor cell columns are optional and nullable.

#### Scenario: Import from CSV with anchor columns
- GIVEN the import dialog is open
- WHEN the user selects a CSV file that includes anchor columns
- THEN anchor fields are parsed and stored on the corresponding records

#### Scenario: Import from CSV without anchor columns
- GIVEN the import dialog is open
- WHEN the user selects a CSV file that does not include anchor columns
- THEN anchor fields default to null and the import succeeds without error

#### Scenario: Malformed lines skipped during import
- GIVEN the import dialog is open
- WHEN the user selects a CSV file
- THEN malformed lines are skipped
- AND a new session is created containing the successfully parsed records

### Requirement: GeoJSON Import

The system SHALL allow importing cell records from a GeoJSON FeatureCollection file. Anchor cell properties are optional and nullable.

#### Scenario: Import from GeoJSON with anchor properties
- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file that includes anchor properties
- THEN anchor fields are parsed and stored on the corresponding records

#### Scenario: Import from GeoJSON without anchor properties
- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file that does not include anchor properties
- THEN anchor fields default to null and the import succeeds without error# Data Import and Export Specification (Delta)

## ADDED Requirements

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