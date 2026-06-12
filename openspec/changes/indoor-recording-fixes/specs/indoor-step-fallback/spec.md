## ADDED Requirements

### Requirement: Accelerometer Step Detection Fallback

The system SHALL provide accelerometer-based step detection as a fallback when `TYPE_STEP_DETECTOR` is unavailable, permission is denied, or sensor registration fails.

#### Scenario: Falling back to accelerometer when TYPE_STEP_DETECTOR not available
- GIVEN the user attempts to start an indoor recording
- WHEN `TYPE_STEP_DETECTOR` returns null from `getDefaultSensor()` or `registerListener()` returns false
- THEN the system starts accelerometer-based step detection using `TYPE_ACCELEROMETER`
- AND steps are detected by monitoring acceleration magnitude peaks

#### Scenario: Falling back to accelerometer when registration fails
- GIVEN the `ACTIVITY_RECOGNITION` permission is granted
- WHEN `TYPE_STEP_DETECTOR.registerListener()` returns false
- THEN the system falls back to accelerometer-based step detection
- AND indoor recording proceeds with the fallback

#### Scenario: Accelerometer step detection algorithm
- GIVEN the accelerometer fallback is active
- WHEN accelerometer sensor events are received
- THEN the acceleration magnitude is computed as `sqrt(x² + y² + z²)`
- AND a low-pass filter (alpha = 0.1) smooths the magnitude signal
- AND a step is detected when the filtered magnitude exceeds 1.15× the gravity baseline
- AND a cooldown of 350ms is enforced between detected steps
- AND each detected step increments the position by `indoorStepLengthM` in the current heading direction

#### Scenario: Accelerometer fallback registers on IMU thread
- GIVEN the accelerometer fallback is active
- THEN the sensor listener is registered with `SENSOR_DELAY_GAME` for responsive step detection
- AND the sensor events are delivered on the main looper for thread-safe state updates

## REMOVED Requirements

### Requirement: Step detector unavailable (removed from indoor-positioning)

**Reason**: Replaced by accelerometer fallback — the system no longer blocks indoor recording when `TYPE_STEP_DETECTOR` is unavailable. Instead, it falls back to accelerometer-based step detection.

**Migration**: `TYPE_STEP_DETECTOR` remains the primary step detection source when available. The `TYPE_ACCELEROMETER` fallback is added as a secondary source.

## MODIFIED Requirements

### Requirement: Step Detection Position Tracking

The system SHALL estimate indoor position using Android's `TYPE_STEP_DETECTOR` sensor for distance and `TYPE_GAME_ROTATION_VECTOR` sensor for heading, producing relative (X,Y) coordinates from a (0,0) origin. When `TYPE_STEP_DETECTOR` is unavailable or registration fails, the system SHALL fall back to accelerometer-based step detection.

#### Scenario: Step detected updates position
- GIVEN an active indoor recording
- WHEN a step is detected (by `TYPE_STEP_DETECTOR` or accelerometer fallback)
- THEN the position is advanced by `indoorStepLengthM` meters in the current heading direction
- AND the new (X,Y) coordinate is emitted

#### Scenario: Step detector unavailable
- GIVEN the user attempts to start an indoor recording
- WHEN `TYPE_STEP_DETECTOR` is not available on the device or registration fails
- THEN the system falls back to accelerometer-based step detection
- AND indoor recording proceeds with the fallback