# InsightCard Specification

## Purpose

Defines the InsightCard composable that renders real analytics insight cards in the session analytics panel, replacing the previous placeholder.

## Scope

This spec covers the InsightCard composable's data model, rendering behavior, and empty-state handling. It does not define:
- How insights are generated (see `analytics/spec.md`).
- The AnalyticsPanel layout (see `ui/spec.md`).

## Related Specs

- `analytics/spec.md` — Insight Cards generation.
- `ui/spec.md` — AnalyticsPanel layout and Insight Cards display requirement.

## Requirements

### Requirement: InsightCard renders real analytics insights

The system SHALL display computed insight cards in the session analytics panel. The InsightCard composable SHALL accept a list of insight data objects and render each as a distinct card with a title and descriptive body. When no insights are available, a compact empty-state message SHALL be displayed instead of the previous placeholder.

#### Scenario: Insights displayed
- WHEN the AnalyticsPanel is rendered for a session that has generated insights
- THEN each insight card is displayed with its title and body text
- AND the cards are stacked vertically in the panel

#### Scenario: No insights available
- WHEN the AnalyticsPanel is rendered for a session with no insights
- THEN a message "No insights for this session" is displayed
- AND the previous placeholder robot emoji and "AI-generated insights will appear here" text is NOT shown

#### Scenario: Multiple insight types
- WHEN a session has multiple insight types (e.g., Massive MIMO Candidate and Load Balancing Detected)
- THEN each insight is rendered as a separate card
- AND all cards use the same visual style (tertiary container background, rounded corners, 16 dp padding)
