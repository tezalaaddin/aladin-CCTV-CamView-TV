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
import java.io.PrintWriter

class HybridScanner(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveredDevices = Collections.synchronizedMap(mutableMapOf<String, DiscoveryDevice>())
    private val scanSemaphore = Semaphore(25) // Increased concurrency

    private val ONVIF_ADDRESS = "239.255.255.250"
    private val ONVIF_PORT = 3702
    private val RTSP_PORT = 554
    private val HTTP_PORT = 80
    private val HTTP_ALT_PORT = 8000
    private val HTTPS_PORT = 443

    private val SERVICE_TYPES = listOf("_http._tcp.", "_rtsp._tcp.", "_onvif._tcp.", "_axis-video._tcp.")
    
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
        
        // Multi-layered scanning
        jobs.add(launch { scanOnvif() })
        jobs.add(launch { scanArp() })
        SERVICE_TYPES.forEach { type -> jobs.add(launch { scanMdns(type) }) }

        val updateJob = launch {
            while (isActive) {
                delay(1200)
                callback(getSortedDevices())
            }
        }

        delay(12000) // Slightly longer for thoroughness
        jobs.forEach { it.cancel() }
        updateJob.cancel()
        callback(getSortedDevices())
    }

    private fun getSortedDevices(): List<DiscoveryDevice> {
        return discoveredDevices.values.toList().sortedWith(compareByDescending<DiscoveryDevice> { 
            it.protocols.contains("ONVIF") || it.protocols.contains("RTSP")
        }.thenBy { it.ip })
    }

    private suspend fun scanOnvif() = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("onvif_hybrid")
        var socket: DatagramSocket? = null
        
        try {
            lock.acquire()
            socket = DatagramSocket()
            socket.soTimeout = 4000
            val address = InetAddress.getByName(ONVIF_ADDRESS)
            val packet = DatagramPacket(PROBE_TEMPLATE.toByteArray(), PROBE_TEMPLATE.length, address, ONVIF_PORT)
            
            repeat(4) { // More probes for reliability
                socket.send(packet)
                delay(1000)
            }

            val buffer = ByteArray(24576) // Larger buffer for complex XMLs
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 10000) {
                val receivePacket = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val senderIp = receivePacket.address.hostAddress ?: continue
                    if (senderIp.startsWith("239.") || senderIp.startsWith("127.")) continue

                    val device = discoveredDevices.getOrPut(senderIp) { DiscoveryDevice(senderIp) }
                    synchronized(device) {
                        device.protocols.add("ONVIF")
                        if (device.uuid == null) device.uuid = extractUuid(response)
                        parseOnvifPacket(response, device)
                    }
                    
                    CoroutineScope(Dispatchers.IO).launch { 
                        investigateDevice(device) 
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
                        
                        synchronized(device) {
                            device.protocols.add("mDNS")
                            val txtRecords = serviceInfo.attributes
                            val modelInfo = txtRecords["md"]?.let { String(it) } 
                                           ?: txtRecords["model"]?.let { String(it) }
                                           ?: serviceInfo.serviceName
                            
                            if (device.model == null) device.model = cleanText(modelInfo)
                            
                            val combined = (serviceInfo.serviceName + " " + modelInfo).lowercase()
                            val detectedBrand = BrandMatcher.detectFromResponse(combined)
                            if (detectedBrand != null && device.brand == "Generic") {
                                device.brand = detectedBrand
                            }
                        }
                        CoroutineScope(Dispatchers.IO).launch { investigateDevice(device) }
                    }
                })
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
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
                            if (!device.protocols.contains("PING")) device.protocols.add("PING")
                            investigateDevice(device)
                        }
                    } catch (e: Exception) {}
                }
            }
        }.joinAll()
    }

    /**
     * Deep Investigation: Grabs banners, checks ports, and refines brand.
     */
    private suspend fun investigateDevice(device: DiscoveryDevice) = coroutineScope {
        val portsToCheck = mapOf(
            RTSP_PORT to "RTSP", 
            HTTP_PORT to "HTTP", 
            HTTP_ALT_PORT to "HTTP-ALT", 
            HTTPS_PORT to "HTTPS",
            3702 to "ONVIF-UDP",
            8000 to "SDK-HIK",
            37777 to "SDK-DAHUA",
            34567 to "SDK-XMEYE",
            3002 to "SDK-TIANDY",
            9000 to "SDK-REOLINK",
            5000 to "SYNO"
        )

        portsToCheck.forEach { (port, name) ->
            launch(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(device.ip, port), 600)
                    
                    synchronized(device) { 
                        device.protocols.add(name)
                        
                        // Refine Brand based on Port Fingerprint
                        if (device.brand == "Generic" || device.brand == "Dahua") { 
                            val brandFromPort = BrandMatcher.detectFromPort(port)
                            if (brandFromPort != null) device.brand = brandFromPort
                        }
                    }
                    
                    // Try to get MAC from ARP table if we don't have it yet
                    if (device.mac == null) {
                        device.mac = getMacFromArpTable(device.ip)
                    }
                    
                    // Banner Grabbing for HTTP ports
                    if (port == HTTP_PORT || port == HTTP_ALT_PORT || port == HTTPS_PORT) {
                        fetchHttpBanner(device, port)
                    }
                    socket.close()
                } catch (e: Exception) {}
            }
        }

        // Refine Brand from MAC if still generic
        if (device.brand == "Generic" && device.mac != null) {
            val brandFromMac = BrandMatcher.getBrandByMac(device.mac)
            if (brandFromMac != "Generic") synchronized(device) { device.brand = brandFromMac }
        }
    }

    private fun fetchHttpBanner(device: DiscoveryDevice, port: Int) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(device.ip, port), 800)
            val writer = PrintWriter(socket.getOutputStream())
            writer.print("GET / HTTP/1.1\r\nHost: ${device.ip}\r\nConnection: close\r\n\r\n")
            writer.flush()

            val reader = socket.getInputStream().bufferedReader()
            var line: String?
            val headers = StringBuilder()
            var count = 0
            while (reader.readLine().also { line = it } != null && count < 15) {
                headers.append(line).append(" ")
                count++
            }
            socket.close()

            val headerText = headers.toString()
            val detectedBrand = BrandMatcher.detectFromResponse(headerText)
            
            synchronized(device) {
                if (detectedBrand != null && device.brand == "Generic") {
                    device.brand = detectedBrand
                }
                
                // Extract Server Header
                if (device.model == null) {
                    val serverPattern = Pattern.compile("Server: ([^\\r\\n]+)", Pattern.CASE_INSENSITIVE)
                    val matcher = serverPattern.matcher(headerText)
                    if (matcher.find()) {
                        val server = matcher.group(1)?.trim() ?: ""
                        if (server.isNotBlank() && !server.contains("Apache") && !server.contains("nginx")) {
                            device.model = server
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private fun getNetworkPrefix(): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wm.dhcpInfo ?: return null
        val ip = dhcp.ipAddress
        if (ip == 0) return null
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}"
    }

    private fun getMacFromArpTable(ip: String): String? {
        return try {
            val reader = java.io.BufferedReader(java.io.FileReader("/proc/net/arp"))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split("\\s+".toRegex())
                if (parts.size >= 4 && parts[0] == ip) {
                    val mac = parts[3].uppercase()
                    if (mac != "00:00:00:00:00:00") return mac
                }
            }
            reader.close()
            null
        } catch (e: Exception) { null }
    }

    private fun extractUuid(response: String): String? {
        // Robust UUID extraction handling various formats (uuid:..., urn:uuid:...)
        val matcher = Pattern.compile("<(?:[a-zA-Z0-9]+:)?Address>(urn:uuid:|uuid:)?([^<]+)</(?:[a-zA-Z0-9]+:)?Address>", Pattern.CASE_INSENSITIVE).matcher(response)
        return if (matcher.find()) {
            val prefix = matcher.group(1) ?: "uuid:"
            val value = matcher.group(2)
            if (prefix.contains("urn")) "uuid:$value" else "$prefix$value"
        } else null
    }

    private fun parseOnvifPacket(response: String, device: DiscoveryDevice) {
        val r = response
        
        // 1. MAC Extraction (Refined)
        val macRegex = Pattern.compile("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})")
        val macMatcher = macRegex.matcher(r)
        if (macMatcher.find()) {
            val foundMac = macMatcher.group(0)?.uppercase()?.replace("-", ":")
            if (foundMac != null && foundMac != "00:00:00:00:00:00") device.mac = foundMac
        }

        // 2. Scopes Parsing (Advanced)
        val scopeMatcher = Pattern.compile("<Scopes>([^<]+)</Scopes>", Pattern.CASE_INSENSITIVE).matcher(r)
        if (scopeMatcher.find()) {
            val scopes = scopeMatcher.group(1) ?: ""
            val parts = scopes.split("\\s+".toRegex())
            for (scope in parts) {
                try {
                    val decoded = URLDecoder.decode(scope, "UTF-8")
                    val cleanVal = cleanText(decoded.substringAfterLast("/"))
                    val lowerDecoded = decoded.lowercase()
                    
                    when {
                        lowerDecoded.contains("/name/") || lowerDecoded.contains("/brand/") -> 
                            if (cleanVal.isNotBlank() && device.brand == "Generic") device.brand = cleanVal
                        
                        lowerDecoded.contains("/hardware/") || lowerDecoded.contains("/model/") -> 
                            if (cleanVal.isNotBlank()) device.model = cleanVal
                            
                        lowerDecoded.contains("/mac/") -> {
                            val mac = cleanVal.replace("mac:", "", true).replace("-", ":")
                            if (mac.length >= 12 && device.mac == null) device.mac = mac.uppercase()
                        }
                    }
                } catch (e: Exception) {}
            }
        }
        
        // 3. Fallback Brand Detection from Full XML
        if (device.brand == "Generic") {
            val detected = BrandMatcher.detectFromResponse(r)
            if (detected != null) device.brand = detected
        }
        
        // 4. Model extraction from common tags
        if (device.model == null) {
            val modelPatterns = listOf("hardware/([^\\s<]+)", "model/([^\\s<]+)", "Device/([^\\s<]+)")
            for (p in modelPatterns) {
                val m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(r)
                if (m.find()) {
                    val found = cleanText(m.group(1))
                    if (found.length > 3) { device.model = found; break }
                }
            }
        }
    }

    private fun cleanText(text: String?): String {
        if (text == null) return ""
        return try {
            URLDecoder.decode(text, "UTF-8")
                .replace("%20", " ")
                .replace("_", " ")
                .trim()
                .uppercase()
        } catch (e: Exception) {
            text.replace("%20", " ").replace("_", " ").trim().uppercase()
        }
    }
}
