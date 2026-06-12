# Indoor Positioning Specification

## Purpose

Defines the indoor position tracking system using IMU-based pedestrian dead reckoning, including step detection, heading estimation, drift modeling, origin reset, and required permissions.

## Requirements

### Requirement: Step Detection Position Tracking

The system SHALL estimate indoor position using Android sensors and produce relative (X,Y) coordinates from a (0,0) origin. Primary step detection SHALL use `TYPE_STEP_DETECTOR`. Primary heading SHALL use `TYPE_GAME_ROTATION_VECTOR` with `TYPE_ROTATION_VECTOR` as fallback. When `TYPE_STEP_DETECTOR` is unavailable or registration fails, the system SHALL fall back to accelerometer-based step detection. Heading extraction SHALL use `SensorManager.getRotationMatrixFromVector()` followed by `SensorManager.getOrientation()`.

#### Scenario: Step detected updates position
- GIVEN an active indoor recording
- WHEN a step is detected (by `TYPE_STEP_DETECTOR` or accelerometer fallback)
- THEN the position is advanced by `indoorStepLengthM` meters in the current heading direction
- AND the new (X,Y) coordinate is emitted

#### Scenario: Heading from game rotation vector
- GIVEN an active indoor recording
- WHEN a game rotation vector sensor event is received
- THEN the heading is extracted using `SensorManager.getRotationMatrixFromVector()` and `SensorManager.getOrientation()`
- AND the azimuth from `orientation[0]` is used as the current heading in radians
- AND subsequent step positions use this heading

#### Scenario: Game rotation vector unavailable
- GIVEN an active indoor recording
- WHEN `TYPE_GAME_ROTATION_VECTOR` is not available on the device
- THEN the system falls back to `TYPE_ROTATION_VECTOR`
- AND the same `getRotationMatrixFromVector()` + `getOrientation()` pipeline is used

#### Scenario: Step detector unavailable
- GIVEN the user attempts to start an indoor recording
- WHEN `TYPE_STEP_DETECTOR` is not available on the device or sensor registration fails
- THEN the system falls back to accelerometer-based step detection
- AND indoor recording proceeds with the fallback

#### Scenario: Sensor registration failure
- GIVEN an indoor recording is starting
- WHEN registration for all available motion sensors (step detector, accelerometer, rotation vector) fails
- THEN indoor recording SHALL NOT start
- AND an error message SHALL indicate that required sensors are unavailable

### Requirement: Activity Recognition Permission Check

The system SHALL require `android.permission.ACTIVITY_RECOGNITION` for indoor recording on Android 10+ (API 29+).

#### Scenario: Permission required before indoor recording
- GIVEN the user attempts to start an indoor recording on API 29+
- WHEN `ACTIVITY_RECOGNITION` is not granted
- THEN indoor recording SHALL NOT start
- AND a permission request dialog is shown
- AND an error message informs the user that activity recognition permission is required

#### Scenario: Permission denied prevents indoor recording
- GIVEN the user attempts to start an indoor recording on API 29+
- WHEN `ACTIVITY_RECOGNITION` is permanently denied
- THEN indoor recording SHALL NOT start
- AND the user is directed to system Settings to grant the permission

#### Scenario: Permission not required for outdoor recording
- GIVEN the user attempts to start an outdoor recording
- THEN `ACTIVITY_RECOGNITION` is NOT required
- AND outdoor recording proceeds normally

### Requirement: Indoor Position State

The system SHALL maintain and expose the current indoor position state including relative coordinates, heading, step count, and estimated drift.

#### Scenario: Indoor position update emitted
- GIVEN an active indoor recording
- WHEN the position changes (step detected or heading updated)
- THEN an `IndoorPositionUpdate` is emitted containing: `relativeX`, `relativeY`, `headingRad`, `stepCount`, `estimatedDriftM`, `timestamp`

#### Scenario: Initial state at recording start
- GIVEN an indoor recording has just started
- THEN the position state is `relativeX = 0.0`, `relativeY = 0.0`, `headingRad = current device heading`, `stepCount = 0`, `estimatedDriftM = 0.0`

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

The system SHALL allow the user to reset the indoor position to a new (0,0) origin during recording.

#### Scenario: User resets origin
- GIVEN an active indoor recording
- WHEN the user taps the "Reset Origin" button
- THEN the position is reset to `relativeX = 0.0`, `relativeY = 0.0`
- AND the heading is reset to the current device heading
- AND the step counter and drift estimate are reset
- AND a discontinuity marker is recorded at the reset point

#### Scenario: Path preservation after reset
- GIVEN an active indoor recording with a path history
- WHEN the user resets origin
- THEN all previously recorded path segments are preserved
- AND the path has a visible discontinuity at the reset point
- AND new path segments continue from (0,0)

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