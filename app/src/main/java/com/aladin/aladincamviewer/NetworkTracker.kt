package com.aladin.aladincamviewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Background worker to handle DHCP IP changes by tracking ONVIF UUIDs.
 */
class NetworkTracker(private val context: Context, private val repository: CameraRepository) {

    private val scanner = OnvifScanner(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startTracking() {
        scope.launch {
            // Delay to allow network initialization on boot
            kotlinx.coroutines.delay(8000)
            updateCameraIps()
        }
    }

    private suspend fun updateCameraIps() = withContext(Dispatchers.IO) {
        val discovered = scanner.discoverDevices()
        if (discovered.isEmpty()) return@withContext

        // Get snapshot of current cameras
        val savedCameras = repository.allCameras.first()

        savedCameras.forEach { camera ->
            if (camera.uuid.isNotEmpty()) {
                val matchingDevice = discovered.find { it.uuid == camera.uuid }
                
                if (matchingDevice != null && matchingDevice.ip != camera.ipAddress) {
                    // Critical: IP has changed!
                    val oldIp = camera.ipAddress
                    val newIp = matchingDevice.ip
                    
                    val updatedCamera = camera.copy(
                        ipAddress = newIp,
                        mainStreamUrl = camera.mainStreamUrl.replace(oldIp, newIp),
                        subStreamUrl = camera.subStreamUrl.replace(oldIp, newIp)
                    )
                    
                    repository.update(updatedCamera)
                    
                    mainHandler.post {
                        Toast.makeText(context, 
                            "Camera ${camera.name} IP Updated: $newIp", 
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
