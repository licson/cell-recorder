## Context

The app uses a system `ping` process to measure network latency during recording sessions. `PingEngine.pingFlow()` starts a long-running `ping -i <interval>` process and reads its stdout line by line. Currently, `parsePingOutput()` extracts only the `time=X ms` latency value. If the line doesn't contain a latency value, `null` is returned and `PingResult` is created with `latencyMs = null`. The downstream `PingSlidingWindow` counts null-latency results as packet loss.

This approach has two problems:
1. **Dropped packets are invisible**: When a packet is dropped, `ping` outputs no line for it. `pingFlow()` only sees successful replies, so the gap is never detected — loss is undercounted.
2. **Failure modes are conflated**: A process crash/restart, a "Destination Host Unreachable" error, and a genuine timeout all produce `latencyMs = null`. They are indistinguishable downstream, even though they have different diagnostic meaning.

All non-SUCCESS outcomes should count as packet loss — the goal is not to change loss semantics, but to accurately detect when packets are actually dropped and to classify why failures occur.

## Goals / Non-Goals

**Goals:**
- Accurately detect dropped ICMP packets by using the `ping -O` flag for immediate timeout notification
- Classify ping failure reasons (timeout, host unreachable, process error) via a `PingOutcome` enum
- All non-SUCCESS outcomes count as packet loss — `packetLossPct` semantics stay the same
- Migrate `LiveInfoViewModel` off the deprecated `ping()` method to `pingFlow()`
- Add unit tests for ping output parsing

**Non-Goals:**
- Persisting per-ping outcome types in the database (no schema change)
- Changing `packetLossPct` calculation semantics — all failures still count as loss
- Changing CSV/GeoJSON export format
- Adding new UI for outcome breakdown (can be done later)
- Modifying `SessionAnalyticsEngine` to use outcome types (future enhancement)

## Decisions

### Decision 1: `PingOutcome` enum on `PingResult`

Add a `PingOutcome` enum with four values:
- `SUCCESS` — ping reply received with latency
- `TIMEOUT` — no reply for a sent packet (detected via `ping -O` "no answer yet" line)
- `HOST_UNREACHABLE` — ICMP error (Destination Host Unreachable, Network Unreachable, etc.)
- `PROCESS_ERROR` — ping process died or output was unparseable

`PingResult` gains an `outcome: PingOutcome` field. All consumers that create or pattern-match on `PingResult` must be updated.

**Rationale**: A sealed classification is better than encoding meaning in null. It's extensible if new outcome types are needed later. All non-SUCCESS count as loss, so `PingSlidingWindow.packetLossPct()` changes from `count { it.latencyMs == null }` to `count { it.outcome != PingOutcome.SUCCESS }` — functionally equivalent but self-documenting.

**Alternative considered**: Keep `latencyMs == null` and add a separate `isPacketLoss: Boolean` field. Rejected because it doesn't convey *why* the loss occurred, and a null latency already implicitly means loss.

### Decision 2: Use `ping -O` flag for immediate packet loss detection

Add the `-O` flag to the `ping` command in `pingFlow()`. The command changes from:

```
ping -i <interval> -W <timeout> <host>
```

to:

```
ping -O -i <interval> -W <timeout> <host>
```

When a packet is dropped, `ping -O` outputs an explicit line:

```
no answer yet for icmp_seq=N
```

This line is parsed to emit a `PingResult(outcome=TIMEOUT, latencyMs=null)` immediately — no need to wait for the next successful reply to infer a gap.

No ICMP sequence number tracking state is required in `pingFlow()`. The `-O` flag handles gap detection natively.

**Rationale**: The `-O` flag provides explicit, immediate notification of dropped packets directly from the `ping` process. This eliminates the need for manual sequence tracking (`lastSeq` state), reduces complexity, and removes the 1–4 second detection delay inherent in retroactive gap detection. The flag is available on Android's `iputils` ping implementation.

