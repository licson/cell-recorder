## MODIFIED Requirements

### Requirement: Band Distribution

The system SHALL calculate the frequency band usage distribution per SIM, with each band entry tagged by its source RAT. Band labels SHALL use RAT-qualified prefixes (e.g., "n78" for 5G, "B3" for 4G).

#### Scenario: Band distribution computed
- GIVEN a session with recorded points
- WHEN analytics are generated
- THEN bands used per SIM are listed sorted by occurrence count
- AND each band entry includes its source RAT (`5G_NSA`, `5G_SA`, `4G`, `4G_CA`, `3G`, `2G`)
- AND band labels use `BandResolver.formatBand()` to produce qualified names (e.g., "n78", "B3")

#### Scenario: Band distribution chart grouped by RAT
- GIVEN a session with band distribution data
- WHEN the analytics panel renders the band distribution chart
- THEN bands are visually grouped by RAT type (NR bands, LTE primary bands, LTE CA bands)
- AND NR bands use cool-toned colors distinct from LTE bands
- AND the chart legend distinguishes NR from LTE bands
