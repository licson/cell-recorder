# Indoor Step Detection Fallback Specification

## Purpose

Defines the accelerometer-based step detection fallback that activates when `TYPE_STEP_DETECTOR` is unavailable, permission is denied, or sensor registration fails.

## Requirements

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