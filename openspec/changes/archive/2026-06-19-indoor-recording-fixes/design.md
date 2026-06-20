## Context

The indoor recording mode implementation (change `indoor-recording-mode`) added IMU-based pedestrian dead reckoning using `TYPE_STEP_DETECTOR` and `TYPE_GAME_ROTATION_VECTOR`. Manual testing revealed two problems: no visible path when walking, and no ping test data. Both trace to the same root cause: `TYPE_STEP_DETECTOR` requires `android.permission.ACTIVITY_RECOGNITION` on Android 10+ (API 29+), which was neither declared in the manifest nor requested at runtime. Without it, `registerListener()` silently returns `false` — no steps are ever detected, no positions are emitted, and no points are recorded.

Additionally, the heading extraction uses a manual quaternion-to-yaw formula with an incorrect `w` fallback (`1f` instead of `sqrt(1 - x² - y² - z²)` for 3-element vectors), and there is no fallback step detection when the hardware sensor is unavailable or fails to register.

## Goals / Non-Goals

**Goals:**
- Add `ACTIVITY_RECOGNITION` permission to manifest and runtime request flow
- Fix heading extraction to use `SensorManager.getRotationMatrixFromVector()` + `getOrientation()`
- Add accelerometer-based step detection fallback when hardware step detector is unavailable or registration fails
- Show ping latency in indoor live stats bar
- Add sensor health monitoring: warn when no steps detected for > 10s
- Verify sensor registration success and abort if all sensors fail

**Non-Goals:**
- Pedestrian dead reckoning accuracy improvements beyond accelerometer fallback
- WiFi / BLE fingerprinting or floor plan integration
- Machine-learning-based step detection
- Automatic step length calibration

## Decisions

### D1: Accelerometer fallback for step detection (overrides D1 from indoor-recording-mode)

**Choice**: Add accelerometer-based step detection as a fallback when `TYPE_STEP_DETECTOR` is unavailable, permission is denied, or sensor registration returns false.

**Alternatives considered**:
- *Block indoor recording entirely*: Rejected because live testing showed that even with the permission, `TYPE_STEP_DETECTOR` may not fire reliably when the phone is held in hand vs. a pocket.
- *Always use accelerometer + TYPE_STEP_DETECTOR simultaneously*: Over-complicates the detection logic. The phases approach (hardware first, fallback on failure) is simpler.

**Rationale**: The accelerometer fallback uses a well-understood magnitude peak detection approach: low-pass filter the acceleration magnitude, detect peaks above a gravitational baseline threshold, and enforce a cooldown period (350ms) between steps to prevent double-counting. This gives usable step detection on any device with an accelerometer. The fallback activates when `TYPE_STEP_DETECTOR.registerListener()` returns false or the sensor is null.

### D2: Fixed heading extraction via `getRotationMatrix` + `getOrientation` (overrides D2 from indoor-recording-mode)

**Choice**: Replace the manual `calculateYaw()` function (quaternion → yaw) with `SensorManager.getRotationMatrixFromVector()` followed by `SensorManager.getOrientation()`.

**Alternatives considered**:
- *Fix the `w` fallback in the existing function*: The manual formula duplicates logic the SDK provides. The Android API is better tested and handles edge cases (near-identity rotations, sensor coordinate transforms).
- *Use `getQuaternionFromVector()` + manual yaw*: `getOrientation()` from the rotation matrix is the standard, recommended approach per the AOSP documentation (`SensorEvent.java`).

**Rationale**: `getRotationMatrixFromVector()` correctly computes the quaternion scalar (`w = sqrt(1 - x² - y² - z²)` for 3-element vectors) and builds the rotation matrix, while `getOrientation()` extracts azimuth, pitch, and roll in the standard sensor coordinate system. This eliminates both the `w` bug and the unnecessary duplication. The same fix applies to `SensorFusionCollector.kt` which has the identical `calculateYaw()` bug.

### D3: ACTIVITY_RECOGNITION runtime permission

**Choice**: Declare `android.permission.ACTIVITY_RECOGNITION` in the manifest and request it at runtime before starting indoor recording. Add it to `PermissionHelper` alongside the foreground permission group.

**Rationale**: This is a `dangerous` permission. Unlike `ACCESS_FINE_LOCATION` which is always required, `ACTIVITY_RECOGNITION` is only needed for indoor mode. The request should be scoped: show the permission dialog when the user attempts to start an indoor recording (not before). If denied, indoor recording should not start and an error message should explain why.

### D4: Sensor health monitoring

**Choice**: Add a 10-second timeout in the `stateUpdateJob` that monitors step detection activity. If no steps are received within 10 seconds of recording start (or since last step), display a warning in the UI suggesting the user move the phone to a pocket or bag.

**Rationale**: Step detection can fail silently even with the correct permission — device in a backpack, in a non-walking mode, or the sensor just being unreliable. The user needs immediate feedback so they can adjust their phone position. The 10-second timeout is short enough to be actionable but long enough to avoid false positives during slow movement.

### D5: Ping display in indoor LiveStatsBar

**Choice**: Display ping latency in the indoor recording screen's stats bar, matching the outdoor format.

**Rationale**: Ping data is collected identically for indoor and outdoor modes. The indoor screen should show the same ping latency stats so users can correlate signal quality with position. The GPS status, lat/lon, and altitude fields remain hidden for indoor mode.

## Risks / Trade-offs

- **[Accelerometer fallback is less accurate than hardware step detection]** → Mitigation: The fallback only activates when hardware fails. The peak detection parameters (alpha ~0.1 low-pass, 1.2× gravity threshold, 350ms cooldown) are conservative defaults tested across multiple device types.
- **[False positive steps from accelerometer]** → Mitigation: The magnitude threshold (1.2× gravity) filters out noise from vibrations and non-walking movements. The 350ms cooldown limits the maximum rate to ~170 steps/min, well above normal walking pace (100-120 steps/min).
- **[ACTIVITY_RECOGNITION denied by user]** → Mitigation: Indoor recording is blocked with a clear error message. The user must grant the permission in Settings to use indoor mode. Outdoor mode remains unaffected.
- **[Sensor health warning causes confusion]** → Mitigation: The warning text is user-actionable: "No steps detected. Try moving the phone to your pocket." It does not stop the recording.