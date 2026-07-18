package com.aladin.aladincamviewer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.*
import java.util.*
import java.util.regex.Pattern

class HybridScanner(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveredDevices = Collections.synchronizedMap(mutableMapOf<String, DiscoveryDevice>())
    private val scanSemaphore = Semaphore(20) // Limit concurrent network probes

    private val ONVIF_ADDRESS = "239.255.255.250"
    private val ONVIF_PORT = 3702

    private val SERVICE_TYPES = listOf(
        "_http._tcp.",
        "_rtsp._tcp.",
        "_onvif._tcp.",
        "_axis-video._tcp."
    )
    
    private val PROBE_TEMPLATE = """
        <?xml version="1.0" encoding="utf-8"?>
        <Envelope xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns="http://www.w3.org/2003/05/soap-envelope">
            <Header>
                <MessageID xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">uuid:${UUID.randomUUID()}</MessageID>
                <To xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">urn:schemas-xmlsoap-org:ws:2005:04:discovery</To>
                <Action xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</Action>
            </Header>
            <Body><Probe xmlns="http://schemas.xmlsoap.org/ws/2005/04/discovery"><Types>tds:Device</Types></Probe></Body>
        </Envelope>
    """.trimIndent()

    suspend fun startFullScan(callback: (List<DiscoveryDevice>) -> Unit) = coroutineScope {
        discoveredDevices.clear()
        
        val jobs = mutableListOf<Job>()
        jobs.add(launch { scanOnvif() })
        jobs.add(launch { scanArp() })
        
        // Start NSD for each service type
        SERVICE_TYPES.forEach { type ->
            jobs.add(launch { scanMdns(type) })
        }

        // Periodic updates to UI
        val updateJob = launch {
            while (isActive) {
                delay(1000)
                callback(discoveredDevices.values.toList())
            }
        }

        delay(8000) // Scan duration
        jobs.forEach { it.cancel() }
        updateJob.cancel()
        callback(discoveredDevices.values.toList())
    }

    private suspend fun scanOnvif() = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("onvif_hybrid")
        var socket: DatagramSocket? = null
        
        try {
            lock.acquire()
            socket = DatagramSocket()
            socket.soTimeout = 3000
            val address = InetAddress.getByName(ONVIF_ADDRESS)
            val packet = DatagramPacket(PROBE_TEMPLATE.toByteArray(), PROBE_TEMPLATE.length, address, ONVIF_PORT)
            
            repeat(2) {
                socket.send(packet)
                delay(500)
            }

            val buffer = ByteArray(8192)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 6000) {
                val receivePacket = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val ip = extractIp(response)
                    if (ip != null) {
                        val device = discoveredDevices.getOrPut(ip) { DiscoveryDevice(ip) }
                        device.protocols.add("ONVIF")
                        device.uuid = extractUuid(response)
                        device.brand = BrandMatcher.detectFromResponse(response) ?: device.brand
                    }
                } catch (e: Exception) {}
            }
        } finally {
            socket?.close()
            if (lock.isHeld) lock.release()
        }
    }

    private fun scanMdns(serviceType: String) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val ip = serviceInfo.host.hostAddress ?: return
                        val device = discoveredDevices.getOrPut(ip) { DiscoveryDevice(ip) }
                        device.protocols.add("mDNS")
                        
                        // Extract brand/model from TXT records if available
                        val txtRecords = serviceInfo.attributes
                        val modelInfo = txtRecords["md"]?.let { String(it) } 
                                       ?: txtRecords["model"]?.let { String(it) }
                                       ?: serviceInfo.serviceName
                                       
                        device.model = modelInfo
                        if (device.brand == "Generic") {
                            device.brand = BrandMatcher.detectFromResponse(modelInfo) ?: "Generic"
                        }
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private suspend fun scanArp() = withContext(Dispatchers.IO) {
        val prefix = getNetworkPrefix() ?: return@withContext
        (1..254).map { i ->
            launch {
                scanSemaphore.withPermit {
                    val host = "$prefix.$i"
                    try {
                        val inet = InetAddress.getByName(host)
                        if (inet.isReachable(800)) {
                            val device = discoveredDevices.getOrPut(host) { DiscoveryDevice(host) }
                            if (!device.protocols.contains("PING")) {
                                device.protocols.add("PING")
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        }.joinAll()
    }


    private fun getNetworkPrefix(): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wm.dhcpInfo ?: return null
        val ip = dhcp.ipAddress
        if (ip == 0) return null
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}"
    }

    private fun extractIp(response: String): String? {
        val matcher = Pattern.compile("http://(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})").matcher(response)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractUuid(response: String): String? {
        val matcher = Pattern.compile("<Address>(uuid:[^<]+)</Address>").matcher(response)
        return if (matcher.find()) matcher.group(1) else null
    }
}
