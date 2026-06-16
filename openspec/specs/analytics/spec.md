# Session Analytics Specification

## Purpose

Defines the post-hoc analyses the system computes on recorded session data, including coverage analysis, signal statistics, anomaly detection, handoff detection, mobility classification, and network insight generation.

## Global Behavior

The engine sorts cell records by timestamp once at the start of `analyze()`. All downstream functions operate on sorted data.

Anomaly detection functions (RSRP drops, latency spikes, missing ping clusters) run per SIM slot to prevent interleaved dual-SIM records from creating false anomalies.

### Requirement: RAT Coverage Analysis

The system SHALL calculate the percentage of time spent on each radio access technology (RAT) within a session using time-weighted intervals rather than sample count.

#### Scenario: RAT coverage computed using duration
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN the RAT percentage is computed from time intervals between consecutive records, attributing each interval to the current record's RAT
- AND each RAT's time is broken into signal quality buckets — excellent (RSRP >= -80), good (RSRP in -90 to -80), fair (RSRP in -100 to -90), poor (RSRP < -100)
- AND the last record contributes no interval (percentage is zero for any RAT appearing only at session end)
- AND records with a single entry return 100% for their RAT

### Requirement: Band Distribution

The system SHALL calculate the frequency band usage distribution per SIM.

#### Scenario: Band distribution computed
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN bands used per SIM are listed sorted by occurrence count
- AND CA bands are merged into the primary band counts for each SIM

### Requirement: Signal Histograms

The system SHALL generate distribution histograms for RSRP, SINR, and ping latency values. Histogram denominators use the count of records with a non-null value for the metric, not the total record count. Records with null values are excluded from the percentage calculation.

Bin boundaries use half-open intervals: `Above` bins are inclusive (value >= min), `Below` bins are exclusive (value < max), and `Range` bins are inclusive of the lower bound and exclusive of the upper bound (min <= value < max). This ensures every value maps to exactly one bin.

#### Scenario: RSRP histogram
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN a distribution of RSRP values is computed

#### Scenario: SINR histogram
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN a distribution of SINR values is computed

#### Scenario: Ping latency histogram
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN a distribution of ping latency values is computed

### Requirement: Correlation Analysis

The system SHALL compute correlations between signal metrics and network performance metrics per SIM.

#### Scenario: RSRP-ping correlation
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN the correlation between RSRP and ping latency is computed per SIM

#### Scenario: RSRP-packet loss correlation
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN the correlation between RSRP and packet loss is computed per SIM

#### Scenario: SINR-ping correlation
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN the correlation between SINR and ping latency is computed per SIM

#### Scenario: SINR-packet loss correlation
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN the correlation between SINR and packet loss is computed per SIM

### Requirement: Latency Statistics

The system SHALL compute summary latency statistics for a session.

#### Scenario: Latency stats computed
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN mean, p50, p95, p99, and jitter (standard deviation of all latency values) of ping latency are computed

### Requirement: Handoff Detection

The system SHALL detect cell and site handoff events within a session. Handoffs are detected per SIM slot within `handoffTimeWindowMs`.

#### Scenario: Handoff type classified
- GIVEN a session with recorded points
- WHEN a RAT change occurs within `handoffTimeWindowMs`
- THEN it is classified as `RAT_CHANGE`
- WHEN a cell identity change occurs and `enbOrGnbId` is identical between consecutive records
- THEN it is classified as `INTRA_SITE_PCI_CHANGE`
- WHEN a cell identity change occurs and `enbOrGnbId` differs between consecutive records
- THEN it is classified as `INTER_SITE`
- WHEN a band number change occurs without cell identity or PCI change
- THEN it is classified as `BAND_CHANGE`
- WHEN a PCI change occurs without a detectable cell identity change
- THEN it is classified as `UNKNOWN_CELL_CHANGE`

#### Scenario: Handoff enriched payload
- GIVEN a handoff event is detected
- THEN the event includes `fromRat`, `toRat`, `fromBand`, `toBand`, `fromCellId`, `toCellId` fields
- AND the latency impact (`latencyDeltaMs`) and packet loss impact (`packetLossDeltaPct`) of the handoff are tracked

### Requirement: Anomaly Detection — RSRP Drops

