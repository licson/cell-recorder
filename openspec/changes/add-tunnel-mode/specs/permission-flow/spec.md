## ADDED Requirements

### Requirement: Tunnel Mode Permission Path

The system SHALL require neither `ACTIVITY_RECOGNITION` nor background-location stalking for tunnel recording mode. Tunnel mode requires only the foreground service permission, phone-state permission for cell info, and notifications permission for the persistent notification. Permission decision logic is defined in the unified decision function; this requirement specifies which permissions apply to tunnel mode.

#### Scenario: Tunnel mode does not include ACTIVITY_RECOGNITION

- GIVEN the user attempts to start a tunnel recording
- THEN the permission set consulted by the unified decision function for tunnel mode SHALL NOT include `ACTIVITY_RECOGNITION`
- AND no `ACTIVITY_RECOGNITION` request is shown for tunnel mode
- (Tunnel mode: `tunnel/spec.md`)

#### Scenario: Tunnel mode does not require location permissions

- GIVEN the user attempts to start a tunnel recording
- THEN the permission set consulted by the unified decision function for tunnel mode SHALL NOT include `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, or `ACCESS_BACKGROUND_LOCATION`
- AND recording proceeds with no location runtime request shown
- (Tunnel mode: `tunnel/spec.md`)

#### Scenario: RecordingScreen uses the shared state holder for tunnel mode

- GIVEN the `RecordingScreen` Start-button permission checkpoint with `recordingMode = "TUNNEL"`
- THEN the checkpoint uses the shared state holder with `autoRequestOnLaunch = false`
- AND the permissions set includes foreground permissions, phone state, and notifications only
- AND the `indoor_permissions` path is NOT consulted for tunnel mode
- (Existing `RecordingScreen` checkpoint requirement still applies; this scenario specifies the tunnel-mode subset)
