# Session Analytics Specification (Delta)

## ADDED Requirements

### Requirement: Speedtest Analytics — Per-Session Summary

The system SHALL compute throughput summary statistics for sessions that contain speedtest records.

#### Scenario: Speedtest summary computed
- GIVEN a session with speedtest records
- WHEN session analytics are generated
- THEN average download speed, p95 download speed, average upload speed, p95 upload speed, success rate, sample count, failure count, and server name are computed

#### Scenario: No speedtest data
- GIVEN a session without speedtest records
- WHEN session analytics are generated
- THEN speedtest analytics are omitted (null)

### Requirement: Speedtest Analytics — Throughput Correlations

The system SHALL compute correlations between throughput and cellular conditions per session.

#### Scenario: RSRP-download correlation
- GIVEN a session with speedtest records
- WHEN analytics are generated
- THEN speedtest records are grouped by RSRP bins (excellent, good, fair, poor)
- AND average download speed per bin is computed

#### Scenario: RAT-download correlation
- GIVEN a session with speedtest records
- WHEN analytics are generated
- THEN speedtest records are grouped by the RAT at test time (`ratAtTest`)
- AND average download speed per RAT is computed

#### Scenario: SIM-download correlation
- GIVEN a session with speedtest records with multiple SIMs
- WHEN analytics are generated
- THEN speedtest records are grouped by data SIM slot index
- AND average download speed per SIM is computed

### Requirement: Speedtest Analytics — Global Statistics

The system SHALL compute aggregate speedtest statistics across all sessions.

#### Scenario: Global speedtest stats
- GIVEN the Statistics tab is selected
- WHEN speedtest records exist
- THEN the system displays total tests, average download speed, average upload speed, success rate, and average download speed per SIM
- AND if no speedtest records exist, the section is hidden