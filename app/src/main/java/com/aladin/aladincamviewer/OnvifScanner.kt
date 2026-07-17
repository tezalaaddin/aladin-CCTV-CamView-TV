package com.aladin.aladincamviewer

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import java.util.regex.Pattern

/**
 * Discovers ONVIF devices and detects their brand.
 */
class OnvifScanner(private val context: Context) {

    private val DISCOVERY_ADDRESS = "239.255.255.250"
    private val DISCOVERY_PORT = 3702
    private val TIMEOUT = 3000

    data class DiscoveredDevice(val ip: String, val brand: String, val uuid: String = "")

    private fun extractUuidFromResponse(response: String): String {
        val pattern = Pattern.compile("<Address>(uuid:[^<]+)</Address>")
        val matcher = pattern.matcher(response)
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }

    private val PROBE_TEMPLATE = """
        <?xml version="1.0" encoding="utf-8"?>
        <Envelope xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns="http://www.w3.org/2003/05/soap-envelope">
            <Header>
                <MessageID xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">uuid:${UUID.randomUUID()}</MessageID>
                <To xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">urn:schemas-xmlsoap-org:ws:2005:04:discovery</To>
                <Action xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</Action>
            </Header>
            <Body>
                <Probe xmlns="http://schemas.xmlsoap.org/ws/2005/04/discovery">
                    <Types>tds:Device</Types>
                </Probe>
            </Body>
        </Envelope>
    """.trimIndent()

    suspend fun discoverDevices(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val discoveredDevices = mutableMapOf<String, DiscoveredDevice>()
        var socket: DatagramSocket? = null
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("onvif_discovery")

        try {
            lock.acquire()
            socket = DatagramSocket()
            socket.soTimeout = TIMEOUT
            val address = InetAddress.getByName(DISCOVERY_ADDRESS)
            val probeData = PROBE_TEMPLATE.toByteArray()
            val packet = DatagramPacket(probeData, probeData.size, address, DISCOVERY_PORT)

            socket.send(packet)

            val receiveData = ByteArray(8192)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < TIMEOUT) {
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val ip = extractIpFromResponse(response)
                    if (ip != null) {
                        val brand = detectBrand(response)
                        val uuid = extractUuidFromResponse(response)
                        discoveredDevices[ip] = DiscoveredDevice(ip, brand, uuid)
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket?.close()
            if (lock.isHeld) lock.release()
        }

        discoveredDevices.values.toList()
    }

    private fun extractIpFromResponse(response: String): String? {
        val pattern = Pattern.compile("http://(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})")
        val matcher = pattern.matcher(response)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun detectBrand(response: String): String {
        val r = response.lowercase()
        return when {
            r.contains("hikvision") -> "Hikvision"
            r.contains("dahua") || r.contains("ipc-") -> "Dahua"
            r.contains("tiandy") -> "Tiandy"
            r.contains("ajcloud") || r.contains("uniview") -> "AJCloud"
            else -> "Custom"
        }
    }
}
