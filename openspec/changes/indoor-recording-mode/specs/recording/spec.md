## ADDED Requirements

### Requirement: Indoor Recording Mode

The system SHALL support an indoor recording mode that uses IMU-based pedestrian dead reckoning instead of GPS for position tracking.

#### Scenario: Indoor recording lifecycle
- GIVEN a session with `recordingMode = "INDOOR"`
- WHEN the user starts recording
- THEN the recording service begins collecting cell data using time-based triggers
- AND position is estimated via `IndoorPositionCollector` instead of `LocationCollector`
- AND no GPS location requests are made

#### Scenario: Time-based recording triggers for indoor
- GIVEN an active indoor recording
- WHEN `indoorRecordingIntervalMs` has elapsed since the last recorded point
- THEN a new point is recorded with the current indoor position (relativeX, relativeY)
- AND `locationSource = "INDOOR_IMU"`, `isLocationEstimated = false`
- AND `latitude`, `longitude`, `altitude`, `accuracy` are set to null

#### Scenario: No GPS distance triggers in indoor mode
- GIVEN an active indoor recording
- THEN GPS-based distance triggers (`locationChangeThresholdM`) SHALL NOT be used
- AND only the time-based trigger (`indoorRecordingIntervalMs`) applies

### Requirement: Indoor Path Storage

The system SHALL store the indoor movement path using the same efficient data structure as outdoor mode.

#### Scenario: Indoor path in recorded path snapshot
- GIVEN an active indoor recording
- WHEN a point is recorded
- THEN the (relativeX, relativeY) pair is appended to the path buffer
- AND the path buffer uses the same O(1) insertion/removal structure as outdoor mode
- AND the path is exposed via `recordedPathSnapshot` for the recording screen

## MODIFIED Requirements

### Requirement: Recording Start

The system SHALL start a foreground service when the user initiates a recording. For indoor sessions, the service SHALL use `IndoorPositionCollector` instead of `LocationCollector` and SHALL NOT start GPS-based location collection.

#### Scenario: Start recording from recording screen
- GIVEN a session with `endedAt = null`
- WHEN the user taps the Start button
- THEN a foreground service with `FOREGROUND_SERVICE_TYPE_LOCATION` is started
- AND the service begins collecting location and cell data

#### Scenario: Start indoor recording
- GIVEN a session with `endedAt = null` and `recordingMode = "INDOOR"`
- WHEN the user taps the Start button
- THEN a foreground service is started
- AND the service begins collecting cell data using time-based triggers
- AND position is estimated via `IndoorPositionCollector`
- AND no GPS location requests are made

### Requirement: GPS Loss Extrapolation

The system SHALL continue recording using sensor-based dead reckoning when GPS fix is lost, for a limited duration. This requirement applies only to outdoor recording mode.

#### Scenario: Extrapolation mode activation
- GIVEN an active outdoor recording with a previously acquired GPS fix
- WHEN no accurate GPS fix is received for more than 3 seconds
- THEN the system enters extrapolation mode

#### Scenario: Extrapolation mode not used in indoor
- GIVEN an active indoor recording
- THEN GPS loss extrapolation SHALL NOT be used
- AND no fallback recording job for GPS loss detection is launched
