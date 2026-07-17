package com.aladin.aladincamviewer

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Advanced ONVIF PTZ Manager with Dynamic Profile and Port Discovery.
 * Works with Tiandy, AJCloud, Hikvision, and Dahua.
 */
class PtzManager(private val camera: CameraModel) {

    private val TAG = "PtzManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var cachedProfileToken: String? = null
    private var cachedMediaUri: String? = null
    private var cachedPtzUri: String? = null
    private var actualPort: Int? = null

    fun moveUp() = executePtz { token -> getContinuousMoveEnvelope(token, 0.0f, 1.0f) }
    fun moveDown() = executePtz { token -> getContinuousMoveEnvelope(token, 0.0f, -1.0f) }
    fun moveLeft() = executePtz { token -> getContinuousMoveEnvelope(token, -1.0f, 0.0f) }
    fun moveRight() = executePtz { token -> getContinuousMoveEnvelope(token, 1.0f, 0.0f) }
    fun zoomIn() = executePtz { token -> getContinuousZoomEnvelope(token, 1.0f) }
    fun zoomOut() = executePtz { token -> getContinuousZoomEnvelope(token, -1.0f) }

    fun stop() {
        executePtz { token ->
            """
                <tptz:Stop xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl">
                    <tptz:ProfileToken>$token</tptz:ProfileToken>
                    <tptz:PanTilt>true</tptz:PanTilt>
                    <tptz:Zoom>true</tptz:Zoom>
                </tptz:Stop>
            """.trimIndent()
        }
    }

    private fun executePtz(envelopeProvider: (String) -> String) {
        scope.launch {
            try {
                if (cachedProfileToken == null) {
                    discoverServicesAndToken()
                }
                
                val token = cachedProfileToken ?: return@launch
                val ptzUri = cachedPtzUri ?: "/onvif/ptz_service"
                val body = envelopeProvider(token)
                
                executeSoapRequest(createSoapEnvelope(body), ptzUri)
            } catch (e: Exception) {
                Log.e(TAG, "PTZ execution failed", e)
            }
        }
    }

    private suspend fun discoverServicesAndToken() = withContext(Dispatchers.IO) {
        // If IP already contains a port, don't probe
        val ports = if (camera.ipAddress.contains(":")) emptyList() else listOf(80, 8899, 8000, 8080)
        val baseIp = camera.ipAddress.substringBefore(":")
        
        // 1. Try to find the ONVIF port and GetCapabilities
        val paths = listOf("/onvif/device_service", "/device_service", "/onvif/device", "/onvif/media_service")
        
        var foundRes: String? = null
        var workingPort: Int? = null
        var workingPath: String? = null

        val testPorts = if (ports.isEmpty()) listOf(-1) else ports
        
        outer@for (p in testPorts) {
            val host = if (p == -1) camera.ipAddress else "$baseIp:$p"
            for (path in paths) {
                try {
                    val capSoap = createSoapEnvelope("<tds:GetCapabilities xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\"><tds:Category>All</tds:Category></tds:GetCapabilities>")
                    val res = rawSoapRequest("http://$host$path", capSoap)
                    if (res != null) {
                        foundRes = res
                        workingPort = if (p == -1) null else p
                        workingPath = path
                        actualPort = workingPort
                        Log.d(TAG, "Discovered working ONVIF endpoint: http://$host$path")
                        break@outer
                    }
                } catch (e: Exception) { continue }
            }
        }

        if (foundRes == null) return@withContext

        // 2. Extract Media and PTZ XAddrs
        cachedMediaUri = extractXAddr(foundRes, "Media") ?: workingPath
        cachedPtzUri = extractXAddr(foundRes, "PTZ") ?: workingPath
        
        Log.d(TAG, "Discovered Media Service: $cachedMediaUri")
        Log.d(TAG, "Discovered PTZ Service: $cachedPtzUri")

        // 3. Get Profiles to find token
        val getProfilesSoap = createSoapEnvelope("<trt:GetProfiles xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"/>")
        val profilesRes = rawSoapRequest(cachedMediaUri!!, getProfilesSoap)
        if (profilesRes != null) {
            val pattern = Pattern.compile("token=\"([^\"]+)\"")
            val matcher = pattern.matcher(profilesRes)
            if (matcher.find()) {
                cachedProfileToken = matcher.group(1)
                Log.d(TAG, "Discovered Profile Token: $cachedProfileToken")
            }
        }
    }

    private fun extractXAddr(xml: String, type: String): String? {
        val pattern = Pattern.compile("<tt:$type>.*?<tt:XAddr>(.*?)</tt:XAddr>", Pattern.DOTALL)
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun rawSoapRequest(urlStr: String, soap: String): String? {
        try {
            val url = if (urlStr.startsWith("http")) URL(urlStr) else {
                val host = if (actualPort != null && !camera.ipAddress.contains(":")) {
                    "${camera.ipAddress.substringBefore(":")}:$actualPort"
                } else {
                    camera.ipAddress
                }
                URL("http://$host$urlStr")
            }
            
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
            OutputStreamWriter(conn.outputStream).use { it.write(soap) }
            
            if (conn.responseCode == 200) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) { 
            Log.w(TAG, "SOAP request failed for $urlStr: ${e.message}")
        }
        return null
    }

    private fun executeSoapRequest(soap: String, uri: String): String? {
        return rawSoapRequest(uri, soap)
    }

    private fun getContinuousMoveEnvelope(token: String, x: Float, y: Float) = """
        <tptz:ContinuousMove xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">
            <tptz:ProfileToken>$token</tptz:ProfileToken>
            <tptz:Velocity>
                <tt:PanTilt x="$x" y="$y"/>
            </tptz:Velocity>
        </tptz:ContinuousMove>
    """.trimIndent()

    private fun getContinuousZoomEnvelope(token: String, z: Float) = """
        <tptz:ContinuousMove xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">
            <tptz:ProfileToken>$token</tptz:ProfileToken>
            <tptz:Velocity>
                <tt:Zoom x="$z"/>
            </tptz:Velocity>
        </tptz:ContinuousMove>
    """.trimIndent()

    private fun createSoapEnvelope(body: String): String {
        val nonce = generateNonce()
        val created = getUtcNow()
        val digest = generatePasswordDigest(camera.password, nonce, created)
        
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                <s:Header>
                    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                        <wsse:UsernameToken>
                            <wsse:Username>${camera.username}</wsse:Username>
                            <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">$digest</wsse:Password>
                            <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">$nonce</wsse:Nonce>
                            <wsu:Created>$created</wsu:Created>
                        </wsse:UsernameToken>
                    </wsse:Security>
                </s:Header>
                <s:Body>
                    $body
                </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    private fun generateNonce(): String {
        val b = ByteArray(16)
        Random().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    private fun getUtcNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun generatePasswordDigest(password: String, nonceBase64: String, created: String): String {
        return try {
            val nonce = Base64.decode(nonceBase64, Base64.DEFAULT)
            val combined = nonce + created.toByteArray() + password.toByteArray()
            Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest(combined), Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }
}
