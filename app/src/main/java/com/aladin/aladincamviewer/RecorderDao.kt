package com.aladin.aladincamviewer

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecorderDao {
    @Query("SELECT * FROM recorders ORDER BY name COLLATE NOCASE")
    fun observeRecorders(): Flow<List<RecorderEntity>>

    @Transaction
    @Query("SELECT * FROM recorder_channels WHERE enabled = 1 ORDER BY recorderId, channelNumber")
    fun observeEnabledChannels(): Flow<List<RecorderChannelWithRecorder>>

    @Query("SELECT * FROM recorders WHERE id = :id")
    suspend fun getRecorder(id: Long): RecorderEntity?

    @Query("SELECT * FROM recorder_channels WHERE recorderId = :recorderId ORDER BY channelNumber")
    suspend fun getChannels(recorderId: Long): List<RecorderChannelEntity>

    @Query("SELECT COUNT(*) FROM recorders WHERE serialNumber != '' AND serialNumber = :serial AND id != :excludeId")
    suspend fun countBySerial(serial: String, excludeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecorder(recorder: RecorderEntity): Long

    @Update
    suspend fun updateRecorder(recorder: RecorderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<RecorderChannelEntity>)

    @Query("DELETE FROM recorder_channels WHERE recorderId = :recorderId")
    suspend fun deleteChannels(recorderId: Long)

    @Delete
    suspend fun deleteRecorder(recorder: RecorderEntity)
}
