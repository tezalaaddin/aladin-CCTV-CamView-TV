package com.aladin.aladincamviewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Pro-Level Background Network Tracker.
 * Periodically scans the network using HybridScanner to find and update cameras that changed IP via DHCP.
 */
class NetworkTracker private constructor(
    private val context: Context, 
    private val repository: CameraRepository
) {

    companion object {
        private const val TAG = "ALADIN_NETWORK_TRACKER"
        private const val SCAN_INTERVAL_MINUTES = 15L // More frequent scanning
        
        @Volatile
        private var instance: NetworkTracker? = null

        fun getInstance(context: Context, repository: CameraRepository): NetworkTracker {
            return instance ?: synchronized(this) {
                instance ?: NetworkTracker(context.applicationContext, repository).also { instance = it }
            }
        }
    }

    private val hybridScanner = HybridScanner(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var trackingJob: Job? = null

    fun startTracking() {
        if (trackingJob?.isActive == true) return
        
        Log.i(TAG, "🚀 Network Tracking Service Started.")
        trackingJob = scope.launch {
            while (isActive) {
                try {
                    performIpRecoveryScan()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during tracking scan", e)
                }
                delay(TimeUnit.MINUTES.toMillis(SCAN_INTERVAL_MINUTES))
            }
        }
    }

    fun triggerImmediateScan() {
        scope.launch { 
            mainHandler.post { Toast.makeText(context, "Checking for IP changes...", Toast.LENGTH_SHORT).show() }
            performIpRecoveryScan() 
        }
    }

    private suspend fun performIpRecoveryScan() {
        Log.d(TAG, "🔍 Starting IP Recovery Scan...")
        
        hybridScanner.startFullScan { discoveredList ->
            scope.launch {
                processDiscoveredDevices(discoveredList)
            }
        }
    }

    private suspend fun processDiscoveredDevices(discovered: List<DiscoveryDevice>) {
        val savedCameras = repository.allCameras.first()
        if (savedCameras.isEmpty()) return

        var updateCount = 0

        savedCameras.forEach { camera ->
            // High reliability matching: 
            // 1. Try UUID (ONVIF unique ID)
            // 2. Try MAC Address (ARP/mDNS/ONVIF unique ID)
            val matchingDevice = discovered.find { 
                (camera.uuid.isNotEmpty() && it.uuid == camera.uuid) || 
                (camera.macAddress != null && it.mac == camera.macAddress)
            }

            if (matchingDevice != null && matchingDevice.ip != camera.ipAddress) {
                val oldIp = camera.ipAddress
                val newIp = matchingDevice.ip
                
                Log.w(TAG, "⚠️ IP CHANGE DETECTED for ${camera.name} ($oldIp -> $newIp)")
                
                val updatedCamera = camera.copy(
                    ipAddress = newIp,
                    mainStreamUrl = camera.mainStreamUrl.replace(oldIp, newIp),
                    subStreamUrl = camera.subStreamUrl.replace(oldIp, newIp)
                )
                
                repository.update(updatedCamera)
                updateCount++

                mainHandler.post {
                    Toast.makeText(context, "IP Recovered for ${camera.name}: $newIp", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        if (updateCount > 0) {
            Log.i(TAG, "✅ IP Recovery complete. Updated $updateCount cameras.")
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        Log.i(TAG, "🛑 Network Tracking Service Stopped.")
    }
}
