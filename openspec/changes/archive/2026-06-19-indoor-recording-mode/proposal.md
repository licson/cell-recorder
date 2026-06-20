## Why

Cell Recorder currently only supports outdoor recording that depends on GPS for position tracking. Indoor coverage analysis — walking through buildings, basements, or underground stations — is a common use case where GPS is unavailable or unreliable. Users need a way to record cell signal data indoors and visualize their movement path without geographic coordinates.

## What Changes

- Add an **indoor recording mode** alongside the existing outdoor mode, selectable at session creation
- Replace GPS-based position tracking with **IMU-based pedestrian dead reckoning (PDR)** using step detection (`TYPE_STEP_DETECTOR`) and heading estimation (`TYPE_GAME_ROTATION_VECTOR`) to estimate relative movement from a (0,0) origin
- Use **time-based recording triggers** (configurable interval) instead of GPS distance/time triggers for indoor mode
- Visualize indoor paths on a **2D Compose Canvas** with signal-colored path, pan/zoom, and drift indicators instead of a map
- Add **AR-inspired drift management UX**: tracking confidence indicator (🟢🟡🔴), growing drift radius circle on canvas, and "Reset Origin" button to recalibrate position mid-session
- Extend the data model with `recordingMode` on sessions and `relativeX`/`relativeY` coordinates on cell records
- Support CSV and GeoJSON export for indoor sessions (GeoJSON uses approximate lat/lon conversion with `"indoorMode": true` metadata flag)

## Capabilities

### New Capabilities
- `indoor-positioning`: Step-detection-based pedestrian dead reckoning, heading estimation, drift model, origin reset, and indoor position state management

### Modified Capabilities
- `recording`: Indoor recording lifecycle, time-based triggers (no GPS), indoor position collection instead of LocationCollector, indoor path storage
- `sessions`: Recording mode field on SessionEntity, indoor session creation UI, indoor detail/replay with 2D canvas instead of map
- `ui`: Indoor path canvas composable, tracking confidence indicator, origin reset button, recording mode selector in session creation, indoor settings section
- `data`: Indoor CSV columns (relativeX, relativeY), indoor GeoJSON export with approximate lat/lon and indoorMode flag
- `service`: Indoor mode service behavior (no GPS foreground service type change, no fallback recording job, time-based triggers only)
- `analytics`: Indoor sessions excluded from geographic analytics (coverage maps, geographic handoff)

## Impact

- **Data model**: SessionEntity gains `recordingMode`, CellRecordEntity gains `relativeX`/`relativeY` — requires DB migration v10→v11
- **RecordingService**: Branches on recording mode for position collection and trigger logic
- **PointRecorder**: Accepts indoor position updates, populates relative coordinates, sets locationSource to "INDOOR_IMU"
- **RecordingScreen**: Conditionally renders 2D canvas vs. map based on mode
- **Export**: CSV/GeoJSON pipelines extended with indoor-specific columns and metadata
- **Settings**: New indoor-specific configuration (step length, recording interval)
- **Sensors**: Requires `TYPE_STEP_DETECTOR` and `TYPE_GAME_ROTATION_VECTOR` (or `TYPE_ROTATION_VECTOR` fallback) — blocks indoor recording if unavailable
