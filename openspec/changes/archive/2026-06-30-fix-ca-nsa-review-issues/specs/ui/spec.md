## MODIFIED Requirements

### Requirement: Live Info Screen — Structured CA Bands and Anchor

The system SHALL display structured Carrier Aggregation band information and 5G NSA anchor cell details on the Live Info screen. Band labels SHALL use RAT-appropriate prefixes (e.g., "B3", "n78").

#### Scenario: CA bands displayed as chips
- GIVEN the Live Info tab is selected and a SIM has active CA bands
- THEN each CA band is shown as a chip in a FlowRow layout
- AND each chip shows the RAT-appropriate band prefix and PCI
- AND the chip text is color-coded by the CA band's RSRP value

#### Scenario: Anchor cell displayed in structured rows
- GIVEN the Live Info tab is selected and a SIM is on 5G NSA
- THEN the anchor cell section shows structured rows: Band (with "B" prefix), ARFCN, PCI, TAC
- AND a second row shows RSRP, RSRQ, SINR with signal quality color coding

### Requirement: Recording Screen — Expandable SimCard

The system SHALL provide an expandable SimCard on the RecordingScreen that reveals full 5G NSA anchor and CA band details when expanded. The card's chevron icon SHALL respond to tap events to toggle expansion. Band labels SHALL use RAT-appropriate prefixes.

#### Scenario: SimCard collapsed state
- GIVEN the RecordingScreen shows the live stats panel
- THEN each SimCard shows a compact anchor row for 5G NSA (`LTE: B<band> PCI <pci> RSRP <rsrp>`)
- AND a CA band count badge (`<prefix><band>+<N>`) when CA bands are active
- AND the card is clickable when expandable data exists

#### Scenario: SimCard expanded state
- GIVEN the user taps a SimCard with anchor or CA data (or its chevron icon)
- THEN the card expands to show full anchor details (Band, ARFCN, PCI, TAC, RSRP, RSRQ, SINR)
- AND structured CA band rows (Band, PCI, EARFCN, RSRP, RSRQ, SINR per band)
- AND CA bands that have EARFCN data display the actual EARFCN value (not "---")
- AND all signal values are color-coded by quality

### Requirement: Session Detail Screen — Record Detail Bottom Sheet

The system SHALL display a bottom sheet when a user taps a record in the session detail list, showing the full record data. Band labels SHALL use RAT-appropriate prefixes.

#### Scenario: Bottom sheet opens on tap
- GIVEN the SessionDetailScreen record list is visible
- WHEN a user taps a record row
- THEN a ModalBottomSheet opens showing the record's full details

#### Scenario: Primary cell section
- GIVEN the record detail bottom sheet is open
- THEN the Primary Cell section shows RAT, PLMN, Cell ID, PCI, TAC, Band, ARFCN, BW, RSRP, RSRQ, SINR, RSSI, CQI, TA
- AND all signal values are color-coded by quality using the appropriate color function (RSRP uses rsrpColor, RSRQ uses rsrqColor, SINR uses sinrColor)

#### Scenario: CA Bands section
- GIVEN the record detail bottom sheet is open and the record has CA bands
- THEN the CA Bands section shows a card per band with band (using RAT-appropriate prefix), EARFCN, PCI, RSRP, RSRQ, SINR
- AND each signal value is color-coded using the appropriate color function
- AND the section is hidden when no CA bands exist

#### Scenario: Anchor Cell section
- GIVEN the record detail bottom sheet is open and the record is 5G NSA with anchor data
- THEN the Anchor Cell section shows Band (with "B" prefix), EARFCN, PCI, TAC, RSRP, RSRQ, SINR
- AND each signal value is color-coded using the appropriate color function
- AND the section is hidden for non-5G_NSA records or when anchor data is missing

#### Scenario: Location and Connectivity sections
- GIVEN the record detail bottom sheet is open
- THEN the Location section shows lat/lon/alt/accuracy/source for outdoor, or relX/relY for indoor
- AND the Connectivity section shows avgLatencyMs and packetLossPct

#### Scenario: Dismiss bottom sheet
- GIVEN the record detail bottom sheet is open
- WHEN the user taps outside the sheet or swipes down
- THEN the sheet dismisses and the selected record is cleared

### Requirement: Replay Screen — Expandable StatsPanel

The system SHALL provide an expandable StatsPanel in the ReplayScreen that matches the live RecordingScreen SimCard behavior.

#### Scenario: StatsPanel expandable
- GIVEN the ReplayScreen is active and a record is selected
- THEN the StatsPanel is expandable when anchor or CA data exists
- AND the collapsed state shows a compact anchor row and CA band count badge
- AND the expanded state shows full anchor details and structured CA band rows
- AND CA bands that have EARFCN data display the actual EARFCN value
- AND all signal values are color-coded by quality
