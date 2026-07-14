package com.cellrecorder.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cellrecorder.app.data.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Durable shutdown finalization worker. Retries `updateEndedAt` and `updatePrimarySimSlot`
 * when the 5-second in-process attempt fails or times out. Idempotent: `updateEndedAt` is
 * only applied when the session's `endedAt` is still null. After [MAX_ATTEMPTS] failed
 * attempts, returns [Result.failure] so WorkManager stops retrying.
 */
@HiltWorker
class SessionFinalizationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sessionRepository: SessionRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId <= 0L) {
            Timber.e("SessionFinalizationWorker: missing sessionId in input data")
            return Result.failure()
        }
        val primarySlot = inputData.getInt(KEY_PRIMARY_SLOT, -1).let { if (it < 0) null else it }

        return try {
            val session = sessionRepository.getById(sessionId).first()
            if (session == null) {
                Timber.e("SessionFinalizationWorker: session $sessionId not found")
                return Result.failure()
            }
            if (session.endedAt == null) {
                sessionRepository.updateEndedAt(sessionId, System.currentTimeMillis())
                Timber.i("SessionFinalizationWorker: set endedAt for session $sessionId")
            } else {
                Timber.i("SessionFinalizationWorker: endedAt already set for session $sessionId; no-op (idempotent)")
            }
            sessionRepository.updatePrimarySimSlot(sessionId, primarySlot)
            Result.success()
        } catch (e: CancellationException) {
            Timber.e(e, "SessionFinalizationWorker cancelled for session $sessionId")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SessionFinalizationWorker: finalization attempt ${runAttemptCount + 1} failed for session $sessionId")
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                Timber.e("SessionFinalizationWorker: reached max attempts ($MAX_ATTEMPTS); giving up")
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_PRIMARY_SLOT = "primary_slot"
        const val MAX_ATTEMPTS = 5
        const val UNIQUE_WORK_PREFIX = "session_finalization_"

        fun request(sessionId: Long, primarySlot: Int?): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_SESSION_ID to sessionId,
                KEY_PRIMARY_SLOT to (primarySlot ?: -1)
            )
            return OneTimeWorkRequestBuilder<SessionFinalizationWorker>()
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
        }

        fun enqueue(context: Context, sessionId: Long, primarySlot: Int?) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_WORK_PREFIX$sessionId",
                ExistingWorkPolicy.REPLACE,
                request(sessionId, primarySlot)
            )
        }
    }
}
