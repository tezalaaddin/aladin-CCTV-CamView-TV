package com.aladin.aladincamviewer

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras ORDER BY displayOrder ASC")
    fun getAllCameras(): Flow<List<CameraEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCamera(camera: CameraEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCameras(cameras: List<CameraEntity>)

    @Update
    suspend fun updateCamera(camera: CameraEntity)

    @Delete
    suspend fun deleteCamera(camera: CameraEntity)

    @Query("DELETE FROM cameras")
    suspend fun deleteAll()

    @Query("SELECT * FROM cameras WHERE id = :id")
    suspend fun getCameraById(id: Int): CameraEntity?

    @Query("SELECT COUNT(*) FROM cameras WHERE ipAddress = :ipAddress AND id != :excludeId")
    suspend fun countByIp(ipAddress: String, excludeId: Int = 0): Int
}
