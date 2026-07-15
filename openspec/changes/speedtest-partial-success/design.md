## Context

The speedtest engine currently uses a single `succeeded: Boolean` to summarize the outcome of a test cycle. Download and upload are independent HTTP phases, but a failure in either one flips `succeeded = false` and triggers `invalidateCache()`, which discards the cached server list (5–10 MB), config, and gauge. On carrier networks where upload *always* fails (server-independent, time-and-carrier-dependent), every cycle:

1. Re-fetches the multi-MB server list and re-runs the gauge — wasted mobile data.
2. Throws away the (often successful) download result — invisible to analytics.
3. Re-runs the full upload phase (3-second warmup) before failing — more wasted bytes.

Three pieces of context shape the fix:

- **Carrier-hostile upload is persistent.** Once a network is in upload-fail mode, no server choice recovers it. The cache invalidation logic is actively harmful in this case.
- **Download is the headline metric.** Users primarily care about download throughput; upload is secondary. A partial (download-only) result is still valuable.
- **Legacy rows exist.** The DB already has rows with `succeeded = false` and a non-null `downloadBps`. Re-including them retroactively in download analytics is a free win that requires no data backfill — only an analytics filter change.

Relevant files:

- `SpeedTestEngine.kt` — `runTest()` and `invalidateCache()` (lines 52–225, 285–291).
- `SpeedTestMeasurer.kt` — `measureUpload()` (lines 190–280), upload warmup cost.
- `SpeedTestResult.kt` — `succeeded` field.
- `SpeedTestRecordEntity.kt` — `succeeded` column.
- `SpeedTestAnalyticsEngine.kt:47–49` — `succeeded` filter.
- `ExportSpeedTestUseCase.kt` — CSV `succeeded` column.
- `RecordingService.kt:491–508` — entity insert.
- `AppDatabase.kt` — schema version (currently v15; bumped to v16 by this change).

## Goals / Non-Goals

**Goals:**

- Stop burning mobile data on `invalidateCache()` when only upload failed.
- Stop burning mobile data on the 3-second upload warmup when the carrier cannot upload at all.
- Capture and surface partial-success results (download succeeded, upload failed).
- Retroactively re-include legacy partial rows in download analytics.

**Non-Goals:**

- Per-session data budget cap with UI (deferred — possible follow-on).
- Metered/Data-Saver auto-skip (deferred — possible follow-on).
- Adaptive backoff / auto-disable upload after N failures (deferred — possible follow-on; the pre-upload probe already eliminates most of the wasted bytes).
- Changing the WiFi-skip policy or instant bail-out semantics.
- Refactoring the measurement slicing or sample-discard algorithm.

## Decisions

### Decision 1: Replace `succeeded` with `downloadSucceeded` + `uploadSucceeded`

**Choice:** Two booleans, `downloadSucceeded: Boolean` and `uploadSucceeded: Boolean?`. The upload field is nullable to distinguish "upload was not run" (e.g., `uploadEnabled = false`, `SKIPPED_WIFI`, instant bail-outs before the upload phase) from "upload ran and failed".

**Alternatives considered:**

- *Status enum* (`SUCCESS`, `DOWNLOAD_ONLY`, `UPLOAD_ONLY`, `FAILURE`, `SKIPPED`). Rejected — the three axes (download ran? upload ran? either succeeded?) don't compress cleanly into one enum, and analytics needs to filter each phase independently.
- *Keep `succeeded` plus a new `partialSuccess` flag*. Rejected — the meaning of `succeeded` becomes ambiguous (does it require upload success?), and retroactive re-include of legacy rows would change their meaning under the same column name.

**Why two booleans:** Analytics filters become natural (`downloadBps != null` for download samples, `uploadBps != null` for upload samples), and the nullable upload field cleanly encodes "not run" without a sentinel.

### Decision 2: Don't invalidate cache on upload-only failure

**Choice:** In `SpeedTestEngine.runTest()`, only call `invalidateCache()` when the download phase failed or when an exception escapes. Upload-only failures keep the cache warm.

