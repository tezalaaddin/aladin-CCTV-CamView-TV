package com.aladin.aladincamviewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CameraRepository
    val allCameras: Flow<List<CameraModel>>

    init {
        val cameraDao = AppDatabase.getDatabase(application).cameraDao()
        repository = CameraRepository(application, cameraDao)
        val recorderRepository = RecorderRepository(application)
        allCameras = combine(repository.allCameras, recorderRepository.enabledChannels) { cameras, channels ->
            cameras.map { camera ->
                CameraModel(camera.name, camera.mainStreamUrl, camera.subStreamUrl, camera.ipAddress,
                    camera.ptzSupported, camera.username, camera.password, camera.onvifUsername,
                    camera.onvifPassword, camera.brand)
            } + channels.map { row ->
                CameraModel(
                    name = "${row.recorder.name} • ${row.channel.name}",
                    mainStreamUrl = row.channel.mainStreamUrl,
                    subStreamUrl = row.channel.subStreamUrl,
                    ipAddress = row.recorder.ipAddress,
                    username = row.recorder.username,
                    password = row.recorder.password,
                    brand = "Hikvision NVR",
                    recorderId = row.recorder.id,
                    recorderChannel = row.channel.channelNumber
                )
            }
        }
    }
}
