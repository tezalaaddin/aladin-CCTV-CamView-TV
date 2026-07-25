package com.aladin.aladincamviewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Recovers DHCP-changed camera addresses using persistent identity first, RTSP proof second. */
class NetworkTracker private constructor(
    private val context: Context,
    private val repository: CameraRepository
) {
    companion object {
        private const val TAG = "ALADIN_NETWORK_TRACKER"
        private const val SCAN_INTERVAL_MINUTES = 15L

        @Volatile private var instance: NetworkTracker? = null

        fun getInstance(context: Context, repository: CameraRepository): NetworkTracker =
            instance ?: synchronized(this) {
                instance ?: NetworkTracker(context.applicationContext, repository).also { instance = it }
            }
    }

    private val hybridScanner = HybridScanner(context)
    private val streamVerifier = RtspStreamVerifier()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scanMutex = Mutex()
    private var trackingJob: Job? = null

    fun startTracking() {
        if (trackingJob?.isActive == true) return
        Log.i(TAG, "Network tracking started intervalMinutes=$SCAN_INTERVAL_MINUTES")
        trackingJob = scope.launch {
            while (isActive) {
                runCatching { performIpRecoveryScan() }
                    .onFailure { Log.e(TAG, "IP recovery scan failed", it) }
                delay(TimeUnit.MINUTES.toMillis(SCAN_INTERVAL_MINUTES))
            }
        }
    }

    fun triggerImmediateScan() {
        scope.launch {
            mainHandler.post {
                Toast.makeText(context, "Kamera IP adresleri kontrol ediliyor…", Toast.LENGTH_SHORT).show()
            }
            performIpRecoveryScan()
        }
    }

    private suspend fun performIpRecoveryScan() = scanMutex.withLock {
        Log.i(TAG, "IP recovery scan started")
        val discovered = hybridScanner.startFullScan { partial ->
            Log.d(TAG, "Discovery progress devices=${partial.size}")
        }
        logDiscoveredDevices(discovered)
        processDiscoveredDevices(discovered)
    }

    private suspend fun processDiscoveredDevices(discovered: List<DiscoveryDevice>) {
        val savedCameras = repository.allCameras.first().sortedBy { it.displayOrder }
        if (savedCameras.isEmpty()) {
            Log.d(TAG, "IP recovery skipped: no saved cameras")
            return
        }

        val claimedIps = mutableSetOf<String>()
        val resolvedCameraIds = mutableSetOf<Int>()
        var identityEnrichments = 0
        var ipUpdates = 0

        // Existing IP association is the safest opportunity to backfill UUID/MAC
        // for cameras created before identity persistence was added.
        savedCameras.forEach { camera ->
            val sameIp = discovered.find { it.ip == camera.ipAddress.substringBefore(":") } ?: return@forEach
            claimedIps += sameIp.ip
            resolvedCameraIds += camera.id
            val enriched = enrichIdentity(camera, sameIp)
            if (enriched != camera) {
                repository.update(enriched)
                identityEnrichments++
                Log.i(TAG, "Camera identity learned name=${camera.name} ip=${sameIp.ip} uuid=${sameIp.uuid != null} mac=${sameIp.mac != null}")
            }
        }

        // UUID/MAC matches remain authoritative even when the address changed.
        savedCameras.filterNot { it.id in resolvedCameraIds }.forEach { camera ->
            val match = discovered.firstOrNull { it.ip !in claimedIps && CameraIdentityMatcher.strongMatch(camera, it) }
                ?: return@forEach
            updateCameraAddress(camera, match, "persistent_identity")
            claimedIps += match.ip
            resolvedCameraIds += camera.id
            ipUpdates++
        }

        // Legacy records may have neither UUID nor MAC. Never update from brand or
        // port alone: rewrite the saved stream URL and require LibVLC to reach Playing.
        savedCameras.filterNot { it.id in resolvedCameraIds }.forEach { camera ->
            val candidates = discovered.filter { device ->
                device.ip !in claimedIps &&
                    ("RTSP" in device.protocols || "ONVIF" in device.protocols) &&
                    CameraIdentityMatcher.isBrandCompatible(camera.brand, device.brand)
            }
            Log.i(TAG, "Legacy recovery candidates camera=${camera.name} oldIp=${camera.ipAddress} candidates=${candidates.joinToString { "${it.ip}/${it.brand}" }}")

            val verified = mutableListOf<DiscoveryDevice>()
            for (candidate in candidates) {
                val probeUrl = rewriteHost(
                    camera.subStreamUrl.ifBlank { camera.mainStreamUrl },
                    camera.ipAddress.substringBefore(":"),
                    candidate.ip
                )
                if (streamVerifier.canPlay(probeUrl)) {
                    verified += candidate
                    if (verified.size > 1) break
                }
            }

            when (verified.size) {
                1 -> {
                    val match = verified.single()
                    updateCameraAddress(camera, match, "verified_rtsp_legacy")
                    claimedIps += match.ip
                    resolvedCameraIds += camera.id
                    ipUpdates++
                }
                0 -> Log.w(TAG, "Legacy recovery found no playable candidate camera=${camera.name}")
                else -> Log.w(TAG, "Legacy recovery ambiguous camera=${camera.name} playableCandidates=${verified.joinToString { it.ip }}")
            }
        }

        Log.i(TAG, "IP recovery completed saved=${savedCameras.size} discovered=${discovered.size} identityEnrichments=$identityEnrichments ipUpdates=$ipUpdates")
    }

    private suspend fun updateCameraAddress(camera: CameraEntity, device: DiscoveryDevice, reason: String) {
        val oldHost = camera.ipAddress.substringBefore(":")
        val updated = enrichIdentity(camera, device).copy(
            ipAddress = device.ip,
            mainStreamUrl = rewriteHost(camera.mainStreamUrl, oldHost, device.ip),
            subStreamUrl = rewriteHost(camera.subStreamUrl, oldHost, device.ip)
        )
        repository.update(updated)
        Log.w(TAG, "Camera IP updated name=${camera.name} oldIp=$oldHost newIp=${device.ip} reason=$reason")
        mainHandler.post {
            Toast.makeText(context, "${camera.name}: $oldHost → ${device.ip}", Toast.LENGTH_LONG).show()
        }
    }

    private fun enrichIdentity(camera: CameraEntity, device: DiscoveryDevice): CameraEntity = camera.copy(
        uuid = camera.uuid.ifBlank { device.uuid.orEmpty() },
        macAddress = camera.macAddress ?: device.mac
    )

    private fun rewriteHost(url: String, oldHost: String, newHost: String): String =
        url.replace("@$oldHost", "@$newHost").replace("://$oldHost", "://$newHost")

    private fun logDiscoveredDevices(devices: List<DiscoveryDevice>) {
        devices.forEach { device ->
            Log.d(
                TAG,
                "Discovered ip=${device.ip} brand=${device.brand} model=${device.model} uuid=${device.uuid} mac=${device.mac} protocols=${device.protocols.sorted().joinToString()}"
            )
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        Log.i(TAG, "Network tracking stopped")
    }
}
