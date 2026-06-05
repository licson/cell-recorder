# Connectivity Specification

## Purpose

Defines how the system measures network connectivity through ICMP ping during active recording sessions, including latency and packet loss calculation.

## Requirements

### Requirement: Continuous Ping During Recording

The system SHALL continuously ping a configurable destination while a recording is active, using a single long-running ping process.

#### Scenario: Ping process starts
- GIVEN a recording session has started
- WHEN the recording begins
- THEN a single long-running `ping -i <interval>` process is started
- AND ping results are streamed continuously via a `Flow<PingResult>`

#### Scenario: Ping process stops
- GIVEN an active recording with a running ping process
- WHEN the recording is stopped
- THEN the ping process is terminated and the flow completes

#### Scenario: Ping process restart on failure
- GIVEN an active recording with a running ping process
- WHEN the ping process dies unexpectedly
- THEN the ping process is automatically restarted
- AND a null latency result is emitted for any gap period

### Requirement: Ping Timeout

The system SHALL apply a configurable timeout to the overall ping process communication.

#### Scenario: Ping response within timeout
- GIVEN a running ping process
- WHEN a ping response line is parsed from the process output
- THEN the latency is extracted and recorded

#### Scenario: No response from ping process
- GIVEN a running ping process
- WHEN no output is received from the process for an extended period
- THEN the process is considered dead and is restarted
- AND a null latency result is emitted for the gap

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