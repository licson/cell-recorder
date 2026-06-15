## ADDED Requirements

### Requirement: Record detail bottom sheet

The system SHALL display a `ModalBottomSheet` when the user taps a cell record row in the SessionDetailScreen, showing the full record data including primary cell details, CA bands, anchor cell, location, and connectivity.

#### Scenario: Tap record row opens detail sheet
- GIVEN the SessionDetailScreen with record rows displayed
- WHEN the user taps a record row
- THEN a `ModalBottomSheet` opens showing the selected record's details
- AND the sheet contains sections for Primary Cell, CA Bands, Anchor Cell, Location, and Connectivity

#### Scenario: Primary cell section in detail sheet
- GIVEN a record detail bottom sheet is open
- THEN the Primary Cell section displays: RAT, PLMN, Cell ID, PCI, TAC, Band, ARFCN, Bandwidth, RSRP, RSRQ, SINR, RSSI, CQI, and Timing Advance
- AND each signal metric is color-coded by quality (excellent/good/fair/poor)

#### Scenario: CA bands section in detail sheet
- GIVEN a record detail bottom sheet is open for a record with CA bands
- THEN the CA Bands section lists each CA band as a row showing: band, EARFCN, PCI, RSRP, RSRQ, SINR
- AND each band's RSRP is color-coded by quality

#### Scenario: No CA bands section
- GIVEN a record detail bottom sheet is open for a record without CA bands
- THEN the CA Bands section is not displayed

#### Scenario: Anchor cell section for 5G NSA
- GIVEN a record detail bottom sheet is open for a `5G_NSA` record with anchor data
- THEN the Anchor Cell section displays: band, EARFCN, PCI, TAC, bandwidth, RSRP, RSRQ, SINR, RSSI, CQI, and Timing Advance
- AND each signal metric is color-coded by quality

#### Scenario: No anchor section for non-NSA records
- GIVEN a record detail bottom sheet is open for a non-5G_NSA record
- THEN the Anchor Cell section is not displayed

#### Scenario: Location section in detail sheet
- GIVEN a record detail bottom sheet is open
- THEN the Location section displays latitude, longitude, altitude, accuracy, and location source
- AND for indoor records, it displays relativeX, relativeY instead

#### Scenario: Connectivity section in detail sheet
- GIVEN a record detail bottom sheet is open
- THEN the Connectivity section displays average latency (ms) and packet loss percentage

#### Scenario: Dismiss detail sheet
- GIVEN a record detail bottom sheet is open
- WHEN the user swipes down or taps outside the sheet
- THEN the sheet is dismissed and the record list remains at its scroll position
