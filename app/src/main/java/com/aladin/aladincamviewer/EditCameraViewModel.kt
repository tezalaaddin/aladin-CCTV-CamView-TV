package com.aladin.aladincamviewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EditCameraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CameraRepository

    init {
        val cameraDao = AppDatabase.getDatabase(application).cameraDao()
        repository = CameraRepository(cameraDao)
    }

    suspend fun getCameraById(id: Int) = repository.getCameraById(id)

    suspend fun getAllCameras() = repository.allCameras.first()

    fun saveCamera(camera: CameraEntity) {
        viewModelScope.launch {
            if (camera.id == 0) {
                repository.insert(camera)
            } else {
                repository.update(camera)
            }
        }
    }
}
