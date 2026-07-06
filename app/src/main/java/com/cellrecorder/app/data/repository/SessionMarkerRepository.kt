package com.cellrecorder.app.data.repository

import androidx.room.withTransaction
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.dao.RecentMarkerLabelDao
import com.cellrecorder.app.data.local.dao.SessionMarkerDao
import com.cellrecorder.app.data.local.entity.RecentMarkerLabelEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import com.cellrecorder.app.domain.model.MarkerType
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionMarkerRepository @Inject constructor(
    private val sessionMarkerDao: SessionMarkerDao,
    private val recentMarkerLabelDao: RecentMarkerLabelDao,
    private val appDatabase: AppDatabase
) {
    fun getMarkersForSession(sessionId: Long): Flow<List<SessionMarkerEntity>> =
        sessionMarkerDao.getBySessionId(sessionId)

    suspend fun insertMarker(sessionId: Long, type: MarkerType, label: String?): Long =
        insertMarkerInternal(sessionId, type, label, useAutoLabel = false)

    suspend fun insertMarkerWithAutoLabel(sessionId: Long, type: MarkerType): Long =
        insertMarkerInternal(sessionId, type, label = null, useAutoLabel = true)

    private suspend fun insertMarkerInternal(
        sessionId: Long,
        type: MarkerType,
        label: String?,
        useAutoLabel: Boolean
    ): Long {
        val now = System.currentTimeMillis()
        return appDatabase.withTransaction {
            val nextSeq = (sessionMarkerDao.getMaxSeq(sessionId) ?: 0) + 1
            val finalLabel = when {
                useAutoLabel -> buildAutoLabel(type, nextSeq, now)
                label.isNullOrBlank() -> null
                else -> label
            }
            val id = sessionMarkerDao.insert(
                SessionMarkerEntity(
                    sessionId = sessionId,
                    timestamp = now,
                    seq = nextSeq,
                    type = type.toStorageString(),
                    label = finalLabel
                )
            )
            if (finalLabel != null) {
                upsertRecentLabel(type, finalLabel, now)
            }
            id
        }
    }

    suspend fun updateMarker(id: Long, type: MarkerType, label: String?) {
        appDatabase.withTransaction {
            val existing = sessionMarkerDao.getById(id) ?: return@withTransaction
            sessionMarkerDao.update(
                existing.copy(
                    type = type.toStorageString(),
                    label = label
                )
            )
            if (!label.isNullOrBlank()) {
                upsertRecentLabel(type, label, System.currentTimeMillis())
            }
        }
    }

    suspend fun deleteMarker(id: Long) {
        sessionMarkerDao.deleteById(id)
    }

    suspend fun insertAll(markers: List<SessionMarkerEntity>) {
        sessionMarkerDao.insertAll(markers)
    }

    private suspend fun upsertRecentLabel(type: MarkerType, label: String, now: Long) {
        val typeString = type.toStorageString()
        val existing = recentMarkerLabelDao.getByTypeAndLabel(typeString, label)
        if (existing == null) {
            recentMarkerLabelDao.upsert(
                RecentMarkerLabelEntity(type = typeString, label = label, useCount = 1, lastUsed = now)
            )
        } else {
            recentMarkerLabelDao.upsert(
                existing.copy(useCount = existing.useCount + 1, lastUsed = now)
            )
        }
    }

    private fun buildAutoLabel(type: MarkerType, seq: Int, timestamp: Long): String {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
        return "${type.toStorageString()} #$seq $timeStr"
    }
}
