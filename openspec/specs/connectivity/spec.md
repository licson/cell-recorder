# Connectivity Specification

## Purpose

Defines how the system measures network connectivity through ICMP ping during active recording sessions, including latency and packet loss calculation.

## Requirements

### Requirement: Continuous Ping During Recording

The system SHALL continuously ping a configurable destination while a recording is active.

#### Scenario: Ping loop starts
- GIVEN a recording session has started
- WHEN the recording begins
- THEN a continuous ping loop begins
- AND pings are dispatched at `pingIntervalMs` intervals

#### Scenario: Ping loop stops
- GIVEN an active recording with running pings
- WHEN the recording is stopped
- THEN the ping loop is terminated

### Requirement: Ping Timeout

The system SHALL apply a configurable timeout to each ping attempt.

#### Scenario: Ping response within timeout
- GIVEN a running ping loop
- WHEN a ping response is received within `pingTimeoutMs`
- THEN the latency is recorded

#### Scenario: Ping timeout exceeded
- GIVEN a running ping loop
- WHEN no response is received within `pingTimeoutMs`
- THEN the ping is recorded as lost (null)

### Requirement: Sliding Window Latency

The system SHALL maintain a sliding window of recent ping results.

#### Scenario: Window maintained
- GIVEN a running ping loop
- WHEN pings complete
- THEN the last N ping results are maintained in memory
- AND the default window size is 5

### Requirement: Latency Aggregation

The system SHALL compute the average latency from the sliding window at each recording point.

#### Scenario: Latency recorded per point
- GIVEN an active recording with a ping window
- WHEN a recording point is triggered
- THEN `avgLatencyMs` is computed as the mean of all non-null values in the window

### Requirement: Packet Loss Calculation

The system SHALL compute the packet loss percentage from the sliding window at each recording point.

#### Scenario: Packet loss recorded per point
- GIVEN an active recording with a ping window
- WHEN a recording point is triggered
- THEN `packetLossPct` is computed as (null count / window size) * 100