# Connectivity Specification

## Purpose

Defines how the system measures network connectivity through ICMP ping during active recording sessions, including latency and packet loss calculation.

## Requirements

### Requirement: Continuous Ping During Recording

The system SHALL continuously ping a configurable destination while a recording is active, using a single long-running ping process with the `-O` flag for immediate dropped-packet detection. Each ping result SHALL carry a `PingOutcome` classification (`SUCCESS`, `TIMEOUT`, `HOST_UNREACHABLE`, or `PROCESS_ERROR`).

#### Scenario: Ping process starts
- GIVEN a recording session has started
- WHEN the recording begins
- THEN a single long-running `ping -O -i <interval> -W <timeout>` process is started
- AND ping results are streamed continuously via a `Flow<PingResult>`
- AND each `PingResult` includes an `outcome: PingOutcome` field

#### Scenario: Ping process stops
- GIVEN an active recording with a running ping process
- WHEN the recording is stopped
- THEN the ping process is terminated and the flow completes

#### Scenario: Successful ping reply
- GIVEN a running ping process
- WHEN a reply line containing `time=X ms` is parsed
- THEN a `PingResult` with `outcome=SUCCESS` and `latencyMs=X` is emitted

#### Scenario: Dropped packet detected via `-O` flag
- GIVEN a running ping process with the `-O` flag
- WHEN the ping process outputs "no answer yet for icmp_seq=N"
- THEN a `PingResult` with `outcome=TIMEOUT` and `latencyMs=null` SHALL be emitted immediately

#### Scenario: Host unreachable error
- GIVEN a running ping process
- WHEN an output line contains "Destination Host Unreachable", "Network Unreachable", or "No route to host"
- THEN a `PingResult` with `outcome=HOST_UNREACHABLE` and `latencyMs=null` is emitted

#### Scenario: Ping process restart on failure
- GIVEN an active recording with a running ping process
- WHEN the ping process dies unexpectedly (readLine returns null or exception)
- THEN a `PingResult` with `outcome=PROCESS_ERROR` and `latencyMs=null` is emitted
- AND the ping process is automatically restarted after a delay

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

The system SHALL compute the packet loss percentage from the sliding window at each recording point. All non-SUCCESS outcomes SHALL count as packet loss.

#### Scenario: Packet loss recorded per point
- GIVEN an active recording with a ping window
- WHEN a recording point is triggered
- THEN `packetLossPct` is computed as (count where outcome != SUCCESS / window size) * 100

#### Scenario: All outcomes count as loss
- GIVEN a ping window containing results with outcomes TIMEOUT, HOST_UNREACHABLE, and PROCESS_ERROR
- WHEN packet loss is calculated
- THEN all non-SUCCESS outcomes are included in the loss count

### Requirement: Speedtest as Separate Measurement

The system SHALL provide throughput measurement as a separate, complementary measurement to ICMP ping, not as a replacement.

#### Scenario: Speedtest measures throughput
- GIVEN an active recording with speedtest enabled
- WHEN a speed test runs
- THEN the system measures download and upload speed in bits per second
- AND latency, jitter, and packet loss continue to be measured by the PingEngine independently

#### Scenario: Speedtest results independent
- GIVEN a session with both ping and speedtest data
- WHEN analytics are computed
- THEN the PingEngine data provides latency/jitter/packet loss
- AND the speedtest data provides download/upload speed
- AND the two are correlated via the cell capture timestamp on the speedtest record