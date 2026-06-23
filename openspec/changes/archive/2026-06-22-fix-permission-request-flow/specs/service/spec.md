## MODIFIED Requirements

### Requirement: Android Permissions

The system SHALL require the following Android permissions for full functionality. Runtime permission requests SHALL follow the unified permission decision logic defined in the `permission-flow` capability.

#### Scenario: Runtime permissions
- GIVEN the user attempts to start a recording
- WHEN `ACCESS_FINE_LOCATION` is not granted
- THEN a permission request is shown per the `permission-flow` capability
- AND recording does not start until granted

#### Scenario: Coarse location declared alongside fine
- GIVEN the app's AndroidManifest.xml
- THEN `ACCESS_COARSE_LOCATION` is declared as a `<uses-permission>` element
- AND it is declared alongside `ACCESS_FINE_LOCATION` (required on Android 12+ so the system can offer a coarse-only grant)

#### Scenario: Background location
- GIVEN the user has granted fine location
- WHEN `ACCESS_BACKGROUND_LOCATION` is not granted
- THEN a permission request is shown (API 29+) per the `permission-flow` capability

#### Scenario: Phone state
- GIVEN the user has an active recording
- WHEN `READ_PHONE_STATE` is granted
- THEN SIM subscription details are available for multi-SIM recording

#### Scenario: Network state
- GIVEN the app's AndroidManifest.xml
- THEN `ACCESS_NETWORK_STATE` is declared as a `<uses-permission>` element
- AND the speedtest Wi-Fi availability check is permitted without a runtime request (normal permission)

#### Scenario: Notifications permission (API 33+)
- GIVEN the user starts recording on API 33+
- WHEN `POST_NOTIFICATIONS` is not granted
- THEN a permission request is shown per the `permission-flow` capability

#### Scenario: Activity recognition (API 29+)
- GIVEN the user starts an indoor recording on API 29+
- WHEN `ACTIVITY_RECOGNITION` is not granted
- THEN a permission request is shown per the `permission-flow` capability
- AND indoor recording does not start until granted

### Requirement: Activity Recognition Permission in Service

The system SHALL declare and request `android.permission.ACTIVITY_RECOGNITION` for indoor recording. The runtime request SHALL follow the unified permission decision logic defined in the `permission-flow` capability.

#### Scenario: ACTIVITY_RECOGNITION declared in manifest
- GIVEN the app's AndroidManifest.xml
- THEN `android.permission.ACTIVITY_RECOGNITION` is declared as a `<uses-permission>` element

#### Scenario: Runtime permission request for indoor recording
- GIVEN the user taps Start on an indoor session
- WHEN `ACTIVITY_RECOGNITION` is not granted on API 29+
- THEN the permission request flow includes `ACTIVITY_RECOGNITION` alongside the foreground permissions for location and phone state
- AND recording starts only after all required permissions are granted
- AND the Start button is gated by a screen-level permission check before an indoor session is allowed to start
