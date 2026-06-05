# Session Analytics Specification

## Purpose

Defines the post-hoc analyses the system computes on recorded session data, including coverage analysis, signal statistics, anomaly detection, handoff detection, mobility classification, and network insight generation.

## Requirements

### Requirement: RAT Coverage Analysis

The system SHALL calculate the percentage of time spent on each radio access technology (RAT) within a session.

#### Scenario: RAT coverage computed
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN the percentage of time per RAT is computed
- AND each RAT's time is broken into signal quality buckets (excellent, good, fair, poor)

### Requirement: Band Distribution

The system SHALL calculate the frequency band usage distribution per SIM.

#### Scenario: Band distribution computed
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN bands used per SIM are listed sorted by occurrence count

### Requirement: Signal Histograms

The system SHALL generate distribution histograms for RSRP, SINR, and ping latency values.

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
- THEN mean, p50, p95, p99, and jitter (standard deviation) of ping latency are computed

### Requirement: Handoff Detection

The system SHALL detect cell and site handoff events within a session.

#### Scenario: Intra-site handoff
- GIVEN a session with recorded points
- WHEN a PCI change occurs within `handoffTimeWindowMs`
- THEN it is classified as an intra-site PCI change

#### Scenario: Inter-site handoff
- GIVEN a session with recorded points
- WHEN a cell identity change occurs within `handoffTimeWindowMs`
- THEN it is classified as an inter-site handoff
- AND the latency impact of the handoff is tracked

### Requirement: Anomaly Detection — RSRP Drops

The system SHALL detect significant RSRP drops as anomalies.

#### Scenario: RSRP drop anomaly
- GIVEN a session with recorded points
- WHEN RSRP drops by more than `rsrpDropThresholdDbm` within `rsrpDropTimeWindowMs`
- THEN consecutive drops are grouped into a single anomaly
- AND the anomaly includes duration and peak drop magnitude

### Requirement: Anomaly Detection — Latency Spikes

The system SHALL detect latency spikes as anomalies.

#### Scenario: Latency spike anomaly
- GIVEN a session with recorded points
- WHEN a ping latency exceeds the mean plus `latencySpikeSigma` standard deviations
- THEN consecutive spikes are grouped into a single anomaly
- AND the anomaly includes the peak latency value

### Requirement: Anomaly Detection — PCI Flapping

The system SHALL detect rapid PCI changes (flapping) as anomalies.

#### Scenario: PCI flapping anomaly
- GIVEN a session with recorded points
- WHEN more than `pciFlapCountThreshold` distinct PCIs are observed within `pciFlapWindowMs`
- THEN overlapping windows are collapsed into a single anomaly

### Requirement: Anomaly Detection — Missing Ping Clusters

The system SHALL detect sustained periods of missing ping data as anomalies.

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

The system SHALL detect periods of unknown RAT as coverage gaps.

#### Scenario: Coverage gap detected
- GIVEN a session with recorded points
- WHEN a period of UNKNOWN RAT exceeds `coverageGapThresholdMs`
- THEN it is flagged as a coverage gap

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