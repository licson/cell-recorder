## Context

Cell Recorder's recording service runs as a foreground service collecting GPS + cell + ping data at ~1Hz. The current implementation writes each cell snapshot as an individual DB insert inside `PointRecorder.recordPoint()`, holds a coroutine mutex across three concurrent jobs (one of which is read-only), and spawns a new OS process for every ICMP ping. On dual-SIM devices with CA bands, a single GPS tick triggers 5+ individual DB writes plus 2 notification updates. The session analytics engine also has O(n²) nested loops that make session detail screen loading slow for large datasets.

The recording service architecture uses three coroutines: `recordingJob` (GPS-driven writes), `fallbackJob` (extrapolation when GPS is lost), and `stateUpdateJob` (1Hz UI state + notification). All three share `recordingMutex`, meaning the read-only state update blocks and is blocked by both write paths.

## Goals / Non-Goals

**Goals:**
- Reduce DB writes per GPS tick from N+1 individual roundtrips to 1 transactional batch
- Eliminate mutex contention between read-only state updates and write-heavy recording paths
- Cap notification updates at 1Hz maximum (currently up to 2/sec)
- Make recorded path storage O(1) for add/remove operations
- Reduce analytics engine from O(n²) to O(n) for large sessions
- Add DB indices for statistics query columns that currently do full table scans
- Replace per-ping process spawning with a single persistent ping process

**Non-Goals:**
- Pre-computed analytics caching (future optimization if sessions grow beyond O(n) engine capacity)
- Incremental/paginated session detail loading
- Changing the recording architecture (e.g., moving away from foreground service)
- Modifying the GPS location flow or extrapolation logic behavior
- Changing the `PingSlidingWindow` interface

## Decisions

### D1: Batch inserts via Room `@Transaction` in DAO

**Decision:** Add a `@Transaction`-annotated method on `CellRecordDao` that accepts a list of `CellRecordEntity` and a list of `CellRecordCaBandEntity`, then delegates to existing `insertAll` and `insertCaBands`. Callers collect all entities before writing.

**Rationale:** Room's `@Transaction` wraps the entire insert set in a single SQLite transaction. The existing `insertAll` and `insertCaBands` already use `@Insert` which generates batch SQL. The new method just wraps them in one transaction boundary.

**Alternative considered:** Using raw `SupportSQLiteDatabase.execSQL` with compound INSERT statements — rejected because it loses Room's type safety and entity mapping.

### D2: Narrow mutex to recordingJob + fallbackJob only

**Decision:** Keep `recordingMutex` between `recordingJob` and `fallbackJob`. Remove `stateUpdateJob` from the mutex entirely. State update reads `pointRecorder` fields (totalPointCount, lastRecordedLocation, recordedPathSnapshot) and `gpsState` fields without synchronization.

**Rationale:** `stateUpdateJob` only reads; it never writes to `pointRecorder` or `gpsState`. Accepting a one-frame stale read (e.g., pointCount off by 1) is acceptable for a 1Hz UI refresh. The real race is between `recordingJob` and `fallbackJob` both calling `pointRecorder.recordPoint()` — they must not interleave.

**Alternative considered:** `StateFlow` for all shared fields instead of mutex — rejected because `pointRecorder.lastRecordedLocation` and `lastRecordedTime` are mutable vars that aren't StateFlows, and converting them adds complexity for no functional benefit.

### D3: Single notification source from stateUpdateJob

**Decision:** Remove `notificationHelper.notify()` call from `PointRecorder.recordPoint()`. The `stateUpdateJob` already runs at 1Hz and builds+posts a notification on every tick.

**Rationale:** Currently every recorded point triggers a notification, plus the 1Hz state update also triggers one. This means 2+ notifications/sec during active GPS tracking. The state update job already has all the information (point count, GPS status, elapsed time) to build an accurate notification. Throttling to 1Hz matches the UI refresh rate.

### D4: ArrayDeque for recorded path

**Decision:** Replace `MutableList<Pair<Double, Double>>` with `ArrayDeque<Pair<Double, Double>>` in `PointRecorder`. Use `addLast`/`removeFirst` (O(1)) instead of `add`/`removeAt(0)` (O(n)).

**Rationale:** `removeAt(0)` on ArrayList shifts all remaining elements. With MAX_PATH_SIZE=2000, every point after the first 2000 triggers a 2000-element array shift. `ArrayDeque` uses a circular buffer internally, making both add and remove from either end O(1).

