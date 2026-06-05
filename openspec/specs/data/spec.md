# Data Import and Export Specification

## Purpose

Defines the formats and behavior for exporting and importing session data via CSV and GeoJSON files.

## Requirements

### Requirement: CSV Export

The system SHALL allow exporting a session's data as a CSV file.

#### Scenario: Export to CSV
- GIVEN a session with recorded points
- WHEN the user selects CSV export from the session menu
- THEN the system opens a document save dialog
- AND the generated CSV includes columns for timestamp, coordinates, signal metrics, cell identity, and carrier aggregation bands
- AND the `ca_bands` column contains a JSON array string

### Requirement: GeoJSON Export

The system SHALL allow exporting a session's data as a GeoJSON FeatureCollection.

#### Scenario: Export to GeoJSON
- GIVEN a session with recorded points
- WHEN the user selects GeoJSON export from the session menu
- THEN the system opens a document save dialog
- AND the generated GeoJSON follows the FeatureCollection schema with one Feature per recorded point

#### Scenario: GeoJSON feature properties
- GIVEN a GeoJSON export
- WHEN the file is generated
- THEN each Feature's geometry contains `[lon, lat, alt]` coordinates
- AND each Feature's properties include all cell and signal attributes

### Requirement: CSV Import

The system SHALL allow importing cell records from a CSV file.

#### Scenario: Import from CSV
- GIVEN the import dialog is open
- WHEN the user selects a CSV file
- THEN the file is parsed
- AND a new session is created containing the imported records
- AND malformed lines are skipped

### Requirement: GeoJSON Import

The system SHALL allow importing cell records from a GeoJSON FeatureCollection file.

#### Scenario: Import from GeoJSON
- GIVEN the import dialog is open
- WHEN the user selects a GeoJSON file
- THEN the file is parsed
- AND a new session is created containing the imported records