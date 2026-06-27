## MODIFIED Requirements

### Requirement: Handoff Detection

The system SHALL detect cell and site handoff events within a session. Handoffs are detected per SIM slot within `handoffTimeWindowMs`.

#### Scenario: Handoff type classified
- GIVEN a session with recorded points
- WHEN a RAT change occurs within `handoffTimeWindowMs`
- THEN it is classified as `RAT_CHANGE`
- WHEN a cell identity change occurs and `enbOrGnbId` is identical between consecutive records
- THEN it is classified as `INTRA_SITE_PCI_CHANGE`
- WHEN a cell identity change occurs and `enbOrGnbId` differs between consecutive records
- THEN it is classified as `INTER_SITE`
- WHEN a band number change occurs without cell identity or PCI change
- THEN it is classified as `BAND_CHANGE`
- WHEN the primary cell remains identical on a `5G_NSA` connection but the LTE anchor's `anchorEnbOrGnbId` or `anchorPci` changes
- THEN it is classified as `NSA_ANCHOR_CHANGE`
- WHEN a PCI change occurs without a detectable cell identity change
- THEN it is classified as `UNKNOWN_CELL_CHANGE`

#### Scenario: Handoff enriched payload
- GIVEN a handoff event is detected
- THEN the event includes `fromRat`, `toRat`, `fromBand`, `toBand`, `fromCellId`, `toCellId` fields
- AND the latency impact (`latencyDeltaMs`) and packet loss impact (`packetLossDeltaPct`) of the handoff are tracked

### Requirement: Band Distribution with RAT Context

The system SHALL compute band usage distribution per SIM and tag each band entry with its source RAT, using qualified band names in the UI. For CA bands, the RAT SHALL be `"4G"` (or `"4G_CA"`) since CA bands currently originate from LTE secondary cells, even if the primary record is `5G_NSA`.

#### Scenario: Band distribution tagged with RAT
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN each band entry in the distribution is tagged with the source RAT
- AND primary bands inherit the RAT of the primary record
- AND CA bands use the `"4G"` or `"4G_CA"` RAT to ensure they are labeled as LTE bands in distribution charts
- AND the distribution is sorted by count descending within each RAT group

#### Scenario: Qualified band labels in UI
- GIVEN the band distribution chart is rendered in AnalyticsPanel or StatisticsScreen
- THEN legend labels use qualified band names (e.g., "B3" for LTE, "n78" for 5G)
- AND NR bands are grouped with cool tone colors (cyan/teal range)
- AND LTE bands are grouped with warm tone colors (blue/indigo range)
