## MODIFIED Requirements

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
