## ADDED Requirements

### Requirement: Unified Permission Decision Logic

The system SHALL use a single, shared permission decision function to determine the UI state for every runtime permission checkpoint in the app. The function SHALL take: the set of missing permissions, whether a request has been attempted before in the current session (`hasAttemptedOnce`), and the host activity. It SHALL return one of three states: `AllGranted` (no missing permissions), `ShowRationale` (permission missing and a request can be made), or `ShowSettings` (permission missing and permanently denied). The system SHALL NOT use `shouldShowRequestPermissionRationale() == false` alone as the "permanently denied" signal, because that API returns `false` for both "never asked" and "permanently denied".

#### Scenario: All required permissions granted
- GIVEN a permission checkpoint is evaluated
- WHEN the set of missing permissions is empty
- THEN the decision function returns `AllGranted`
- AND no permission dialog is shown

#### Scenario: Permission missing and never asked before
- GIVEN a permission checkpoint is evaluated
- WHEN at least one permission is missing AND `hasAttemptedOnce` is false
- THEN the decision function returns `ShowRationale`
- AND the in-app rationale dialog is shown before the system request is launched

#### Scenario: Permission denied once, can ask again
- GIVEN a permission checkpoint is evaluated
- WHEN at least one permission is missing AND `shouldShowRequestPermissionRationale()` returns true for at least one missing permission
- THEN the decision function returns `ShowRationale`
- AND the in-app rationale dialog is shown before the system request is launched

#### Scenario: Permission permanently denied
- GIVEN a permission checkpoint is evaluated
- WHEN at least one permission is missing AND `shouldShowRequestPermissionRationale()` returns false for all missing permissions AND `hasAttemptedOnce` is true
- THEN the decision function returns `ShowSettings`
- AND the user is directed to system Settings to grant the permission

### Requirement: Rationale Shown Before Every Runtime Request

The system SHALL show the in-app rationale dialog before launching any runtime permission request, except when the permission is permanently denied (in which case the Settings dialog is shown instead). The system SHALL NOT skip the rationale dialog for never-asked permissions. The rationale dialog SHALL list all permissions about to be requested and their purposes, and SHALL adapt its content to the recording mode (e.g., include the physical activity permission only for indoor mode).

#### Scenario: Rationale shown before first request
- GIVEN the user has never been asked for a permission
- WHEN the user triggers an action that requires the permission
- THEN the in-app rationale dialog is shown
- AND after the user taps the grant button, the system request is launched

#### Scenario: Rationale shown after non-permanent denial
- GIVEN the user previously denied a permission without selecting "Don't ask again"
- WHEN the user triggers an action that requires the permission
- THEN the in-app rationale dialog is shown
- AND after the user taps the grant button, the system request is launched

#### Scenario: Settings shown after permanent denial
- GIVEN the user previously denied a permission with "Don't ask again" (or the system otherwise treats it as permanently denied)
- WHEN the user triggers an action that requires the permission
- THEN the Settings dialog is shown instead of the rationale dialog
- AND the user is directed to system Settings to grant the permission

### Requirement: Per-Session Attempt Tracking

The system SHALL track whether a permission request has been attempted in the current session via a `hasAttemptedOnce` flag. The flag SHALL be set to `true` immediately before launching a system permission request. The flag SHALL reset when the user leaves the screen or the host process is recreated; the system SHALL NOT persist the flag across cold starts. The flag SHALL be used to disambiguate "never asked" from "permanently denied" when `shouldShowRequestPermissionRationale()` returns false.

#### Scenario: Flag set before first request
- GIVEN the user has not yet been asked for a permission in this session (`hasAttemptedOnce` is false)
- WHEN the rationale dialog's grant button is tapped
- THEN `hasAttemptedOnce` is set to `true`
- AND the system permission request is launched

#### Scenario: Flag resets on screen leave
- GIVEN the user has been asked for a permission in this session (`hasAttemptedOnce` is true)
- WHEN the user leaves the screen and returns
- THEN `hasAttemptedOnce` is `false` again
- AND the rationale dialog is shown (not Settings) if the permission is still missing and the user has not permanently denied it

#### Scenario: Flag used to detect permanent denial post-request
- GIVEN `hasAttemptedOnce` is `true` (a request was attempted in this session)
- WHEN the permission is still missing after the request returns AND `shouldShowRequestPermissionRationale()` returns false
- THEN the Settings dialog is shown (permanently denied)

### Requirement: Shared Permission Flow State Across Checkpoints

The system SHALL provide a single, shared Compose state holder that encapsulates the permission UI state, attempt tracking, request-in-progress flag, and rationale/settings dialog rendering. The state holder SHALL be used by every runtime permission checkpoint in the app to guarantee identical behavior. Each checkpoint SHALL supply: the full set of permissions it gates on, an `onAllGranted` callback, whether to check automatically on launch, and an `onRequestLaunch` callback that performs the system request.

#### Scenario: MainActivity uses the shared state holder
- GIVEN the `MainActivity` cold-start permission checkpoint
- THEN the checkpoint uses the shared state holder with `autoRequestOnLaunch = true`
- AND the permissions set includes foreground and background location, phone state, and notifications

#### Scenario: RecordingScreen uses the shared state holder
- GIVEN the `RecordingScreen` Start-button permission checkpoint
- THEN the checkpoint uses the shared state holder with `autoRequestOnLaunch = false`
- AND the permissions set includes foreground permissions, the indoor `ACTIVITY_RECOGNITION` permission (for indoor mode), and background location

#### Scenario: Both checkpoints use the same decision logic
- GIVEN any runtime permission checkpoint in the app
- WHEN the post-request decision is evaluated
- THEN the same `decidePermissionState` function is consulted
- AND the result determines whether to show the rationale dialog, the Settings dialog, or proceed with the gated action
