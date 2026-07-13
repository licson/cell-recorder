## ADDED Requirements

### Requirement: Recording Continuity

The system SHALL NOT stop an active recording on transient errors. A recording SHALL only stop when one of the following fatal conditions occurs: (a) the user taps Stop, (b) `maxRecordingDurationMin` is reached, (c) the foreground service cannot legally start (e.g., `startForeground` fails), (d) `OutOfMemoryError`, or (e) a persistent database write failure (disk full, read-only filesystem, or broken migration). All other failures SHALL be logged via Timber and the recording SHALL continue, possibly in a degraded mode (e.g., sentinel coordinates when location is unavailable, empty snapshots when cell info is unavailable).

#### Scenario: Config load failure continues with defaults
- GIVEN a recording is starting
- WHEN `configRepository.getConfig().first()` throws
- THEN the recording continues using default `AppConfigEntity` values
- AND the exception is logged via Timber
- AND the recording does NOT stop

#### Scenario: Location permission revoked mid-recording
- GIVEN an active outdoor recording
- WHEN `SecurityException` is thrown by location updates (e.g., `ACCESS_FINE_LOCATION` revoked)
- THEN GPS location requests are stopped
- AND subsequent records are written with `locationSource = "UNAVAILABLE"` and sentinel coordinates (`latitude = 0, longitude = 0`)
- AND the exception is logged via Timber
- AND the recording continues on its time cadence using cell data only
- AND the recording does NOT stop

#### Scenario: Indoor step sensor unavailable continues on time cadence
- GIVEN a recording is starting in INDOOR mode
- WHEN `IndoorPositionCollector.isAnyStepDetectionActive()` returns false
- THEN the recording continues using time-based triggers only
- AND records are written with `locationSource = "INDOOR_NOSENSOR"` and sentinel `relativeX`/`relativeY = 0.0`
- AND the exception is logged via Timber
- AND the recording does NOT stop

#### Scenario: CellInfoCollector snapshots throw continues with empty list
- GIVEN an active recording (any mode)
- WHEN `CellInfoCollector.snapshots()` throws (e.g., modem state transition)
- THEN the per-call exception is caught inside `PointRecorder`
- AND `emptyList()` is used in place of the snapshots for that trigger
- AND the exception is logged via Timber
- AND the recording continues and does NOT stop

#### Scenario: Generic exception in recording loop continues after backoff
- GIVEN an active recording
- WHEN a generic `Exception` (not `SecurityException`, not `CancellationException`) is thrown in the recording loop
- THEN the exception is logged via Timber
- AND a brief backoff delay is applied
- AND the recording loop re-enters and continues
- AND `CancellationException` is rethrown and not treated as a transient error

#### Scenario: Persistent database failure stops fatally
- GIVEN an active recording
- WHEN a database write throws a persistent-failure exception (`SQLiteFullException`, `SQLiteReadOnlyDatabaseException`, or migration `IllegalStateException`)
- THEN the recording stops fatally
- AND `RecordingState.errorMessage` is set to "Storage failure: <message>"
- AND the exception is logged via Timber
- AND the foreground service stops itself

### Requirement: Marker Insert Failure Visibility

The system SHALL surface a marker insert failure to the user via a Toast without stopping or interrupting the recording cycle. The marker insert runs in a fire-and-forget coroutine under `serviceScope` and does not affect the recording loop, sibling jobs, or DB batch writes for cell records.

#### Scenario: Marker insert failure shows Toast
- GIVEN an active recording (any mode)
- WHEN the user taps "Mark Note" (from the notification action or the UI button)
- AND the `SessionMarkerRepository.insertMarkerWithAutoLabel()` call throws
- THEN a Toast is shown on the main thread with the message "Marker could not be saved"
- AND the exception is logged via Timber
- AND the recording continues uninterrupted
- AND no other recording state is affected

#### Scenario: Marker insert failure does not block subsequent markers
- GIVEN a marker insert has just failed
- WHEN the user taps "Mark Note" again
- THEN a new marker insert is attempted
- AND the Toast is shown again if the insert fails
- AND the recording continues uninterrupted

