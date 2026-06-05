## MODIFIED Requirements

### Requirement: Anomaly Detection — RSRP Drops

The system SHALL detect significant RSRP drops as anomalies using an O(n) algorithm.

#### Scenario: RSRP drop anomaly
- GIVEN a session with recorded points
- WHEN RSRP drops by more than `rsrpDropThresholdDbm` within `rsrpDropTimeWindowMs`
- THEN consecutive drops are grouped into a single anomaly
- AND the anomaly includes duration and peak drop magnitude
- AND the detection algorithm runs in O(n) time complexity

### Requirement: Anomaly Detection — PCI Flapping

The system SHALL detect rapid PCI changes (flapping) as anomalies using an O(n) algorithm.

#### Scenario: PCI flapping anomaly
- GIVEN a session with recorded points
- WHEN more than `pciFlapCountThreshold` distinct PCIs are observed within `pciFlapWindowMs`
- THEN overlapping windows are collapsed into a single anomaly
- AND the detection algorithm runs in O(n) time complexity