**Alternative considered**: Track `icmp_seq` numbers manually and emit synthetic TIMEOUT results for sequence gaps. Rejected because it requires maintaining `lastSeq` state, emits results retroactively (delayed by one RTT), and adds complexity for edge cases (process restart resets, first reply initialization). The `-O` flag handles all of this natively.

### Decision 3: Error line parsing

Parse each non-reply line from `ping` output for known error patterns:
- Lines containing "Destination Host Unreachable" → `HOST_UNREACHABLE`
- Lines containing "Network Unreachable" → `HOST_UNREACHABLE`
- Lines containing "No route to host" → `HOST_UNREACHABLE`
- Lines containing "Request timeout" / "100% packet loss" → redundant with `-O` output, but if seen, emit `TIMEOUT`

Unrecognized non-empty lines that don't match success or error patterns are ignored (not emitted as results). This prevents noisy/spurious output from creating fake results.

**Rationale**: The system `ping` outputs specific error messages when the host is unreachable. Parsing these gives diagnostic context without changing loss semantics.

### Decision 4: Process restart behavior

When `readLine()` returns `null` (process EOF) or an exception occurs, emit a single `PingResult(outcome=PROCESS_ERROR)` instead of `PingResult(latencyMs=null)`. Then restart the process after a 1-second delay (same as current behavior).

**Rationale**: The current code emits `latencyMs=null` on process death, which looks like a single dropped packet. Using `PROCESS_ERROR` makes it clear this isn't a real ICMP timeout. It still counts as loss for `packetLossPct()`, but the classification is more accurate.

### Decision 5: Deprecate `ping()` method removal

The deprecated `ping()` method will be updated to return `PingResult` with the appropriate `outcome` field, and `LiveInfoViewModel` will be migrated to use `pingFlow()` instead. The `ping()` method remains deprecated and can be removed in a future change.

**Rationale**: Two ping pipelines exist — `RecordingService` uses `pingFlow()`, `LiveInfoViewModel` uses the deprecated `ping()`. Migrating `LiveInfoViewModel` ensures both pipelines benefit from the improved parsing. Keeping `ping()` minimally updated avoids a wider refactor.

### Decision 6: No database schema change

`PingOutcome` is an in-memory classification only. It is not persisted to `CellRecordEntity`. The `packetLossPct` and `avgLatencyMs` columns remain unchanged. This avoids a Room migration and export format changes.

**Rationale**: The outcome type is useful for real-time display and debugging, but per-ping outcomes are aggregated into the sliding window before being recorded. The window already produces `packetLossPct` which captures the loss rate. Persisting outcome breakdown would require a schema change and is not needed for the current analytics.

## Risks / Trade-offs

- **[Breaking change to PingResult]** → All consumers that construct or destructure `PingResult` must be updated. Mitigation: the codebase has a small number of consumers (PingEngine, PingSlidingWindow, LiveInfoViewModel), all identified and straightforward to update.
- **[Regex parsing of ping output is fragile]** → Different Android versions or OEM modifications could produce slightly different `ping` output formats. Mitigation: use broad patterns; fall back gracefully (unrecognized lines are ignored, not crashed on). Add tests covering multiple output formats.
- **[PROCESS_ERROR on restart inflates loss slightly]** → When the process dies and restarts, one PROCESS_ERROR is emitted. This counts as loss, which is technically correct (connectivity was interrupted) but may slightly inflate the loss percentage during unstable periods. Mitigation: the 1-second delay already bounds this to at most one extra loss event per restart.
- **[`-O` flag availability]** → The `-O` flag depends on the `iputils` ping implementation used on Android. If an OEM uses a different ping binary that doesn't support `-O`, the "no answer yet" lines won't appear and dropped packets won't be detected. Mitigation: all known Android implementations use `iputils`; the flag has been available for years. If `-O` is not supported, the system degrades gracefully to the current behavior (undercounting loss rather than crashing).
