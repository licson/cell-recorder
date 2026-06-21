## Why

Existing users who already granted foreground and background location (via outdoor sessions) get stuck at the "Permissions Required" dialog when they try to start an indoor recording session. The physical activity permission (`ACTIVITY_RECOGNITION`) is never requested at runtime; instead the app incorrectly routes the user to system Settings. Root cause: the pre-request gate uses `shouldShowRequestPermissionRationale() == false` as the "permanently denied" signal, but that API returns `false` for both "never asked" and "permanently denied" — so never-asked users are misclassified as permanently-denied and never shown the runtime request. The same flawed helper is used at multiple checkpoints (`MainActivity` and `RecordingScreen`), so the fix needs to be applied consistently across the app.

## What Changes

- Remove the broken `PermissionHelper.hasPermanentDenial()` and `PermissionHelper.hasPermanentDenialForMode()` helpers that misclassify never-asked permissions as permanently-denied.
- Add a unified `PermissionHelper.decidePermissionState(hasAttemptedOnce, missingPermissions, activity)` helper that correctly distinguishes three states: all-granted, can-show-rationale (denied once or never asked), and permanently-denied (asked before + no rationale).
- Add `PermissionHelper.missingAllForMode(recordingMode, context)` so the indoor post-request check considers `ACTIVITY_RECOGNITION` (the existing `missingPermissionsForMode` omits background permissions, which are requested in a separate launcher step).
- Introduce a shared Compose helper (`rememberPermissionFlowState`) that encapsulates `permissionState`, `hasAttemptedOnce`, `isRequestingPermissions`, the rationale/settings dialog rendering, and the `decidePermissionState` calls — used by both `MainActivity` and `RecordingScreen` to guarantee consistent behavior across checkpoints.
- Rationale is always shown pre-request when permissions are missing (unless the user has already denied permanently, in which case the Settings dialog is shown). The `hasAttemptedOnce` flag is set to `true` right before launching a system request, and resets on screen leave / cold start (no persistence).
- Update `MainActivity` and `RecordingScreen` to route through the shared helper and the new decision function.
- Add an instrumented test verifying that an existing user (foreground + background granted) starting an indoor session triggers the `ACTIVITY_RECOGNITION` runtime request.

## Capabilities

### New Capabilities

- `permission-flow`: Unified runtime permission decision logic and rationale-first request flow shared across all permission checkpoints in the app. Defines how the system distinguishes "all granted", "can show rationale" (never asked or denied once), and "permanently denied" (asked before + no rationale), and when each of the three dialogs (none, rationale, settings) is shown.

### Modified Capabilities

- `indoor-positioning`: The "Activity Recognition Permission Check" requirement is simplified to reference the new `permission-flow` capability for the decision logic, instead of inlining implementation-specific helper names. The behavioral requirement (request `ACTIVITY_RECOGNITION` before indoor start; block start until granted; direct to Settings if permanently denied) is unchanged.
- `service`: The "Android Permissions" runtime scenarios (foreground, background, notifications, activity recognition) are simplified to reference the `permission-flow` capability for the request flow, instead of duplicating the decision logic per permission. The behavioral requirements are unchanged.

## Impact

- **Code**: `app/src/main/java/com/cellrecorder/app/ui/shared/PermissionHelper.kt` (remove 2 helpers, add 2 helpers), new `app/src/main/java/com/cellrecorder/app/ui/shared/PermissionFlow.kt`, `app/src/main/java/com/cellrecorder/app/ui/MainActivity.kt`, `app/src/main/java/com/cellrecorder/app/ui/recording/RecordingScreen.kt`.
- **Tests**: New instrumented test `app/src/androidTest/java/com/cellrecorder/app/ui/RecordingScreenPermissionTest.kt` (with a unit-test fallback for `PermissionHelper.decidePermissionState` if UI automation proves too brittle).
- **No API changes**, no manifest changes, no database changes, no dependency changes.
- **User-facing**: Existing users can now start indoor recording sessions without getting stuck at the permissions dialog. The fix is otherwise behavior-preserving for outdoor sessions and fresh installs.
