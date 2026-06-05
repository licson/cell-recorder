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
