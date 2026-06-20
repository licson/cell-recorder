# Cell Information Specification

## Purpose

Defines how the system collects and processes cellular network information from all active SIM subscriptions, including carrier aggregation bands and cell identity splitting.
## Requirements
### Requirement: Cell Info Collection per Subscription

The system SHALL collect cell information for every active SIM subscription separately.

#### Scenario: Collect per-subscription cell data
- GIVEN an active recording with one or more SIM subscriptions
- WHEN a recording point is triggered
- THEN cell information is queried for each subscription individually
- AND the serving cell is identified per subscription

### Requirement: Serving Cell Detection

The system SHALL identify the primary serving cell for each subscription. For 5G NSA subscriptions, the NR cell is identified as the primary cell and the LTE anchor is identified as the anchor cell.

#### Scenario: Identify primary serving cell (non-NSA)
- GIVEN cell data for a subscription where `networkType` is not `NetworkType.Nr.Nsa`
- WHEN the cells are enumerated
- THEN the cell with `connectionStatus` equal to `PrimaryConnection` is identified as the serving cell

#### Scenario: Identify primary serving cell (NSA)
- GIVEN cell data for a subscription where `networkType` is `NetworkType.Nr.Nsa`
- AND a `CellNr` cell is present
- WHEN the cells are enumerated
- THEN the `CellNr` cell is identified as the primary cell
- AND the `CellLte` cell with `PrimaryConnection` is identified as the anchor cell

### Requirement: Carrier Aggregation Band Detection

The system SHALL detect and capture secondary LTE cells used for carrier aggregation.

#### Scenario: CA band detection
- GIVEN an LTE serving cell
- WHEN additional LTE cells have `connectionStatus` equal to `SecondaryConnection`
- THEN those secondary cells are captured as carrier aggregation bands

#### Scenario: No CA bands
- GIVEN an LTE serving cell
- WHEN no secondary LTE cells are detected
- THEN no carrier aggregation bands are captured

### Requirement: Cell ID Split for 4G

The system SHALL split the full LTE cell identity into eNB ID and local cell ID components.

#### Scenario: LTE cell ID split
- GIVEN an LTE cell with a `fullCellIdentity`
- WHEN the identity is processed
- THEN `enbOrGnbId` is derived from bits 8 and above
- AND `lcid` is derived from the lower 8 bits

### Requirement: Cell ID Split for 5G

The system SHALL split the full 5G NR cell identity (NCI) into gNB ID and local cell ID components using a configurable bit length.

#### Scenario: NR cell ID split with default bit length
- GIVEN a 5G cell with a `fullCellIdentity` (NCI)
- WHEN the identity is processed
- THEN `enbOrGnbId` is derived from the upper (36 - bitLen) bits
- AND `lcid` is derived from the lower (36 - bitLen) bits
- AND the default bit length is 24

### Requirement: Configurable NR gNB Bit Length

The system SHALL allow the user to configure the NR gNB bit length used for cell ID splitting.

#### Scenario: Change bit length in settings
- GIVEN the Settings screen
- WHEN the user changes the NR gNB Bit-Length value
- THEN all subsequent cell ID splits use the new bit length

### Requirement: Batch Re-Split

The system SHALL allow the user to re-apply the cell ID split formula to all points in an existing session.

#### Scenario: Batch re-split
- GIVEN a session with recorded points
- WHEN the user initiates a batch re-split action
- THEN every point in the session has its `enbOrGnbId` and `lcid` recalculated using the current bit length

### Requirement: 5G NSA Cell Detection

When the network type is `NetworkType.Nr.Nsa`, the system SHALL identify the NR cell as the primary cell and the LTE anchor cell as the anchor, and record the primary cell with `rat` equal to `"5G_NSA"`. The modem's `NetworkType.Nr.Nsa` flag alone is not sufficient to label a tick `5G_NSA`; a `CellNr` cell SHALL be present in the subscription's cell list. When no `CellNr` cell is present, the tick SHALL be recorded using standard LTE behavior (or as `UNKNOWN` if no LTE primary is present), with the LTE anchor's full identity, band, and signal metrics populated as primary-cell fields, and `networkTypeCode` preserving the modem's NSA technology code for diagnostic visibility.

