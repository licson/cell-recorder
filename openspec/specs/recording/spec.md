# Recording Specification

## Purpose

Manages the lifecycle of recording sessions, including creation, start/stop controls, location-based point triggers, GPS accuracy filtering, sensor fusion fallback during GPS loss, multi-SIM data capture, and indoor recording mode with IMU-based position tracking.

## Requirements

### Requirement: Session Creation

The system SHALL allow the user to create a named recording session.

#### Scenario: Create session via FAB
- GIVEN the user is on the Session List screen
- WHEN the user taps the FAB and enters a session name
- THEN a new session is persisted with `endedAt = null`

### Requirement: Recording Start

The system SHALL start a foreground service when the user initiates a recording. For indoor sessions, the service SHALL use `IndoorPositionCollector` instead of `LocationCollector` and SHALL NOT start GPS-based location collection.

#### Scenario: Start recording from recording screen
- GIVEN a session with `endedAt = null`
- WHEN the user taps the Start button
- THEN a foreground service with `FOREGROUND_SERVICE_TYPE_LOCATION` is started
- AND the service begins collecting location and cell data

#### Scenario: Start indoor recording
- GIVEN a session with `endedAt = null` and `recordingMode = "INDOOR"`
- WHEN the user taps the Start button
- THEN a foreground service is started
- AND the service begins collecting cell data using time-based triggers
- AND position is estimated via `IndoorPositionCollector`
- AND no GPS location requests are made

### Requirement: Recording Stop

The system SHALL stop the foreground service and finalize the session when the user ends the recording.

#### Scenario: Stop recording via button
- GIVEN an active recording
- WHEN the user taps the Stop button
- THEN the session is updated with `endedAt = now()`
- AND the recording service stops itself

### Requirement: Max Recording Duration

The system SHALL automatically stop recording when the configured maximum duration is reached.

#### Scenario: Duration limit reached
- GIVEN an active recording
- WHEN the elapsed time reaches `maxRecordingDurationMin`
- THEN the session is ended with `endedAt = now()`
- AND the recording service stops itself

### Requirement: Location-Based Recording Triggers

The system SHALL record a data point when the device moves beyond a configured distance threshold, or at a periodic interval when stationary.

#### Scenario: Movement triggers immediate recording
- GIVEN an active recording
- WHEN the GPS-reported distance since the last point exceeds `locationChangeThresholdM`
- THEN a new point is recorded immediately

#### Scenario: Stationary periodic recording
- GIVEN an active recording with no significant movement
- WHEN `recordingIntervalMs` has elapsed since the last point
- THEN a new point is recorded

### Requirement: GPS Accuracy Filtering

The system SHALL discard GPS readings whose accuracy exceeds a configured threshold.

#### Scenario: Inaccurate GPS reading discarded
- GIVEN an active recording
- WHEN a GPS reading has accuracy exceeding `gpsAccuracyThresholdM`
- THEN the reading is discarded and not used for point recording

### Requirement: GPS Loss Extrapolation

The system SHALL continue recording using sensor-based dead reckoning when GPS fix is lost, for a limited duration. This requirement applies only to outdoor recording mode.

#### Scenario: Extrapolation mode activation
- GIVEN an active outdoor recording with a previously acquired GPS fix
- WHEN no accurate GPS fix is received for more than 3 seconds
- THEN the system enters extrapolation mode

#### Scenario: Estimated position calculation
- GIVEN the system is in extrapolation mode
- WHEN a recording point is triggered
- THEN the position is estimated using last known speed, bearing, and sensor-derived heading delta
- AND the point is tagged with `isLocationEstimated = true` and `locationSource = "SENSOR_FUSION"`

#### Scenario: Extrapolation timeout
- GIVEN the system is in extrapolation mode
- WHEN `maxGpsLossExtrapolationSec` has elapsed without GPS recovery
- THEN extrapolation stops

#### Scenario: GPS recovery settling
- GIVEN the system is in extrapolation mode
- WHEN an accurate GPS fix is recovered
- THEN a settling delay is applied before resuming normal GPS-based recording

