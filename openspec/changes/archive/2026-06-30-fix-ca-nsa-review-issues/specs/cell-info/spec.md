## MODIFIED Requirements

### Requirement: Carrier Aggregation Band Detection

The system SHALL detect and capture secondary LTE cells used for carrier aggregation, including bandwidth per secondary cell.

#### Scenario: CA band detection
- GIVEN an LTE serving cell
- WHEN additional LTE cells have `connectionStatus` equal to `SecondaryConnection`
- THEN those secondary cells are captured as carrier aggregation bands
- AND each CA band captures `bandNumber`, `earfcn`, `pci`, `rsrp`, `rsrq`, `sinr`, `rssi`, `cqi`, `timingAdvance`, and `bandwidthKhz`

#### Scenario: No CA bands
- GIVEN an LTE serving cell
- WHEN no secondary LTE cells are detected
- THEN no carrier aggregation bands are captured

### Requirement: 5G NSA Cell Detection

When the network type is `NetworkType.Nr.Nsa`, the system SHALL identify the NR cell as the primary cell and the LTE anchor cell as the anchor, and record the primary cell with `rat` equal to `"5G_NSA"`. The modem's `NetworkType.Nr.Nsa` flag alone is not sufficient to label a tick `5G_NSA`; a `CellNr` cell SHALL be present in the subscription's cell list. When no `CellNr` cell is present, the tick SHALL be recorded using standard LTE behavior (or as `UNKNOWN` if no LTE primary is present), with the LTE anchor's full identity, band, and signal metrics populated as primary-cell fields, and `networkTypeCode` preserving the modem's NSA technology code for diagnostic visibility. The primary cell TAC SHALL be sourced from the NR cell when available, falling back to the LTE anchor TAC.

#### Scenario: NSA mode detected with NR secondary and LTE anchor
- GIVEN a subscription with `networkType` equal to `NetworkType.Nr.Nsa`
- AND a `CellNr` cell and a `CellLte` cell with `PrimaryConnection` are present
- WHEN a recording point is triggered
- THEN a record with `rat` equal to `"5G_NSA"` is created from the `CellNr` cell
- AND the LTE anchor cell's identity, band, and signal metrics are stored as anchor fields on the same record
- AND the primary cell `tac` is set to `nrCell.tac` when non-null, otherwise `lteAnchor.tac`

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

### Requirement: 5G NR Bandwidth Not Captured

The system SHALL leave `bandwidthKhz` null for 5G NR primary cells (both SA and NSA), because the NetMonster 1.3.0 library does not expose a bandwidth property on `CellNr` or `BandNr`. LTE primary cell bandwidth continues to be captured via `CellLte.bandwidth`, and the LTE anchor bandwidth on 5G NSA records continues to be captured as `anchorBandwidthKhz`.

#### Scenario: NR bandwidth not captured (SA)
- GIVEN a `CellNr` cell used as a 5G SA primary cell
- WHEN a record is created
- THEN `bandwidthKhz` is null

#### Scenario: NR bandwidth not captured (NSA)
- GIVEN a `CellNr` cell used as a 5G NSA primary cell
- WHEN a record is created
- THEN primary `bandwidthKhz` is null
- AND `anchorBandwidthKhz` is populated from the LTE anchor's bandwidth when available

### Requirement: Cell ID Split for 4G

The system SHALL split the full LTE cell identity into eNB ID and local cell ID components. The `cellIdBitLength` for LTE records SHALL be set to 8.

#### Scenario: LTE cell ID split
- GIVEN an LTE cell with a `fullCellIdentity`
- WHEN the identity is processed
- THEN `enbOrGnbId` is derived from bits 8 and above
- AND `lcid` is derived from the lower 8 bits
- AND `cellIdBitLength` is set to 8

## ADDED Requirements

### Requirement: LTE CA RAT Detection Based on Actual Bands

The system SHALL determine the `4G_CA` RAT label based on whether extracted CA bands are present, not based on the modem's reported `NetworkType.LTE_CA` technology code. If `extractCaBands()` returns a non-empty list for a non-NSA LTE cell, the record's `rat` SHALL be `"4G_CA"`; otherwise `"4G"`.

#### Scenario: LTE cell with secondary cells detected
- GIVEN a non-NSA LTE subscription with secondary LTE cells
- WHEN a recording point is triggered
- THEN the record's `rat` is `"4G_CA"` regardless of the modem's reported network type

#### Scenario: LTE cell without secondary cells
- GIVEN a non-NSA LTE subscription with no secondary LTE cells
- WHEN a recording point is triggered
- THEN the record's `rat` is `"4G"` regardless of the modem's reported network type
