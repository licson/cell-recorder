## MODIFIED Requirements

### Requirement: Session Replay

The system SHALL replay a session's recorded path with animated playback. For `5G_NSA` records, the replay stats panel SHALL display both the NR cell data and the LTE anchor cell data, including the anchor Cell ID formatted as `anchorEnbOrGnbId:anchorLcid` (rendering `---` when either component is missing). 5G NSA anchor semantics are defined in `cell-info/spec.md`.

#### Scenario: Replay started
- GIVEN a session with recorded points
- WHEN the user taps the Replay button
- THEN the map shows all points (faded) with an animated marker along the path

#### Scenario: Playback controls
- GIVEN replay mode is active
- THEN the user can play, pause, scrub with a time slider, and select speed (1x, 2x, 5x, 10x)

#### Scenario: Replay stats panel
- GIVEN replay mode is active
- WHEN the marker reaches a point
- THEN the current point's RAT, PCI, RSRP, RSRQ, SINR, ping, and packet loss are shown in a stats panel
- AND for `5G_NSA` records, the LTE anchor's Cell ID (`anchorEnbOrGnbId:anchorLcid`, or `---` if either is missing), band, PCI, and RSRP are also displayed

#### Scenario: Timeline chart in replay
- GIVEN replay mode is active
- THEN RSRP and latency curves are displayed over time
- AND a vertical cursor is synced to the replay position