The system SHALL detect significant RSRP drops as anomalies using an O(n) algorithm, run per SIM.

#### Scenario: RSRP drop anomaly
- GIVEN a session with recorded points
- WHEN RSRP drops by more than `rsrpDropThresholdDbm` within `rsrpDropTimeWindowMs`
- THEN consecutive drops are grouped into a single anomaly
- AND the anomaly includes duration and peak drop magnitude
- AND the detection algorithm runs in O(n) time complexity

### Requirement: Anomaly Detection — Latency Spikes

The system SHALL detect latency spikes as anomalies using a robust baseline per SIM: median + MAD (median absolute deviation) instead of mean + standard deviation. The threshold has a floor of `median + 80ms` to prevent false positives on low-latency connections.

#### Scenario: Latency spike anomaly
- GIVEN a session with recorded points
- WHEN a ping latency exceeds the median plus `latencySpikeSigma` median absolute deviations (with an absolute floor of median + 80ms)
- THEN consecutive spikes are grouped into a single anomaly
- AND the anomaly includes the peak latency value
- AND the detection is run per SIM slot

### Requirement: Anomaly Detection — PCI Flapping

The system SHALL detect rapid PCI changes (flapping) as anomalies using an O(n) algorithm.

#### Scenario: PCI flapping anomaly
- GIVEN a session with recorded points
- WHEN more than `pciFlapCountThreshold` distinct PCIs are observed within `pciFlapWindowMs`
- THEN overlapping windows are collapsed into a single anomaly
- AND the detection algorithm runs in O(n) time complexity

### Requirement: Anomaly Detection — Missing Ping Clusters

The system SHALL detect sustained periods of missing ping data as anomalies, run per SIM.

#### Scenario: Missing ping cluster anomaly
- GIVEN a session with recorded points
- WHEN three or more consecutive samples have no ping data
- THEN the cluster is flagged as an anomaly

### Requirement: Mobility Classification

The system SHALL classify the session into mobility segments.

#### Scenario: Mobility segments computed
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN each point is classified as stationary, walking, driving, indoor, or tunnel based on speed and signal characteristics

### Requirement: Coverage Gap Detection

The system SHALL detect periods of poor or absent coverage, classified into four gap types:

- `NO_RAT`: RAT is reported as "UNKNOWN"
- `NO_SERVING_CELL`: RAT is known but both `enbOrGnbId` and `pci` are null
- `NO_SIGNAL_METRIC`: RSRP is null on a known serving cell
- `WEAK_SIGNAL`: RSRP is below -110 dBm

#### Scenario: Coverage gaps detected by type
- GIVEN a session with recorded points
- WHEN a period of coverage impairment exceeds `coverageGapThresholdMs`
- THEN it is flagged as a coverage gap with the appropriate type
- AND multiple consecutive impairments of different types are merged into a single gap
- AND the gap includes the last known valid location before the gap

### Requirement: Timeline Segments

The system SHALL group contiguous points by RAT into timeline segments.

#### Scenario: Timeline segments computed
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN contiguous points sharing the same RAT are grouped into segments

### Requirement: Insight Cards

The system SHALL generate insight cards based on handoff analysis. 5G insight cards apply to both `5G_NSA` and `5G_SA` RATs.

#### Scenario: Insight card generated
- GIVEN a session with recorded points and handoff events
- WHEN analytics are generated
- THEN insight cards are produced (e.g., Massive MIMO Candidate, Load Balancing Detected, Cross-Site Handoff Impact)
- AND 5G insight cards consider both `5G_NSA` and `5G_SA` handoff events

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

### Requirement: Indoor Session Analytics Exclusion

The system SHALL exclude indoor sessions from geographic-dependent analytics.

#### Scenario: Indoor sessions excluded from geographic analytics
- GIVEN a session with `recordingMode = "INDOOR"`
- WHEN analytics are generated
- THEN geographic coverage maps are not generated
- AND geographic handoff detection is not performed
- AND non-geographic analytics (RAT coverage, band distribution, signal histograms, correlations, latency stats, anomaly detection, timeline segments, insight cards) are generated normally

#### Scenario: Indoor mobility classification
- GIVEN a session with `recordingMode = "INDOOR"`
- WHEN mobility classification runs
- THEN all points are classified as "indoor" regardless of speed characteristics

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