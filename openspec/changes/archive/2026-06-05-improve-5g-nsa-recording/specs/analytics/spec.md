## MODIFIED Requirements

### Requirement: Insight Cards

The system SHALL generate insight cards based on handoff analysis. 5G insight cards apply to both `5G_NSA` and `5G_SA` RATs.

#### Scenario: Insight card generated
- GIVEN a session with recorded points and handoff events
- WHEN analytics are generated
- THEN insight cards are produced (e.g., Massive MIMO Candidate, Load Balancing Detected, Cross-Site Handoff Impact)
- AND 5G insight cards consider both `5G_NSA` and `5G_SA` handoff events
