## Context

The app has two runtime permission checkpoints:

1. **`MainActivity`** — checks foreground (location, phone state, notifications) + background location on cold start and `onResume`. Drives the rationale/settings dialogs app-wide until the user grants the core permissions.
2. **`RecordingScreen`** — checks foreground + indoor (`ACTIVITY_RECOGNITION`) + background when the user taps Start on a recording session. The indoor permission is only relevant here.

Both checkpoints use `PermissionHelper.hasPermanentDenial()` / `hasPermanentDenialForMode()` to decide between showing the rationale dialog and the settings dialog. These helpers use `ActivityCompat.shouldShowRequestPermissionRationale() == false` as the "permanently denied" signal, but that API returns `false` for **both** "never asked" **and** "permanently denied". There is no Android API to distinguish these two states without tracking attempt history.

The reported bug: an existing user (foreground + background already granted via outdoor sessions) tries to start an indoor session. `ACTIVITY_RECOGNITION` has never been asked for, so `shouldShowRequestPermissionRationale` returns `false`. The pre-request gate in `RecordingScreen.kt:274` calls `hasPermanentDenialForMode`, which returns `true` (false positive), so the app jumps straight to `ShowSettings` — the `PermissionDeniedDialog` — and never launches the runtime request. The user is stuck.

A secondary latent bug: `RecordingScreen.handlePermissionResult()` (line 105) calls `hasPermanentDenial` (not `ForMode`), so it doesn't see `ACTIVITY_RECOGNITION` at all. This is currently masked by the `hasAttemptedOnce` short-circuit, but would surface if the pre-request gate were fixed in isolation.

The two checkpoints share ~80 lines of near-duplicate logic (`permissionState`, `hasAttemptedOnce`, `isRequestingPermissions`, the launcher callbacks, the dialog rendering). Keeping them in sync manually is fragile — the bugs above are partly a result of the two implementations drifting.

## Goals / Non-Goals

**Goals:**

