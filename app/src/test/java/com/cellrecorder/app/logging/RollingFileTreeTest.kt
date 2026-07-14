package com.cellrecorder.app.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import timber.log.Timber
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RollingFileTreeTest {

    @TempDir
    lateinit var tempDir: File

    private fun plantTree(maxBytes: Long = 1024L): RollingFileTree {
        val tree = RollingFileTree(tempDir, maxBytes = maxBytes)
        Timber.plant(tree)
        return tree
    }

    private fun logDir(): File = File(tempDir, RollingFileTree.LOG_DIR_NAME)

    private fun currentFile(): File = File(logDir(), RollingFileTree.CURRENT_FILE_NAME)

    private fun rotatedFile(): File = File(logDir(), RollingFileTree.ROTATED_FILE_NAME)

    @Test
    fun `append under cap does not rotate`() {
        val tree = plantTree(maxBytes = 1024L)
        try {
            Timber.e("hello world")
            tree.flush()

            assertTrue(currentFile().exists(), "runtime.log should exist")
            val content = currentFile().readText()
            assertTrue(content.contains("hello world"), "content should contain the message")
            assertFalse(rotatedFile().exists(), "runtime.log.1 should NOT exist (no rotation)")
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun `rotation at cap moves current to rotated`() {
        // Small cap so a single line triggers rotation on the NEXT line.
        val tree = plantTree(maxBytes = 60L)
        try {
            // First message: writes the file with content (~50 bytes including timestamp).
            Timber.e("first line that is long enough to nearly fill the cap")
            tree.flush()
            assertTrue(currentFile().exists(), "first write should create runtime.log")

            // Second message: pushes us over the cap → rotate then write fresh.
            Timber.e("second line that triggers the rotation")
            tree.flush()

            assertTrue(rotatedFile().exists(), "runtime.log.1 should exist after rotation")
            val rotatedContent = rotatedFile().readText()
            assertTrue(
                rotatedContent.contains("first line that is long enough"),
                "rotated file should hold the first line"
            )
            val currentContent = currentFile().readText()
            assertTrue(
                currentContent.contains("second line that triggers the rotation"),
                "current file should hold only the second line"
            )
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun `second rotation overwrites the existing rotated file`() {
        val tree = plantTree(maxBytes = 50L)
        try {
            Timber.e("first message long enough")
            tree.flush()
            Timber.e("second message long enough")
            tree.flush()
            // After the second line we've rotated once; rotated holds "first".
            assertTrue(rotatedFile().exists())
            val firstRotated = rotatedFile().readText()
            assertTrue(firstRotated.contains("first message"))

            Timber.e("third message long enough")
            tree.flush()
            // After the third line we've rotated again; rotated now holds "second".
            assertTrue(rotatedFile().exists())
            val secondRotated = rotatedFile().readText()
            assertTrue(secondRotated.contains("second message"))
            assertFalse(secondRotated.contains("first message"), "old .1 must be overwritten")
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun `concurrent appends from multiple threads are serialized`() {
        val tree = plantTree(maxBytes = 10L * 1024 * 1024) // large cap; no rotation here
        try {
            val threadCount = 8
            val perThread = 200
            val startLatch = CountDownLatch(1)
            val doneLatch = CountDownLatch(threadCount)

            val threads = (0 until threadCount).map { idx ->
                thread(start = false) {
                    startLatch.await()
                    try {
                        repeat(perThread) { i ->
                            Timber.e("thread=$idx iteration=$i")
                        }
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
            startLatch.countDown()
            threads.forEach { it.start() }
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "all writers should finish")

            tree.flush()

            val content = currentFile().readText()
            val lines = content.lines().filter { it.isNotBlank() }
            // Each Timber.e call produces exactly one line (no throwable → no stack trace).
            assertEquals(threadCount * perThread, lines.size, "no torn or dropped lines")

            // Each line must be well-formed: contains the expected prefix tokens.
            // Format: "yyyy-MM-dd HH:mm:ss.SSS E [TAG] thread=N iteration=M"
            // The RollingFileTree uses no tag by default (Timber.e with no explicit tag).
            val malformed = lines.filter { !it.contains("thread=") || !it.contains("iteration=") }
            assertEquals(emptyList<String>(), malformed, "no malformed lines")
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun `log dir and files created on first write`() {
        val tree = plantTree(maxBytes = 1024L)
        try {
            // The constructor creates the directory eagerly.
            assertTrue(logDir().exists(), "log dir should be created eagerly by the constructor")
            // The current file is created lazily on the first write.
            assertFalse(currentFile().exists(), "current file should not exist before first write")
            Timber.e("first")
            tree.flush()
            assertTrue(currentFile().exists(), "current file should be created on first write")
        } finally {
            Timber.uproot(tree)
        }
    }
}
