## MODIFIED Requirements

### Requirement: Speedtest Protocol — Server Selection

The system SHALL select the best server by geographic distance and HTTP latency. The cached server is reused across test cycles within a session, invalidated only when the download phase fails or an exception escapes the engine, and may be inherited from a successful manual prime (see `speedtest-diagnostics/spec.md`). Upload-only failures SHALL NOT invalidate the cache, because the server is by construction reachable (download just succeeded).

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
- AND the cached server is invalidated only if the download phase fails or an exception escapes the engine
- AND an upload-only failure SHALL NOT invalidate the cache
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

### Requirement: Speedtest Protocol — Upload Measurement

The system SHALL, when upload is enabled and the pre-upload probe (see "Speedtest Protocol — Pre-Upload Probe") has succeeded, measure upload speed using multi-threaded HTTP POST requests to the speedtest server. When the probe fails, the full upload measurement SHALL be skipped for the current cycle.

#### Scenario: Upload data constructed
- GIVEN the best server is selected, upload is enabled, and the pre-upload probe succeeded
- WHEN an upload test begins
- THEN upload payload data is constructed as `content1={chars}` where characters repeat `0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ`
- AND the largest payload size is pre-allocated and cached; subsequent requests for the same size reuse the cached payload
- AND payload sizes follow the server config's upload sizes array

#### Scenario: Upload test executed
- GIVEN the upload request list
- WHEN the test runs
- THEN a fixed number of worker coroutines (equal to `threads * 2` from the server config, capped at 16) each loop through the request list until the deadline, keeping the network pipe saturated for the full test duration
- AND concurrent HTTP POST requests are made to `{server.url}` using OkHttp
- AND each request sends the full payload as a `RequestBody` with `Content-Length` declared
- AND the first 3 seconds of data is counted as a warmup period (not used in speed calculation)
- AND data transferred after the warmup period is used for throughput sampling
- AND throughput samples are recorded at ~500ms intervals
- AND upload ends when the configured `testlength` + warmup (seconds) is reached
- AND a transient failure in one request is caught and the worker continues to the next request rotation

#### Scenario: Upload speed calculated
- GIVEN completed upload transfers
- WHEN the speed is calculated
- THEN throughput samples are sorted by speed
- AND the fastest 10% and slowest 30% of samples are discarded
- AND the remaining 60% of samples are averaged
- AND the average is multiplied by an overhead compensation factor of 1.06×
- AND the final result is expressed in bits per second

#### Scenario: Upload measurement skipped when probe fails
- GIVEN the best server is selected and upload is enabled
- WHEN the pre-upload probe fails (non-2xx response, exception, or timeout)
- THEN the full upload measurement SHALL NOT be executed
- AND no upload warmup bytes are transferred
- AND the resulting `SpeedTestResult` SHALL have `uploadSucceeded = false`, `uploadBps = null`
- AND the `errorMessage` SHALL record `"Upload probe failed: <reason>"`
- AND the engine SHALL NOT invalidate the cached server, config, or gauge

### Requirement: WiFi Skip Policy

The system SHALL skip speed tests when WiFi is the active network. A WiFi-skipped cycle records `downloadSucceeded = false` and `uploadSucceeded = null` (upload was not run).

#### Scenario: Skip on WiFi
- GIVEN an active recording with speedtest enabled
- WHEN the device's active network is WiFi
- THEN the speed test is skipped for this cycle
- AND a speedtest record is created with `downloadSucceeded = false`, `uploadSucceeded = null`, `errorMessage = "SKIPPED_WIFI"`, and `networkType = "WIFI"`
- AND the test schedule continues toward the next cycle

#### Scenario: Test on cellular
- GIVEN an active recording with speedtest enabled
- WHEN the device's active network is cellular
- THEN the speed test runs normally
- AND `networkType` is set to `"CELLULAR"`

### Requirement: Speedtest Result Timing

The system SHALL capture both start and finish wall-clock timestamps on every `SpeedTestResult` produced by the engine. The engine owns the test lifecycle and is the source of truth for timing.

#### Scenario: Successful test timing

- GIVEN a speedtest runs to completion successfully (both `downloadSucceeded` and `uploadSucceeded` are true, or `downloadSucceeded` is true and upload was not enabled)
- WHEN `runTest()` returns
- THEN `SpeedTestResult.startedAt` is the wall-clock millisecond timestamp captured at engine entry
- AND `SpeedTestResult.finishedAt` is the wall-clock millisecond timestamp captured at the moment `runTest()` returns
- AND `finishedAt > startedAt` (duration is positive)

#### Scenario: Partial-success test timing

