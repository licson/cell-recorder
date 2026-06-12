## ADDED Requirements

### Requirement: Activity Recognition Permission in Service

The system SHALL declare and request `android.permission.ACTIVITY_RECOGNITION` for indoor recording.

#### Scenario: ACTIVITY_RECOGNITION declared in manifest
- GIVEN the app's AndroidManifest.xml
- THEN `android.permission.ACTIVITY_RECOGNITION` is declared as a `<uses-permission>` element

#### Scenario: Runtime permission request for indoor recording
- GIVEN the user taps Start on an indoor session
- WHEN `ACTIVITY_RECOGNITION` is not granted on API 29+
- THEN the permission request flow includes `ACTIVITY_RECOGNITION` alongside location and phone state
- AND recording starts only after all required permissions are granted

### Requirement: Sensor Registration Verification

The system SHALL verify that sensor registration succeeds before continuing with indoor recording.

#### Scenario: Verify sensor registration success
- GIVEN an indoor recording is started
- WHEN `registerListener()` is called for step detection or accelerometer sensors
- THEN the return value of `registerListener()` is checked
- AND if all sensor registrations fail, indoor recording is aborted with an error

### Requirement: Sensor Health Monitoring

The system SHALL monitor step detection activity during indoor recording and provide feedback when no steps are detected.

#### Scenario: No steps detected within 10 seconds
- GIVEN an active indoor recording
- WHEN no step events have been received for more than 10 seconds
- THEN a sensor health warning is emitted: "No steps detected. Try moving the phone to your pocket."
- AND the tracking confidence indicator shows "No steps"

#### Scenario: Steps resume after warning
- GIVEN a sensor health warning is displayed
- WHEN step events resume
- THEN the sensor health warning is cleared
- AND the tracking confidence indicator returns to its normal drift-based state