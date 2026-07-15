## MODIFIED Requirements

### Requirement: Speedtest Analytics — Per-Session Summary

The system SHALL compute throughput summary statistics for sessions that contain speedtest records. Speedtest data semantics are defined in `speedtest/spec.md`. Download statistics SHALL be computed from records where `downloadBps` is non-null (i.e., download ran, regardless of whether upload succeeded). Upload statistics SHALL be computed from records where `uploadBps` is non-null (i.e., upload ran and succeeded). This retroactively re-includes legacy rows where the old whole-test `succeeded = false` but `downloadBps` was set.

#### Scenario: Speedtest summary computed
- GIVEN a session with speedtest records
- WHEN session analytics are generated
- THEN average download speed and p95 download speed are computed over records where `downloadBps != null`
- AND average upload speed and p95 upload speed are computed over records where `uploadBps != null`
- AND success rate is computed as (records with `downloadSucceeded = true`) divided by (records with `errorMessage != "SKIPPED_WIFI"`)
- AND sample count, failure count, and server name are computed
- AND legacy rows where the old `succeeded = false` but `downloadBps != null` SHALL be included in download statistics (retroactive re-include)

#### Scenario: WiFi-skipped speedtest samples excluded from success metrics
- GIVEN a session with speedtest records where some have `errorMessage = "SKIPPED_WIFI"` (the device was on WiFi at test time)
- WHEN session analytics are generated
- THEN WiFi-skipped records are excluded from sample count, failure count, and success rate
- AND only genuinely attempted cellular measurements are counted toward success rate
- AND if all records are WiFi-skipped, the summary reports zero samples and zero failures rather than null

#### Scenario: No speedtest data
- GIVEN a session without speedtest records
- WHEN session analytics are generated
- THEN speedtest analytics are omitted (null)

### Requirement: Speedtest Analytics — Throughput Correlations

The system SHALL compute correlations between throughput and cellular conditions per session. Speedtest data and RSRP/band semantics are defined in `speedtest/spec.md` and `cell-info/spec.md`. Download correlations SHALL be computed over records where `downloadBps` is non-null. Upload correlations SHALL be computed over records where `uploadBps` is non-null.

#### Scenario: RSRP-download correlation
- GIVEN a session with speedtest records
- WHEN analytics are generated
- THEN speedtest records with non-null `downloadBps` are grouped by RSRP bins (excellent, good, fair, poor)
- AND average download speed per bin is computed

#### Scenario: RAT-download correlation
- GIVEN a session with speedtest records
- WHEN analytics are generated
- THEN speedtest records with non-null `downloadBps` are grouped by the RAT at test time (`ratAtTest`)
- AND average download speed per RAT is computed

#### Scenario: SIM-download correlation
- GIVEN a session with speedtest records with multiple SIMs
- WHEN analytics are generated
- THEN speedtest records with non-null `downloadBps` are grouped by data SIM slot index
- AND average download speed per SIM is computed

#### Scenario: RSRP-upload correlation
- GIVEN a session with speedtest records where upload ran
- WHEN analytics are generated
- THEN speedtest records with non-null `uploadBps` are grouped by RSRP bins
- AND average upload speed per bin is computed

### Requirement: Speedtest Analytics — Global Statistics

The system SHALL compute aggregate speedtest statistics across all sessions. Download aggregates SHALL be computed over records where `downloadBps` is non-null; upload aggregates SHALL be computed over records where `uploadBps` is non-null. Success rate SHALL be computed over records with `errorMessage != "SKIPPED_WIFI"` using `downloadSucceeded`.

#### Scenario: Global speedtest stats
- GIVEN the Statistics tab is selected
- WHEN speedtest records exist
- THEN the system displays total tests, average download speed (over records with non-null `downloadBps`), average upload speed (over records with non-null `uploadBps`), success rate (over non-WiFi-skipped records), and average download speed per SIM
- AND if no speedtest records exist, the section is hidden
