# Sessions Specification (Delta)

## ADDED Requirements

### Requirement: Session Replay — Speedtest Markers

The system SHALL display speedtest markers on the replay timeline when speedtest records exist for the session.

#### Scenario: Speedtest markers on timeline
- GIVEN a session with speedtest records and replay mode is active
- WHEN the replay timeline is displayed
- THEN colored markers are shown on the RAT timeline at positions corresponding to the nearest cell record timestamps
- AND markers are color-coded by download speed (red for slow, yellow for moderate, green for fast)

#### Scenario: Speedtest detail card
- GIVEN the replay timeline with speedtest markers
- WHEN a user taps a marker
- THEN a detail card is shown below the timeline
- AND the card displays: timestamp, download speed, upload speed, server name/ID, RSRP at test, RAT at test, and band at test