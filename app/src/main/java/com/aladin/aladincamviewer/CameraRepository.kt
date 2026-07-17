package com.aladin.aladincamviewer

import kotlinx.coroutines.flow.Flow

class CameraRepository(private val cameraDao: CameraDao) {
    val allCameras: Flow<List<CameraEntity>> = cameraDao.getAllCameras()

    suspend fun insert(camera: CameraEntity) {
        cameraDao.insertCamera(camera)
    }

    suspend fun insertAll(cameras: List<CameraEntity>) {
        cameraDao.insertCameras(cameras)
    }

    suspend fun update(camera: CameraEntity) {
        cameraDao.updateCamera(camera)
    }

    suspend fun delete(camera: CameraEntity) {
        cameraDao.deleteCamera(camera)
    }

    suspend fun deleteAll() {
        cameraDao.deleteAll()
    }

    suspend fun getCameraById(id: Int): CameraEntity? {
        return cameraDao.getCameraById(id)
    }
}
