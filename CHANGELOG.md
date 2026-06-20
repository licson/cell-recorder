# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Coarse location permission** — the app now declares `ACCESS_COARSE_LOCATION` alongside `ACCESS_FINE_LOCATION`, as required on Android 12 and later so the system can offer users a coarse-only location grant.
- **Network state permission** — declared `ACCESS_NETWORK_STATE` so the speedtest's Wi-Fi detection works correctly and cellular tests are skipped when on Wi-Fi.

### Changed

- **Updated all libraries and Android build tools to their latest versions** for long-term compatibility, security patches, and continued support in future Android Studio releases. This includes the Android Gradle Plugin, Kotlin, Compose, Hilt, Room, and all test libraries.
- **Launcher icon now supports Android 13+ themed icons** — added a monochrome layer so the app icon adapts to themed home screens.

### Fixed

- **Resolved all static analysis (lint) errors and warnings** — the project now builds clean with zero lint findings. Fixes include locale-safe number formatting in CSV exports and UI, correct Compose modifier parameter ordering, removal of redundant SDK version checks, and deletion of unused string/color resources.
- **Themed/monochrome launcher icon** is now properly defined so the icon renders correctly on Android 13+ themed launchers.

## [1.2.0] - 2026-06-12

### Added

- **Continuous speedtest during recording** — measure download and upload throughput at regular intervals while recording cell data, so you can see how signal strength relates to actual network performance. Uses a custom implementation of the Speedtest.net HTTP protocol (no third-party app required).
- **Speedtest settings** — enable or disable speedtests, toggle upload testing, set the interval between tests, and optionally pin a specific speedtest server in Settings.
- **Speedtest EULA dialog** — first-time speedtest users see a brief notice about the Speedtest.net terms before enabling the feature.
- **Live speedtest status** — the recording screen shows the current speedtest phase (selecting server, downloading, uploading) and latest results in Mbps.
- **Speedtest markers on replay** — speedtest results appear as dots on the session replay timeline; tap one to see its details, or the summary card auto-shows the result at your current replay position.
- **Speedtest analytics** — session detail now includes a speedtest section with average/peak download and upload speeds, throughput histograms, and correlation charts showing how speed varies with signal strength, network type, and SIM.
- **Speedtest CSV export** — export speedtest results as a separate CSV file alongside cell data.
- **Global speedtest stats** — the Statistics screen now shows overall speedtest averages across all sessions.
- **Indoor recording mode** — record cell coverage inside buildings, basements, and anywhere GPS can't reach. Choose "Indoor" when creating a session; your phone tracks your walking path using motion sensors instead of satellite signals.
- **Accelerometer step detection fallback** — if your phone's built-in step counter isn't available (some devices don't have one, or it may not detect steps when the phone is in your hand), the app detects steps from the accelerometer automatically.
- **2D path canvas** — indoor sessions show your walking path on a pannable, zoomable canvas instead of a map. The path is color-coded by signal strength, packet loss, cell ID, network type, or band.
- **Tracking confidence indicator** — shows how reliable your indoor position estimate is: Confident, Degrading, or High drift, based on how long since your last origin reset and how far you've walked.
- **Drift radius** — a translucent circle around your position grows over time to show how much uncertainty has accumulated.
- **Reset Origin button** — tap to recenter your position mid-recording. The old path is preserved with a gap marker so you can see where you reset.
- **Sensor health warning** — if no steps are detected for 10 seconds, a banner suggests moving the phone to your pocket for better tracking.
- **Indoor recording settings** — adjust your step length (0.3–1.2 m) and recording interval in Settings.
- **Ping stats in indoor mode** — live ping latency and packet loss are now visible while recording indoors.
- **Indoor session detail and replay** — review your indoor path with full replay support, signal coloring, and the same display modes as the outdoor map.
- **Indoor export and import** — indoor sessions export relative coordinates in CSV and GeoJSON; GeoJSON files include an `indoorMode` flag so tools can distinguish them from GPS-tracked data.

### Changed

- The recording screen now shows the path canvas for indoor sessions and the map for outdoor sessions.
- Heading direction is now calculated using the standard Android sensor API, which is more reliable on devices with 3-element rotation vectors.
- Database schema updated to v11 to store speedtest records, indoor session mode, relative coordinates, and indoor settings.

### Fixed

- **Speedtest always failed on Android** because the config fetch sent a gzip header that Android's HTTP stack doesn't expect, and latency pings used the wrong URL scheme.
- **Speedtest always returned 0 Mbps** because Android 11+ blocks unencrypted HTTP by default — the app now uses the correct network security configuration and upgrades to HTTPS when the server supports it.
- **Speedtest server was re-discovered on every test** after any error, even unrelated ones. Cache now persists across measurement failures.
- **Speedtest upload analytics showed download speeds** instead of upload speeds — now correctly computed.
- **Mbps displayed as 0** for sub-1 Mbps speeds (e.g., 0.4 Mbps showed as 0) — now shows one decimal place.
- **Replay speedtest summary always showed the latest test** instead of the one at your current playback position — now auto-updates as you scrub through the timeline.
- **Indoor recording produced no path or data** on Android 10+ because the physical activity permission was missing. The app now requests it automatically before starting an indoor session.
- **Wrong heading direction on some devices** due to an incorrect fallback in the compass calculation, which could cause the indoor path to point the wrong way.
- **Indoor recording could silently fail** if the step sensor couldn't be started — no error was shown, and no data was collected. The app now falls back to the accelerometer automatically or shows an error if no sensors are available.

## [1.1.1] - 2026-06-05

### Added

- Carrier Aggregation support: secondary LTE bands are now recorded alongside the primary band
- Live Info screen now shows secondary (CA) bands in real time per SIM
- Band Distribution charts now include secondary CA bands
- CSV and GeoJSON exports now include secondary CA bands
- Re-imported sessions correctly restore CA band data
- When GPS signal is lost during recording, the app now estimates your speed using the accelerometer to keep your position on track until GPS returns
- 5G NSA (Non-Standalone) recording support: when on a 5G NSA network, the app now correctly records the NR cell (band, PCI, signal) and saves the underlying LTE anchor cell's details alongside it
- Replay and session detail screens now show the LTE anchor's band, PCI, and signal strength for 5G NSA records
- Live Info screen now shows the LTE anchor cell info when connected via 5G NSA
- 5G NSA recordings now include carrier aggregation bands from the LTE anchor

### Fixed

- Fixed direction tracking during GPS loss — movement now follows the correct heading
- Fixed 5G NSA recordings being incorrectly labeled as "4G" — now correctly recorded as "5G_NSA"
- Fixed NR cell data (PCI, RSRP, RSRQ, SINR, band) being lost during 5G NSA sessions
- Analytics "Massive MIMO Candidate" insight now includes 5G NSA handoff events alongside 5G SA

### Changed

- CSV and GeoJSON export now include anchor cell columns for 5G NSA records (prefixed with `anchor_`)
- Room database schema updated to v8 (automatic migration, no data loss)

## [1.0.3] - 2026-06-03

### Added

- Added filter chips to the anomaly inspector for quicker issue navigation
- Repeated anomaly messages are now grouped, showing how long each issue lasted

### Fixed

- Fixed floating action button position
- Fixed extra spacing in landscape mode
- Fixed duplicate bottom spacing on all screens

### Changed

- Improved overall UI smoothness and responsiveness

## [1.0.2] - 2026-06-03

### Fixed

- Fixed a crash that could occur while recording

## [1.0.1] - 2026-06-03

### Fixed

- Fixed version info display on the About screen

## [1.0.0] - 2026-06-03

### Added

- Initial release
- Real-time cell tower recording (signal strength, Cell ID, RAT, frequency bands)
- GPS coordinate logging during recording sessions
- Background recording via foreground service
- Session management with custom names
- CSV and GeoJSON export
- Session replay on interactive OpenStreetMap
- In-app crash logger with device info capture
- About screen showing version, git hash, source link, and pre-filled GitHub issue reporting