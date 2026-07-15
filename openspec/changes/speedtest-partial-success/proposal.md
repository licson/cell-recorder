## Why

When a speedtest's upload phase fails on a carrier-hostile network, the engine discards the entire test result and invalidates the cached server, config, and gauge — forcing the next cycle to re-fetch the multi-megabyte server list and re-run the gauge. On a network where upload *always* fails (carrier-dependent, server-independent), this waste repeats every cycle for the whole session: the partial download result is silently dropped from analytics, and mobile data is burned on rediscovery that cannot fix the underlying problem.

## What Changes

- **Preserve cache on upload-only failure.** When the download phase succeeded but upload failed, the engine keeps the cached server, config, and gauge. Cache invalidation only happens when download itself fails or the server is unreachable.
- **Per-phase success semantics. **BREAKING** to consumers of `SpeedTestResult` and `SpeedTestRecordEntity`. The single `succeeded: Boolean` is replaced with `downloadSucceeded: Boolean` and `uploadSucceeded: Boolean?` (nullable when upload was not run).
- **Pre-upload probe.** Before running the full upload measurement (which has a 3-second warmup cost), the engine issues a single tiny POST to the server. If the probe fails, the upload phase is skipped for this cycle and `uploadSucceeded = false` is recorded without burning the warmup bytes.
- **Analytics consume per-phase results.** Download samples are drawn from records with `downloadBps != null`; upload samples from records with `uploadBps != null`. This retroactively re-includes legacy rows where `succeeded = false` but `downloadBps` was set.
- **CSV export reflects per-phase status. **BREAKING** to the exported CSV schema. The `succeeded` column is replaced by `download_succeeded` and `upload_succeeded` columns.
- **WiFi-skip and instant bail-outs keep their semantics.** `SKIPPED_WIFI`, config-fetch failure, and server-selection failure records `downloadSucceeded = false` and `uploadSucceeded = null`.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `speedtest`: Replace whole-test `succeeded` with per-phase `downloadSucceeded`/`uploadSucceeded`; stop invalidating cache on upload-only failure; add pre-upload probe requirement; clarify instant bail-out semantics under the new schema.
- `speedtest-diagnostics`: Update debug card to render per-phase result label (Success / Download only / Failed); add `probe` to the ring-buffer phase list; update prime-on-success condition to use `downloadSucceeded`/`uploadSucceeded`.
- `analytics`: Replace "filter by `succeeded`" rule with "filter by non-null per-phase bps"; retroactive re-include of legacy partial rows.
- `data`: Replace `succeeded` CSV column with `download_succeeded` and `upload_succeeded`.
- `recording`: Replace persisted-record field `succeeded` with the two per-phase fields; clarify that the engine's per-phase booleans flow through to the persisted entity.

## Impact

- **Code:**
  - `SpeedTestEngine.kt` — split success state, gate `invalidateCache()` on download-only failure, add probe call before `measureUpload`.
  - `SpeedTestMeasurer.kt` — new `probeUpload()` function (small POST, success/failure).
  - `SpeedTestResult.kt` — replace `succeeded` field with `downloadSucceeded` + `uploadSucceeded`.
  - `SpeedTestRecordEntity.kt` — replace `succeeded` column with two columns.
  - `RecordingService.kt` — adapt insert to per-phase fields.
  - `SpeedTestAnalyticsEngine.kt` — change filters from `succeeded` to per-phase non-null bps.
  - `ExportSpeedTestUseCase.kt` + tests — change CSV columns.
  - `SettingsViewModel.kt` manual launch path — adapt to per-phase result.
  - `SessionDetailViewModel` / replay marker rendering / `StatisticsViewModel` — adapt to per-phase fields.
- **Database:** Schema migration (v15 → v16). Add `downloadSucceeded` and `uploadSucceeded` columns. Migrate legacy rows: `downloadSucceeded = (downloadBps IS NOT NULL)`, `uploadSucceeded = (uploadBps IS NOT NULL)`. Drop the `succeeded` column via the table-rebuild pattern.
- **Specs:** Updates to `speedtest/spec.md`, `analytics/spec.md`, `data/spec.md`, `recording/spec.md`.
- **Tests:** Unit tests for analytics filter change; instrumented tests for DAO migration; CSV export test update; engine test for cache-preserved-on-upload-failure behavior; engine test for probe-skip path.
- **CSV consumers:** External tools that parse the `succeeded` column of the speedtest CSV will break. Documented in CHANGELOG as a breaking change.
