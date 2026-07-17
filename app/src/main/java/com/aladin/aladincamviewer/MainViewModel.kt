package com.aladin.aladincamviewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.Flow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CameraRepository
    val allCameras: Flow<List<CameraEntity>>

    init {
        val cameraDao = AppDatabase.getDatabase(application).cameraDao()
        repository = CameraRepository(cameraDao)
        allCameras = repository.allCameras
    }
}
