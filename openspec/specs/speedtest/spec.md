# Speedtest Specification

## Purpose

Defines the behavior of continuous throughput measurement during active recording sessions using a custom Kotlin implementation of the Speedtest.net HTTP protocol, including server discovery, download/upload measurement, WiFi skip policy, and data collection.

## HTTP Client

All HTTP requests SHALL use a shared `OkHttpClient` instance with connection pooling (8 idle connections, 30s keep-alive) to enable TLS session resumption and reduce connection establishment overhead across parallel measurement threads.

### Requirement: Speedtest Protocol — Config Retrieval

The system SHALL fetch the Speedtest.net configuration at the start of each recording session to determine test parameters.

#### Scenario: Config fetched successfully
- GIVEN a recording session with speedtest enabled
- WHEN the first test cycle begins
- THEN the system sends a GET request to `https://www.speedtest.net/speedtest-config.php`
- AND parses the XML response to extract client IP, lat/lon, download/upload thread counts, test durations, and chunk sizes

#### Scenario: Config fetch failure
- GIVEN a recording session with speedtest enabled
- WHEN the config fetch request fails
- THEN the current test cycle is skipped
- AND the error is recorded as a failed speedtest result

### Requirement: Speedtest Protocol — Server Discovery

The system SHALL discover and select the optimal speedtest server at the start of each recording session.

#### Scenario: Server list fetched
- GIVEN a recording session with speedtest enabled
- WHEN the system has the client location from config
- THEN the system fetches the server list from `https://www.speedtest.net/speedtest-servers-static.php`
- AND parses the XML response into a list of servers with URL, lat, lon, sponsor, and ID

#### Scenario: Server list fallback URLs
- GIVEN the primary server list URL fails
- WHEN the system retries with fallback URLs in order
- THEN it attempts `http://c.speedtest.net/speedtest-servers-static.php`, then `https://www.speedtest.net/speedtest-servers.php`, then `http://c.speedtest.net/speedtest-servers.php`

### Requirement: Speedtest Protocol — Server Selection

The system SHALL select the best server by geographic distance and HTTP latency.

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

#### Scenario: Server ID override
- GIVEN an optional `speedTestServerId` is configured in Settings
- WHEN a best server is configured
- THEN the system skips automatic server discovery
- AND uses the specified server ID from the server list

### Requirement: Speedtest Protocol — Gauge Phase

The system SHALL perform a short gauge download before the full download test to estimate connection speed and select appropriate file sizes.

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

### Requirement: Speedtest Protocol — Download Measurement

The system SHALL measure download speed using multi-threaded HTTP GET requests to the speedtest server.

#### Scenario: Download URLs constructed
- GIVEN the best server is selected
- WHEN a download test begins
- THEN URLs are constructed as `{server_url_dir}/random{size}x{size}.jpg` for each size selected by the gauge phase
- AND each size is repeated `threadsperurl` times from the config

#### Scenario: Download test executed
- GIVEN the download URL list
- WHEN the test runs
- THEN concurrent HTTP GET requests are made using OkHttp with a semaphore limiting concurrency to `threadcount * 2`
- AND each response body is read in 64 KB chunks
- AND the first 1.5 seconds of data is counted as a warmup period (not used in speed calculation)
- AND data transferred after the warmup period is used for throughput sampling
- AND throughput samples are recorded at ~500ms intervals
- AND reading stops when the configured `testlength` + warmup (seconds) is reached

#### Scenario: Download speed calculated
- GIVEN completed download transfers
- WHEN the speed is calculated
- THEN throughput samples are sorted by speed
- AND the fastest 10% and slowest 30% of samples are discarded
- AND the remaining 60% of samples are averaged
- AND the average is multiplied by an overhead compensation factor of 1.06×
- AND the final result is expressed in bits per second

### Requirement: Speedtest Protocol — Upload Measurement

The system SHALL, when upload is enabled, measure upload speed using multi-threaded HTTP POST requests to the speedtest server.

#### Scenario: Upload data constructed
- GIVEN the best server is selected and upload is enabled
- WHEN an upload test begins
- THEN upload payload data is constructed as `content1={chars}` where characters repeat `0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ`
- AND the largest payload size is pre-allocated and cached; subsequent requests for the same size reuse the cached payload
- AND payload sizes follow the server config's upload sizes array

#### Scenario: Upload test executed
- GIVEN the upload request list
- WHEN the test runs
- THEN concurrent HTTP POST requests are made to `{server.url}` using OkHttp
- AND each request sends the full payload as a `RequestBody`
- AND the first 3 seconds of data is counted as a warmup period (not used in speed calculation)
- AND data transferred after the warmup period is used for throughput sampling
- AND throughput samples are recorded at ~500ms intervals
- AND upload ends when the configured `testlength` + warmup (seconds) is reached

#### Scenario: Upload speed calculated
- GIVEN completed upload transfers
- WHEN the speed is calculated
- THEN throughput samples are sorted by speed
- AND the fastest 10% and slowest 30% of samples are discarded
- AND the remaining 60% of samples are averaged
- AND the average is multiplied by an overhead compensation factor of 1.06×
- AND the final result is expressed in bits per second

### Requirement: WiFi Skip Policy

The system SHALL skip speed tests when WiFi is the active network.

#### Scenario: Skip on WiFi
- GIVEN an active recording with speedtest enabled
- WHEN the device's active network is WiFi
- THEN the speed test is skipped for this cycle
- AND a speedtest record is created with `succeeded = false`, `errorMessage = "SKIPPED_WIFI"`, and `networkType = "WIFI"`
- AND the test schedule continues toward the next cycle

#### Scenario: Test on cellular
- GIVEN an active recording with speedtest enabled
- WHEN the device's active network is cellular
- THEN the speed test runs normally
- AND `networkType` is set to `"CELLULAR"`

### Requirement: Test Cadence

The system SHALL run speed tests at a configurable minimum interval.

#### Scenario: Interval measured from test start
- GIVEN a completed speed test
- WHEN the next test is scheduled
- THEN the system waits `speedTestIntervalMs` milliseconds measured from the START of the previous test (not its completion)

#### Scenario: Single test guarantee
- GIVEN a running speed test
- WHEN the next test interval elapses before the current test finishes
- THEN the next test cycle is skipped (no queuing)
- AND a new test begins at the next scheduled interval

### Requirement: Cell Correlation Snapshot

The system SHALL capture cellular conditions at the start of each speed test for correlation analysis.

#### Scenario: Correlation fields captured
- GIVEN a speed test begins
- WHEN the system records cell info
- THEN the current cellular conditions are snapshotted via `CellInfoCollector.snapshots()`
- AND the primary data SIM's RAT, RSRP, band number, and SIM slot index are stored on the speedtest record as `ratAtTest`, `rsrpAtTest`, `bandAtTest`, and `dataSimSlotIndex`