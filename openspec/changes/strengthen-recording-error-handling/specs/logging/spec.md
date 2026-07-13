## ADDED Requirements

### Requirement: Runtime Log Capture

The system SHALL capture runtime diagnostic logs via Timber into a rolling file log at `app_logs/runtime.log` (1 MB cap, rotates to `runtime.log.1`) alongside the existing crash log files at `crash_logs/crash_<timestamp>.txt` (5 most recent). The rolling file tree SHALL serialize writes via a single-thread executor and SHALL NOT block the calling thread on disk I/O. All caught exceptions in the service layer SHALL be logged via Timber.

#### Scenario: Timber planted on app start
- GIVEN the app is starting
- WHEN `Application.onCreate()` runs
- THEN a `DebugTree` is planted in debug builds (logcat output)
- AND a `RollingFileTree` is planted in all builds (file output at `app_logs/runtime.log`)
- AND the existing uncaught-exception crash logger continues to write to `crash_logs/`

#### Scenario: Rolling log rotation
- GIVEN the rolling log file at `app_logs/runtime.log` has reached 1 MB
- WHEN a new log entry is appended
- THEN the current `runtime.log` is rotated to `runtime.log.1` (overwriting any existing `.1`)
- AND the new entry is written to a fresh `runtime.log`
- AND the total on-disk log footprint never exceeds approximately 2 MB (rolling) + 5 crash files

#### Scenario: Service-layer exception logging
- GIVEN an active recording
- WHEN any caught exception occurs in the service layer (recording loop, sibling jobs, DB writes, sensor unregister, marker insert)
- THEN the exception is logged via `Timber.e` or `Timber.w` with a descriptive message
- AND the log entry is appended to the rolling file log

### Requirement: Unified Share Logs Action

The system SHALL expose a "Share Logs" action in Settings that concatenates the latest crash log file (if any) with the rolling runtime log (and its rotated `.1` if present) into a single text payload shared via `Intent.ACTION_SEND`.

#### Scenario: Share with both crash and rolling log
- GIVEN the user opens Settings and a crash log file exists at `crash_logs/` and a rolling log exists at `app_logs/runtime.log`
- WHEN the user taps "Share Logs"
- THEN an `Intent.ACTION_SEND` with `type = "text/plain"` is started
- AND the shared text contains the latest crash file content followed by the rolling log content
- AND the share chooser is displayed with the title "Share Logs"

#### Scenario: Share with only rolling log (no crash)
- GIVEN no crash log file exists at `crash_logs/` but a rolling log exists at `app_logs/runtime.log`
- WHEN the user taps "Share Logs"
- THEN the shared text contains only the rolling log content
- AND no crash placeholder is included

#### Scenario: Share with no logs available
- GIVEN neither a crash log nor a rolling log exists
- WHEN the user taps "Share Logs"
- THEN a Toast is shown: "No logs available"
- AND no share intent is started

#### Scenario: Renamed from "Share Crash Log"
- GIVEN the Settings screen is rendered
- THEN the previously-named "Share Crash Log" action is renamed to "Share Logs"
- AND the action's behavior follows the Unified Share Logs Action requirement above (not the previous crash-only behavior)
