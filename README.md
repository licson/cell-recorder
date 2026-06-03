# Cell Recorder

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-30-brightgreen)]()
[![Version](https://img.shields.io/badge/version-1.0.0-blue)]()

Cell Recorder is an Android app that records cellular tower information (signal strength, Cell ID, RAT type, frequency bands) alongside GPS coordinates. Sessions can be exported as CSV or GeoJSON, and replayed on an interactive map.

## Features

- **Real-time cell tower logging** — Records signal strength, Cell ID, RAT (4G/5G/etc.), and frequency bands using [NetMonster Core](https://github.com/mroczis/netmonster-core)
- **GPS tracking** — Logs location coordinates during recording sessions
- **Background recording** — Foreground service keeps recording active even when the app is in the background
- **Session management** — Name, view, and manage multiple recording sessions
- **Export** — Save sessions as CSV or GeoJSON
- **Replay** — Replay recorded sessions on an interactive OpenStreetMap view
- **Crash logging** — Built-in uncaught exception handler captures crash logs for easy bug reporting
- **In-app issue reporting** — Pre-filled GitHub issue template from the About screen

## Screenshots

<!-- TODO: Add screenshots to docs/screenshots/ and update paths below -->

<!-- ![Recording Screen](docs/screenshots/recording.png) -->
<!-- ![Session List](docs/screenshots/sessions.png) -->
<!-- ![Map Replay](docs/screenshots/replay.png) -->
<!-- ![Settings](docs/screenshots/settings.png) -->

## Permissions

| Permission | Why it's needed |
|---|---|
| `ACCESS_FINE_LOCATION` | Record precise GPS coordinates alongside cell data |
| `ACCESS_BACKGROUND_LOCATION` | Continue recording location when the app is not in the foreground |
| `READ_PHONE_STATE` | Read cell tower information (signal strength, Cell ID, RAT, bands) |
| `POST_NOTIFICATIONS` | Show a persistent notification while recording is active |
| `FOREGROUND_SERVICE` | Run the recording service in the foreground |
| `FOREGROUND_SERVICE_LOCATION` | Declare the foreground service type for location tracking |

## Building

### Debug
```bash
./gradlew assembleDebug
```
The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Release
Release builds require signing environment variables:
```bash
export RELEASE_STORE_PASSWORD="your_keystore_password"
export RELEASE_KEY_ALIAS="your_key_alias"
export RELEASE_KEY_PASSWORD="your_key_password"
./gradlew assembleRelease
```
The signed release APK will be at `app/build/outputs/apk/release/app-release.apk`.

> **Note:** Do not commit your keystore (`*.jks`) or hardcode signing passwords in `build.gradle.kts`.

## Architecture

- **UI:** Jetpack Compose with Material 3
- **Dependency Injection:** Hilt
- **Database:** Room (for sessions and cell readings)
- **Cell Info:** NetMonster Core
- **Maps:** osmdroid (OpenStreetMap)
- **Location:** Google Play Services Location

## Contributing

Contributions are welcome! Please open an issue to discuss changes before submitting a pull request.

## License

[Apache License 2.0](LICENSE)