#### Scenario: Extrapolation not used in indoor
- GIVEN an active indoor recording
- THEN GPS loss extrapolation SHALL NOT be used
- AND no fallback recording job for GPS loss detection is launched

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

### Requirement: Ping Attribution

The system SHALL attribute ICMP ping measurements to the device's default data SIM.

#### Scenario: Ping label with SIM slot
- GIVEN an active recording
- WHEN ping results are displayed
- THEN the ping value is labeled with the active data SIM slot number

### Requirement: Point Recording Resilience

The system SHALL continue recording remaining snapshots even if a single snapshot insert fails.

#### Scenario: Single insert failure
- GIVEN an active recording with multiple SIMs
- WHEN one snapshot's database insert fails
- THEN the remaining snapshots are still recorded
- AND the overall recording continues uninterrupted

#### Scenario: Single insert failure within batch
- GIVEN an active recording with multiple SIMs
- WHEN one snapshot's data is invalid
- THEN the invalid snapshot is skipped and its CA bands are not inserted
- AND the remaining valid snapshots are still written in the same batch transaction
- AND the overall recording continues uninterrupted

### Requirement: Efficient Path Storage

The system SHALL store the recorded GPS path using a data structure that provides O(1) insertion and oldest-point removal.

#### Scenario: Path capacity exceeded
- GIVEN an active recording with a full path buffer (MAX_PATH_SIZE entries)
- WHEN a new point is recorded
- THEN the oldest point is removed in O(1) time
- AND the new point is appended in O(1) time

### Requirement: Optional Speedtest During Recording

The system SHALL, when speedtest is enabled in config, run continuous throughput tests alongside cell recording.

#### Scenario: Speedtest starts with recording
- GIVEN a recording session has started
- WHEN speedtest is enabled in config
- THEN a speedtest coroutine job is launched alongside the cell recording and ping jobs

#### Scenario: Speedtest stops with recording
- GIVEN an active recording with a running speedtest job
- WHEN the recording is stopped
- THEN the speedtest job is cancelled and any in-progress test is aborted

#### Scenario: Speedtest skipped when disabled
- GIVEN a recording session has started
- WHEN speedtest is disabled in config
- THEN no speedtest job is launched

### Requirement: Speedtest Config Reload

The system SHALL read speedtest configuration at recording start.

#### Scenario: Config read at start
- GIVEN a recording is about to start
- WHEN speedtest is enabled
- THEN the current speedtest config (interval, upload toggle, server ID) is read from app config and used for the duration of the recording

### Requirement: Indoor Recording Mode

The system SHALL support an indoor recording mode that uses IMU-based pedestrian dead reckoning instead of GPS for position tracking.

#### Scenario: Indoor recording lifecycle
- GIVEN a session with `recordingMode = "INDOOR"`
- WHEN the user starts recording
- THEN the recording service begins collecting cell data using time-based triggers
- AND position is estimated via `IndoorPositionCollector` instead of `LocationCollector`
- AND no GPS location requests are made

#### Scenario: Time-based recording triggers for indoor
- GIVEN an active indoor recording
- WHEN `indoorRecordingIntervalMs` has elapsed since the last recorded point
- THEN a new point is recorded with the current indoor position (relativeX, relativeY)
- AND `locationSource = "INDOOR_IMU"`, `isLocationEstimated = false`
- AND `latitude`, `longitude`, `altitude`, `accuracy` are set to null

#### Scenario: No GPS distance triggers in indoor mode
- GIVEN an active indoor recording
- THEN GPS-based distance triggers (`locationChangeThresholdM`) SHALL NOT be used
- AND only the time-based trigger (`indoorRecordingIntervalMs`) applies

### Requirement: Indoor Path Storage

The system SHALL store the indoor movement path using the same efficient data structure as outdoor mode.

#### Scenario: Indoor path in recorded path snapshot
- GIVEN an active indoor recording
- WHEN a point is recorded
- THEN the (relativeX, relativeY) pair is appended to the path buffer
- AND the path buffer uses the same O(1) insertion/removal structure as outdoor mode
- AND the path is exposed via `recordedPathSnapshot` for the recording screen