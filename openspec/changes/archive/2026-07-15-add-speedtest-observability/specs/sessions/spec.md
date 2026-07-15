## ADDED Requirements

### Requirement: Speedtest Duration Badge in Session Detail

The system SHALL display a duration badge per speedtest entry in the Session Detail screen when the finish time is known. Duration is computed as `finishedAt - timestamp`.

#### Scenario: Duration badge shown for completed tests

- GIVEN a session detail screen with speedtest records
- WHEN a speedtest record has `finishedAt > 0` AND `finishedAt > timestamp`
- THEN a duration badge is rendered next to the speedtest entry
- AND the badge displays the duration formatted as human-readable time (e.g., "2.3s")

#### Scenario: Duration badge hidden for legacy rows

- GIVEN a session detail screen with speedtest records
- WHEN a speedtest record has `finishedAt = 0` (legacy row, unknown finish time)
- THEN no duration badge is rendered for that entry

#### Scenario: Duration badge hidden for instant bail-outs

- GIVEN a session detail screen with speedtest records
- WHEN a speedtest record has `finishedAt = timestamp` (instant bail-out, duration zero)
- THEN no duration badge is rendered (or a "skipped"/"instant" label is shown instead)

## MODIFIED Requirements

### Requirement: Session Replay — Speedtest Markers

The system SHALL display speedtest markers on the replay timeline when speedtest records exist for the session. When the finish time is known and the test ran for a positive duration, the marker is rendered as a range indicator spanning start to finish on the RAT timeline. Speedtest data semantics are defined in `speedtest/spec.md`.

#### Scenario: Speedtest markers on timeline
- GIVEN a session with speedtest records and replay mode is active
- WHEN the replay timeline is displayed
- THEN colored markers are shown on the RAT timeline at positions corresponding to the nearest cell record timestamps
- AND markers are color-coded by download speed (red for slow, yellow for moderate, green for fast)

#### Scenario: Speedtest range indicator for positive-duration tests
- GIVEN a session with speedtest records where `finishedAt > timestamp` and `finishedAt > 0`
- WHEN the replay RAT timeline is displayed
- THEN a range indicator is rendered spanning from the start timestamp to the finish timestamp
- AND the range indicator is color-coded by download speed (same coloring as point markers)
- AND the range indicator visually communicates the test's duration on the timeline

#### Scenario: Point marker for instant bail-outs
- GIVEN a session with speedtest records where `finishedAt = timestamp` or `finishedAt = 0`
- WHEN the replay RAT timeline is displayed
- THEN a point marker (not a range) is rendered at the start timestamp
- AND no range indicator is shown (the test did not run for a positive duration)

#### Scenario: Speedtest detail card
- GIVEN the replay timeline with speedtest markers
- WHEN a user taps a marker or range indicator
- THEN a detail card is shown below the timeline
- AND the card displays: timestamp, finish time (when known), duration (when positive), download speed, upload speed, server name/ID, RSRP at test, RAT at test, and band at test

#### Scenario: Speedtest card auto-updates during playback
- GIVEN replay mode is active and the user is scrubbing or playing
- WHEN the current position passes a speedtest marker or range indicator
- THEN the speedtest detail card updates to show the last speedtest result at or before the current position
- AND the card continues showing that result until the next marker is passed
- AND the card shows "Position" label for auto-updated markers and "Selected" label for manually tapped markers
