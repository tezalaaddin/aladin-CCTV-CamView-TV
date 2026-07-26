package com.aladin.aladincamviewer

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CameraRepository(context: Context, private val cameraDao: CameraDao) {
    private val crypto = CredentialCrypto()
    val allCameras: Flow<List<CameraEntity>> = cameraDao.getAllCameras().map { rows -> rows.map(::decrypt) }

    suspend fun insert(camera: CameraEntity) {
        cameraDao.insertCamera(encrypt(camera))
    }

    suspend fun insertAll(cameras: List<CameraEntity>) {
        cameraDao.insertCameras(cameras.map(::encrypt))
    }

    suspend fun update(camera: CameraEntity) {
        cameraDao.updateCamera(encrypt(camera))
    }

    suspend fun delete(camera: CameraEntity) {
        cameraDao.deleteCamera(encrypt(camera))
    }

    suspend fun deleteAll() {
        cameraDao.deleteAll()
    }

    suspend fun getCameraById(id: Int): CameraEntity? {
        return cameraDao.getCameraById(id)?.let(::decrypt)
    }

    suspend fun isIpAlreadyUsed(ipAddress: String, excludeId: Int = 0): Boolean =
        cameraDao.countByIp(ipAddress.trim(), excludeId) > 0

    suspend fun replaceAll(cameras: List<CameraEntity>) = cameraDao.replaceAll(cameras.map(::encrypt))

    suspend fun getAllOnce(): List<CameraEntity> = cameraDao.getAllCamerasOnce().map(::decrypt)

    suspend fun migrateLegacySecrets() {
        cameraDao.getAllCamerasOnce().forEach { row ->
            val encrypted = encrypt(row)
            if (encrypted != row) cameraDao.updateCamera(encrypted)
        }
    }

    private fun encrypt(camera: CameraEntity) = camera.copy(
        username = crypto.encrypt(camera.username),
        password = crypto.encrypt(camera.password),
        onvifUsername = crypto.encrypt(camera.onvifUsername),
        onvifPassword = crypto.encrypt(camera.onvifPassword),
        mainStreamUrl = crypto.encrypt(camera.mainStreamUrl),
        subStreamUrl = crypto.encrypt(camera.subStreamUrl)
    )

    private fun decrypt(camera: CameraEntity) = camera.copy(
        username = safeDecrypt(camera.username),
        password = safeDecrypt(camera.password),
        onvifUsername = safeDecrypt(camera.onvifUsername),
        onvifPassword = safeDecrypt(camera.onvifPassword),
        mainStreamUrl = safeDecrypt(camera.mainStreamUrl),
        subStreamUrl = safeDecrypt(camera.subStreamUrl)
    )

    private fun safeDecrypt(value: String): String = runCatching { crypto.decrypt(value) }
        .onFailure { AppLog.e("ALADIN_SECURITY", "Stored camera secret could not be decrypted", it) }
        .getOrDefault("")
}
