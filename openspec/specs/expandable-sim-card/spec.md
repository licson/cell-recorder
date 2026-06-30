# Expandable SimCard Specification

## Purpose

Defines the expandable `SimCard` composable on the RecordingScreen that collapses to a compact NSA/CA indicator and expands to show full anchor and CA band details when tapped.

## Scope

This spec covers the SimCard expand/collapse behavior, the structured `SimLiveState` fields that drive it, and the visual presentation of anchor and CA band data. It does not define:
- Cell identity processing (see `cell-info/spec.md`).
- Signal quality color thresholds (see `ui/spec.md`).
- The RecordingScreen layout (see `ui/spec.md`).

## Related Specs

- `ui/spec.md` — RecordingScreen layout and signal quality color coding.
- `cell-info/spec.md` — anchor cell and CA band semantics.
- `analytics/spec.md` — band distribution labels.

## Requirements

### Requirement: Expandable SimCard on RecordingScreen

The system SHALL provide an expandable `SimCard` on the RecordingScreen that collapses to a compact NSA/CA indicator and expands to show full anchor and CA band details when tapped.

#### Scenario: Collapsed SimCard for 5G NSA
- GIVEN an active recording with a 5G NSA SIM
- WHEN the SimCard is in collapsed state
- THEN the card shows the existing two rows (identity/signal)
- AND a third row shows: `LTE: B<band> PCI <pci> RSRP <rsrp>` with RSRP color-coded by quality
- AND an expand indicator chevron is visible at the right edge

#### Scenario: Collapsed SimCard for 4G CA
- GIVEN an active recording with a 4G CA SIM
- WHEN the SimCard is in collapsed state
- THEN the Band field shows `B<band>+<N>` where N is the CA band count
- AND an expand indicator chevron is visible at the right edge

#### Scenario: Collapsed SimCard for non-CA 4G/3G/2G
- GIVEN an active recording with a non-CA SIM
- WHEN the SimCard is in collapsed state
- THEN the card shows the existing two rows with no additional rows
- AND no expand indicator is shown (card is not expandable)

#### Scenario: Expanded SimCard for 5G NSA
- GIVEN a collapsed 5G NSA SimCard
- WHEN the user taps the card
- THEN the card expands to show an Anchor section with: Band, ARFCN, PCI, TAC, RSRP, RSRQ, SINR (color-coded)
- AND a CA Bands section listing each CA band as: `B<band> PCI <pci> RSRP <rsrp> RSRQ <rsrq> SINR <sinr>`

#### Scenario: Expanded SimCard for 4G CA
- GIVEN a collapsed 4G CA SimCard
- WHEN the user taps the card
- THEN the card expands to show a CA Bands section listing each CA band as: `B<band> PCI <pci> RSRP <rsrp> RSRQ <rsrq> SINR <sinr>`

#### Scenario: Collapse expanded SimCard
- GIVEN an expanded SimCard
- WHEN the user taps the card again
- THEN the card collapses back to its compact state

#### Scenario: Structured anchor and CA data in SimLiveState
- GIVEN the RecordingViewModel polling cell info
- WHEN snapshots are mapped to `SimLiveState`
- THEN `SimLiveState` includes `anchorBand`, `anchorPci`, `anchorArfcn`, `anchorTac`, `anchorRsrp`, `anchorRsrq`, `anchorSinr` as individual fields
- AND `SimLiveState` includes `caBandDetails: List<CaBandDetail>` where each entry has `band`, `pci`, `rsrp`, `rsrq`, `sinr`
