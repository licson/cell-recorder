## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Composite database index for analytics queries

The system SHALL provide a composite index on `(sessionId, timestamp)` in the `cell_records` table to optimize analytics queries that filter by session and order by timestamp.

#### Scenario: Analytics query performance
- GIVEN a session with many recorded points
- WHEN analytics queries execute `ORDER BY timestamp ASC` with a `sessionId` filter
- THEN the composite index is used for query optimization
