package com.cellrecorder.app.data.repository

import com.cellrecorder.app.data.local.dao.RecentMarkerLabelDao
import com.cellrecorder.app.data.local.entity.RecentMarkerLabelEntity
import com.cellrecorder.app.domain.model.MarkerType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentMarkerLabelRepository @Inject constructor(
    private val recentMarkerLabelDao: RecentMarkerLabelDao
) {
    suspend fun getByTypeOrdered(type: MarkerType): List<RecentMarkerLabelEntity> =
        recentMarkerLabelDao.getByTypeOrdered(type.toStorageString()).takeTop(20)

    suspend fun deleteAll() = recentMarkerLabelDao.deleteAll()

    private fun List<RecentMarkerLabelEntity>.takeTop(limit: Int): List<RecentMarkerLabelEntity> =
        if (size <= limit) this else subList(0, limit).toList()
}
