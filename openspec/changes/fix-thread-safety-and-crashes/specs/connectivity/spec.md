## MODIFIED Requirements

### Requirement: Packet Loss Calculation

The system SHALL compute the packet loss percentage from the sliding window at each recording point. All non-SUCCESS outcomes SHALL count as packet loss. The percentage SHALL be calculated based on the actual buffer contents, not the window size.

#### Scenario: Packet loss recorded per point
- GIVEN an active recording with a ping window
- WHEN a recording point is triggered
- THEN `packetLossPct` is computed as (count where outcome != SUCCESS / buffer.size) * 100

#### Scenario: All outcomes count as loss
- GIVEN a ping window containing results with outcomes TIMEOUT, HOST_UNREACHABLE, and PROCESS_ERROR
- WHEN packet loss is calculated
- THEN all non-SUCCESS outcomes are included in the loss count

#### Scenario: Partial window packet loss
- GIVEN a ping window with fewer entries than `windowSize` (e.g., 3 of 5)
- WHEN packet loss is calculated
- THEN the percentage is computed based on the 3 actual entries
- AND a 100% loss rate among 3 entries yields `packetLossPct = 100.0`
