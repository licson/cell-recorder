## MODIFIED Requirements

### Requirement: Speedtest Record Entity

The system SHALL store speedtest results in a separate Room entity, not mixed with cell records. The entity persists both the test start time (`timestamp`) and the test finish time (`finishedAt`). The legacy single `succeeded` column is replaced by per-phase success columns: `downloadSucceeded` (non-null Boolean) and `uploadSucceeded` (nullable Boolean). `uploadSucceeded` is `null` when upload was not run (upload disabled, WiFi skip, instant bail-out, or probe-skip) and `false` when upload ran but failed.

#### Scenario: SpeedTestRecordEntity structure
- GIVEN a speed test completes
- WHEN the result is persisted
- THEN a `SpeedTestRecordEntity` is created with: sessionId, timestamp (start), finishedAt (finish), downloadBps, uploadBps, serverName, serverHost, serverLocation, serverId, dataSimSlotIndex, ratAtTest, rsrpAtTest, bandAtTest, downloadSucceeded, uploadSucceeded, errorMessage, networkType
- AND `finishedAt` is a non-null `Long` (wall-clock milliseconds)
- AND for instant bail-outs `finishedAt = timestamp`
- AND `downloadSucceeded` is a non-null `Boolean`
- AND `uploadSucceeded` is a nullable `Boolean` (null when upload was not run)
- AND the entity is stored in the `speed_test_records` table
- AND a foreign key links `sessionId` to `sessions.id` with ON DELETE CASCADE

#### Scenario: Legacy rows backfilled with finishedAt zero
- GIVEN rows inserted before the `finishedAt` column existed
- WHEN the additive migration runs
- THEN those rows have `finishedAt = 0`
- AND consumers treat `finishedAt = 0` as "unknown finish time"

#### Scenario: Legacy rows migrated to per-phase success columns
- GIVEN rows persisted before this change with the old `succeeded` column
- WHEN the v15 → v16 migration runs
- THEN `downloadSucceeded` is backfilled as `(downloadBps IS NOT NULL)`
- AND `uploadSucceeded` is backfilled as `(uploadBps IS NOT NULL)` where `uploadBps` is non-null, and remains `null` where `uploadBps` is null
- AND the legacy `succeeded` column is dropped via the table-rebuild pattern (API 30 SQLite limitation)
- AND all surviving columns' data is preserved through the copy step

### Requirement: Speedtest CSV Export

The system SHALL include speedtest data in session export when speedtest records exist. The CSV includes an additive `finished_at` column after the existing `timestamp` column. The legacy single `succeeded` column is replaced by `download_succeeded` and `upload_succeeded` columns. Speedtest data semantics are defined in `speedtest/spec.md`.

#### Scenario: Speedtest CSV export
- GIVEN a session with speedtest records
- WHEN the user exports the session
- THEN a separate `session_name_speedtest.csv` file is generated alongside the cell record CSV
- AND the speedtest CSV contains columns in order: timestamp, finished_at, download_bps, upload_bps, server_name, server_host, server_location, download_succeeded, upload_succeeded, error_message, data_sim_slot, rat_at_test, rsrp_at_test, band_at_test, network_type
- AND `finished_at` is the test finish time (wall-clock milliseconds), equal to `timestamp` for instant bail-outs, and `0` for legacy rows
- AND `download_succeeded` is `1` or `0`
- AND `upload_succeeded` is `1`, `0`, or empty (when upload was not run)

#### Scenario: No speedtest export when empty
- GIVEN a session without speedtest records
- WHEN the user exports the session
- THEN only the cell record CSV is generated (no speedtest file)
