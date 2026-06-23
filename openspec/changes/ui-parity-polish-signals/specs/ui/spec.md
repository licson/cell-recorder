## MODIFIED Requirements

### Requirement: Recording Screen — Live Stats Panel
The live stats panel SHALL display per-SIM cell data with signal quality color coding on RSRP, RSRQ, and SINR values. When the SimCard is in its collapsed state, these values SHALL be color-coded by quality (excellent/good/fair/poor). When expanded, the same color coding SHALL apply to the anchor cell and CA band signal metrics.

#### Scenario: Signal quality colors on live stats
- **WHEN** the RecordingScreen shows the live stats panel
- **THEN** RSRP, RSRQ, and SINR values on each SimCard SHALL be colored using the shared signal quality color helpers
- **AND** the color thresholds SHALL match the existing map display mode thresholds (excellent > -80 dBm, good -80 to -90 dBm, fair -90 to -100 dBm, poor < -100 dBm for RSRP)

#### Scenario: Anchor and CA band signal colors in expanded SimCard
- **WHEN** the SimCard is expanded to show anchor or CA band details
- **THEN** anchor cell RSRP, RSRQ, and SINR values SHALL be color-coded using the same quality helpers
- **AND** each CA band's RSRP, RSRQ, and SINR values SHALL be color-coded

### Requirement: Live Info Screen — LiveSimCard
The LiveSimCard SHALL apply signal quality color coding to primary cell RSRP, RSRQ, and SINR values. The card SHALL also use the structured CA band and anchor fields added by the `improve-5g-nsa-4g-ca-ui` change.

#### Scenario: Live info signal colors
- **WHEN** the LiveInfoScreen displays a LiveSimCard
- **THEN** RSRP, RSRQ, and SINR values SHALL be colored using the shared signal quality helpers
- **AND** CA band chips SHALL show per-band RSRP color-coded text
- **AND** anchor cell metrics SHALL show color-coded signal values

### Requirement: Replay Screen — StatsPanel
The StatsPanel in ReplayScreen SHALL be expandable and display structured anchor cell and CA band data with signal quality color coding. When collapsed, it SHALL show a compact anchor row for 5G NSA and a CA band count badge for 4G CA. When expanded, it SHALL reveal full anchor fields and structured CA band rows.

#### Scenario: Replay stats panel collapsed state
- **WHEN** the ReplayScreen shows the StatsPanel for a 5G NSA record
- **THEN** a compact anchor row is displayed: "LTE: B<band> PCI <pci> RSRP <rsrp>"
- **AND** RSRP is color-coded using the shared signal quality helpers

#### Scenario: Replay stats panel with CA bands
- **WHEN** the ReplayScreen shows the StatsPanel for a 4G CA record
- **THEN** the band label shows "B<band>+<N>" where N is the CA band count

#### Scenario: Replay stats panel expanded state
- **WHEN** the user taps the StatsPanel to expand it
- **THEN** full anchor details are shown (Band, ARFCN, PCI, TAC, RSRP, RSRQ, SINR)
- **AND** structured CA band rows are shown (band, PCI, EARFCN, RSRP, RSRQ, SINR)
- **AND** all signal values are color-coded

#### Scenario: Replay stats panel signal quality colors
- **WHEN** the StatsPanel displays primary cell, anchor cell, or CA band signal metrics
- **THEN** RSRP, RSRQ, and SINR values SHALL be colored using the shared signal quality helpers

### Requirement: Session Detail Screen — Record Detail Sheet
The RecordDetailSheet SHALL apply signal quality color coding to all signal metrics displayed in the Primary Cell, CA Bands, and Anchor Cell sections.

#### Scenario: Detail sheet signal colors
- **WHEN** the RecordDetailSheet is displayed for a selected record
- **THEN** primary cell RSRP, RSRQ, and SINR values SHALL be color-coded
- **AND** anchor cell RSRP, RSRQ, and SINR values SHALL be color-coded
- **AND** each CA band's RSRP, RSRQ, and SINR values SHALL be color-coded
- **AND** all color coding uses the shared signal quality helpers
