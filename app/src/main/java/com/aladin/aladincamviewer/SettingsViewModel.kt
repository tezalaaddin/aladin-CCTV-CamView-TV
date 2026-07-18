package com.aladin.aladincamviewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CameraRepository
    private val prefHelper: PreferenceHelper
    val allCameras: Flow<List<CameraEntity>>

    init {
        val cameraDao = AppDatabase.getDatabase(application).cameraDao()
        repository = CameraRepository(cameraDao)
        prefHelper = PreferenceHelper(application)
        allCameras = repository.allCameras
    }

    fun updatePin(pin: String) {
        prefHelper.appPin = pin
    }

    fun getPin() = prefHelper.appPin

    fun updateOfflineAlarm(enabled: Boolean) {
        prefHelper.isOfflineAlarmEnabled = enabled
    }

    fun isOfflineAlarmEnabled() = prefHelper.isOfflineAlarmEnabled

    fun saveCamera(camera: CameraEntity) {
        viewModelScope.launch {
            if (camera.id == 0) repository.insert(camera) else repository.update(camera)
        }
    }

    fun exportConfig(outputStream: OutputStream, cameras: List<CameraEntity>) {
        viewModelScope.launch {
            val config = ConfigModel(cameras, prefHelper.appPin, prefHelper.isOfflineAlarmEnabled)
            val jsonString = Json { prettyPrint = true }.encodeToString(ConfigModel.serializer(), config)
            outputStream.use { it.write(jsonString.toByteArray()) }
        }
    }

    fun importConfig(inputStream: InputStream, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val config = Json.decodeFromString<ConfigModel>(jsonString)
                
                repository.deleteAll()
                repository.insertAll(config.cameras)
                prefHelper.appPin = config.appPin
                prefHelper.isOfflineAlarmEnabled = config.offlineAlarm
                
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
