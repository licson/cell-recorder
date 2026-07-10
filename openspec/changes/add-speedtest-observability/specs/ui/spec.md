## MODIFIED Requirements

### Requirement: Settings Screen — Speed Test Section

The system SHALL provide a "Speed Test" settings section for configuring continuous throughput tests. The section also provides a manual "Launch Test" affordance for priming the mobile connection and a debug card for diagnosing engine behavior. Manual launch and diagnostics are defined in `speedtest-diagnostics/spec.md`.

#### Scenario: Speed test settings displayed
- GIVEN the Settings screen
- THEN a "Speed Test" card is displayed
- AND the card contains: a master enable toggle, a "Download Speed" label indicating the feature provides throughput measurement, an upload test toggle (default ON), an interval picker (default 60s), and an optional server ID input (blank for auto-select)

#### Scenario: EULA dialog on enable
- GIVEN the user toggles "Enable Speed Test" to ON
- WHEN the speedtest binary or feature has not been previously accepted
- THEN a dialog is displayed informing the user of Speedtest.net's Terms of Use and Privacy Policy
- AND a data usage warning: "Each test uses approximately 5-15 MB of cellular data (download only) or 10-30 MB (with upload). With a 60-second interval, expect ~300-1800 MB per hour of recording."
- AND the dialog provides a link to open Speedtest.net Terms in a browser
- AND a link to open Speedtest.net Privacy Policy in a browser
- AND the user can either Accept (toggle stays ON) or Decline (toggle reverts to OFF)

#### Scenario: EULA re-prompt on binary absence
- GIVEN the user has previously accepted the EULA
- WHEN the user toggles speed tests OFF and ON again
- THEN the EULA dialog is not shown again (toggle activates immediately)

#### Scenario: Launch Test button shown when enabled
- GIVEN the Settings screen
- WHEN `speedTestEnabled` is true
- THEN a "Launch Test" button is rendered inside the Speed Test card
- AND tapping the button triggers a manual speedtest launch (see `speedtest-diagnostics/spec.md`)

#### Scenario: Launch Test button hidden when disabled
- GIVEN the Settings screen
- WHEN `speedTestEnabled` is false
- THEN the "Launch Test" button is NOT rendered

#### Scenario: Debug card collapsed by default
- GIVEN the Settings screen and `speedTestEnabled` is true
- WHEN no manual launch is in progress
- THEN the debug card is collapsed or summary-only
- AND does not dominate the Settings page

#### Scenario: Debug card expands on launch
- GIVEN the "Launch Test" button is tapped
- WHEN the manual launch begins
- THEN the debug card expands to show the live event stream from the ring buffer (see `speedtest-diagnostics/spec.md`)
- AND the event list auto-scrolls to the newest event

#### Scenario: Share Debug Log action
- GIVEN the debug card is expanded
- WHEN the user taps the "Share Debug Log" action
- THEN the current ring buffer snapshot is serialized as plain text
- AND an `Intent.ACTION_SEND` chooser is displayed (mirrors the existing "Share Crash Log" pattern)
