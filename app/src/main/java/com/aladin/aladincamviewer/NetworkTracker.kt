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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

data class RecoveryProposal(
    val cameraId: Int,
    val cameraName: String,
    val oldIp: String,
    val newIp: String,
    val uuid: String?,
    val macAddress: String?,
    val brand: String
)

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
    private val proposalChannel = Channel<RecoveryProposal>(Channel.BUFFERED)
    private val pendingProposalKeys = ConcurrentHashMap.newKeySet<String>()
    private var trackingJob: Job? = null
    val recoveryProposals = proposalChannel.receiveAsFlow()

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

    fun confirmRecovery(proposal: RecoveryProposal) {
        scope.launch {
            val key = proposalKey(proposal.cameraId, proposal.newIp)
            try {
                val camera = repository.getCameraById(proposal.cameraId) ?: return@launch
                if (repository.isIpAlreadyUsed(proposal.newIp, camera.id)) {
                    Log.w(TAG, "Recovery confirmation rejected: IP already used newIp=${proposal.newIp}")
                    return@launch
                }
                val probeUrl = rewriteHost(
                    camera.subStreamUrl.ifBlank { camera.mainStreamUrl },
                    camera.ipAddress.substringBefore(":"),
                    proposal.newIp
                )
                if (!streamVerifier.canPlay(probeUrl)) {
                    Log.w(TAG, "Recovery confirmation failed revalidation camera=${camera.name} newIp=${proposal.newIp}")
                    return@launch
                }
                updateCameraAddress(
                    camera,
                    DiscoveryDevice(
                        ip = proposal.newIp,
                        uuid = proposal.uuid,
                        mac = proposal.macAddress,
                        brand = proposal.brand
                    ),
                    "user_confirmed_legacy"
                )
            } finally {
                pendingProposalKeys.remove(key)
            }
        }
    }

    fun rejectRecovery(proposal: RecoveryProposal) {
        scope.launch {
            pendingProposalKeys.remove(proposalKey(proposal.cameraId, proposal.newIp))
            Log.i(TAG, "Recovery proposal rejected camera=${proposal.cameraName} newIp=${proposal.newIp}")
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
            val sameIp = discovered.find {
                it.ip == camera.ipAddress.substringBefore(":") &&
                    ("RTSP" in it.protocols || "ONVIF" in it.protocols)
            } ?: return@forEach
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

        // RTSP proves compatibility, not physical identity. A verified legacy match
        // is therefore proposed to the user and never applied automatically.
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
                    claimedIps += match.ip
                    val proposal = RecoveryProposal(
                        cameraId = camera.id,
                        cameraName = camera.name,
                        oldIp = camera.ipAddress.substringBefore(":"),
                        newIp = match.ip,
                        uuid = match.uuid,
                        macAddress = match.mac,
                        brand = match.brand
                    )
                    val key = proposalKey(proposal.cameraId, proposal.newIp)
                    if (pendingProposalKeys.add(key)) {
                        proposalChannel.trySend(proposal)
                        Log.w(TAG, "Legacy recovery requires confirmation camera=${camera.name} oldIp=${proposal.oldIp} candidateIp=${proposal.newIp}")
                    }
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
        uuid = camera.uuid.takeIf(CameraIdentityMatcher::isValidUuid)
            ?: device.uuid.takeIf(CameraIdentityMatcher::isValidUuid).orEmpty(),
        macAddress = camera.macAddress ?: device.mac
    )

    private fun rewriteHost(url: String, oldHost: String, newHost: String): String =
        url.replace("@$oldHost", "@$newHost").replace("://$oldHost", "://$newHost")

    private fun proposalKey(cameraId: Int, newIp: String) = "$cameraId@$newIp"

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