#### Scenario: NSA mode detected with NR secondary and LTE anchor
- GIVEN a subscription with `networkType` equal to `NetworkType.Nr.Nsa`
- AND a `CellNr` cell and a `CellLte` cell with `PrimaryConnection` are present
- WHEN a recording point is triggered
- THEN a record with `rat` equal to `"5G_NSA"` is created from the `CellNr` cell
- AND the LTE anchor cell's identity, band, and signal metrics are stored as anchor fields on the same record

#### Scenario: NSA mode with no NR cell found
- GIVEN a subscription with `networkType` equal to `NetworkType.Nr.Nsa`
- AND no `CellNr` cell is present in the subscription's cell list
- AND a `CellLte` cell with `PrimaryConnection` is present
- WHEN a recording point is triggered
- THEN the LTE anchor cell is recorded as the primary cell with full LTE field coverage identical to a non-NSA LTE tick (identity split, band, ARFCN, PCI, TAC, bandwidth, RSRP, RSRQ, SINR, RSSI, CQI, timing advance, MCC, MNC, and CA bands)
- AND the record's `rat` is labeled `"4G_CA"` when LTE carrier aggregation bands are present, otherwise `"4G"`
- AND the record's `networkTypeCode` is populated with the modem's NSA technology code so the tick can be distinguished from a genuine LTE tick

#### Scenario: NSA mode with no NR cell and no LTE anchor found
- GIVEN a subscription with `networkType` equal to `NetworkType.Nr.Nsa`
- AND no `CellNr` cell is present in the subscription's cell list
- AND no `CellLte` cell with `PrimaryConnection` is present
- WHEN a recording point is triggered
- THEN a record with `rat` equal to `"UNKNOWN"` is created
- AND the record's `networkTypeCode` is populated with the modem's NSA technology code
- AND all other cell-identity, band, and signal fields are null

#### Scenario: NSA mode with no LTE anchor found
- GIVEN a subscription with `networkType` equal to `NetworkType.Nr.Nsa`
- AND a `CellNr` cell is present but no `CellLte` cell with `PrimaryConnection` is found
- WHEN a recording point is triggered
- THEN a record with `rat` equal to `"5G_NSA"` is created from the `CellNr` cell
- AND all anchor fields are null

### Requirement: 5G NSA Anchor Cell Capture

The system SHALL capture the LTE anchor cell's identity, band, and signal metrics as anchor fields on `5G_NSA` records.

#### Scenario: LTE anchor fields populated
- GIVEN a `5G_NSA` record and an LTE anchor cell with `PrimaryConnection`
- WHEN the record is created
- THEN the following anchor fields are populated from the LTE anchor cell:
  - `anchorEnbOrGnbId` from `eci shr 8`
  - `anchorLcid` from `eci and 0xFF`
  - `anchorPci` from `pci`
  - `anchorTac` from `tac`
  - `anchorBandNumber` from the LTE band lookup
  - `anchorEarfcn` from `downlinkEarfcn`
  - `anchorBandwidthKhz` from `bandwidth`
  - `anchorRsrp` from `rsrp`
  - `anchorRsrq` from `rsrq`
  - `anchorSinr` from `snr`
  - `anchorRssi` from `rssi`
  - `anchorCqi` from `cqi`
  - `anchorTimingAdvance` from `timingAdvance`

#### Scenario: LTE anchor fields not available
- GIVEN a `5G_NSA` record and no LTE anchor cell
- WHEN the record is created
- THEN all anchor fields are null

### Requirement: 5G NSA CA Band Attachment

The system SHALL attach LTE carrier aggregation bands from the LTE anchor's secondary cells to the `5G_NSA` record.

#### Scenario: CA bands from LTE anchor
- GIVEN a `5G_NSA` record and an LTE anchor cell with secondary LTE cells
- WHEN the record is created
- THEN the LTE anchor's `SecondaryConnection` LTE cells are captured as CA bands on the NSA record

#### Scenario: No CA bands on NSA record
- GIVEN a `5G_NSA` record and an LTE anchor cell with no secondary LTE cells
- WHEN the record is created
- THEN no CA bands are attached to the NSA record

### Requirement: 5G NR Bandwidth Capture

The system SHALL populate `bandwidthKhz` for 5G NR cells when the modem reports bandwidth.

#### Scenario: NR bandwidth available
- GIVEN a `CellNr` cell where `band.bandwidth` is not null
- WHEN a record is created
- THEN `bandwidthKhz` is populated with the bandwidth value

#### Scenario: NR bandwidth not available
- GIVEN a `CellNr` cell where `band.bandwidth` is null
- WHEN a record is created
- THEN `bandwidthKhz` is null

