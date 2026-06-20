## ADDED Requirements

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
