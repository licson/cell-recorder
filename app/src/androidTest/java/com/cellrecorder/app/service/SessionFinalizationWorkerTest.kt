package com.cellrecorder.app.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.repository.SessionRepository
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionFinalizationWorkerTest {

    private val sessionRepository: SessionRepository = mockk(relaxed = true)
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun buildWorker(runAttemptCount: Int = 0): SessionFinalizationWorker {
        return TestListenableWorkerBuilder<SessionFinalizationWorker>(
            context,
            runAttemptCount = runAttemptCount
        ).setWorkerFactory(object : androidx.work.WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return SessionFinalizationWorker(appContext, workerParameters, sessionRepository)
            }
        }).build()
    }

    @Test
    fun idempotent_whenEndedAtAlreadySet_doesNotOverwrite_andReturnsSuccess() = runBlocking {
        val sessionId = 42L
        val existingEndedAt = 1000L
        coEvery { sessionRepository.getById(sessionId) } returns flowOf(
            SessionEntity(id = sessionId, name = "test", createdAt = 500L, endedAt = existingEndedAt)
        )

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { sessionRepository.updateEndedAt(sessionId, any()) }
        coVerify(exactly = 1) { sessionRepository.updatePrimarySimSlot(sessionId, any()) }
    }

    @Test
    fun success_whenEndedAtNull_setsEndedAt_andUpdatesPrimarySlot() = runBlocking {
        val sessionId = 42L
        coEvery { sessionRepository.getById(sessionId) } returns flowOf(
            SessionEntity(id = sessionId, name = "test", createdAt = 500L, endedAt = null)
        )

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { sessionRepository.updateEndedAt(sessionId, any()) }
        coVerify(exactly = 1) { sessionRepository.updatePrimarySimSlot(sessionId, 0) }
    }

    @Test
    fun exception_returnsRetry_whenUnderMaxAttempts() = runBlocking {
        val sessionId = 42L
        coEvery { sessionRepository.getById(sessionId) } throws RuntimeException("DB error")

        val worker = buildWorker(runAttemptCount = 0)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun exception_returnsFailure_whenAtMaxAttempts() = runBlocking {
        val sessionId = 42L
        coEvery { sessionRepository.getById(sessionId) } throws RuntimeException("DB error")

        val worker = buildWorker(runAttemptCount = SessionFinalizationWorker.MAX_ATTEMPTS - 1)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun missingSessionId_returnsFailure() = runBlocking {
        // Build a worker with empty input data — sessionId defaults to -1L.
        val worker = TestListenableWorkerBuilder<SessionFinalizationWorker>(
            context,
            runAttemptCount = 0
        ).setWorkerFactory(object : androidx.work.WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return SessionFinalizationWorker(appContext, workerParameters, sessionRepository)
            }
        }).build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun sessionNotFound_returnsFailure() = runBlocking {
        val sessionId = 999L
        coEvery { sessionRepository.getById(sessionId) } returns flowOf(null)

        // Use the request builder to construct proper input data.
        val worker = TestListenableWorkerBuilder<SessionFinalizationWorker>(
            context,
            inputData = androidx.work.workDataOf(
                SessionFinalizationWorker.KEY_SESSION_ID to sessionId,
                SessionFinalizationWorker.KEY_PRIMARY_SLOT to -1
            ),
            runAttemptCount = 0
        ).setWorkerFactory(object : androidx.work.WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return SessionFinalizationWorker(appContext, workerParameters, sessionRepository)
            }
        }).build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
