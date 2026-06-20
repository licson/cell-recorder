## Context

Cell Recorder currently records cell signal data outdoors using GPS for position tracking. The recording service runs as a foreground service with `FOREGROUND_SERVICE_TYPE_LOCATION`, collecting location via `FusedLocationProviderClient` (with `GPS_PROVIDER` fallback) and cell info via NetMonster Core. Position triggers are distance-based (GPS movement) and time-based (periodic interval). During GPS loss, `SensorFusionCollector` provides dead-reckoning extrapolation using `TYPE_GAME_ROTATION_VECTOR` and `TYPE_LINEAR_ACCELERATION`.

Indoor coverage analysis is a common use case (buildings, basements, underground stations) where GPS is unavailable. The system needs a recording mode that uses IMU sensors instead of GPS for position estimation, with honest drift management UX.

## Goals / Non-Goals

**Goals:**
- Add an indoor recording mode that uses pedestrian dead reckoning (step detection + heading) instead of GPS
- Provide a 2D canvas visualization of the indoor movement path with signal color-coding
- Implement AR-inspired drift management: tracking confidence indicator, growing drift radius, origin reset
- Extend the data model to store relative coordinates for indoor sessions
- Support CSV and GeoJSON export for indoor session data

**Non-Goals:**
- Camera-based visual-inertial odometry (ARCore integration) for high-accuracy indoor positioning
- WiFi/Bluetooth fingerprinting or beacon-based indoor positioning
- Floor plan import or multi-floor/3D path tracking
- Step length calibration flow (just a default + settings slider)
- Magnetometer-based absolute heading (unreliable indoors due to metal/steel)
- Altitude/floor change tracking via barometer

## Decisions

### D1: Step detection via `TYPE_STEP_DETECTOR` for distance

**Choice**: Use Android's hardware step detector event (`TYPE_STEP_DETECTOR`) for per-step distance increments.

**Alternatives considered**:
- *Raw accelerometer integration*: Double-integrating acceleration produces quadratic drift. Step detection is far more stable because it uses zero-velocity updates (each footfall resets the integration baseline).
- *Activity recognition (`TYPE_STEP_COUNTER`)*: Provides cumulative step count but no per-step timing. `TYPE_STEP_DETECTOR` gives per-step events which are needed for real-time path updates.

**Rationale**: `TYPE_STEP_DETECTOR` provides clean per-step events with built-in zero-velocity detection. Each step increments distance by a configurable step length (default 0.7m). This is standard pedestrian dead reckoning.

### D2: Game rotation vector for heading

**Choice**: Use `TYPE_GAME_ROTATION_VECTOR` for heading estimation.

**Alternatives considered**:
- *`TYPE_ROTATION_VECTOR`*: Includes magnetometer, which is unreliable indoors (metal structures, rebar, electrical equipment distort the magnetic field).
- *Gyroscope-only integration*: Drifts without correction; game rotation vector fuses gyro + accelerometer internally.

**Rationale**: Game rotation vector uses gyroscope + accelerometer (no magnetometer), providing drift-resistant heading relative to the device's initial orientation. Heading is relative (not true north), but this is acceptable for indoor path tracing — the path shape is correct even if absolute orientation is unknown.

### D3: Relative (0,0) origin coordinate system

**Choice**: All indoor coordinates are meter offsets from a (0,0) origin.

**Rationale**: AR frameworks use local coordinate systems. Indoors, there's no reliable geographic reference. Relative coordinates are simple, accurate for path shape, and sufficient for coverage visualization. The user can mentally map the path to their building.

### D4: Time-based recording triggers only

**Choice**: Indoor mode records at fixed time intervals only (default 5s, configurable).

**Alternatives considered**:
- *Step-based triggers*: Record on each detected step. Too high-frequency (1-2 steps/sec walking) and creates uneven data density.
- *IMU-derived distance triggers*: Use dead-reckoning estimated distance as a trigger. Adds complexity — the estimated distance is already imprecise, so using it as a trigger doesn't improve over simple time intervals.

**Rationale**: Time-based triggers are simple, predictable, and produce evenly-spaced data points. The outdoor mode's distance trigger relies on GPS accuracy which doesn't apply indoors.

### D5: 2D Compose Canvas for path visualization

**Choice**: Render the indoor path on a Jetpack Compose Canvas with pan/zoom, signal-colored polyline, drift radius, and discontinuity markers.