- Fix the reported bug: existing users must get the `ACTIVITY_RECOGNITION` runtime request when starting an indoor session, not the Settings dialog.
- Eliminate the false-positive "permanently denied" classification for never-asked permissions across all checkpoints.
- Unify the permission decision logic into a single, testable helper so both checkpoints behave identically.
- Ensure the rationale dialog is always shown before launching a runtime request (per the user's explicit requirement).
- Fix the latent `ACTIVITY_RECOGNITION` blind spot in `RecordingScreen.handlePermissionResult()`.
- Add a regression test covering the existing-user-indoor-start path.

**Non-Goals:**

- Persisting `hasAttemptedOnce` across cold starts (a permanently-denied user will see the rationale dialog once after a cold start, then Settings — acceptable, avoids `SharedPreferences` plumbing).
- Changing the outdoor permission flow behavior (it already works; the fix is behavior-preserving for outdoor).
- Adding, removing, or changing any declared permission.
- Changing the `RecordingService` start path (it has no permission gate and trusts the caller; that stays).
- Changing the rationale dialog content (which permissions are listed, their descriptions).
- Reworking the file-picker launchers in `SessionListScreen` / `SessionDetailScreen` (they use `OpenDocument` / `OpenDocumentTree`, not permission contracts).

## Decisions

### D1: Replace `hasPermanentDenial` / `hasPermanentDenialForMode` with `decidePermissionState`

**Choice**: Remove both broken helpers. Add a single `PermissionHelper.decidePermissionState(hasAttemptedOnce, missingPermissions, activity)` function returning `PermissionUiState`:

```
missing.isEmpty()                                  -> AllGranted
anyRationale (shouldShowRequestPermissionRationale) -> ShowRationale
hasAttemptedOnce                                   -> ShowSettings
else                                               -> ShowRationale
```

**Rationale**: `shouldShowRequestPermissionRationale` returns `true` only when the user has denied before **without** "Don't ask again". That's the only reliable signal Android gives. "Never asked" and "permanently denied" both return `false` — the only way to distinguish them without persistence is to track whether we've already attempted a request in this session (`hasAttemptedOnce`). The function above uses both signals correctly.

**Alternatives considered**:
- *Persist `hasAttemptedOnce` in `SharedPreferences`*: would let us skip the rationale for users who denied permanently on a previous cold start. Rejected per user decision — gentler UX and simpler code win; a permanently-denied user sees the rationale once per cold start, then Settings.
- *Keep `hasPermanentDenial` and add a `wasEverAsked` parameter*: more invasive at call sites and doesn't fix the structural problem. The single decision function is cleaner.

### D2: Shared Compose helper `rememberPermissionFlowState`

**Choice**: Create `app/src/main/java/com/cellrecorder/app/ui/shared/PermissionFlow.kt` exposing:

```
@Composable
fun rememberPermissionFlowState(
    permissions: List<String>,
    onAllGranted: () -> Unit,
    autoRequestOnLaunch: Boolean,
    onRequestLaunch: () -> Unit,
): PermissionFlowState
```

`PermissionFlowState` exposes: `permissionState: PermissionUiState?`, `isRequestingPermissions: Boolean`, `hasAttemptedOnce: Boolean`, `setHasAttemptedOnce(Boolean)`, `update()`, and dialog rendering. `MainActivity` calls it with `autoRequestOnLaunch=true` (cold start triggers the check); `RecordingScreen` calls it with `autoRequestOnLaunch=false` (only on Start tap).

**Rationale**: Dedupes ~80 lines and guarantees the two checkpoints never drift again. The bug above was caused by the two implementations diverging (`RecordingScreen` got `hasPermanentDenialForMode` while `MainActivity` got `hasPermanentDenial`).

**Alternatives considered**:
- *Apply `decidePermissionState` to each checkpoint independently*: smaller diff, but the two flows remain near-duplicates and prone to future drift. Rejected per user decision (shared composable).
- *Move the launchers into the helper*: tempting, but the foreground/background split (background must be requested **after** foreground is granted, per Android's `ACCESS_BACKGROUND_LOCATION` rule) makes the launcher orchestration checkpoint-specific. The helper owns state + decision + dialog rendering; the call site owns launcher orchestration. This keeps the helper's API simple.

### D3: `hasAttemptedOnce` resets on screen leave / cold start, no persistence

**Choice**: `remember { mutableStateOf(false) }` inside `rememberPermissionFlowState`. Set to `true` right before launching a system request (in the rationale dialog's `onGrant` callback, and before any `autoRequestOnLaunch` request). Reset to `false` when the composable leaves the composition or the process is recreated.

**Rationale**: Per user decision. Avoids `SharedPreferences`/`DataStore` plumbing. A permanently-denied user who returns to the screen sees the rationale dialog once more, taps Grant, the system request immediately returns denied, and `decidePermissionState` now sees `hasAttemptedOnce=true` + `anyRationale=false` → `ShowSettings`. One extra tap per cold start is acceptable.

### D4: Rationale always shown pre-request (per user requirement)

**Choice**: `decidePermissionState` returns `ShowRationale` for both "never asked" and "denied once" cases. The only case that returns `ShowSettings` pre-request is "asked before + no rationale" — i.e., the user has already denied with "Don't ask again" in this session. There is **no** path that skips the rationale and goes straight to a system request.

**Rationale**: Per the user's explicit correction ("the permission rationale dialog should always show when we request permission"). This is also standard Android UX — the in-app rationale primes the user before the system dialog.

### D5: Add `missingAllForMode` helper

**Choice**: Add `PermissionHelper.missingAllForMode(recordingMode, context): Array<String>` returning `missingForegroundPermissions + missingIndoorPermissions + missingBackgroundPermissions`. Keep the existing `missingPermissionsForMode` (which returns only fg + indoor, because the orchestration splits fg and bg into separate launchers).

**Rationale**: `RecordingScreen.handlePermissionResult()` currently calls `missingPermissionsForMode` then checks `hasPermanentDenial` (not `ForMode`) — so `ACTIVITY_RECOGNITION` denial is invisible to the post-request decision. Using `missingAllForMode` with `decidePermissionState` fixes the latent bug. The split between `missingPermissionsForMode` (launcher orchestration) and `missingAllForMode` (post-result decision) is intentional and matches the existing orchestration structure.

## Risks / Trade-offs

- **[hasAttemptedOnce resets on cold start]** → A permanently-denied user sees the rationale dialog once per cold start, then Settings (one extra tap). Mitigated by the fact that the rationale dialog's "Grant Permissions" button immediately routes to the system request, which returns denied instantly for permanently-denied permissions, then `decidePermissionState` routes to Settings. Acceptable per user decision.

- **[No unit-test infrastructure for Compose permission flow]** → The shared composable uses `rememberLauncherForActivityResult` which is hard to test in pure JVM unit tests. Mitigation: the decision logic lives in `PermissionHelper.decidePermissionState` (a pure function, no Android framework dependency beyond `Activity`), which IS unit-testable. The instrumented test covers the end-to-end flow; the unit test covers the decision logic. If the instrumented test proves too brittle (no prior permission-flow test infra exists), the unit test of `decidePermissionState` is the fallback regression guard.

- **[Shared composable adds indirection]** → A reader following the Start button's `onClick` now jumps to `rememberPermissionFlowState` instead of seeing inline state. Mitigated by keeping the helper's API small (4 parameters, 1 return type) and adding a KDoc comment explaining the flow. The dedup benefit outweighs the indirection cost.

- **[Behavior change for permanently-denied users]** → Previously, a permanently-denied user at `RecordingScreen` saw Settings immediately (correct outcome, wrong code path — it was the false-positive case that happened to match). After the fix, they see the rationale dialog once, tap Grant, system returns denied, then Settings. This is a one-tap regression for that specific case, but it's the correct Android UX and the trade-off for fixing the never-asked case. Documented in CHANGELOG.

- **[Modifying `indoor-positioning` and `service` specs]** → The existing specs inline implementation-specific helper names (`PermissionHelper.missingPermissionsForMode(...)`, `PermissionHelper.allGrantedForMode(...)`) which violates the AGENTS.md spec policy ("implementation details go in design.md"). The MODIFIED requirements clean these up and reference the new `permission-flow` capability. Behavioral requirements are unchanged; this is a spec hygiene change bundled with the new capability introduction.
