package com.cellrecorder.app.service

/**
 * Thrown when a fatal condition (e.g., persistent DB failure) requires the recording
 * to stop. Caught by [RecordingService] at the recordingJob boundary, where it triggers
 * [RecordingService.stopRecording] instead of the transient-error retry path.
 */
class FatalRecordingException(
    val userFacingMessage: String,
    cause: Throwable
) : RuntimeException(cause?.message ?: userFacingMessage, cause)