**Rationale:** The server is by construction reachable (download just succeeded). On carrier-hostile-upload networks, every cycle would otherwise re-fetch the 5–10 MB server list to no avail.

**Risk:** If the server itself is selectively broken on upload (e.g., backend accepts GETs but rejects POSTs), we'll keep retrying upload against it. The pre-upload probe (Decision 3) catches this cheaply per cycle; if the probe keeps failing we still don't invalidate, but the next session's cold start will pick a different server via `consumePrimeFlag()` → `invalidateCache()` at session start (existing behavior, `RecordingService.kt:445`).

### Decision 3: Pre-upload probe before the full upload measurement

**Choice:** Before invoking `SpeedTestMeasurer.measureUpload()`, the engine calls a new `SpeedTestMeasurer.probeUpload(serverUrl, httpClient)` that issues a single small HTTP POST (e.g., a 1 KB payload) with a short timeout. If the probe fails (non-2xx, exception, or timeout), the upload phase is skipped for this cycle: `uploadSucceeded = false`, `uploadBps = null`, `errorMessage` records `"Upload probe failed: <reason>"`. The full 3-second warmup is not executed.

**Probe characteristics:**

- Payload: ~1 KB of the same `content1=...` shape used by full upload, capped small.
- Timeout: 5 seconds (shorter than the full warmup + measurement).
- Success criterion: HTTP 2xx response.
- Failure handling: caught and recorded; no cache invalidation (consistent with Decision 2).

**Alternatives considered:**

- *Skip the probe and rely on per-phase success semantics alone.* Rejected — the user explicitly chose to add the probe; the 3-second warmup is non-trivial data on a failing carrier.
- *Probe once per session, cache the result.* Rejected — a transient flake would lock the session out of upload for the rest of recording. Per-cycle probing is cheap (1 KB) and lets the engine recover if the carrier's upload comes back.
- *Disable upload for the rest of the session after K probe failures.* Deferred (Non-Goal). Per-cycle probing already saves the warmup bytes; session-wide auto-disable is a UX decision that deserves its own proposal.

### Decision 4: Legacy row migration via SQL backfill, drop `succeeded` column

**Choice:** The DB migration (v15 → v16) adds `downloadSucceeded` and `uploadSucceeded` columns and backfills them from existing `downloadBps` / `uploadBps` columns, then drops the `succeeded` column.

**Migration SQL (illustrative — implemented as table rebuild on API 30):**

```sql
-- New table with per-phase columns in place of `succeeded`
CREATE TABLE speed_test_records_new (
    ...all existing columns...,
    downloadSucceeded INTEGER NOT NULL,
    uploadSucceeded INTEGER,                    -- nullable
    ...remaining columns...
);

INSERT INTO speed_test_records_new (...) SELECT
    ...,
    CASE WHEN downloadBps IS NOT NULL THEN 1 ELSE 0 END,
    CASE WHEN uploadBps IS NOT NULL THEN 1 ELSE NULL END,
    ...
FROM speed_test_records;

DROP TABLE speed_test_records;
ALTER TABLE speed_test_records_new RENAME TO speed_test_records;
-- Recreate indexes
```

The actual `MIGRATION_15_16` in `AppDatabase.kt` implements the rebuild literally (CREATE TABLE new / INSERT ... SELECT ... / DROP / RENAME / recreate indexes) because SQLite on API 30 cannot `ALTER TABLE ... DROP COLUMN` directly.

**Alternatives considered:**

- *Keep `succeeded` as a deprecated column for one release.* Rejected — Room entity fields don't gracefully tolerate "deprecated but still in the entity", and analytics would have to handle both old and new fields. Clean break is simpler and matches the user's "retroactively re-include" decision.
- *Use a fallbackToDestructiveMigration.* Rejected — violates `data/spec.md` (every version step has a registered migration, no destructive fallback).

