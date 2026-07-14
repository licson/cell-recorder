package com.cellrecorder.app

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cellrecorder.app.logging.RollingFileTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class CellRecorderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        installTimber()
        installCrashLogger()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun installTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(RollingFileTree(filesDir))
    }

    private fun installCrashLogger() {
        val logDir = File(filesDir, "crash_logs")
        logDir.mkdirs()

        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                FileWriter(File(logDir, "crash_$ts.txt")).use { writer ->
                    writer.write("=== Crash Report ===\n")
                    writer.write("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
                    writer.write("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    writer.write("Git: ${BuildConfig.GIT_HASH}\n")
                    writer.write("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    writer.write("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n")
                    throwable.printStackTrace(PrintWriter(writer))
                }
                val files = logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                if (files.size > 5) files.drop(5).forEach { it.delete() }
            } catch (_: Exception) {
            } finally {
                originalHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}