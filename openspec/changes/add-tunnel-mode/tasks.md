## 1. Database Schema and Migration

- [ ] 1.1 Add `SessionMarkerEntity` data class with columns: `id` (PK autoincrement), `sessionId` (FK to `sessions.id` with `ON DELETE CASCADE`), `timestamp` (Long), `seq` (Int), `type` (String), `label` (String?); add foreign key declaration, the `Index("sessionId", "timestamp")` index, and a single-column index on `sessionId` for the FK
- [ ] 1.2 Add `RecentMarkerLabelEntity` data class with columns: `type` (String), `label` (String), `useCount` (Int, default 1), `lastUsed` (Long); primary key is `PrimaryKey(type, label)`; no foreign keys (app-local user state)
- [ ] 1.3 Add both entities to the `entities` array in `@Database(...)` in `AppDatabase.kt` and bump `@Database(version = N+1)`
- [ ] 1.4 Add `Migration_N_to_N+1` that `CREATE TABLE`s `session_markers` and `recent_marker_labels` with their FKs, indexes, and columns; register it in `DatabaseModule.addMigrations(...)` in the existing chain order
- [ ] 1.5 Add a row-seeded migration test in `MigrationTest.kt` (following the existing column-dropping/rebuild precedent): seed a v-N database with one session and zero markers, run the migration, assert both new tables exist with the expected schemas and no rows; assert existing `cell_records` data round-trips unchanged
- [ ] 1.6 Add `SessionMarkerDao` with `insert(marker)`, `update(marker)`, `getBySessionId(sessionId): Flow<List<SessionMarkerEntity>>`, `deleteById(id)`, `countBySessionId(sessionId): Int`; add `RecentMarkerLabelDao` with `upsert(entity)`, `getByTypeOrdered(type): List<RecentMarkerLabelEntity>`, `deleteAll()`; add both to `@Database` DAO list
- [ ] 1.7 Add `SessionMarkerRepository` (interface + Room-backed impl) with `insertMarker(sessionId, type, label)` that computes the next `seq` per-session inside a transaction AND upserts into `recent_marker_labels` if `label` is non-null/non-blank; `updateMarker(id, type, label)` that updates the existing row in place (immutable `seq`/`timestamp`/`sessionId`) AND upserts into `recent_marker_labels` if the new `label` is non-null/non-blank (without decrementing the old label); `getMarkersForSession(sessionId): Flow<List<SessionMarkerEntity>>`; `deleteMarker(id)`; `MarkerType` enum with the generalized universal values `WAYPOINT`, `SEGMENT_START`, `SEGMENT_END`, `STOP`, `NOTE` (per design D13) and `toStorageString()`/`fromStorageString()` helpers
- [ ] 1.8 Add `ConfigDaoTest` / new `SessionMarkerDaoTest` and `RecentMarkerLabelDaoTest` covering: insert/get/delete for markers; `update()` updates the row in place without changing `seq`; `seq` increment per session (two sessions each get `seq=1`); cascade delete when session is deleted; upsert into `recent_marker_labels` increments `useCount` and updates `lastUsed`; `getByTypeOrdered` returns rows sorted by `lastUsed` descending; `recent_marker_labels` survives session deletion
- [ ] 1.9 Update `ConfigRepositoryTest` (or new test) to verify the new DAOs/migration are wired into Hilt's `DatabaseModule`

## 2. Recording Service — Tunnel Mode Branch

