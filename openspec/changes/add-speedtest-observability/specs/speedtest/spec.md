## ADDED Requirements

### Requirement: Speedtest Result Timing

The system SHALL capture both start and finish wall-clock timestamps on every `SpeedTestResult` produced by the engine. The engine owns the test lifecycle and is the source of truth for timing.

#### Scenario: Successful test timing

- GIVEN a speedtest runs to completion successfully
- WHEN `runTest()` returns
- THEN `SpeedTestResult.startedAt` is the wall-clock millisecond timestamp captured at engine entry
- AND `SpeedTestResult.finishedAt` is the wall-clock millisecond timestamp captured at the moment `runTest()` returns
- AND `finishedAt > startedAt` (duration is positive)

#### Scenario: Instant bail-out timing

- GIVEN a speedtest exits early via SKIPPED_WIFI, config fetch failure, server selection failure, or exception
- WHEN `runTest()` returns
- THEN `SpeedTestResult.startedAt` is the wall-clock millisecond timestamp captured at engine entry
- AND `SpeedTestResult.finishedAt = startedAt` (duration is zero, signalling the test never ran)

### Requirement: Speedtest Engine Re-prime

The system SHALL provide a `reprimeServerAndGauge()` operation on the speedtest engine that clears the cached server, cached gauge, and gauge-attempted flag while retaining the cached config. This is the priming primitive used by manual launches.

#### Scenario: Re-prime clears server and gauge

- GIVEN the engine has a cached server, cached gauge, and gauge-attempted flag
- WHEN `reprimeServerAndGauge()` is called
- THEN `cachedServer` is set to null
- AND `cachedGaugeBps` is set to null
- AND `gaugeAttempted` is set to false
- AND `cachedConfig` is retained

#### Scenario: Re-prime triggers fresh server selection

- GIVEN `reprimeServerAndGauge()` has been called
- WHEN `runTest()` next executes
- THEN server selection runs fresh (geographic discovery + latency pings, or preferred-server bypass)
- AND the gauge phase runs fresh

## MODIFIED Requirements

### Requirement: Speedtest Protocol — Server Selection

The system SHALL select the best server by geographic distance and HTTP latency. The cached server is reused across test cycles within a session, invalidated on test failure, and may be inherited from a successful manual prime (see `speedtest-diagnostics/spec.md`).

#### Scenario: Closest servers selected
- GIVEN a parsed server list
- WHEN servers are ranked
- THEN Haversine distance is computed from client lat/lon to each server
- AND the top 5 closest servers are selected

#### Scenario: Best server determined by latency
- GIVEN the 5 closest servers
- WHEN each server is pinged
- THEN 3 HTTP GET requests are sent to `{server_dir}/latency.txt?x={timestamp}.{i}` per server
- AND each request must return HTTP 200 with body starting with `test=test`
- AND the server with the lowest average latency is selected as the best server

#### Scenario: Server cached for session
- GIVEN the best server has been determined
- WHEN subsequent test cycles run
- THEN the best server is reused without re-discovery
- AND the cached server is invalidated only if a test fails
- AND a successful manual prime prior to session start MAY be inherited (see `speedtest-diagnostics/spec.md`)

#### Scenario: Server ID override
- GIVEN an optional `speedTestServerId` is configured in Settings
- WHEN a best server is configured
- THEN the system skips automatic server discovery
- AND uses the specified server ID from the server list

#### Scenario: Server re-primed on manual launch
- GIVEN a manual "Launch Test" is triggered (see `speedtest-diagnostics/spec.md`)
- WHEN `reprimeServerAndGauge()` is called
- THEN the cached server is cleared
- AND the next `runTest()` re-runs server selection fresh

### Requirement: Speedtest Protocol — Gauge Phase

The system SHALL perform a short gauge download before the full download test to estimate connection speed and select appropriate file sizes. The cached gauge is reused across test cycles, invalidated on test failure, and cleared by `reprimeServerAndGauge()` on manual launch.

#### Scenario: Gauge download executed
- GIVEN the best server is selected
- WHEN the first test cycle begins
- THEN a single HTTP GET request is sent to `{server_url_dir}/random350x350.jpg` for 2 seconds
- AND total bytes received and elapsed time are recorded
- AND the estimated speed determines the file size range for the full download test:
  - < 10 Mbps: small files (350–750px)
  - 10–100 Mbps: full range (350–4000px)
  - > 100 Mbps: large files (1000–4000px)

#### Scenario: Gauge result cached
- GIVEN a gauge result from the first test cycle
- WHEN subsequent test cycles run
- THEN the gauge result is reused without re-gauging
- AND the cached gauge is invalidated if a test fails

#### Scenario: Gauge re-primed on manual launch
- GIVEN a manual "Launch Test" is triggered (see `speedtest-diagnostics/spec.md`)
- WHEN `reprimeServerAndGauge()` is called
- THEN the cached gauge and gauge-attempted flag are cleared
- AND the next `runTest()` re-runs the gauge phase fresh
