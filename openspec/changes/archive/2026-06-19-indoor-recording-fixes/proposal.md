## Why

Manual testing revealed that indoor recording produces no visible path and no ping data. The root cause is a missing `ACTIVITY_RECOGNITION` permission (required by `TYPE_STEP_DETECTOR` on Android 10+) — without it, `registerListener()` silently returns false and no steps are ever detected. Additionally, the heading extraction uses a manual quaternion-to-yaw function instead of the recommended Android API, and there is no accelerometer fallback for step detection when the hardware sensor doesn't fire.

## What Changes

- Add `ACTIVITY_RECOGNITION` permission to the manifest and request it at runtime before indoor recording
- Fix heading extraction: replace `calculateYaw()` with `SensorManager.getRotationMatrixFromVector()` + `getOrientation()` in both `IndoorPositionCollector` and `SensorFusionCollector`
- Add accelerometer-based step detection fallback when `TYPE_STEP_DETECTOR` is unavailable, permission is denied, or sensor registration fails
- Show ping latency in the indoor recording live stats bar
- Add sensor health monitoring: warn when no steps detected for > 10 seconds
- Add sensor registration success check: verify `registerListener()` returned `true` before proceeding

## Capabilities

### New Capabilities
- `indoor-step-fallback`: Accelerometer-based step detection using magnitude peak detection with low-pass filter and cooldown

### Modified Capabilities
- `indoor-positioning`: Fix heading extraction to use recommended Android API; add accelerometer fallback step detection; add ACTIVITY_RECOGNITION permission check
- `service`: Request ACTIVITY_RECOGNITION permission for indoor recording; verify sensor registration success
- `ui`: Show ping latency in indoor LiveStatsBar; display sensor health status warning

## Impact

- **AndroidManifest.xml**: Add `<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />`
- **PermissionHelper.kt**: Add activity recognition to the permission request flow
- **RecordingScreen.kt**: Request ACTIVITY_RECOGNITION before indoor recording; show ping in indoor LiveStatsBar; add sensor health warning
- **RecordingViewModel.kt**: Add sensor health state
- **IndoorPositionCollector.kt**: Fix heading extraction; add accelerometer fallback step detection; add `registerListener()` success check
- **SensorFusionCollector.kt**: Fix heading extraction (same `calculateYaw` bug)
- **RecordingService.kt**: Abort indoor recording if sensor registration fails