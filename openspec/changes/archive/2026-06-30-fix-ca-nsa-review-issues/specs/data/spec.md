## MODIFIED Requirements

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
