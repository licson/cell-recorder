package com.cellrecorder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {

    @Query("SELECT * FROM app_config WHERE id = 1")
    fun get(): Flow<AppConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(config: AppConfigEntity)
}