## Context

Cell Recorder currently measures network performance via ICMP ping (latency and packet loss) but cannot measure throughput (download/upload speed). The original spec proposal planned to use the Ookla speedtest-cli binary, but testing on a real Android device revealed the binary fails with a bad system call (`SO_BINDTODEVICE`) when trying to bind to a network interface — Android's kernel doesn't support this glibc-dependent syscall. This invalidates the binary-based approach.

Instead, we implement a custom Kotlin version of the Speedtest.net HTTP protocol, modeled after the `sivel/speedtest-cli` Python reference implementation, which uses standard HTTP requests to Speedtest.net's public API endpoints.

## Goals / Non-Goals

**Goals:**
- Add optional, user-configurable continuous throughput tests during recording sessions
- Measure download and upload speed correlated with cellular conditions (RSRP, RAT, SIM)
- Use pure Kotlin/HTTP (no external binary) — works on all Android architectures
- Skip tests when WiFi is active (cellular-only measurement)
- Integrate results into session analytics, replay, and export

**Non-Goals:**
- Not replacing or duplicating ICMP ping latency/jitter/packet loss measurement (PingEngine already handles this)
- Not implementing Speedtest.net result sharing (no POST to speedtest.net API)
- Not measuring per-application or per-interface throughput (system-wide measurement only)
- Not implementing server-side ML or cross-session trend analysis (future work)

## Decisions

### Decision 1: Custom Speedtest.net Protocol (not Ookla binary)

The official Ookla binary is linked against glibc and fails on Android's Bionic libc due to `SO_BINDTODEVICE` syscall restrictions. We implement the same protocol `sivel/speedtest-cli` uses — pure HTTP requests to Speedtest.net's public endpoints.

**Alternatives considered:** Ookla binary (fails on Android), custom CDN download test (no server selection intelligence), iperf3 (requires server + native binary).

### Decision 2: Server Selection Cached Per Session

Fetch server list once per recording session, HTTP ping top 5 closest by Haversine distance, select lowest latency, and cache the result. If a test fails, invalidate cache and re-discover on the next cycle.

**Rationale:** Server discovery adds 3–5 seconds of overhead; doing it once per session (instead of once per test) saves significant time over a multi-hour recording.

### Decision 3: WiFi Skip (Option A)

Check network type before each test. If WiFi is active, skip the test and record `networkType = "WIFI"` with `succeeded = false, errorMessage = "SKIPPED_WIFI"`.

**Rationale:** The goal is cellular throughput correlation. WiFi tests measure the WiFi link, not cellular, and would pollute the dataset. Skipped events are still recorded so analytics can distinguish "skipped" from "failed".

### Decision 4: Single Test at a Time

If a test is still running when the interval elapses, skip the next cycle (do not queue).

**Rationale:** Prevents process accumulation. The Ookla protocol is already multi-threaded within a single test; overlapping tests would be counterproductive.

### Decision 5: Snapshot Correlation at Test Start

Capture `ratAtTest`, `rsrpAtTest`, `bandAtTest`, and `dataSimSlotIndex` from `CellInfoCollector.snapshots()` at the start of each test.

**Rationale:** The test takes 7–15 seconds. Snapshotting at start gives a deterministic correlation point rather than trying to temporally join with cell records after the fact. The `rsrpAtTest` enables RSRP↔download correlation charts.

### Decision 6: Upload Test as Config Toggle

Upload test is enabled by default but can be disabled in Settings. When disabled, only download speed is measured.

**Rationale:** Upload tests double data usage (~10-30 MB per test vs ~5-15 MB for download-only). Users on metered plans may prefer to disable upload. The toggle gives them control.

### Decision 7: Replay Markers (not Chart)

Speedtest data is sparse (~1 per minute) compared to cell records (~1 per 5 seconds). Instead of a sparse line chart, display colored markers on the RAT timeline bar. Tapping a marker shows a detail card.

**Rationale:** Sparse data on a line chart visually degrades the dense cell record charts. Markers on the timeline provide spatial context (when did tests run relative to handoffs/signal changes) with color-coded throughput at a glance.

## Risks / Trade-offs

- **Speedtest.net API availability**: The protocol depends on Speedtest.net's public endpoints (`speedtest-config.php`, `speedtest-servers-static.php`, random file servers). If these change or go down, the feature degrades. **Mitigation**: Fail gracefully with an error status; feature remains disabled until next successful test cycle.
- **Data usage on metered plans**: Each full test uses ~10-30 MB. **Mitigation**: Data usage warning in EULA dialog; upload toggle to reduce usage; user must explicitly opt in via toggle.
- **Speedtest.net terms**: We use Speedtest.net's servers. **Mitigation**: EULA dialog links to Speedtest.net's Terms and Privacy Policy; user must accept before enabling.
- **Single-thread vs multi-thread inconsistency**: The Ookla official app and CLI may use different thread counts per test, leading to different speed results. **Mitigation**: Follow `speedtest-config.php` parameters exactly (threads, sizes, test lengths) for parity with `sivel/speedtest-cli`.