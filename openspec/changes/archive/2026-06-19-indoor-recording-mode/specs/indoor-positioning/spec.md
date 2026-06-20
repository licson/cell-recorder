## ADDED Requirements

### Requirement: Step Detection Position Tracking

The system SHALL estimate indoor position using Android's `TYPE_STEP_DETECTOR` sensor for distance and `TYPE_GAME_ROTATION_VECTOR` sensor for heading, producing relative (X,Y) coordinates from a (0,0) origin.

#### Scenario: Step detected updates position
- GIVEN an active indoor recording
- WHEN a step is detected by `TYPE_STEP_DETECTOR`
- THEN the position is advanced by `indoorStepLengthM` meters in the current heading direction
- AND the new (X,Y) coordinate is emitted

#### Scenario: Heading from game rotation vector
- GIVEN an active indoor recording
- WHEN a game rotation vector sensor event is received
- THEN the heading (yaw) is extracted from the rotation vector
- AND subsequent step positions use this heading

#### Scenario: Game rotation vector unavailable
- GIVEN an active indoor recording
- WHEN `TYPE_GAME_ROTATION_VECTOR` is not available on the device
- THEN the system falls back to `TYPE_ROTATION_VECTOR`
- AND indoor recording proceeds with the fallback sensor

#### Scenario: Step detector unavailable
- GIVEN the user attempts to start an indoor recording
- WHEN `TYPE_STEP_DETECTOR` is not available on the device
- THEN indoor recording SHALL NOT start
- AND an error message SHALL inform the user that the step sensor is not available

### Requirement: Indoor Position State

The system SHALL maintain and expose the current indoor position state including relative coordinates, heading, step count, estimated drift, and time since the last origin reset.

#### Scenario: Indoor position update emitted
- GIVEN an active indoor recording
- WHEN the position changes (step detected or heading updated)
- THEN an `IndoorPositionUpdate` is emitted containing: `relativeX`, `relativeY`, `headingRad`, `stepCount`, `estimatedDriftM`, `timestamp`

#### Scenario: Initial state at recording start
- GIVEN an indoor recording has just started
- THEN the position state is `relativeX = 0.0`, `relativeY = 0.0`, `headingRad = current device heading`, `stepCount = 0`, `estimatedDriftM = 0.0`

#### Scenario: Time since origin reset exposed
- GIVEN an active indoor recording
- THEN `IndoorPositionCollector` exposes `originResetTimestampMs` (the wall-clock time of the last origin reset)
- AND `RecordingState.timeSinceOriginResetMs` is populated as `now - originResetTimestampMs` by `RecordingService.stateUpdateJob`
- AND `TrackingConfidenceIndicator` displays this elapsed time

### Requirement: Drift Estimation

The system SHALL estimate position drift based on step count and elapsed time since the last origin reset.

#### Scenario: Drift calculation
- GIVEN an active indoor recording
- WHEN the position is updated
- THEN the estimated drift is computed as `stepCount * indoorStepLengthM * driftRate`
- AND `driftRate` starts at 0.02 (2%)
- AND `driftRate` increases linearly by 0.004 per minute since last origin reset
- AND `driftRate` is capped at 0.20 (20%)

#### Scenario: Drift at origin
- GIVEN an active indoor recording
- WHEN origin reset has just been performed
- THEN `estimatedDriftM = 0.0`
- AND `driftRate` resets to 0.02

### Requirement: Origin Reset

The system SHALL allow the user to reset the indoor position to a new (0,0) origin during recording, and SHALL track each reset as a discontinuity in the recorded path so the UI can render a visible break.

#### Scenario: User resets origin
- GIVEN an active indoor recording
- WHEN the user taps the "Reset Origin" button
- THEN the position is reset to `relativeX = 0.0`, `relativeY = 0.0`
- AND the heading is reset to the current device heading
- AND the step counter and drift estimate are reset
- AND the origin reset timestamp is updated (`IndoorPositionCollector.originResetTimestampMs`)
- AND the next path point recorded after the reset is tagged as a discontinuity in `PointRecorder.recordedDiscontinuitiesSnapshot`

#### Scenario: Path preservation after reset
- GIVEN an active indoor recording with a path history
- WHEN the user resets origin
- THEN all previously recorded path segments are preserved
- AND the path has a visible discontinuity at the reset point
- AND new path segments continue from (0,0)
- AND `RecordingState.recordedDiscontinuities` includes the index of the pre-reset path point (the last point before the origin reset), so that the line from the pre-reset point to the post-reset (0,0) point is not drawn
- AND `IndoorPathCanvas` renders an orange break/gap marker at that index and does not draw a line across it

### Requirement: Step Length Configuration

The system SHALL use a configurable step length for converting detected steps to distance.

#### Scenario: Default step length
- GIVEN the app is installed with default configuration
- THEN `indoorStepLengthM` is 0.7 meters

#### Scenario: Custom step length
- GIVEN the Settings screen
- WHEN the user adjusts the step length slider
- THEN `indoorStepLengthM` is updated to the new value
- AND the new value is used for subsequent indoor recordings

### Requirement: Indoor Recording Interval Configuration

The system SHALL use a configurable time interval for indoor recording triggers.

#### Scenario: Default indoor recording interval
- GIVEN the app is installed with default configuration
- THEN `indoorRecordingIntervalMs` is 5000 milliseconds

#### Scenario: Custom indoor recording interval
- GIVEN the Settings screen
- WHEN the user adjusts the indoor recording interval
- THEN `indoorRecordingIntervalMs` is updated to the new value
- AND the new value is used for subsequent indoor recordings
