# Connectivity Specification (Delta)

## MODIFIED Requirements

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

## ADDED Requirements

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