## MODIFIED Requirements

### Requirement: Point Recording Resilience

The system SHALL continue recording remaining snapshots even if a single snapshot insert fails, at both the entity-build layer and the database-insert layer. The system SHALL use a two-tier insert strategy: first attempt a single batched `@Transaction` insert; on a transient DB exception (e.g., `SQLiteConstraintException`, `IOException` — classified as transient by `DbExceptionClassifier`), fall back to per-snapshot `insertSingle` calls, skipping individual failures and continuing the session. On a persistent DB exception (disk full, read-only, migration broken — classified as persistent by `DbExceptionClassifier`), the recording SHALL stop fatally per the Recording Continuity requirement.

#### Scenario: Single insert failure
- GIVEN an active recording with multiple SIMs
- WHEN one snapshot's database insert fails with a transient exception
- THEN the system falls back to per-snapshot inserts for the batch
- AND the remaining snapshots are still recorded (their per-snapshot inserts succeed)
- AND the failed snapshot is skipped and logged via Timber
- AND the overall recording continues uninterrupted

#### Scenario: Single insert failure within batch
- GIVEN an active recording with multiple SIMs
- WHEN one snapshot's data is invalid (build-time failure, before reaching the DB)
- THEN the invalid snapshot is skipped and its CA bands are not built
- AND the remaining valid snapshots are still written in the same batch transaction
- AND the overall recording continues uninterrupted

#### Scenario: Batch insert succeeds (fast path)
- GIVEN an active recording with multiple valid snapshots
- WHEN `insertRecordBatch` is called
- THEN all snapshots are inserted in a single database transaction
- AND the inserted count equals the snapshot count
- AND the recording continues

#### Scenario: Transient batch failure falls back to per-snapshot inserts
- GIVEN an active recording with multiple snapshots
- WHEN `insertRecordBatch` throws a transient exception (e.g., `SQLiteConstraintException`)
- THEN the system falls back to per-snapshot `insertSingle` calls
- AND each per-snapshot insert that succeeds is committed
- AND each per-snapshot insert that fails is skipped and logged via Timber
- AND the inserted count equals the number of successful per-snapshot inserts
- AND the recording continues uninterrupted

#### Scenario: Persistent batch failure stops fatally
- GIVEN an active recording with multiple snapshots
- WHEN `insertRecordBatch` throws a persistent exception (e.g., `SQLiteFullException`, read-only, migration `IllegalStateException`)
- THEN the recording stops fatally per the Recording Continuity requirement
- AND `RecordingState.errorMessage` is set to "Storage failure: <message>"
- AND the exception is logged via Timber
- AND the foreground service stops itself

### Requirement: Multi-SIM Recording

The system SHALL record separate data points for every active SIM slot simultaneously upon each location trigger, using a single batched database transaction. The session `pointCount` SHALL increment by the number of rows actually inserted for the trigger, not by a fixed count per trigger. An empty batch (all snapshots failed to build or insert) SHALL produce zero increment, preventing drift between the displayed `pointCount` and the actual row count in `cell_records`.

#### Scenario: Multiple SIM data points
- GIVEN an active recording with multiple active SIM slots
- WHEN a location recording point is triggered
- THEN one `CellRecordEntity` row is created per SIM with visible cells
- AND each row contains the subscription's serving cell info and signal metrics

#### Scenario: Batched database writes
- GIVEN an active recording with multiple active SIM slots and CA bands
- WHEN a location recording point is triggered
- THEN all cell record inserts and CA band inserts for that point are written in a single database transaction (with per-snapshot fallback on transient DB failure per the Point Recording Resilience requirement)

#### Scenario: Session point count
- GIVEN an active recording with multiple SIMs
- WHEN a location recording point is triggered and N rows are actually inserted (across all SIMs)
- THEN the session `pointCount` increments by N (the number of inserted rows), not per SIM and not per trigger
- AND if zero rows are inserted (all snapshots failed to build or transient-failed at the DB layer), `pointCount` does NOT increment
- AND the recording continues per the Recording Continuity requirement regardless of the inserted count
