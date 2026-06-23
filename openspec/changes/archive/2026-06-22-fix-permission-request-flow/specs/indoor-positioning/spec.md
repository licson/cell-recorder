## MODIFIED Requirements

### Requirement: Activity Recognition Permission Check

The system SHALL require `android.permission.ACTIVITY_RECOGNITION` for indoor recording on Android 10+ (API 29+). The permission SHALL be gated at the screen layer before an indoor session is allowed to start, using the unified permission decision logic defined in the `permission-flow` capability. The permission SHALL be requested at runtime when the user attempts to start an indoor session and the permission is not granted.

#### Scenario: Permission required before indoor recording
- GIVEN the user attempts to start an indoor recording on API 29+
- WHEN `ACTIVITY_RECOGNITION` is not granted
- THEN indoor recording SHALL NOT start
- AND a permission request is shown (rationale dialog, then system request) per the `permission-flow` capability
- AND an error message informs the user that activity recognition permission is required

#### Scenario: Permission denied prevents indoor recording
- GIVEN the user attempts to start an indoor recording on API 29+
- WHEN `ACTIVITY_RECOGNITION` is permanently denied
- THEN indoor recording SHALL NOT start
- AND the user is directed to system Settings per the `permission-flow` capability

#### Scenario: Permission not required for outdoor recording
- GIVEN the user attempts to start an outdoor recording
- THEN `ACTIVITY_RECOGNITION` is NOT required
- AND outdoor recording proceeds normally
