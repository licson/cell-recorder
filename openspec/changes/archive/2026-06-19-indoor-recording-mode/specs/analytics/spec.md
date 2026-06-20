## ADDED Requirements

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
