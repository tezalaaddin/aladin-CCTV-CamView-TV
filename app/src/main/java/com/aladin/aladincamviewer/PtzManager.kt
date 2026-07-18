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
 * Advanced ONVIF PTZ Manager.
 * Supports 8-way movement and optical zoom.
 */
class PtzManager(private val camera: CameraModel) {

    private val TAG = "PtzManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var cachedProfileToken: String? = null
    private var cachedPtzUri: String? = null
    private var actualPort: Int? = null

    // 8-Way Movement
    fun moveUp() = executePtz { token -> getContinuousMoveEnvelope(token, 0.0f, 1.0f) }
    fun moveDown() = executePtz { token -> getContinuousMoveEnvelope(token, 0.0f, -1.0f) }
    fun moveLeft() = executePtz { token -> getContinuousMoveEnvelope(token, -1.0f, 0.0f) }
    fun moveRight() = executePtz { token -> getContinuousMoveEnvelope(token, 1.0f, 0.0f) }
    fun moveUpLeft() = executePtz { token -> getContinuousMoveEnvelope(token, -1.0f, 1.0f) }
    fun moveUpRight() = executePtz { token -> getContinuousMoveEnvelope(token, 1.0f, 1.0f) }
    fun moveDownLeft() = executePtz { token -> getContinuousMoveEnvelope(token, -1.0f, -1.0f) }
    fun moveDownRight() = executePtz { token -> getContinuousMoveEnvelope(token, 1.0f, -1.0f) }

    // Zoom
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
                if (cachedProfileToken == null) discoverServicesAndToken()
                val token = cachedProfileToken ?: return@launch
                val ptzUri = cachedPtzUri ?: "/onvif/ptz_service"
                executeSoapRequest(createSoapEnvelope(envelopeProvider(token)), ptzUri)
            } catch (e: Exception) {
                Log.e(TAG, "PTZ Error", e)
            }
        }
    }

    private suspend fun discoverServicesAndToken() = withContext(Dispatchers.IO) {
        val baseIp = camera.ipAddress.substringBefore(":")
        val ports = listOf(80, 8899, 8000, 8080)
        val paths = listOf("/onvif/device_service", "/device_service")

        outer@for (p in ports) {
            for (path in paths) {
                try {
                    val capSoap = createSoapEnvelope("<tds:GetCapabilities xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\"><tds:Category>All</tds:Category></tds:GetCapabilities>")
                    val res = rawSoapRequest("http://$baseIp:$p$path", capSoap)
                    if (res != null) {
                        actualPort = p
                        cachedPtzUri = extractXAddr(res, "PTZ")
                        val mediaUri = extractXAddr(res, "Media")
                        
                        if (mediaUri != null) {
                            val getProfilesSoap = createSoapEnvelope("<trt:GetProfiles xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"/>")
                            val profRes = rawSoapRequest(mediaUri, getProfilesSoap)
                            if (profRes != null) {
                                val m = Pattern.compile("token=\"([^\"]+)\"").matcher(profRes)
                                if (m.find()) cachedProfileToken = m.group(1)
                            }
                        }
                        break@outer
                    }
                } catch (e: Exception) { continue }
            }
        }
    }

    private fun extractXAddr(xml: String, type: String): String? {
        val m = Pattern.compile("<tt:$type>.*?<tt:XAddr>(.*?)</tt:XAddr>", Pattern.DOTALL).matcher(xml)
        return if (m.find()) m.group(1) else null
    }

    private fun rawSoapRequest(urlStr: String, soap: String): String? {
        return try {
            val url = URL(if (urlStr.startsWith("http")) urlStr else "http://${camera.ipAddress.substringBefore(":")}:$actualPort$urlStr")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
            OutputStreamWriter(conn.outputStream).use { it.write(soap) }
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: Exception) { null }
    }

    private fun executeSoapRequest(soap: String, uri: String) = rawSoapRequest(uri, soap)

    private fun getContinuousMoveEnvelope(token: String, x: Float, y: Float) = """
        <tptz:ContinuousMove xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">
            <tptz:ProfileToken>$token</tptz:ProfileToken>
            <tptz:Velocity><tt:PanTilt x="$x" y="$y"/></tptz:Velocity>
        </tptz:ContinuousMove>
    """.trimIndent()

    private fun getContinuousZoomEnvelope(token: String, z: Float) = """
        <tptz:ContinuousMove xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">
            <tptz:ProfileToken>$token</tptz:ProfileToken>
            <tptz:Velocity><tt:Zoom x="$z"/></tptz:Velocity>
        </tptz:ContinuousMove>
    """.trimIndent()

    private fun createSoapEnvelope(body: String): String {
        val nonce = Base64.encodeToString(ByteArray(16).also { Random().nextBytes(it) }, Base64.NO_WRAP)
        val created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val digest = try {
            val combined = Base64.decode(nonce, Base64.DEFAULT) + created.toByteArray() + camera.password.toByteArray()
            Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest(combined), Base64.NO_WRAP)
        } catch (e: Exception) { "" }
        
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
                <s:Body>$body</s:Body>
            </s:Envelope>
        """.trimIndent()
    }
}