- [ ] 2.1 In `RecordingService.kt` recording job, add a `when (recordingMode) { "OUTDOOR" -> {...}; "INDOOR" -> {...}; "TUNNEL" -> {...} }` branch (or extend the existing `if/else` into a `when`); tunnel branch starts a periodic ticker using `delay(config.recordingIntervalMs)` inside a `launch` block — no `LocationCollector`, no `IndoorPositionCollector`
- [ ] 2.2 Add `PointRecorder.recordTunnelPoint(sessionId, config, activeSubs, pingWindow)` that writes one `CellRecordEntity` per SIM slot in a batched transaction with `latitude = 0.0, longitude = 0.0, altitude = 0.0, accuracy = 0f, relativeX = null, relativeY = null, locationSource = "TUNNEL", isLocationEstimated = false`; reuse the existing `recordPoint(...)` multi-SIM batch logic where possible
- [ ] 2.3 In `RecordingService.kt` state-update job (around line 422), add a tunnel branch that populates `currentRelativeX = null`, `currentRelativeY = null`, `currentHeading = null`, `currentStepCount = null`, `estimatedDriftM = null`, `noStepWarning = false` — tunnel mode never has these
- [ ] 2.4 In `RecordingService.kt` stop/cleanup, add the tunnel branch (mostly a no-op beyond stopping the ticker job; no sensors to unregister)
- [ ] 2.5 Update the notification content for tunnel mode: elapsed time, point count, and marker count (replace "GPS status"/"tracking confidence" with "N markers"); add a `markerCount` field to `RecordingState` so the state-update job can populate it. Add a "Mark Note" notification action for EVERY recording mode (OUTDOOR, INDOOR, TUNNEL) that calls `SessionMarkerRepository.insertMarker(...)` directly from the service (bypasses `RecordingViewModel`), using an immutable `PendingIntent` with `FLAG_IMMUTABLE` (per design D3 and D12 — universal markers)
- [ ] 2.6 Add a `RecordingServiceTest` (or extend the existing instrumented test) covering: tunnel mode starts a ticker, writes N rows at `recordingIntervalMs` cadence, with `locationSource = "TUNNEL"` and sentinel coordinates; multi-SIM batched write works; max-recording-duration auto-stop works in tunnel mode; tapping "Mark Note" in the notification intent creates a NOTE marker serialized through the same mutex as recording ticks, in EVERY recording mode (OUTDOOR, INDOOR, TUNNEL)

## 3. Marker Creation and Editing — Repository and ViewModel Wiring

- [ ] 3.1 In `RecordingViewModel`, inject `SessionMarkerRepository` and `RecentMarkerLabelRepository`; expose `markers: StateFlow<List<SessionMarkerEntity>>` collected from `repository.getMarkersForSession(sessionId)`
- [ ] 3.2 Add `fun quickMark(): Job` to `RecordingViewModel` that calls `repository.insertMarker(sessionId, type = NOTE, label = "NOTE #<nextSeq> HH:MM:SS")` (type-prefixed auto-label per `markers/spec.md`); available in ANY recording mode per D12
- [ ] 3.3 Add `fun createMarker(type: MarkerType, label: String?): Job` for the long-press dialog path (passes null if label is blank or hidden for the selected type per `markers/spec.md`)
- [ ] 3.4 Add `fun editMarker(id: Long, type: MarkerType, label: String?): Job` for the post-recording edit path (calls `repository.updateMarker(...)` which updates the row in place and upserts into `recent_marker_labels`)
- [ ] 3.5 Add `fun deleteMarker(id: Long): Job` for replay/detail deletion
- [ ] 3.6 Serialize marker inserts/edits and recording ticks through the existing recording mutex (or a dedicated marker mutex in `RecordingService`) to satisfy the "marker creation serialized with recording tick" requirement in `markers/spec.md`
- [ ] 3.7 Add a `RecordingViewModelTest` covering: `quickMark()` inserts a NOTE marker with auto-label `"NOTE #N HH:MM:SS"`; `createMarker(WAYPOINT, "Central")` inserts with the given type/label; `editMarker(id, WAYPOINT, "King's Cross")` updates the row in place without changing `seq`; `seq` increments monotonically per session; concurrent `quickMark` calls do not collide on `seq` (use `runTest` + a fake repository with a synchronized counter); editing a marker's label upserts into `recent_marker_labels` with the new label (and does NOT decrement the old label)

## 4. RecordingScreen — Mark Button (Universal), Type-Aware MarkerDialog, and Tunnel Layout

