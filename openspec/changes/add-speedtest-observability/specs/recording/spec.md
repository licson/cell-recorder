## ADDED Requirements

### Requirement: Speedtest Record Finish Time

The system SHALL persist both the start time and the finish time of each speedtest on the `SpeedTestRecordEntity`. The existing `timestamp` column holds the start time; a new `finishedAt` column holds the finish time. `finishedAt` is always non-null.

#### Scenario: FinishedAt populated on insert

- GIVEN a speedtest completes during recording (success or failure)
- WHEN `RecordingService` inserts the `SpeedTestRecordEntity`
- THEN `timestamp` is set to the test start time (as today)
- AND `finishedAt` is set to the test finish time from `SpeedTestResult.finishedAt`

#### Scenario: FinishedAt equals timestamp for instant bail-outs

- GIVEN a speedtest exits early via SKIPPED_WIFI, config fetch failure, server selection failure, or exception
- WHEN the record is persisted
- THEN `finishedAt = timestamp` (duration is zero, signalling the test never ran)

#### Scenario: Legacy rows have finishedAt zero

- GIVEN rows inserted before this migration
- WHEN the migration completes
- THEN those rows have `finishedAt = 0` (default)
- AND UI and consumers treat `finishedAt = 0` as "unknown finish time" (no duration badge, no duration computation)

## MODIFIED Requirements

### Requirement: Optional Speedtest During Recording

The system SHALL, when speedtest is enabled in config, run continuous throughput tests alongside cell recording. Each test result persists both start and finish timestamps.

#### Scenario: Speedtest starts with recording
- GIVEN a recording session has started
- WHEN speedtest is enabled in config
- THEN a speedtest coroutine job is launched alongside the cell recording and ping jobs

#### Scenario: Speedtest stops with recording
- GIVEN an active recording with a running speedtest job
- WHEN the recording is stopped
- THEN the speedtest job is cancelled and any in-progress test is aborted

#### Scenario: Speedtest skipped when disabled
- GIVEN a recording session has started
- WHEN speedtest is disabled in config
- THEN no speedtest job is launched

#### Scenario: Speedtest record persists start and finish
- GIVEN a speedtest cycle completes (success or failure)
- WHEN the result is persisted to `speed_test_records`
- THEN `timestamp` is set to `SpeedTestResult.startedAt`
- AND `finishedAt` is set to `SpeedTestResult.finishedAt`
- AND for instant bail-outs `finishedAt = timestamp`
