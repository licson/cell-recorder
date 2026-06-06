# Background Service Specification (Delta)

## ADDED Requirements

### Requirement: Speedtest in Notification

The system SHALL include optional speedtest status in the recording notification when speedtest is enabled.

#### Scenario: Speedtest status in notification
- GIVEN an active recording with speedtest enabled
- THEN the notification displays the current speedtest status ("Running", "Completed", "Failed")
- AND the notification is updated at the standard 1Hz rate from the state update job