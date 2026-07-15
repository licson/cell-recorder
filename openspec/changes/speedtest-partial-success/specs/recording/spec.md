## MODIFIED Requirements

### Requirement: Optional Speedtest During Recording

The system SHALL, when speedtest is enabled in config, run continuous throughput tests alongside cell recording. Each test result persists both start and finish timestamps and the per-phase success flags (`downloadSucceeded`, `uploadSucceeded`) defined in `speedtest/spec.md`.

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
- GIVEN a speedtest cycle completes (success, partial success, or failure)
- WHEN the result is persisted to `speed_test_records`
- THEN `timestamp` is set to `SpeedTestResult.startedAt`
- AND `finishedAt` is set to `SpeedTestResult.finishedAt`
- AND for instant bail-outs `finishedAt = timestamp`

#### Scenario: Speedtest record persists per-phase success

- GIVEN a speedtest cycle completes (success, partial success, or failure)
- WHEN `RecordingService` inserts the `SpeedTestRecordEntity`
- THEN `downloadSucceeded` is set to `SpeedTestResult.downloadSucceeded`
- AND `uploadSucceeded` is set to `SpeedTestResult.uploadSucceeded` (nullable: null when upload was not run)
