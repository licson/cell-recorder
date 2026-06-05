## Why

PingEngine cannot distinguish between a real dropped ICMP packet and other failure modes (process crash, host unreachable). It treats every non-success outcome identically as `latencyMs = null`, which `PingSlidingWindow` interprets as packet loss. A process restart emitting a spurious null or a "Destination Host Unreachable" message should still count as packet loss — but the system needs to know *why* the loss occurred and must accurately detect sequence gaps rather than relying on the fragile "null latency = loss" heuristic.

## What Changes

- Add a `PingOutcome` enum to `PingResult` with values `SUCCESS`, `TIMEOUT`, `HOST_UNREACHABLE`, `PROCESS_ERROR` so each ping result carries its true disposition.
- All non-SUCCESS outcomes count as packet loss — `packetLossPct` semantics remain unchanged.
- Enhance `PingEngine.pingFlow()` to parse ICMP sequence numbers from `ping` output and detect sequence gaps (e.g., icmp_seq 2 missing after 1), emitting `TIMEOUT` results for missing packets.
- Parse error lines from `ping` output (e.g., "Destination Host Unreachable", "Network Unreachable") and emit `HOST_UNREACHABLE` outcomes.
- Emit `PROCESS_ERROR` when the ping process dies and restarts (currently emits a spurious null that looks like a single dropped packet).
- Update `PingSlidingWindow` to use `outcome != SUCCESS` instead of `latencyMs == null` for loss calculation (functionally equivalent but explicit).
- **BREAKING**: `PingResult` gains a new `outcome` field; all consumers must be updated.
- Migrate `LiveInfoViewModel` from the deprecated `ping()` method to `pingFlow()`.
- Add unit tests for `PingEngine` output parsing covering success, timeout, host unreachable, sequence gap, and process death scenarios.

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

- `connectivity`: PingEngine must parse ICMP sequence numbers and error messages to accurately classify ping outcomes; `PingResult` model gains outcome field; `PingSlidingWindow` uses explicit outcome instead of null-latency heuristic.

## Impact

- **`PingResult` model** — new `outcome` field (breaking for all consumers)
- **`PingEngine`** — new ICMP sequence tracking and error line parsing logic
- **`PingSlidingWindow`** — loss calculation changes from `latencyMs == null` to `outcome != SUCCESS`
- **`PointRecorder`** — reads sliding window methods (no logic change)
- **`RecordingService`** — passes `PingResult` through (no logic change)
- **`LiveInfoViewModel`** — migrates from deprecated `ping()` to `pingFlow()`
- **`SessionAnalyticsEngine`** — can use outcome types to improve missing-ping cluster detection (future enhancement, not required now)
- **`CellRecordEntity` / database** — no schema change; `packetLossPct` semantics unchanged
- **Export/Import** — no change; `packetLossPct` and `avgLatencyMs` columns unchanged
- **UI** — no immediate change; outcome type enables richer display in future