**Alternatives considered**:
- *OSM map with fake coordinates*: OSM tiles show outdoor geography that's misleading indoors. A blank canvas is cleaner.
- *Floor plan overlay*: Requires users to import floor plan images — adds significant UI complexity and scope.
- *Data-only (export for external viz)*: No in-app visualization severely limits usability.

**Rationale**: A blank 2D canvas avoids the complexity of map/floor-plan integration while providing immediate visual feedback. Signal color-coding (RSRP-mapped colors on the path) gives coverage insight at a glance. The existing `MapDisplayMode` concept (SIGNAL_TRAILS, PACKET_LOSS, CELL_ID, RAT, BAND) carries over to the canvas — the same coloring and marker logic applies on a blank grid instead of map tiles.

### D6: Drift estimation model

**Choice**: Estimate drift as `stepCount * stepLength * driftRate`, where `driftRate` starts at 0.02 (2%) and grows linearly with time (capped at 0.20). The drift radius grows proportionally.

**Rationale**: This is a conservative, honest model. PDR drift comes from two sources: step length variability (varies per person, pace, surface) and heading error accumulation. A 2% drift rate means ~1m error per 50m walked initially, growing with time. This is realistic for consumer-grade IMU PDR. The visual drift radius communicates this honestly to the user.

### D7: Origin reset with discontinuity marker

**Choice**: When the user taps "Reset Origin", reset (X,Y) to (0,0) and heading to current orientation. Preserve old path segments and mark the discontinuity with a visible gap/break indicator on the canvas.

**Alternatives considered**:
- *Clear path on reset*: Loses valuable coverage data from before the reset.
- *Auto-scale to fit both segments*: Path "jumps" visually, which is confusing.
- *Offset old segments to align*: Requires knowing the true position difference, which we don't have.

**Rationale**: Preserving old path with a discontinuity marker keeps all coverage data while being honest about the position discontinuity. The user can trace which path segment corresponds to which part of their walk.

### D8: GeoJSON export for indoor sessions

**Choice**: Convert relative meter offsets to approximate lat/lon using `(0 + relativeX / 111320, 0 + relativeY / 111320)`. Add `"indoorMode": true` and `"coordinateReference": "relative"` properties to the FeatureCollection.

**Rationale**: This hack allows indoor sessions to use the same GeoJSON pipeline. The coordinates won't be geographically meaningful, but tools that parse GeoJSON will still render the path shape. The metadata flags make it clear these aren't real geographic coordinates. CSV export includes raw `relativeX`/`relativeY` columns for precision.

### D9: Sensor availability gating

**Choice**: Block indoor recording if `TYPE_STEP_DETECTOR` is unavailable. Fall back to `TYPE_ROTATION_VECTOR` if `TYPE_GAME_ROTATION_VECTOR` is unavailable.

**Rationale**: Step detection is fundamental to the PDR approach — without it, we can't estimate distance. Game rotation vector is preferred (no magnetometer), but `TYPE_ROTATION_VECTOR` is acceptable as fallback since heading drift is already expected. Most modern devices have both sensors.

### D10: DB migration v10 → v11

**Choice**: Add `recordingMode` (TEXT, default "OUTDOOR") to `sessions`, `relativeX` (REAL, nullable) and `relativeY` (REAL, nullable) to `cell_records`.

**Rationale**: Nullable columns with defaults ensure backward compatibility. Existing sessions and records remain valid. `recordingMode` defaults to "OUTDOOR" so all pre-existing sessions are correctly classified.

## Risks / Trade-offs

- **[Significant PDR drift]** → Mitigation: Honest drift UX (confidence indicator, growing radius), origin reset button, session time guidance (< 5 min recommended). Drift is inherent to IMU-only PDR; VIO would eliminate it but is far too complex for this scope.
- **[Step detector unavailable on some devices]** → Mitigation: Pre-flight sensor check before starting indoor recording; show clear error message. No software fallback for step detection (accelerometer-based step detection is unreliable and device-specific).
- **[Magnetometer interference indoors]** → Mitigation: Use game rotation vector (no magnetometer) by default. Accept that heading is relative, not absolute. For indoor coverage analysis, relative path shape is sufficient.
- **[Path discontinuity confusion after origin reset]** → Mitigation: Visual discontinuity marker on canvas. The gap makes it clear the path segments aren't spatially connected.
- **[GeoJSON coordinates are fake for indoor]** → Mitigation: `"indoorMode": true` metadata flag. CSV export provides precise relative coordinates. GeoJSON is best-effort for path shape visualization only.
