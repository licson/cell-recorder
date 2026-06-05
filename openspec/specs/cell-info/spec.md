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

The system SHALL identify the primary serving cell for each subscription.

#### Scenario: Identify primary serving cell
- GIVEN cell data for a subscription
- WHEN the cells are enumerated
- THEN the cell with `connectionStatus` equal to `PrimaryConnection` is identified as the serving cell

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