**Alternative considered:** Ring buffer with fixed array — rejected because `ArrayDeque` already provides this with a cleaner API and `toList()` for snapshots.

### D5: O(n) algorithms for analytics engine

**Decision:** Rewrite `detectRsrpDrops` using a sliding window approach (single pass, track min within window). Rewrite `detectPciFlapping` using a deque-based approach (single pass, maintain window boundaries). Keep multiple passes over data (each O(n)) but eliminate nested loops.

**Rationale:** Current `detectRsrpDrops` iterates i→j for every record within a time window, giving O(n×w) where w is average window size. A single-pass approach tracking the window endpoint achieves O(n). Similarly, `detectPciFlapping` uses nested iteration to find distinct PCIs in each window.

**Alternative considered:** Pre-computed analytics table — deferred as a future optimization. The O(n) engine should handle sessions up to ~50K records comfortably.

### D6: DB indices via migration v8→v9

**Decision:** Add composite index `Index("simSlotIndex", "rat")` and single-column index `Index("bandNumber")` on `cell_records`. Bump DB version to 9 with `MIGRATION_8_9` executing `CREATE INDEX IF NOT EXISTS` statements.

**Rationale:** Statistics queries (`getRatDistributionPerSim`, `getBandDistributionPerSim`, `getSimSlotDistribution`, `get5GTimePerSim`) all filter/group by `simSlotIndex`, `rat`, or `bandNumber`. Without indices, these are full table scans. The composite index covers the most common query pattern. A migration is required because Room's schema versioning tracks index definitions.

**Alternative considered:** Adding indices in entity annotations only (no migration) — rejected because existing installs won't get the indices without a migration.

### D7: Long-running ping with Flow output

**Decision:** Rewrite `PingEngine` to start a persistent `ping -i <interval> <host>` process. Parse stdout line-by-line, emit `PingResult` for each parsed line. Expose as `pingFlow(host, intervalSec): Flow<PingResult>`. Handle process death by auto-restarting. Tie process lifecycle to coroutine cancellation.

**Rationale:** `ProcessBuilder("ping", "-c", "1", ...)` spawns a new process per ping. Each spawn costs ~50-100ms. At 1 ping/sec, that's significant cumulative overhead. A long-running `ping -i` process streams results continuously with zero per-ping spawn cost.

**Alternative considered:** `InetAddress.isReachable()` — rejected because it uses a different ICMP path and may not work without root on some Android versions. `ping -i` uses the same ICMP approach as the current `-c 1` method.

**Key implementation detail:** The `ping -i` flag accepts float intervals (e.g., `-i 1.0`). Android's `ping` binary supports this. Process stdout must be read on a background thread to avoid blocking. Process death detection: `process.waitFor()` in a separate coroutine or read timeout on stdout.

## Risks / Trade-offs

- **Stale UI reads without mutex** → Acceptable: 1Hz state update may read pointCount or location one frame behind. No functional impact — the next tick self-corrects.
- **Long-running ping process death** → Mitigation: auto-restart with exponential backoff. Common causes: network interface changes, process killed by OS. Detection via stream EOF or `process.isAlive`.
- **Migration v8→v9** → Low risk: `CREATE INDEX IF NOT EXISTS` is idempotent and doesn't modify data. On failure, Room throws `IllegalStateException` which prevents app from opening — same as any failed migration.
- **ArrayDeque `toList()` copy** → Still O(n) for snapshot, but now occurs outside the mutex in stateUpdateJob at 1Hz. The copy itself is fast (array copy) and doesn't block recording.
- **`ping -i` availability** → Mitigation: verify `ping -i` is supported at recording start (fallback to `-c 1` if not). All tested Android devices support it.
- **Batch insert transaction** → If one insert in a batch fails, the entire batch rolls back. Current code already has try/catch per snapshot, so a single bad snapshot losing the entire batch's worth of data would be worse. Mitigation: validate entities before inserting; the current per-snapshot try/catch can still wrap the batch call.

## Migration Plan

1. Deploy DB migration v8→v9 — indices are additive, no data transformation
2. All other changes are code-only (no data model changes beyond the index annotations)
3. No rollback needed for indices — they're purely performance improvements
4. If long-running ping causes issues, fallback is straightforward: revert to single-shot mode

## Open Questions

- Should the batch insert method also include the `incrementPointCount` SQL, or keep that as a separate call? (Leaning: separate — it's a different table and only needs to happen once per GPS tick, not per snapshot)
