package com.cellrecorder.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import androidx.core.app.NotificationCompat
import com.cellrecorder.app.R
import com.cellrecorder.app.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingNotificationHelper @Inject constructor() {

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.recording_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(
        context: Context,
        sessionId: Long,
        elapsedMs: Long,
        pointCount: Int,
        isExtrapolating: Boolean,
        hasGpsFix: Boolean
    ): Notification {
        val stopIntent = PendingIntent.getService(
            context, 0, Intent(context, RecordingService::class.java).apply {
                action = RecordingService.ACTION_STOP
                putExtra(RecordingService.EXTRA_SESSION_ID, sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                putExtra(RecordingService.EXTRA_SESSION_ID, sessionId)
                addFlags(FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val totalSec = elapsedMs / 1000
        val elapsed = String.format("%02d:%02d", totalSec / 60, totalSec % 60)
        val gps = when {
            isExtrapolating -> "GPS ! Hold phone steady"
            hasGpsFix -> "GPS OK"
            else -> "GPS ..."
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Cell Recorder")
            .setContentText("$elapsed — $pointCount pts — $gps")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    fun notify(context: Context, notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "cell_recorder_channel"
        const val NOTIFICATION_ID = 1001
    }
}