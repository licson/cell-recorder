## 1. Permission — Add ACTIVITY_RECOGNITION

- [x] 1.1 Add `<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />` to `AndroidManifest.xml`
- [x] 1.2 Add `Manifest.permission.ACTIVITY_RECOGNITION` to `PermissionHelper.foregroundPermissions()` for Android 10+ (API 29+)
- [x] 1.3 Update permission request flow in `RecordingScreen.kt` to include `ACTIVITY_RECOGNITION` before starting indoor recording
- [x] 1.4 Add indoor mode permission check in `RecordingViewModel`: verify `ACTIVITY_RECOGNITION` is granted before allowing start

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

- [x] 4.1 Track which sensor registrations succeeded in `IndoorPositionCollector` (step detector, accelerometer, rotation vector booleans)
- [x] 4.2 Add `isAnyStepDetectionActive()` method returning true if at least one step source (TYPE_STEP_DETECTOR or accelerometer) is active
- [x] 4.3 In `RecordingService.startRecording()` for indoor mode: abort with error if `IndoorPositionCollector` reports no active step detection after registration
- [x] 4.4 Check `registerListener()` return values and propagate failures

## 5. UI — Ping in Indoor LiveStatsBar

- [x] 5.1 In `RecordingScreen.LiveStatsBar`: show ping latency line for indoor mode (remove the `if (!isIndoor)` guard on the ping display)
- [x] 5.2 Format indoor ping display as "Ping: X.X ms" without GPS/lat/lon fields

## 6. UI — Sensor Health Monitoring

- [x] 6.1 Add `lastStepTimeMs` tracking in `IndoorPositionCollector`, updated on every step
- [x] 6.2 Add `secondsSinceLastStep()` method returning elapsed seconds since last step (or -1 if no steps ever)
- [x] 6.3 Add `noStepWarning: Boolean` to `RecordingState` and `RecordingViewModel`
- [x] 6.4 In `RecordingService.stateUpdateJob`: set `noStepWarning = true` when `secondsSinceLastStep() > 10`, else false
- [x] 6.5 Display sensor health warning below tracking confidence indicator in recording screen when `noStepWarning` is true
- [x] 6.6 Add "No steps" content description to sensor health warning

## 7. Build & Verify

- [x] 7.1 Run `./gradlew clean assembleDebug` to verify no compilation errors
- [x] 7.2 Run `./gradlew test` to verify existing tests pass
- [x] 7.3 Verify DB migration still works (no schema changes introduced)