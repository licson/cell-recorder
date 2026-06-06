# Data Import and Export Specification (Delta)

## ADDED Requirements

### Requirement: Speedtest Record Entity

The system SHALL store speedtest results in a separate Room entity, not mixed with cell records.

#### Scenario: SpeedTestRecordEntity structure
- GIVEN a speed test completes
- WHEN the result is persisted
- THEN a `SpeedTestRecordEntity` is created with: sessionId, timestamp, downloadBps, uploadBps, serverName, serverHost, serverLocation, serverId, dataSimSlotIndex, ratAtTest, rsrpAtTest, bandAtTest, succeeded, errorMessage, networkType
- AND the entity is stored in the `speed_test_records` table
- AND a foreign key links `sessionId` to `sessions.id` with ON DELETE CASCADE

### Requirement: Speedtest CSV Export

The system SHALL include speedtest data in session export when speedtest records exist.

#### Scenario: Speedtest CSV export
- GIVEN a session with speedtest records
- WHEN the user exports the session
- THEN a separate `session_name_speedtest.csv` file is generated alongside the cell record CSV
- AND the speedtest CSV contains columns: timestamp, download_bps, upload_bps, server_name, server_host, server_location, succeeded, error_message, data_sim_slot, rat_at_test, rsrp_at_test, band_at_test, network_type

#### Scenario: No speedtest export when empty
- GIVEN a session without speedtest records
- WHEN the user exports the session
- THEN only the cell record CSV is generated (no speedtest file)