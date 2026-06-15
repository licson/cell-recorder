## Why

Cell Recorder currently measures latency and packet loss via ICMP ping but cannot measure throughput (download/upload speed) correlated with cellular conditions. Adding optional continuous speedtests during recording sessions lets users understand how their network bandwidth varies by signal strength, RAT type, SIM, and location.

## What Changes

- Add configurable continuous throughput tests during active recording sessions using a custom Kotlin implementation of the Speedtest.net HTTP protocol (not the Ookla binary, which fails on Android)
- Introduce a new `speed_test_records` Room table with correlation fields captured at test time
- Add speedtest controls to Settings (toggle, EULA dialog, upload enable/disable, interval, server override)
- Show speedtest status on the live recording screen
- Add speedtest analytics to session analytics (summary card, correlations by RSRP/RAT/SIM)
- Display speedtest markers on the replay timeline with detail card
- Include speedtest data in session CSV export
- Skip tests when WiFi is active (cellular-only measurement)

## Capabilities

### New Capabilities

- `speedtest`: Continuous speedtest throughput measurement during recording

### Modified Capabilities

- `recording`: Add optional continuous speedtest lifecycle within recording service
- `connectivity`: Add Speedtest.net HTTP protocol for throughput measurement
- `analytics`: Add speedtest correlation analytics per session and globally
- `sessions`: Add speedtest markers in replay screen
- `service`: Add speedtest job lifecycle to foreground recording service
- `ui`: Add speedtest settings, live stats display, and replay markers
- `data`: Add `speed_test_records` entity, DAO, migration, and export

## Impact

- **New files**: data layer (`SpeedTestRecordEntity`, `SpeedTestRecordDao`, `SpeedTestRecordRepository`), domain layer (`SpeedTestEngine`, config parser, server selector, measurer, analytics engine), UI components (EULA dialog, speedtest settings, replay markers, analytics cards)
- **Modified files**: `AppDatabase` (migration 9→10), `AppConfigEntity` (5 new config fields), `RecordingService` (speedtest coroutine job), `RecordingState` (status fields), `RecordingScreen`/`LiveStatsBar`, analytics panel, replay screen, settings screen, statistics screen, export use case, DI modules
- **Dependencies**: OkHttp, Android `XmlPullParser` (both already available)
- **No external binaries**: Pure Kotlin/HTTP implementation — no APK size increase, no architecture restrictions