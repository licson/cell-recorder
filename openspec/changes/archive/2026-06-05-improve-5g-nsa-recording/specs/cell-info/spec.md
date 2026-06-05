## ADDED Requirements

### Requirement: 5G NSA Cell Detection

When the network type is `NetworkType.Nr.Nsa`, the system SHALL identify the NR cell as the primary cell and the LTE anchor cell as the anchor, and record the primary cell with `rat` equal to `"5G_NSA"`.

#### Scenario: NSA mode detected with NR secondary and LTE anchor
- GIVEN a subscription with `networkType` equal to `NetworkType.Nr.Nsa`
- AND a `CellNr` cell and a `CellLte` cell with `PrimaryConnection` are present
- WHEN a recording point is triggered
- THEN a record with `rat` equal to `"5G_NSA"` is created from the `CellNr` cell
- AND the LTE anchor cell's identity, band, and signal metrics are stored as anchor fields on the same record

#### Scenario: NSA mode with no NR cell found
- GIVEN a subscription with `networkType` equal to `NetworkType.Nr.Nsa`
- AND no `CellNr` cell is present in the subscription's cell list
- WHEN a recording point is triggered
- THEN the LTE anchor cell is recorded as the primary cell (same as non-NSA LTE behavior)

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

## MODIFIED Requirements

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
