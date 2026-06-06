# Recording Specification (Delta)

## ADDED Requirements

### Requirement: Optional Speedtest During Recording

The system SHALL, when speedtest is enabled in config, run continuous throughput tests alongside cell recording.

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

### Requirement: Speedtest Config Reload

The system SHALL read speedtest configuration at recording start.

#### Scenario: Config read at start
- GIVEN a recording is about to start
- WHEN speedtest is enabled
- THEN the current speedtest config (interval, upload toggle, server ID) is read from app config and used for the duration of the recording