**Why this works for legacy rows:** A row with `succeeded = false` and `downloadBps = 50_000_000` gets `downloadSucceeded = true` after migration. Analytics now includes it in download metrics — exactly the "retroactive re-include" behavior the user chose. Rows where `downloadBps IS NULL` (true failures, SKIPPED_WIFI, instant bail-outs) get `downloadSucceeded = false` and continue to be excluded from download metrics.

### Decision 5: CSV schema change is a clean break

**Choice:** The speedtest CSV `succeeded` column is replaced by `download_succeeded` and `upload_succeeded` columns. The `upload_succeeded` value is empty for rows where upload was not run.

**Alternatives considered:**

- *Keep `succeeded` and add the two new columns.* Rejected — same reasoning as Decision 4; the meaning of `succeeded` would be ambiguous.

**Breaking impact:** External consumers of the CSV parsing the `succeeded` column will break. Documented in CHANGELOG. There is no CSV import path for speedtest records (only cell records and markers are imported), so no import-side migration is needed.

## Risks / Trade-offs

- **[Carrier recovers upload mid-session]** After K cycles of probe-fail skip, if upload comes back, the next cycle's probe will succeed and upload resumes. No data lost, no manual intervention. → Mitigation: none needed; per-cycle probing self-heals.
- **[Server is selectively upload-broken]** We never invalidate on upload-only failure, so a server with broken upload stays cached for the session. → Mitigation: the pre-upload probe skips the full upload each cycle (cost: 1 KB), and the next session cold-starts with `invalidateCache()` at recording start.
- **[CSV consumers break]** External tools reading the `succeeded` column stop working. → Mitigation: CHANGELOG entry under a "Breaking" or "Changed" section; the speedtest CSV is a power-user feature.
- **[Migration test surface]** Dropping a column on API 30 requires a table rebuild; the migration must round-trip all surviving columns. → Mitigation: covered by existing `data/spec.md` requirement "Column-dropping migration uses table rebuild pattern" with a row-seeded migration test.
- **[Analytics numbers shift retroactively]** Users may notice their session stats change after upgrade because legacy partial rows are now included. → Mitigation: this is the intended behavior (user explicitly chose "retroactively re-include"); CHANGELOG entry under "Changed" explains the recompute.

## Migration Plan

1. Bump `AppDatabase` `@Database(version = 16)` with `exportSchema = true` (already done).
2. `Migration(15, 16)` is registered in `DatabaseModule.addMigrations(...)` and implements the SQL above with the table-rebuild pattern for dropping `succeeded`.
3. Row-seeded instrumented test `MigrationTest.migrateFrom15To16` verifies: (a) legacy row with `downloadBps != null, succeeded = false` becomes `downloadSucceeded = true` post-migration (retroactive re-include); (b) legacy `SKIPPED_WIFI` row with `downloadBps = null` becomes `downloadSucceeded = false, uploadSucceeded = null`; (c) all surviving columns round-trip; (d) the `succeeded` column is gone. The `migrateFullChain1To16` test verifies the full upgrade chain from v1.
4. `SpeedTestRecordEntity`, DAO queries, repository, `RecordingService` insert path, analytics, CSV export, and UI consumers are updated.
5. Unit and instrumented tests for the new fields are updated.
6. CHANGELOG entry describing the user-visible behavior change (partial results now visible; CSV schema change) is added under `[Unreleased]`.

Rollback: not supported. Once the v16 migration runs, downgrading the app would trigger `fallbackToDestructiveMigration` (forbidden by spec) or a crash. Standard policy: forward-only.

## Open Questions

- Should the pre-upload probe timeout be configurable, or hard-coded at 5 seconds? **Default:** hard-coded; revisit if real-world data shows 5 s is too short on slow networks.
- Should the upload probe payload be exactly 1 KB or smaller (e.g., 256 B)? **Default:** 1 KB — small enough to be cheap, large enough to exercise the POST path meaningfully.
- Should the engine emit a `SpeedTestDebugEvent` for the probe phase (new phase `probe`)? **Default:** yes, for parity with existing phase instrumentation; trivial addition.
