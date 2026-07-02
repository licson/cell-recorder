## Context

The app today supports two recording modes whose loops both assume a position source: Outdoor (GPS) and Indoor (pedestrian dead reckoning via `IndoorPositionCollector`). Neither works for mapping cellular coverage inside metro tunnels:

- Outdoor stops receiving useful GPS fixes underground, so no `LocationCollector` events fire.
- Indoor relies on `TYPE_STEP_DETECTOR` + `TYPE_GAME_ROTATION_VECTOR`, which both produce zero output when the rider is stationary relative to the moving train (no human gait impulses, and the device yaw does not track the train's heading).

The result is a pile of samples all pinned to the same coordinate, with the apparent success of the recording hiding the failure.

`RecordingService` already branches on `recordingMode == "INDOOR"` to switch the loop (`RecordingService.kt:178`). The codebase also already supports a "mode-specific button" pattern (`Reset Origin` is rendered only when `isIndoor && isRecording`, `RecordingScreen.kt:250-256`), a mode chip selector in the New Session dialog (`SessionListScreen.kt:551-562`), and a mode-specific permission path through `PermissionHelper`. Tunnel mode reuses all of these patterns.

The recorded data model (`CellRecordEntity`) has non-nullable `latitude`/`longitude` columns. Indoor mode already uses `0,0` as a sentinel disambiguated by `locationSource = "INDOOR_IMU"` and non-null `relativeX`/`relativeY`. Tunnel mode follows the same precedent: `0,0` sentinel disambiguated by `locationSource = "TUNNEL"` and null `relativeX`/`relativeY`. No `cell_records` schema change is required.

## Goals / Non-Goals

**Goals:**
- A `TUNNEL` recording mode that produces a usable linear coverage map: cell measurements on a fixed time cadence with no position bookkeeping at sample time.
- A first-class marker concept (`session_markers` table) for user-stamped landmarks along the timeline, with structured types and optional labels.
- A Mark button on the RecordingScreen with both a one-tap quick-mark path and a long-press dialog path, mirroring the conditional visibility of `Reset Origin`.
- Markers visible as timeline pins on the Replay screen and as a collapsible section on the Session Detail screen.
- Markers exported (separate `markers_<session>.csv` and as GeoJSON `Point` features) and re-imported, with a `tunnelMode: true` session-level flag.
- No `cell_records` schema migration — tunnel rows reuse existing columns with sentinels.

**Non-Goals:**
- Opportunistic GPS capture on above-ground metro segments. (Decided against in exploration: pure tunnel mode for v1; the user maps time → distance offline via known geometry. Can be revisited as a v2.)
- Any form of vehicle dead reckoning (IMU double-integration, train kinematics, map matching to metro geometry). Out of scope; mode A from the exploration phase.
- Distance estimation along the tunnel by the app itself. Distance mapping is a post-processing concern.
- Reusing indoor's `relativeX`/`relativeY` columns for a linear coordinate. Tunnel rows keep these null; the timeline axis is timestamp, not distance.
- Analytics classification of tunnel segments. (The `analytics` spec is not modified; tunnel sessions simply have no mobility segments in v1.)
- Markers on non-tunnel sessions. The schema is generic enough to allow it later, but the UI only exposes markers in tunnel mode for v1.

## Decisions

### D1. Sampling loop: pure time-driven ticker

**Choice:** Add a `TUNNEL` arm to the recording loop in `RecordingService` that ticks on `recordingIntervalMs` (the existing outdoor interval config, default 5000 ms) and calls a new `PointRecorder.recordTunnelPoint(...)` that writes a `CellRecordEntity` with `latitude = 0, longitude = 0, locationSource = "TUNNEL"`, skipping all `relativeX`/`relativeY` bookkeeping.

**Rationale:** A `Handler.postDelayed`-style ticker (or a `delay()` inside a `launch` block) is the simplest, most reliable trigger when there is no external event source. No sensor registration, no `LocationCollector`, no `IndoorPositionCollector`. Reusing `recordingIntervalMs` keeps the existing Settings row semantics ("min time between samples") and avoids adding a new config.

**Alternatives considered:**
- *Re-use the indoor loop body but feed zero steps:* Rejected. Indoor's loop is gated on `indoorPositionCollector.positionUpdate.collect { ... }`, which only emits on step/rotation events — so a stationary-in-train user would never get a sample. Would require either faking emissions or adding a parallel ticker, both messier than a dedicated arm.
- *Reuse outdoor's `locationChangeThresholdM` distance trigger:* Rejected. Tunnel mode has no location, so the distance trigger can never fire. Only the `recordingIntervalMs` fallback would fire, and the code paths for distance-vs-time gating would be dead branches.
- *New `tunnelRecordingIntervalMs` config:* Rejected in exploration. The existing interval already has the right semantics.

### D2. Markers: new `session_markers` table, generic schema

**Choice:** New Room entity:

```
session_markers
──────────────────────────────────────────────
 id           INTEGER PK AUTOINCREMENT
 sessionId    INTEGER FK → sessions.id (CASCADE)
 timestamp    INTEGER NOT NULL  (wall clock, indexed)
 seq          INTEGER NOT NULL  (per-session counter, starts at 1)
 type         TEXT NOT NULL    (STATION | TUNNEL_ENTRY | TUNNEL_EXIT | STOP | NOTE)
 label        TEXT             (free text, nullable)
──────────────────────────────────────────────
 INDEX (sessionId, timestamp)
```

**Rationale:** Keeping markers in their own table leaves `cell_records` schema untouched and matches the precedent of `SpeedTestRecordEntity` having its own table. A generic schema (not tunnel-specific columns) lets markers be reused on other modes in the future without another migration. The per-session `seq` counter is preserved on insert so markers can be re-ordered by their original creation order, not just wall-clock time (relevant if multiple markers are batched, e.g., import).

**Alternatives considered:**
- *Marker rows inside `cell_records` with a `markerType` column:* Rejected. Would require a schema migration on the hottest table in the app, and the row semantics are completely different (markers have no cell info).
- *A `relativeX`/`relativeY`-style marker:* Rejected. Tunnel markers are temporal, not spatial.
- *`type` as an enum constant stored as `Int`:* Rejected. Text is more debuggable in SQLite, matches the codebase's pattern of storing `recordingMode` and `locationSource` as text, and the cardinality is small enough that index performance is a non-issue.

### D3. Marker UI: quick-tap, long-press, and persistent notification

**Choice:** The Mark button appears on `RecordingScreen` only when `isTunnel && isRecording`. Quick tap creates a `NOTE` marker with auto-label `"#<seq> HH:MM:SS"`. Long-press opens a small dialog with the five type chips, an optional label field, and a confirm button. Additionally, a "Mark Note" action button is added to the ongoing foreground notification during tunnel mode.

**Rationale:** A moving train means typing is impractical most of the time. The quick-tap path is the 80% case; long-press is the power-user path for distinguishing stations from tunnel entry/exit. Auto-label includes the sequence number so the user can later correlate "#3 14:32:05" with offline notes. Long-press avoids the dialog hijacking the screen on accidental taps. The notification action allows users to drop a marker directly from the lock screen without unlocking their phone or navigating back to the app, which is critical for reducing friction while standing on a moving train.

**Alternatives considered:**
- *Always show the dialog:* Rejected. Too slow when the user is repeatedly stamping stations as they pass.
- *Two separate buttons (Mark Note / Mark Station):* Rejected. Crowds the action row; the long-press shortcut handles it.
- *Voice input:* Rejected for v1; out of scope.
- *Volume key interception:* Rejected. While highly tactile, Android only delivers volume key events reliably when the app is in the foreground and the screen is on. The notification action provides background/lock-screen reliability without hardware hijacking.

### D4. Markers in Replay and Session Detail

**Choice:** Replay timeline draws markers as vertical pins at their timestamp's x-position. Session Detail gains a collapsible "Markers (N)" section above the existing cell-records list, showing each marker as a row (`#seq  type  label  timestamp`).

**Rationale:** Replay already groups records by timestamp on a timeline (`SessionDetailViewModel.TimestampGroup`), so adding marker pins is a natural extension. The Session Detail section is the user's reading view; a collapsible section keeps it from dominating the cell-records list.

**Alternatives considered:**
- *Markers in Replay only:* Rejected per user preference (both requested).
- *Markers as a separate screen reachable from Session Detail:* Rejected. Adds a navigation hop and a new screen; a collapsible section is enough.

### D5. Export: parallel markers CSV + GeoJSON Point features

**Choice:**
- CSV: emit `markers_<session>.csv` alongside the existing cell-records CSV when the session has markers. Columns: `timestamp,seq,type,label`. This mirrors the existing `session_name_speedtest.csv` precedent.
- GeoJSON: emit each marker as a `Point` feature in the existing FeatureCollection. Properties: `markerType`, `label`, `seq`. Geometry coordinates are `[0, 0]` since tunnel markers are temporal. Add a session-level `"tunnelMode": true` flag mirroring the existing `"indoorMode": true` convention.
- Import: read both back. CSV detection: presence of a `markers_*.csv` file. GeoJSON detection: presence of features with `markerType` property.

**Rationale:** The CSV side mirrors the speedtest precedent exactly, so the export/import UI plumbing (multi-file export, zip handling if needed) is the same pattern. The GeoJSON side keeps everything in one file for users who want a single self-describing export.

**Alternatives considered:**
- *Markers in the same CSV as cell records:* Rejected. The schemas are unrelated; mixing them creates a malformed row schema.
- *Markers as a separate GeoJSON file:* Rejected. GeoJSON FeatureCollections can hold heterogeneous feature types cleanly; one file is more convenient for users.

### D6. Permissions: no sensor or background-location stalking for tunnel

**Choice:** `PermissionHelper` gains a tunnel-mode path that requires only the foreground service permission and the phone-state permission for cell info — no `ACTIVITY_RECOGNITION` (indoor-only) and no special background-location stalking beyond what the foreground service already requires. The `PermissionHelper.indoorPermissions()` and `missingPermissionsForMode(...)` switch gain a tunnel arm.

**Rationale:** Tunnel mode touches no sensors and no location; the foreground service exists only because Android requires it for an active long-running task while the screen is off. There is nothing extra to request.

**Alternatives considered:**
- *Require `ACCESS_BACKGROUND_LOCATION` "just in case":* Rejected. The foreground service handles background execution; background location is not used.

### D7. Recording-mode string: `"TUNNEL"`

**Choice:** `SessionEntity.recordingMode` stores `"TUNNEL"`. UI selector adds a third `FilterChip` to the New Session dialog. `isTunnel` flag computed locally on screens as `recordingMode == "TUNNEL"`.

**Rationale:** Matches the existing `OUTDOOR` / `INDOOR` text-constant convention; no enum refactor needed.

### D8. Marker editing: type and label editable in place

**Choice:** Markers can be edited after recording via the marker detail card (Replay) or the markers-section row (Session Detail). Editing updates the existing row in place; `id`, `sessionId`, `timestamp`, and `seq` are immutable. Only `type` and `label` can be changed. The same `MarkerDialog` composable is reused for both create and edit (title changes from "New Marker" to "Edit Marker"; an additional "Delete" button is shown in edit mode).

**Rationale:** The user explicitly requested post-recording editing. On a moving train, the realistic workflow is: drop markers during the ride with whatever label fits in the moment (often no label, or a typed-short label), then refine labels and fix types afterward when reviewing the session. Reusing the create dialog for editing avoids a second composable and keeps the UX consistent. Making `seq`/`timestamp` immutable means the timeline ordering never changes on edit — important because the user may be correlating marker positions against cell-record samples that are also on the timeline.

**Alternatives considered:**
- *Edit only the label, freeze the type:* Rejected. A user may quick-tap a NOTE during a ride and later realize it was actually a STATION they meant to mark. Type is cheap to change in-place.
- *Soft-delete + re-insert instead of in-place update:* Rejected. Would change the `seq` (a new insert gets the next `seq`), breaking the timeline ordering the user is staring at while editing.
- *A separate "Edit Marker" screen:* Rejected. Adds a navigation hop; the same dialog used for creation is sufficient.

### D9. Recent label suggestions: app-local `recent_marker_labels` table

**Choice:** Add a small app-local table:

```
recent_marker_labels
──────────────────────────────────────────────
 type      TEXT NOT NULL  (e.g., "STATION")
 label     TEXT NOT NULL  (e.g., "King's Cross")
 useCount  INTEGER NOT NULL DEFAULT 1
 lastUsed  INTEGER NOT NULL  (wall clock)
 PRIMARY KEY (type, label)
──────────────────────────────────────────────
```

On every marker insert OR edit with a non-null, non-empty `label`, upsert into this table: increment `useCount` and update `lastUsed` for an existing `(type, label)` row, or insert a new row with `useCount = 1`. In the `MarkerDialog`, when a type is selected that exposes the label field (per D10), render a `FlowRow` of the top-N (default 20, capped at 20) most-recently-used labels for that type as chips above the label field. Tapping a chip fills the label field but keeps the dialog open (lets the user tweak before saving — safer than immediate save). This table is global across sessions; it is NOT included in session CSV/GeoJSON export (it's user state, not session data).

**Rationale:** The user explicitly said "don't assume user behaviour. Cater for both" — meaning we should not assume either repeated rides or one-shot rides. Recent-labels caters for both: repeat riders on the same line get one-tap chips for station names they've used before; one-shot riders see an empty chip row on their first ride and simply type (the chip row is unobtrusive when empty). The table is global rather than per-line because the user's session-name field doesn't enforce which line they're on, and overlapping station names across lines (e.g., shared interchange stations) are a feature, not a bug — the user knows which session they're in.

**Alternatives considered:**
- *Per-session recents:* Rejected. Defeats the purpose — recents need to persist across sessions to help on the next ride.
- *Per-line recents keyed off session name parsing:* Rejected. Fragile, depends on naming convention.
- *Pre-loaded station list:* Rejected for v1. Adds setup friction (paste a list before each ride) that recent-labels avoids. Could be revisited as a v3 if recents prove insufficient for some users.
- *Voice input:* Rejected for v1. Adds a permission, async UX, and noise-accuracy tradeoffs. Recent-labels solves the 80% case (frequently-used station names) without these costs.
- *Decrement `useCount` on marker delete:* Rejected. The user might delete a marker by accident; the label should stay in recents. A future "clear recents" affordance in Settings (out of scope for v1) can handle the cleanup case.
- *Chip-tap saves immediately:* Rejected. "Fill and keep dialog open" is safer — the user can tweak "King's Cross" → "King's Cross St Pancras" before saving, without invoking the edit flow. The extra tap on Save is cheap.

### D10. Type-aware marker dialog: hide label for boundaries

**Choice:** The `MarkerDialog` conditionally renders the label field based on the selected type:
- `TUNNEL_ENTRY`, `TUNNEL_EXIT`: the label field is NOT shown. The type is the entire semantic content of the marker; there's nothing meaningful to label.
- `STATION`, `STOP`, `NOTE`: the label field IS shown, optional, single-line. Recent-label chips (per D9) appear above the field for these three types when recents exist.

**Rationale:** Five chips plus an always-visible label field creates decision fatigue on a moving train. Hiding the field for the two boundary types removes a "what do I type for TUNNEL_ENTRY?" question the user shouldn't have to answer. For the remaining three types, the field is optional — auto-label (per D11) is sufficient if the user skips typing.

**Alternatives considered:**
- *Always show the label field:* Rejected. Creates a "do I fill this in?" question for boundary markers that has no good answer.
- *Different chip sets for boundary vs. non-boundary types:* Rejected. The five-type enum is a single mental model; splitting it complicates the UI without value.

### D11. Auto-label format: type-prefixed

**Choice:** The auto-label generated for the quick-tap and notification paths uses the format `<TYPE> #<seq> HH:MM:SS` (e.g., `STATION #5 14:32:05`), not the originally planned `#<seq> HH:MM:SS`.

**Rationale:** The quick-tap and notification paths skip the dialog, so the type isn't visible anywhere on the timeline pin without opening it. A type-prefixed auto-label makes the pin self-describing — the user can tell a STATION from a NOTE on the timeline without an extra tap. The length cost is small (~8 chars) and the user can edit the label after recording (per D8) if they want to replace it with a station name.

**Alternatives considered:**
- *Short type codes (`S#5 14:32:05`):* Rejected. Ambiguous (S = STATION or STOP?), harder to read in CSV exports.
- *No type prefix (`#5 14:32:05`):* Rejected. The original plan; loses self-describing affordance on the timeline.

## Risks / Trade-offs

- **[Marker edit loses original label from recents]** When a user edits a marker's label, the new label is upserted into `recent_marker_labels` but the old label's `useCount` is NOT decremented (per D9's "no decrement" choice). Over many edits, the recents table can accumulate stale entries.
  → Mitigation: Acceptable for v1 — the table is small (capped at 20 per type on display, unbounded on disk but unlikely to grow large in practice), and a future "clear recents" affordance in Settings (out of scope) can reset it. If it becomes a real problem, add a background cleanup that drops entries with `useCount = 0` after 30 days.

- **[Recent labels leak cross-session information]** The `recent_marker_labels` table is global, so labels from session A appear as suggestions in session B. A user riding the Piccadilly line will see Central-line station names as suggestions.
  → Mitigation: This is intentional per the "cater for both" decision — shared interchange stations benefit from this, and the user knows which session they're in. If a user is surprised by cross-session labels, the chip row is unobtrusive when irrelevant — they simply don't tap it.

- **[Type-prefix length in CSV/GeoJSON]** The auto-label is now type-prefixed, which lengthens the `label` column in exports. CSV escaping already handles this; no action needed.
  → Mitigation: None needed. The label is already escaped per the existing CSV helper.

- **[Sentinel ambiguity]** Tunnel rows use `latitude = 0, longitude = 0`. A naive consumer that ignores `locationSource` will think tunnel rows are at the Gulf of Guinea off Africa.
  → Mitigation: `locationSource = "TUNNEL"` is non-null on every tunnel row; export adds a `tunnelMode` session flag; UI guards on `isTunnel` so maps are never shown for tunnel sessions. Document the sentinel in the data spec delta.

- **[Marker stamp race]** The user taps the Mark button at the same instant a recording tick fires. The marker and the cell record get timestamps within a few ms of each other but are written to different tables.
  → Mitigation: Both writes go through `RecordingMutex.withLock` on the service's recording mutex so they are serialized. Marker timestamp is wall-clock at lock acquisition; cell-record timestamp is wall-clock at sample time. Both are first-class and need no cross-table foreign key.

- **[Import path complexity]** Today the import path detects indoor via the presence of `relativeX` / `relativeY` columns or the `"indoorMode"` GeoJSON flag. Tunnel adds a third branch.
  → Mitigation: Detection is independent: tunnel CSV is a separate file, so the import flow checks for `markers_*.csv` presence first; for GeoJSON, `"tunnelMode": true` is checked independently of `"indoorMode"`. Add unit tests covering all three mode-detection branches.

- **[Marker types vs. free-form drift]** A fixed enum (`STATION | TUNNEL_ENTRY | TUNNEL_EXIT | STOP | NOTE`) might not cover everything the user encounters (e.g., "junction", "depot", "changeover").
  → Mitigation: The `NOTE` type + free-text `label` field covers anything outside the enum. New types can be added to the enum later without a schema migration (text column).

- **[Marker button on accidental tap]** Quick tap creates a marker the user didn't intend.
  → Mitigation: Markers are visible immediately on the timeline and the Session Detail section, with an inline delete affordance. Deletion is a soft-delete (no undo), but the marker count is small and the action is rare. Long-press is the dialog path, not the deletion path.

- **[Schema migration ordering]** Adding `session_markers` requires a new `Migration` and a `@Database(version = N+1)` bump. Existing users upgrade transparently.
  → Mitigation: The migration is purely additive (`CREATE TABLE`), so it follows the established pattern. Add a `MigrationTest` case following the precedent in `MigrationTest.kt`. Update the `Database upgrade path coverage` requirement in `data/spec.md` if the requirement needs to mention the new version (it currently states the rule generically).

## Migration Plan

1. Bump `@Database(version = N+1)` in `AppDatabase.kt` and add a `Migration_N_to_N+1` that `CREATE TABLE`s `session_markers` (per D2) AND `CREATE TABLE`s `recent_marker_labels` (per D9) with their FKs, indexes, and columns. Register the migration in `DatabaseModule.addMigrations(...)`.
2. Add `SessionMarkerEntity` + `SessionMarkerDao` + `SessionMarkerRepository`; add `RecentMarkerLabelEntity` + `RecentMarkerLabelDao` + `RecentMarkerLabelRepository`. No data backfill needed (both tables start empty).
3. Add the `TUNNEL` arm to `RecordingService` and `PointRecorder.recordTunnelPoint(...)`. No `cell_records` schema change.
4. Add the Mark button + type-aware `MarkerDialog` (per D10) + recent-labels chips (per D9) to `RecordingScreen`. Wire `RecordingViewModel` to call the repositories.
5. Add markers to `ExportSessionUseCase` and `ImportSessionUseCase`. The `recent_marker_labels` table is NOT exported.
6. Add marker pins to `ReplayScreen` (tappable to open editable `MarkerDialog` in edit mode) and a collapsible "Markers (N)" section with per-row edit and delete affordances to `SessionDetailScreen`.
7. Update `PermissionHelper` to handle the tunnel-mode permission path.
8. Update specs: `recording`, `service`, `permission-flow`, `data`, `ui`, `sessions` delta; new `tunnel` spec.

**Rollback:** Each step is independently revertible. Reverting step 1 (the migration) leaves unused `session_markers` and `recent_marker_labels` tables on existing installs; if necessary, follow up with a column-drop migration (using the table-rebuild pattern per `data/spec.md`'s column-dropping scenario). Tunnel sessions recorded before rollback remain valid — their `cell_records` rows have `locationSource = "TUNNEL"` and are simply not filterable by the rolled-back UI. The `recent_marker_labels` table can be dropped with no data loss (it is purely derived).

## Open Questions

- **Marker deletion UX:** Should the Replay timeline pins and the Session Detail section both expose delete, or only one of them? (Recommendation: delete from both. Both already offer edit per D8; delete alongside edit is consistent and discoverable.)
- **Marker ordering by `seq` vs. by `timestamp`:** If two markers are batched on import with the same `timestamp`, the `seq` counter preserves insertion order. The Replay timeline and Session Detail should sort by `(timestamp, seq)` to be deterministic. Confirm this is acceptable.
- **Markers shared across re-imports:** When importing a CSV that contains a `markers_*.csv` companion, should the import reset `seq` to start at 1, or preserve the original `seq` values? (Recommendation: preserve original `seq` from the file; the parser treats `seq` as authoritative when present, otherwise assigns sequentially on insert.)
- **Empty-tunnel-session export:** A tunnel session with zero cell records but several markers — should the export still emit the (empty) cell-records CSV plus the markers CSV? (Recommendation: yes, mirror the speedtest behavior where the file is emitted whenever the session has any matching records, but skip the markers file when the session has zero markers.)
- **Clear recents affordance:** Should the Settings screen gain a "Clear recent marker labels" button to reset the `recent_marker_labels` table? (Recommendation: defer to v2. The table is small and unobtrusive; if it accumulates cruft, a manual reset can ship later.)
