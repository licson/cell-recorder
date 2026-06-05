## 1. Data Model

- [x] 1.1 Add `PingOutcome` enum to `PingResult.kt` with values `SUCCESS`, `TIMEOUT`, `HOST_UNREACHABLE`, `PROCESS_ERROR`
- [x] 1.2 Add `outcome: PingOutcome` field to `PingResult` data class

## 2. PingEngine Parsing

- [x] 2.1 Add `parseNoAnswerLine()` private function to detect "no answer yet for icmp_seq=N" pattern and return `PingOutcome.TIMEOUT` or null
- [x] 2.2 Add `parseErrorLine()` private function to detect "Destination Host Unreachable", "Network Unreachable", "No route to host" patterns and return `PingOutcome.HOST_UNREACHABLE` or null
- [x] 2.3 Refactor `parsePingOutput()` to return a structured result (latency + outcome) instead of just `Double?`, or add a companion `parseOutcome()` that classifies the line
- [x] 2.4 Update `pingFlow()` to add `-O` flag to `ProcessBuilder`, parse "no answer yet" lines as `TIMEOUT`, parse error lines as `HOST_UNREACHABLE`, emit `PROCESS_ERROR` on process death, and emit `SUCCESS` for successful replies
- [x] 2.5 Update deprecated `ping()` method to return `PingResult` with appropriate `outcome` field

## 3. PingSlidingWindow

- [x] 3.1 Update `packetLossPct()` to count `outcome != SUCCESS` instead of `latencyMs == null`
- [x] 3.2 Update `avgLatencyMs()` to filter by `outcome == SUCCESS` instead of `latencyMs != null` (functionally equivalent but explicit)

## 4. Consumer Updates

- [x] 4.1 Update `RecordingService` ping collection to work with new `PingResult` (verify no breakage — it passes results to `PingSlidingWindow` which is already updated)
- [x] 4.2 Migrate `LiveInfoViewModel` from deprecated `pingEngine.ping()` loop to `pingEngine.pingFlow()`
- [x] 4.3 Update `LiveInfoViewModel` history tracking to use `outcome` field for packet loss classification

## 5. Tests

- [x] 5.1 Add unit tests for `parseNoAnswerLine()`: "no answer yet for icmp_seq=3", multi-digit seq, non-matching line returns null
- [x] 5.2 Add unit tests for `parseErrorLine()`: "Destination Host Unreachable", "Network Unreachable", "No route to host", non-error line returns null
- [x] 5.3 Add unit tests for `parsePingOutput()` / `parseOutcome()`: success line, no-answer line, error line, unparseable line
- [x] 5.4 Add unit test for `-O` flag integration: verify "no answer yet" line produces `TIMEOUT` result immediately
- [x] 5.5 Add unit tests for `PingSlidingWindow` with outcomes: loss count includes TIMEOUT, HOST_UNREACHABLE, PROCESS_ERROR; avg excludes non-SUCCESS
- [x] 5.6 Update existing `PingSlidingWindowTest` to use new `PingResult` constructor with `outcome` field

## 6. Verification

- [x] 6.1 Build project with `./gradlew assembleDebug` and fix any compilation errors
- [x] 6.2 Run unit tests and verify all pass