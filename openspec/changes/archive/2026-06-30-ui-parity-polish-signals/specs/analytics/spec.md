## MODIFIED Requirements

### Requirement: Insight Cards
The system SHALL display generated insight cards in the session analytics panel. Insight cards produced by the analytics engine SHALL be visible to the user rather than replaced by a placeholder.

#### Scenario: Insight cards visible in analytics panel
- **WHEN** session analytics are generated and contain insight cards
- **THEN** the AnalyticsPanel SHALL render the actual insight cards
- **AND** each card displays its title and body text
- **AND** the placeholder "AI-generated insights will appear here" message is NOT shown

### Requirement: Band Distribution
The system SHALL calculate the frequency band usage distribution per SIM and display bands using qualified names that include the RAT prefix (e.g., "B3" for LTE, "n78" for NR).

#### Scenario: Band distribution with qualified labels
- **WHEN** the band distribution is computed
- **THEN** each band entry is tagged with its source RAT
- **AND** display labels use BandResolver.formatBand() with the RAT context
- **AND** the StatisticsScreen band distribution chart uses the same qualified labels

#### Scenario: Band distribution grouping by RAT
- **WHEN** the band distribution chart is rendered in AnalyticsPanel or StatisticsScreen
- **THEN** NR bands are displayed with cool tone colors (cyan/teal range)
- **AND** LTE bands are displayed with warm tone colors (blue/indigo range)
- **AND** the legend labels show qualified band names (e.g., "B3", "n78")
