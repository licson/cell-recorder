## MODIFIED Requirements

### Requirement: Multi-SIM Recording

The system SHALL record separate data points for every active SIM slot simultaneously upon each location trigger, using a single batched database transaction.

#### Scenario: Multiple SIM data points
- GIVEN an active recording with multiple active SIM slots
- WHEN a location recording point is triggered
- THEN one `CellRecordEntity` row is created per SIM with visible cells
- AND each row contains the subscription's serving cell info and signal metrics

#### Scenario: Batched database writes
- GIVEN an active recording with multiple active SIM slots and CA bands
- WHEN a location recording point is triggered
- THEN all cell record inserts and CA band inserts for that point are written in a single database transaction

#### Scenario: Session point count
- GIVEN an active recording with multiple SIMs
- WHEN a location recording point is triggered
- THEN the session `pointCount` increments by one per location trigger, not per SIM

### Requirement: Point Recording Resilience

The system SHALL continue recording remaining snapshots even if a single snapshot insert fails within a batch.

#### Scenario: Single insert failure within batch
- GIVEN an active recording with multiple SIMs
- WHEN one snapshot's data is invalid
- THEN the invalid snapshot is skipped and its CA bands are not inserted
- AND the remaining valid snapshots are still written in the same batch transaction
- AND the overall recording continues uninterrupted

## ADDED Requirements

### Requirement: Efficient Path Storage

The system SHALL store the recorded GPS path using a data structure that provides O(1) insertion and oldest-point removal.

#### Scenario: Path capacity exceeded
- GIVEN an active recording with a full path buffer (MAX_PATH_SIZE entries)
- WHEN a new point is recorded
- THEN the oldest point is removed in O(1) time
- AND the new point is appended in O(1) time
