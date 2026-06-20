## ADDED Requirements

### Requirement: Activity Recognition Permission Check

The system SHALL require `android.permission.ACTIVITY_RECOGNITION` for indoor recording on Android 10+ (API 29+). The permission is exposed via a dedicated `PermissionHelper.indoorPermissions()` helper (separate from `foregroundPermissions()`) and gated at the screen layer before an indoor session is allowed to start.

#### Scenario: Permission required before indoor recording
- GIVEN the user attempts to start an indoor recording on API 29+
- WHEN `ACTIVITY_RECOGNITION` is not granted
- THEN indoor recording SHALL NOT start
- AND a permission request dialog is shown (via `PermissionHelper.missingPermissionsForMode(...)` in `RecordingScreen`)
- AND an error message informs the user that activity recognition permission is required

#### Scenario: Permission denied prevents indoor recording
- GIVEN the user attempts to start an indoor recording on API 29+
- WHEN `ACTIVITY_RECOGNITION` is permanently denied
- THEN indoor recording SHALL NOT start (the Start button's gate `PermissionHelper.allGrantedForMode(recordingMode, context)` returns false)
- AND the user is directed to system Settings to grant the permission

#### Scenario: Permission not required for outdoor recording
- GIVEN the user attempts to start an outdoor recording
- THEN `ACTIVITY_RECOGNITION` is NOT required
- AND `PermissionHelper.indoorPermissions()` is not consulted
- AND outdoor recording proceeds normally

## MODIFIED Requirements

### Requirement: Step Detection Position Tracking

The system SHALL estimate indoor position using Android's `TYPE_STEP_DETECTOR` sensor for distance and `TYPE_GAME_ROTATION_VECTOR` sensor for heading, producing relative (X,Y) coordinates from a (0,0) origin. Heading extraction SHALL use `SensorManager.getRotationMatrixFromVector()` followed by `SensorManager.getOrientation()`.

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