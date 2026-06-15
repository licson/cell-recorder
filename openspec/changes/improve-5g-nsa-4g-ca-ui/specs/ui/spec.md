## MODIFIED Requirements

### Requirement: Live Info Screen

The system SHALL display real-time cell information for all active SIMs, including structured CA band chips and expanded anchor sections.

#### Scenario: Live info displayed
- GIVEN the Live Info tab is selected
- THEN a card is shown for each active SIM
- AND each card displays PLMN, RAT, Band, ARFCN, Cell ID, PCI, TAC, RSRP, RSRQ, and SINR
- AND sparkline charts show RSRP and SINR history per SIM

#### Scenario: CA bands displayed as structured chips
- GIVEN a SIM with active carrier aggregation bands
- WHEN the Live Info card is rendered
- THEN CA bands are displayed as individual chips in a FlowRow
- AND each chip shows `B<band> PCI <pci>` with RSRP text color-coded by quality

#### Scenario: Anchor section with structured rows
- GIVEN a 5G NSA SIM with an anchor cell
- WHEN the Live Info card is rendered
- THEN the Anchor section displays: Band, ARFCN, PCI, TAC in row 1
- AND RSRP, RSRQ, SINR in row 2 (color-coded by quality)
- AND if available: TA, CQI, RSSI, Bandwidth in row 3

#### Scenario: No cell data
- GIVEN the Live Info tab is selected
- WHEN no SIM data is detected
- THEN a "No cell data available" message is displayed

### Requirement: Recording Screen

The system SHALL provide a screen for controlling and monitoring an active recording. The screen layout SHALL differ based on recording mode: outdoor mode shows an OSM map with GPS status; indoor mode shows a 2D canvas with tracking confidence and drift indicators. SimCards on the live stats panel SHALL be expandable to show 5G NSA anchor and 4G CA band details.

#### Scenario: Recording screen layout
- GIVEN a session has been created
- WHEN the user navigates to recording
- THEN the screen displays a top bar with session name, elapsed timer, and point counter
- AND if outdoor: an OSM map is shown with GPS status indicator and accuracy
- AND if indoor: a 2D path canvas is shown with tracking confidence indicator and drift radius
- AND a Start/Stop button is centered at the bottom
- AND a live stats panel shows per-SIM cell data and ping latency
- AND SimCards are expandable to reveal anchor and CA band details

#### Scenario: Map markers and path (outdoor)
- GIVEN an active outdoor recording
- THEN recorded points are shown as RAT-colored markers on the map
- AND a path polyline connects the markers

#### Scenario: Indoor canvas path (indoor)
- GIVEN an active indoor recording
- THEN recorded points are shown as signal-colored segments on the 2D canvas
- AND a path polyline connects the points with discontinuity markers at origin resets

#### Scenario: GPS status indicator
- GIVEN an active recording
- THEN a GPS status indicator is shown with one of: "OK", "Searching...", or "EXTRAPOLATING"
- AND the current GPS accuracy is displayed
- AND for indoor mode the GPS status is NOT displayed

#### Scenario: Point tooltip
- GIVEN the recording screen map
- WHEN the user taps a point marker
- THEN a tooltip with all point attributes is displayed

#### Scenario: Speedtest status in live stats
- GIVEN an active recording with speedtest enabled
- THEN the live stats bar displays speedtest status in the format: idle (`Speed: ---`), discovering (`Speed: Selecting server...`), downloading (`Speed: Testing ↓...`), uploading (`Speed: Testing ↑...`), completed (`Speed: ↓156 ↑42 Mbps`), failed (`Speed: Failed`), or skipped on WiFi (`Speed: (WiFi)`)

### Requirement: Session Detail Record Row CA Badge

The system SHALL display a CA band count badge on session detail record rows when a record has carrier aggregation bands.

#### Scenario: Record row with CA bands
- GIVEN a session detail record row for a record with CA bands
- WHEN the row is rendered
- THEN the Band column shows `<formatted_band> +<N>` where N is the number of CA bands

#### Scenario: Record row without CA bands
- GIVEN a session detail record row for a record without CA bands
- WHEN the row is rendered
- THEN the Band column shows `<formatted_band>` with no count suffix

### Requirement: Session Detail Column Headers

The system SHALL display correct column headers in the session detail records list.

#### Scenario: Column headers match data
- GIVEN the SessionDetailScreen records list
- WHEN the column header row is rendered
- THEN the columns are: #, SIM, PLMN, Band, RSRP (dBm), RSRQ (dBm), and either relX/relY (indoor) or Ping (ms) (outdoor)
- AND no duplicate header labels exist
