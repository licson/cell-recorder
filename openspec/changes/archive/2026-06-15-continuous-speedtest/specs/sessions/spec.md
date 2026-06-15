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

#### Scenario: Speedtest card auto-updates during playback
- GIVEN replay mode is active and the user is scrubbing or playing
- WHEN the current position passes a speedtest marker
- THEN the speedtest detail card updates to show the last speedtest result at or before the current position
- AND the card continues showing that result until the next marker is passed
- AND the card shows "Position" label for auto-updated markers and "Selected" label for manually tapped markers