- GIVEN a speedtest completes where `downloadSucceeded = true` and `uploadSucceeded = false` (probe failed or upload measurement failed after download succeeded)
- WHEN `runTest()` returns
- THEN `SpeedTestResult.startedAt` is the wall-clock millisecond timestamp captured at engine entry
- AND `SpeedTestResult.finishedAt` is the wall-clock millisecond timestamp captured at the moment `runTest()` returns
- AND `finishedAt > startedAt` (duration is positive — the download phase ran)

#### Scenario: Instant bail-out timing

- GIVEN a speedtest exits early via SKIPPED_WIFI, config fetch failure, server selection failure, or exception
- WHEN `runTest()` returns
- THEN `SpeedTestResult.startedAt` is the wall-clock millisecond timestamp captured at engine entry
- AND `SpeedTestResult.finishedAt = startedAt` (duration is zero, signalling the test never ran)
- AND `downloadSucceeded = false` and `uploadSucceeded = null`

## ADDED Requirements

### Requirement: Speedtest Result Per-Phase Success

The system SHALL capture per-phase success on every `SpeedTestResult` produced by the engine. The single legacy `succeeded: Boolean` field is replaced by `downloadSucceeded: Boolean` and `uploadSucceeded: Boolean?`. `uploadSucceeded` is `null` when upload was not run (upload disabled, WiFi skip, instant bail-out before the upload phase, or probe-skip); it is `false` when upload ran but failed; it is `true` only when upload ran and succeeded.

#### Scenario: Both phases succeed

- GIVEN a speedtest runs to completion with download and upload both enabled and both succeeding
- WHEN `runTest()` returns
- THEN `SpeedTestResult.downloadSucceeded = true`
- AND `SpeedTestResult.uploadSucceeded = true`
- AND `downloadBps` and `uploadBps` are both non-null

#### Scenario: Download succeeds, upload fails

- GIVEN a speedtest completes where the download phase succeeded but the upload phase failed (either at the probe or during measurement)
- WHEN `runTest()` returns
- THEN `SpeedTestResult.downloadSucceeded = true`
- AND `SpeedTestResult.uploadSucceeded = false`
- AND `downloadBps` is non-null
- AND `uploadBps` is null
- AND the engine SHALL NOT invalidate the cached server, config, or gauge

#### Scenario: Download fails

- GIVEN a speedtest completes where the download phase failed
- WHEN `runTest()` returns
- THEN `SpeedTestResult.downloadSucceeded = false`
- AND `SpeedTestResult.uploadSucceeded = null` (upload is not run when download fails)
- AND `downloadBps` is null
- AND `uploadBps` is null
- AND the engine SHALL invalidate the cached server, config, and gauge

#### Scenario: Upload disabled in config

- GIVEN a speedtest runs with `uploadEnabled = false`
- WHEN `runTest()` returns
- THEN `SpeedTestResult.downloadSucceeded` reflects the download phase outcome
- AND `SpeedTestResult.uploadSucceeded = null` (upload was not run)
- AND `uploadBps` is null

#### Scenario: Instant bail-out (WiFi skip, config/selection failure, exception)

- GIVEN a speedtest exits early via SKIPPED_WIFI, config fetch failure, server selection failure, or exception
- WHEN `runTest()` returns
- THEN `SpeedTestResult.downloadSucceeded = false`
- AND `SpeedTestResult.uploadSucceeded = null`
- AND `downloadBps` and `uploadBps` are both null

### Requirement: Speedtest Protocol — Pre-Upload Probe

The system SHALL issue a single small HTTP POST request to the speedtest server before invoking the full upload measurement. The probe detects carrier-hostile or server-broken upload conditions cheaply (without burning the 3-second upload warmup) and produces a `probe` phase `SpeedTestDebugEvent` for instrumentation parity.

#### Scenario: Probe request executed

- GIVEN the best server is selected and upload is enabled
- WHEN the upload phase is about to begin
- THEN the engine issues a single HTTP POST to `{server.url}` with a small payload (~1 KB) of the same `content1=...` shape used by the full upload
- AND the request has a short timeout (5 seconds)
- AND a `SpeedTestDebugEvent` with phase `probe`, status `info`, and the probe outcome is appended to the ring buffer

#### Scenario: Probe succeeds

- GIVEN the pre-upload probe is sent
- WHEN the server returns HTTP 2xx within the timeout
- THEN the probe is considered successful
- AND the engine proceeds with the full upload measurement (`measureUpload`)
- AND the debug event status is `ok`

#### Scenario: Probe fails

- GIVEN the pre-upload probe is sent
- WHEN the server returns a non-2xx response, throws an exception, or times out
- THEN the probe is considered failed
- AND the full upload measurement SHALL NOT be executed
- AND the resulting `SpeedTestResult.uploadSucceeded = false` and `uploadBps = null`
- AND the `errorMessage` records `"Upload probe failed: <reason>"`
- AND the engine SHALL NOT invalidate the cached server, config, or gauge
- AND the debug event status is `fail`
