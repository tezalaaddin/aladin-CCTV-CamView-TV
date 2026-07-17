package com.aladin.aladincamviewer

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras ORDER BY displayOrder ASC")
    fun getAllCameras(): Flow<List<CameraEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCamera(camera: CameraEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCameras(cameras: List<CameraEntity>)

    @Update
    suspend fun updateCamera(camera: CameraEntity)

    @Delete
    suspend fun deleteCamera(camera: CameraEntity)

    @Query("DELETE FROM cameras")
    suspend fun deleteAll()

    @Query("SELECT * FROM cameras WHERE id = :id")
    suspend fun getCameraById(id: Int): CameraEntity?
}
