## 1. Permission — Add ACTIVITY_RECOGNITION

- [x] 1.1 Add `<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />` to `AndroidManifest.xml`
- [x] 1.2 Add `Manifest.permission.ACTIVITY_RECOGNITION` to a dedicated `PermissionHelper.indoorPermissions()` helper for Android 10+ (API 29+), separate from `foregroundPermissions()` (per design.md D3 — added "alongside the foreground permission group" as a scoped indoor-only request)
- [x] 1.3 Update permission request flow in `RecordingScreen.kt` to include `ACTIVITY_RECOGNITION` before starting indoor recording (via `PermissionHelper.missingPermissionsForMode(...)` and `allIndoorGranted(...)`)
- [x] 1.4 Verify `ACTIVITY_RECOGNITION` is granted before allowing indoor start (implemented as a screen-level gate in `RecordingScreen.kt:125,260` via `PermissionHelper.allGrantedForMode(recordingMode, context)`, rather than in `RecordingViewModel`)

## 2. Heading Fix — Use getRotationMatrix + getOrientation

- [x] 2.1 Update `IndoorPositionCollector.calculateYaw()` to use `SensorManager.getRotationMatrixFromVector()` + `SensorManager.getOrientation()`
- [x] 2.2 Update `SensorFusionCollector.calculateYaw()` with the same fix (identical bug)
- [x] 2.3 Remove unused `atan2`, `cos`, `sin` imports if they become unused after the fix

## 3. Accelerometer Step Detection Fallback

- [x] 3.1 Add accelerometer listener registration in `IndoorPositionCollector.start()` when `TYPE_STEP_DETECTOR` is null or `registerListener()` returns false
- [x] 3.2 Implement accelerometer step detection logic: low-pass filter on magnitude, peak detection at 1.15× gravity threshold, 350ms cooldown
- [x] 3.3 Wire accelerometer step detections into the same `onStepDetected()` path so position updates work identically
- [x] 3.4 Add `hasAccelerometer()` availability check

## 4. Sensor Registration Verification

- [x] 4.1 Track which sensor registrations succeeded in `IndoorPositionCollector` via stored booleans for `stepDetector`, `accelerometer`, and `rotationVector` (capturing the return value of every `registerListener()` call)
- [x] 4.2 Add `isAnyStepDetectionActive()` method returning true only when at least one step source actually registered successfully (checks `stepDetectorRegistered || accelerometerRegistered`, not sensor availability)
- [x] 4.3 In `RecordingService.startRecording()` for indoor mode: abort with error if `IndoorPositionCollector` reports no active step detection after registration (`RecordingService.kt:178-185`)
- [x] 4.4 Check `registerListener()` return values and propagate failures for ALL sensors (step detector, accelerometer, AND rotation vector)

## 5. UI — Ping in Indoor LiveStatsBar

- [x] 5.1 In `RecordingScreen.LiveStatsBar`: show ping latency line for indoor mode (remove the `if (!isIndoor)` guard on the ping display)
- [x] 5.2 Format indoor ping display as "Ping: X.X ms" without GPS/lat/lon fields

## 6. UI — Sensor Health Monitoring

- [x] 6.1 Add `lastStepTimeMs` tracking in `IndoorPositionCollector`, updated on every step
- [x] 6.2 Add `secondsSinceLastStep()` method on `IndoorPositionCollector` returning elapsed seconds since last step (or -1 if no steps ever)
- [x] 6.3 Add `noStepWarning: Boolean` to `RecordingState` (consumed by `RecordingViewModel` via `serviceState`; not duplicated as a separate ViewModel-owned field)
- [x] 6.4 In `RecordingService.stateUpdateJob`: set `noStepWarning = true` when `secondsSinceLastStep() > 10`, else false
- [x] 6.5 Display sensor health warning below tracking confidence indicator in recording screen when `noStepWarning` is true
- [x] 6.6 Add "No steps" content description to sensor health warning `Surface` at `RecordingScreen.kt:214` (`Modifier.semantics { contentDescription = "No steps" }`)

## 7. Build & Verify

- [x] 7.1 Run `./gradlew clean assembleDebug` to verify no compilation errors
- [x] 7.2 Run `./gradlew test` to verify existing tests pass
- [x] 7.3 Verify DB migration still works (no schema changes introduced)