## Why

The app currently supports two recording modes (Outdoor, Indoor) that both assume a working position source. Neither works for mapping cellular coverage inside metro tunnels: Outdoor loses GPS fixes underground, and Indoor's pedestrian dead reckoning produces zero steps when the rider is stationary relative to the moving train. There is no way to record a tunnel ride today without producing a pile of samples all pinned to the same coordinate.

We need a recording mode purpose-built for linear, marker-driven coverage mapping — where the phone samples on a fixed time cadence and the user manually stamps the timeline with landmarks (stations, tunnel entry/exit) so the recorded samples can later be mapped to known tunnel geometry.

## What Changes

- Add a third recording mode `TUNNEL`, selectable in the New Session dialog alongside Outdoor and Indoor.
- Sample cell info and ping measurements on a pure time-driven cadence using the existing `recordingIntervalMs`. No GPS reading, no step detection, no `IndoorPositionCollector`. Cell records are written with `latitude=0, longitude=0` and `locationSource="TUNNEL"` as sentinels.
- Add a new `session_markers` table that records user-stamped events along the timeline, each with a `type` (STATION, TUNNEL_ENTRY, TUNNEL_EXIT, STOP, NOTE), a free-text `label`, a per-session sequence number, and a timestamp.
- Add a "Mark" button on the RecordingScreen that is visible only when `isTunnel && isRecording`. A quick tap records a `NOTE` marker with an auto-generated label (`#N HH:MM:SS`); a long-press opens a small dialog with type chips and an editable label.
- Add a "Mark Note" action button to the foreground service notification when in tunnel mode, allowing the user to quickly drop a `NOTE` marker directly from the lock screen without unlocking the device.
- Make the marker dialog type-aware: hide the label field for `TUNNEL_ENTRY` / `TUNNEL_EXIT` (the type is the entire semantic content), and show a free-text label field for `STATION`, `STOP`, and `NOTE`. The auto-label becomes type-prefixed (e.g., `STATION #5 14:32:05`) so the quick-tap and notification paths are self-describing on the timeline.
- Allow any marker's `type` and `label` to be edited after recording via the marker detail card (Replay) and the markers section row (Session Detail). `id`, `sessionId`, `timestamp`, and `seq` are immutable; editing updates the row in place.
- Suggest recently-used labels in the marker dialog based on the selected type, persisted globally across sessions in a new `recent_marker_labels` table. After the first ride on a line, common labels (e.g., "King's Cross") become one-tap chips above the label field, removing the typing burden for repeat riders without penalizing one-shot riders.
- Surface markers as vertical pins on the Replay timeline and as a collapsible "Markers (N)" section on the Session Detail screen.
- Export markers as a separate `markers_<session>.csv` file (alongside the existing cell-records CSV) and as `Point` features in the existing GeoJSON export, with a session-level `tunnelMode: true` flag mirroring the existing `indoorMode: true` convention.
- Round-trip markers through the Import session flow so tunnel sessions can be exported and re-imported losslessly.
- Require neither `ACTIVITY_RECOGNITION` nor background-location stalking for tunnel mode in the unified permission flow; the foreground service still needs the standard foreground permission and cell-info/ping plumbing.

## Capabilities

### New Capabilities
- `tunnel`: Linear, marker-driven recording mode for cellular coverage mapping inside tunnels. Covers the time-driven sampling loop, user-stamped session markers (types, labels, sequence), the Mark button UX, marker persistence, and round-trip export/import of markers.

### Modified Capabilities
- `recording`: Adds `TUNNEL` as a third `recordingMode` value and a third arm of the recording loop in `RecordingService` (time-driven ticker instead of GPS- or step-driven).
- `service`: Foreground service initializes tunnel mode with no GPS listener and no step/rotation sensor registration; only the cell-info and ping engines start.
- `permission-flow`: `TUNNEL` mode requires neither `ACTIVITY_RECOGNITION` nor the indoor permission path; foreground + cell-info permissions only.
- `data`: CSV export adds a `markers_<session>.csv` companion file; GeoJSON export emits marker `Point` features and a `tunnelMode` session flag; Import reads both back.
- `ui`: New Session dialog gains a Tunnel mode chip; RecordingScreen gains a Mark button; marker dialog is type-aware and offers recent-label suggestions; Replay shows markers as timeline pins (tappable to open an editable detail card); Session Detail shows a collapsible markers section with per-row edit and delete affordances.
- `sessions`: Session detail data model gains the markers collection; replay/scrubbing is aware of marker positions on the timeline; markers can be edited in place (type + label).

## Impact

- **Database**: New `session_markers` table (one migration in `AppDatabase`) plus a small app-local `recent_marker_labels` table for the label-suggestion feature. No schema change to `cell_records` — tunnel rows reuse existing non-nullable `latitude`/`longitude` columns with the `0,0` sentinel and disambiguate via `locationSource="TUNNEL"`.
- **Service**: `RecordingService` gains a `TUNNEL` branch in its recording-loop dispatcher (`recordingMode == "INDOOR" | "TUNNEL"`); `PointRecorder` gains a `recordTunnelPoint(...)` path that skips all position bookkeeping.
- **UI**: New Session dialog mode selector grows from two to three chips; RecordingScreen action row conditionally shows the Mark button (mirror of the existing `Reset Origin` visibility rule for indoor); Replay and Session Detail screens gain marker rendering.
- **Export/Import**: `ExportSessionUseCase` produces a second CSV file and additional GeoJSON features; `ImportSessionUseCase` reads them back. The `recent_marker_labels` table is app-local user state and is NOT included in session exports. Existing non-tunnel exports are unchanged.
- **Permissions**: `PermissionHelper` adds a tunnel-mode path with no sensor-specific permission gating.
- **Specs**: New `specs/tunnel/spec.md`; delta specs for `recording`, `service`, `permission-flow`, `data`, `ui`, `sessions`.
