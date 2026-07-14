package com.cellrecorder.app.logging

import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * A [Timber.Tree] that appends each log line to `app_logs/runtime.log` under [baseDir],
 * rotating to `runtime.log.1` when the current file reaches [maxBytes]. Writes are
 * serialized through a single-thread [ExecutorService] so concurrent calls from any
 * thread never produce torn lines and never block the caller on disk I/O.
 *
 * On-disk footprint is bounded at approximately 2 * [maxBytes] (the current file plus
 * one rotated predecessor). Older `.1` files are overwritten on each rotation.
 */
class RollingFileTree(
    baseDir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES
) : Timber.Tree() {

    private val logDir = File(baseDir, LOG_DIR_NAME).apply { mkdirs() }
    private val currentFile = File(logDir, CURRENT_FILE_NAME)
    private val rotatedFile = File(logDir, ROTATED_FILE_NAME)

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "RollingFileTree-writer").apply { isDaemon = true }
    }

    private val currentSize = AtomicLong(if (currentFile.exists()) currentFile.length() else 0L)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        registerInstance(this)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = priorityChar(priority)
        val tagPart = tag?.let { " [$it]" } ?: ""
        // Defer timestamp formatting to the writer thread to avoid racing on the
        // non-thread-safe SimpleDateFormat from concurrent caller threads.
        executor.execute {
            val ts = timestampFormat.format(Date())
            val line = buildString {
                append(ts).append(' ').append(level).append(tagPart).append(' ').append(message)
                if (t != null) {
                    append('\n')
                    append(android.util.Log.getStackTraceString(t))
                }
                append('\n')
            }
            appendLine(line)
        }
    }

    private fun appendLine(line: String) {
        try {
            val bytes = line.toByteArray(Charsets.UTF_8)
            // Rotate BEFORE writing if this line would push us past the cap (and the file
            // already has content). This keeps the current file under the cap.
            if (currentSize.get() > 0L && currentSize.get() + bytes.size > maxBytes) {
                rotate()
            }
            FileOutputStream(currentFile, true).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            currentSize.addAndGet(bytes.size.toLong())
        } catch (_: Throwable) {
            // Logging must never throw into the caller. Drop the line silently.
        }
    }

    private fun rotate() {
        try {
            if (rotatedFile.exists()) rotatedFile.delete()
            if (currentFile.exists()) {
                currentFile.renameTo(rotatedFile)
            }
            currentSize.set(0L)
        } catch (_: Throwable) {
            // Best-effort rotation; if it fails we keep writing to the current file.
        }
    }

    /**
     * Blocks until all queued log writes have been flushed to disk. Intended to be
     * called before sharing logs (so the share payload reflects the latest entries)
     * and from tests. Safe to call from any thread except the writer thread itself.
     */
    fun flush(timeoutMs: Long = 2000L) {
        try {
            val latch = java.util.concurrent.CountDownLatch(1)
            executor.execute { latch.countDown() }
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            // Best-effort flush; never block the caller indefinitely.
        }
    }

    private fun priorityChar(priority: Int): Char = when (priority) {
        android.util.Log.VERBOSE -> 'V'
        android.util.Log.DEBUG -> 'D'
        android.util.Log.INFO -> 'I'
        android.util.Log.WARN -> 'W'
        android.util.Log.ERROR -> 'E'
        android.util.Log.ASSERT -> 'A'
        else -> '?'
    }

    companion object {
        const val LOG_DIR_NAME = "app_logs"
        const val CURRENT_FILE_NAME = "runtime.log"
        const val ROTATED_FILE_NAME = "runtime.log.1"
        const val DEFAULT_MAX_BYTES: Long = 1L * 1024 * 1024 // 1 MB

        @Volatile
        private var instance: RollingFileTree? = null

        internal fun registerInstance(tree: RollingFileTree) {
            instance = tree
        }

        /**
         * Flushes the planted [RollingFileTree] if one is registered, so any queued
         * log writes are persisted before reading the log files. Best-effort; safe
         * to call even if no tree has been planted.
         */
        fun flushPlanted(timeoutMs: Long = 2000L) {
            instance?.flush(timeoutMs)
        }
    }
}