- [ ] 4.1 In `RecordingScreen.kt`, compute `val isTunnel = recordingMode == "TUNNEL"` next to `isIndoor`. (The `isTunnel` flag drives the tunnel-only layout — no map, no canvas, placeholder panel — NOT the Mark button visibility.)
- [ ] 4.2 Add a "Mark" `OutlinedButton` in the action row that renders whenever `isRecording` is true, regardless of recording mode (per design D3 and D12 — universal markers); quick-tap calls `viewModel.quickMark()`. Mirror the existing `Reset Origin` visibility pattern but widen the condition to `isRecording` (with `Reset Origin` still gated to `isIndoor && isRecording`)
- [ ] 4.3 Add a `combinedClickable` modifier (or `pointerInput` for `detectTapGestures(onTap = ..., onLongClick = ...)`) so a long-press opens a `MarkerDialog` composable (defined next)
- [ ] 4.4 Implement `MarkerDialog` as an `AlertDialog` with two modes: "create" (title "New Marker", Save/Cancel) and "edit" (title "Edit Marker", Save/Cancel/Delete, pre-populated with the existing marker's `type` and `label`). In both modes:
  - Render a row of `FilterChip`s for the five `MarkerType` values (`WAYPOINT`, `SEGMENT_START`, `SEGMENT_END`, `STOP`, `NOTE` — per design D13); NOTE selected by default in create mode; the existing marker's type selected in edit mode
  - When the selected type is `SEGMENT_START` or `SEGMENT_END`, the label field is NOT shown
  - When the selected type is `WAYPOINT`, `STOP`, or `NOTE`, render an optional single-line `OutlinedTextField` for the label, auto-focused when the dialog opens via long-press (user opened the dialog to type)
  - When the label field is shown AND the `recent_marker_labels` table has rows for the selected type, render a `FlowRow` of the top-20 most-recently-used labels as chips above the field; tapping a chip fills the field but does NOT auto-save (user can tweak before Save)
  - Save calls `viewModel.createMarker(selectedType, label.ifBlank { null })` (create mode) or `viewModel.editMarker(markerId, selectedType, label.ifBlank { null })` (edit mode) and dismisses
  - Delete (edit mode only) calls `viewModel.deleteMarker(markerId)` and dismisses
- [ ] 4.5 Show a transient confirmation (Snackbar via the existing `snackbarHostState`) after a quick-tap, Save, or Delete: "Marked #N", "Edited #N", or "Deleted #N"
- [ ] 4.6 Add the tunnel-mode main content area: when `isTunnel`, render a `Surface` panel with "Tunnel recording in progress" text, the elapsed time, point count, and marker count (instead of the OSM map / indoor canvas / tracking confidence / drift radius / sensor warning UI elements)
- [ ] 4.7 In the `LiveStatsBar`, add a tunnel branch that mirrors the indoor branch (ping only, no GPS / lat / lon / alt) — extract the indoor `Text("Ping: ${state.currentLatency} ms $dataSimLabel")` into a shared composable used by both indoor and tunnel modes
- [ ] 4.8 Add a `RecordingScreenTest` (instrumented) covering: Mark button IS rendered for OUTDOOR/INDOOR/TUNNEL mode when recording is active; Mark button is NOT rendered when recording is not active; quick-tap calls `viewModel.quickMark()`; long-press opens the dialog; the dialog renders 5 type chips, the label field shown for NOTE/WAYPOINT/STOP, the label field hidden for SEGMENT_START/SEGMENT_END, and a Save button; recent-label chips render when the table has rows for the selected type and are empty/unobtrusive when it has none; tapping a chip fills the field but does not auto-save; the tunnel placeholder panel renders in TUNNEL mode (and only in TUNNEL mode — outdoor shows the OSM map, indoor shows the canvas)
- [ ] 4.9 Add a `RecordingScreenPermissionTest` case: tunnel mode does not request `ACTIVITY_RECOGNITION` (assert `PermissionHelper.missingAllForMode("TUNNEL", context)` does not include `ACTIVITY_RECOGNITION`)

## 5. Permission Helper — Tunnel Mode Path

- [ ] 5.1 In `PermissionHelper.kt`, add a tunnel arm to `indoorPermissions()`/`missingPermissionsForMode(recordingMode, context)` and `allGrantedForMode(recordingMode, context)`: tunnel mode requires only foreground + phone-state + notifications (no `ACTIVITY_RECOGNITION`, no `ACCESS_FINE_LOCATION`, no `ACCESS_BACKGROUND_LOCATION`); reuse the existing foreground/background split helpers
- [ ] 5.2 In `PermissionRationaleDialog.kt`, ensure the rationale content for tunnel mode does not mention step sensors or GPS (it should just describe the standard foreground service / notification permission); add a tunnel-aware path or reuse the outdoor path
- [ ] 5.3 Update `PermissionHelperTest` to cover: tunnel mode returns no `ACTIVITY_RECOGNITION` in `indoor_permissions`; `allGrantedForMode("TUNNEL", ...)` is true when only foreground + phone state + notifications are granted; `missingAllForMode("TUNNEL", ...)` returns the correct missing set when one is missing

## 6. New Session Dialog — Tunnel Mode Chip

- [ ] 6.1 In `SessionListScreen.kt` New Session dialog (around line 551), add a third `FilterChip` for "Tunnel" so the row has three chips; default selection remains "Outdoor"
- [ ] 6.2 Update the conditional guidance note block: when `recordingMode == "TUNNEL"` show "Tunnel mode samples on a fixed time cadence and uses manual markers for landmarks. Best for mapping coverage inside metro tunnels." (existing indoor note unchanged)
- [ ] 6.3 Confirm the `onConfirm(name, recordingMode)` callback receives `"TUNNEL"` for tunnel sessions; no further wiring needed (the value flows into `SessionEntity.recordingMode`)
- [ ] 6.4 Add a `SessionListScreenTest` (or extend an existing one) covering: three chips are rendered; selecting Tunnel shows the tunnel guidance note; confirming creates a session with `recordingMode = "TUNNEL"`

## 7. Replay — Marker Pins, Detail Card with Edit and Delete

- [ ] 7.1 In `SessionDetailViewModel`, load markers alongside cell records: collect from `SessionMarkerRepository.getMarkersForSession(sessionId)` into a `markers: StateFlow<List<SessionMarkerEntity>>`; expose to `ReplayScreen`
- [ ] 7.2 In `ReplayScreen.kt`, render each marker as a vertical pin on the timeline at the x-position corresponding to `marker.timestamp`; pins are a distinct color/shape from the existing speedtest markers (e.g., diamond shape, orange color)
- [ ] 7.3 Make each pin tappable; on tap, show a small `MarkerDetailCard` (new composable or `AlertDialog`) with `#seq`, `type`, `label` (or "—"), `timestamp` formatted, and two action buttons: "Edit" (opens the `MarkerDialog` in edit mode, reusing the composable from task 4.4) and "Delete" (calls `viewModel.deleteMarker(id)`)
- [ ] 7.4 Re-render pins and detail card whenever `markers` updates (after an edit or a delete)
- [ ] 7.5 Add a `ReplayScreenTest` (instrumented) covering: pins are rendered at the correct timestamps for a session with markers; tapping a pin opens the detail card with Edit and Delete buttons; tapping Edit opens the `MarkerDialog` in edit mode pre-populated with the existing `type` and `label`; editing and saving updates the timeline pin in place (same position, updated type/label); tapping Delete removes the pin from the timeline

## 8. Session Detail — Markers Section with Edit and Delete Affordances

- [ ] 8.1 In `SessionDetailScreen.kt`, between the analytics toggle and the cell-records list, add a `MarkersSection` composable that renders only when `markers.isNotEmpty()`
- [ ] 8.2 Implement `MarkersSection` as a collapsible card: header row "Markers (N)" with a chevron icon; collapsed by default; tap toggles expansion
- [ ] 8.3 When expanded, render one row per marker: `#<seq>`, type chip with generalized-type color (`WAYPOINT` = blue, `SEGMENT_START` = green, `SEGMENT_END` = red, `STOP` = orange, `NOTE` = grey — per design D13), `label` (or "—"), timestamp formatted, and two action buttons per row: an edit pencil-icon button and a trash-icon delete button
- [ ] 8.4 Wire the edit button to open the `MarkerDialog` in edit mode (reusing the composable from task 4.4, pre-populated with the existing `type` and `label`); on Save, the row re-renders in place with the new `type` and `label` (no `seq` change, no row reordering)
- [ ] 8.5 Wire the delete button to `viewModel.deleteMarker(id)`
- [ ] 8.6 Add a `SessionDetailScreenTest` covering: the section is hidden when there are 0 markers; the section shows when there are N markers with the correct count; expanding the section shows N rows with edit + delete buttons; editing a row opens the dialog pre-populated and saves in place (row position unchanged); deleting a row re-renders the list with N-1 rows; `seq` gaps are preserved after delete; recent labels are NOT decremented on delete

## 9. Tunnel Session Detail — No Map, No Indoor Canvas

- [ ] 9.1 In `SessionDetailScreen.kt`, add `val isTunnel = session?.recordingMode == "TUNNEL"` next to `isIndoor`
- [ ] 9.2 Add a `TunnelPlaceholderPanel` composable rendered in place of the map/indoor canvas when `isTunnel`: shows session name, total point count, total duration, and marker count
- [ ] 9.3 In the records table column-headers block (around line 426 in `ui/spec.md`), add a tunnel branch: omit `latitude`, `longitude`, `altitude`, `accuracy`, `relativeX`, `relativeY` columns; add a `src` (location source) column showing `"TUNNEL"` for every row
- [ ] 9.4 In the record detail bottom sheet `Location` section, add a tunnel branch: show `"Tunnel record (no GPS coordinates)"` instead of lat/lon/alt/accuracy or relX/relY
- [ ] 9.5 Add a `SessionDetailScreenTest` covering: tunnel session shows the placeholder panel instead of a map; tunnel session records table has a `src` column with `"TUNNEL"` for all rows; tunnel record bottom sheet shows the no-GPS message in the Location section

## 10. Export — Markers CSV Companion File (recents excluded)

- [ ] 10.1 In `ExportSessionUseCase.kt`, add a `exportMarkersCsv(sessionId): String` returning `"timestamp,seq,type,label\n"` followed by one row per marker (sorted by `seq` ascending), CSV-escaping `label` per the existing escape helper
- [ ] 10.2 Add a new `MarkerType.toStorageString()` / `MarkerType.fromStorageString()` helper if not already done in the repository step; use it for serialization
- [ ] 10.3 Update the CSV export entry point to emit `markers_<session>.csv` as a sibling file when the session has any markers (follow the existing speedtest CSV companion-file pattern); omit the file when the session has zero markers
- [ ] 10.4 Verify the `recent_marker_labels` table is NOT included in any session export (CSV or GeoJSON) — it is app-local user state, not session data
- [ ] 10.5 Add an `ExportSessionUseCaseTest` covering: tunnel session with 3 markers produces a `markers_<session>.csv` with the expected header and 3 rows sorted by `seq`; outdoor session with markers also produces a markers file (universal markers per D12); indoor session with markers also produces a markers file; session with zero markers produces no markers file; CSV escaping of labels containing commas/quotes/newlines works; the export contains no `recent_marker_labels` data

## 11. Export — Markers GeoJSON Features and Tunnel Flag (recents excluded)

- [ ] 11.1 In `ExportSessionUseCase.exportGeoJson(...)`, when the session has markers, emit one `Feature` per marker with `Point` geometry, coordinates `[0.0, 0.0]`, and properties `markerType`, `label` (may be null), `seq`
- [ ] 11.2 When the session is tunnel mode (`session.recordingMode == "TUNNEL"`), add `"tunnelMode": true` to the FeatureCollection's top-level properties; do NOT set `"indoorMode": true`
- [ ] 11.3 For tunnel cell-record features, the geometry coordinates are `[0.0, 0.0]` (sentinel); ensure the existing export path does not skip these (verify the existing `latitude == 0.0 && longitude == 0.0` "Waiting for GPS..." UI guard on `RecordingScreen` does not affect export — it should not, since export reads from the DB, not the state)
- [ ] 11.4 Verify no `recent_marker_labels` data is exported in the GeoJSON FeatureCollection
- [ ] 11.5 Add an `ExportSessionUseCaseTest` covering: tunnel session GeoJSON has `"tunnelMode": true` and no `"indoorMode"`; markers are emitted as `Point` features with the expected properties; tunnel cell-record features have `[0.0, 0.0]` coordinates; outdoor session with markers emits marker `Point` features with `[0.0, 0.0]` coordinates (temporal-only per D12 — no `tunnelMode` flag for outdoor); indoor session with markers emits marker `Point` features with `[0.0, 0.0]` coordinates (preserves `"indoorMode": true`, no `tunnelMode`); the export contains no recent-marker-labels data

## 12. Import — Markers CSV and GeoJSON (recents untouched)

- [ ] 12.1 In `ImportSessionUseCase.kt`, detect a markers companion file by name pattern `markers_*.csv` alongside the cell-records CSV; if present, parse it into `SessionMarkerEntity` rows preserving the original `seq` values; malformed lines are skipped with a warning (matching the existing cell-records CSV behavior)
- [ ] 12.2 Set the imported session's `recordingMode = "TUNNEL"` when a markers file is present OR when any cell-record row has `location_source = "TUNNEL"` (prefer markers-file presence; this is the most explicit signal)
- [ ] 12.3 In GeoJSON import, detect `"tunnelMode": true` on the FeatureCollection to set `recordingMode = "TUNNEL"`; any features with a `markerType` property are parsed as markers and inserted with their `seq` values preserved (or assigned sequentially if absent)
- [ ] 12.4 Verify the import path does NOT touch the `recent_marker_labels` table — imported labels are NOT auto-upserted into recents (the recents table is a per-user, per-device concern; import reconstructs the session as it was, not the user's global recents state)
- [ ] 12.5 Add an `ImportSessionUseCaseTest` covering: import a cell-records CSV with a markers companion → markers table populated with original `seq` and `recordingMode = "TUNNEL"`; import a cell-records CSV without markers companion → no markers inserted, `recordingMode` follows existing detection rules; import a GeoJSON with `"tunnelMode": true` and marker features → markers inserted with original `seq`; import an outdoor-session GeoJSON with marker features but no `"tunnelMode"` → markers inserted with original `seq`, `recordingMode = "OUTDOOR"` (per D12 — markers are universal); import an indoor-session GeoJSON with `"indoorMode": true` and marker features → markers inserted, `recordingMode = "INDOOR"`; round-trip a tunnel session (export then import) → cell-record count, marker count, `type`, `label`, `seq`, and `locationSource` all preserved; the `recent_marker_labels` table is unchanged by import

## 13. Analytics and Notifications Adaptations

- [ ] 13.1 In `SessionAnalyticsEngine.kt` (around line 552 where `indoorAccuracyThresholdM` is checked for indoor mode), add a tunnel branch: tunnel sessions produce no mobility segments (no stationary/walking/driving/indoor/tunnel classification based on speed — there is no speed) and no handoff events (tunnel sessions have no geographic position); add a `TUNNEL` segment label if needed for the timeline display, but with no classification logic
- [ ] 13.2 In the notification rendering code (where the indoor branch adds tracking confidence), add a tunnel branch showing marker count (e.g., "5 markers"); the "Mark Note" action button is universal across all recording modes (per task 2.5 and design D12)
- [ ] 13.3 Add a `SessionAnalyticsEngineTest` covering: tunnel mode produces no handoff events (parallel to the existing "indoor mode produces no handoff events" test); tunnel mode produces a single `TUNNEL` mobility segment if that design is chosen, or no segments if chosen otherwise

## 14. Specs and Documentation

- [ ] 14.1 After implementation, sync the delta specs in `specs/markers/`, `specs/tunnel/`, `specs/recording/`, `specs/service/`, `specs/permission-flow/`, `specs/data/`, `specs/ui/`, `specs/sessions/` into the main `openspec/specs/` directory using `openspec sync-specs --change "add-tunnel-mode"` (per the OpenSpec sync-specs skill workflow) — note the new `markers/` capability
- [ ] 14.2 Add a `## [Unreleased]` section to `CHANGELOG.md` (or, if the user requests a release, a new versioned section) with `Added`, `Changed`, and `Fixed` categories describing tunnel mode, universal session markers, recent-label suggestions, marker editing, and the type-aware dialog from the user's perspective
- [ ] 14.3 Verify the spec deltas still match the implemented behavior (no drift); if the implementation diverged (e.g., a different marker-color mapping), update the deltas before syncing

## 15. End-to-End Verification

- [ ] 15.1 Run `./gradlew clean` then `./gradlew assembleDebug` to verify the new migration, entities, and DAOs compile and the APK builds
- [ ] 15.2 Run `./gradlew :app:testDebugUnitTest` to verify unit tests pass (PermissionHelperTest, RecordingViewModelTest, ExportSessionUseCaseTest, ImportSessionUseCaseTest, SessionAnalyticsEngineTest, SettingsViewModelTest if touched, SessionMarkerDaoTest, RecentMarkerLabelDaoTest)
- [ ] 15.3 Run `./gradlew :app:connectedAndroidTest` (or the equivalent for instrumented tests on a connected device/emulator) to verify instrumented tests pass (SessionMarkerDaoTest, RecentMarkerLabelDaoTest, MigrationTest, RecordingScreenTest, SessionListScreenTest, SessionDetailScreenTest, ReplayScreenTest)
- [ ] 15.4 Manually smoke-test on a device: create a tunnel session, start recording, tap Mark a few times (verify auto-labels are type-prefixed), long-press Mark to open the dialog (verify label field hidden for SEGMENT_START/SEGMENT_END, shown for WAYPOINT/STOP/NOTE), type a station name and save, repeat with the same station name on a second marker (verify it appears as a recent-label chip the next time the dialog is opened), stop, view the session detail (markers section collapses/expands, edit a marker's type and label in place, delete a marker), replay (pins render, tap to open detail, edit and delete from detail card), export to CSV (markers file appears, no recents data), export to GeoJSON (markers features appear, `tunnelMode: true` is set, no recents data), delete the session (markers cascade-deleted with the session; recents table is NOT cleared). REPEAT the smoke test for an OUTDOOR session and an INDOOR session (verify Mark button appears in all three modes when recording is active; verify markers render on the Replay timeline and Session Detail for all modes; verify markers CSV/GeoJSON are emitted for all modes, with `tunnelMode: true` only for tunnel sessions).
