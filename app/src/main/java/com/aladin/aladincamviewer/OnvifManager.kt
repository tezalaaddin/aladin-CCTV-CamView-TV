package com.aladin.aladincamviewer

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Professional ONVIF Manager for authenticated device discovery and repair.
 * Optimized for Redline/Dahua H.265 fix and Multi-channel NVRs.
 */
class OnvifManager(private val ip: String, private val user: String, private val pass: String) {

    private val TAG = "ALADIN_DEBUG_ONVIF"
    private var serviceUrl = ""
    private var mediaUrl = ""

    data class OnvifDeviceDetails(
        val manufacturer: String?,
        val model: String?,
        val firmware: String?,
        val serial: String?,
        val uuid: String? = null,
        val mainStreamUrl: String?,
        val subStreamUrl: String?,
        val ptzSupported: Boolean,
        val allChannels: List<OnvifChannel> = emptyList()
    )

    data class OnvifChannel(
        val channelNumber: Int,
        val name: String,
        val mainUrl: String,
        val subUrl: String
    )

    suspend fun getDeviceDetails(): OnvifDeviceDetails? = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 Starting ONVIF Deep Discovery for IP: $ip")
        try {
            val ports = listOf(80, 8080, 28080, 8899, 8000, 5000, 8008, 8888)
            val paths = listOf("/onvif/device_service", "/device_service", "/onvif/device")
            
            var infoRes: String? = null
            val infoSoap = createSoapEnvelope("<tds:GetDeviceInformation xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\"/>")

            outer@for (port in ports) {
                for (path in paths) {
                    val url = "http://$ip:$port$path"
                    val res = sendSoapRequest(url, infoSoap)
                    if (res != null && (res.contains("Manufacturer") || res.contains("Model"))) {
                        infoRes = res
                        serviceUrl = url
                        break@outer
                    }
                }
            }

            if (infoRes == null) return@withContext null

            val manufacturer = extractXmlTag(infoRes, "Manufacturer")
            val model = extractXmlTag(infoRes, "Model")
            val firmware = extractXmlTag(infoRes, "FirmwareVersion")
            val serial = extractXmlTag(infoRes, "SerialNumber")

            val capSoap = createSoapEnvelope("<tds:GetCapabilities xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\"><tds:Category>All</tds:Category></tds:GetCapabilities>")
            val capRes = sendSoapRequest(serviceUrl, capSoap)
            mediaUrl = extractXAddr(capRes, "Media") ?: serviceUrl
            val ptzUrl = extractXAddr(capRes, "PTZ")

            val profilesSoap = createSoapEnvelope("<trt:GetProfiles xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"/>")
            val profilesRes = sendSoapRequest(mediaUrl, profilesSoap)
            val profiles = extractProfiles(profilesRes)

            val resolvedProfiles = profiles.mapNotNull { profile ->
                getStreamUri(profile.token)?.let { profile to it }
            }.sortedByDescending { it.first.pixels }
            val mainUrl = resolvedProfiles.firstOrNull()?.second
            val subUrl = resolvedProfiles.lastOrNull()?.second
            val channels = resolvedProfiles.mapIndexed { index, entry ->
                OnvifChannel(index + 1, entry.first.name.ifBlank { "Profile ${index + 1}" }, entry.second, entry.second)
            }

            return@withContext OnvifDeviceDetails(
                manufacturer = manufacturer?.trim(),
                model = model?.trim(),
                firmware = firmware?.trim(),
                serial = serial?.trim(),
                mainStreamUrl = mainUrl,
                subStreamUrl = subUrl,
                ptzSupported = ptzUrl != null,
                allChannels = channels
            )

        } catch (e: Exception) {
            Log.e(TAG, "ONVIF Discovery Error", e)
            null
        }
    }

    suspend fun standardizeEncoderSettings(): Boolean = withContext(Dispatchers.IO) {
        Log.w(TAG, "🚀 Standardizing ALL profiles on $ip")
        try {
            if (serviceUrl.isEmpty()) {
                val details = getDeviceDetails()
                if (details == null) {
                    Log.e(TAG, "Standardization failed: Could not get device details.")
                    return@withContext false
                }
            }

            val getConfigsSoap = createSoapEnvelope("<trt:GetVideoEncoderConfigurations xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"/>")
            val configsRes = sendSoapRequest(mediaUrl, getConfigsSoap) ?: return@withContext false
            
            // Extract tokens and their encodings
            val configPattern = Pattern.compile("<trt:Configurations token=['\"]([^'\"]+)['\"]>.*?<tt:Encoding>([^<]+)</tt:Encoding>", Pattern.DOTALL)
            val matcher = configPattern.matcher(configsRes)
            
            var totalSuccess = 0
            while (matcher.find()) {
                val token = matcher.group(1) ?: continue
                val encoding = matcher.group(2) ?: "H264"
                
                Log.d(TAG, "🔧 Standardizing token: $token (Encoding: $encoding)")
                
                val codecXml = if (encoding.contains("H265")) {
                    "<tt:H265><tt:GovLength>25</tt:GovLength><tt:H265Profile>Main</tt:H265Profile></tt:H265>"
                } else {
                    "<tt:H264><tt:GovLength>25</tt:GovLength><tt:H264Profile>Main</tt:H264Profile></tt:H264>"
                }

                val setConfigSoap = createSoapEnvelope("""
                    <trt:SetVideoEncoderConfiguration xmlns:trt="http://www.onvif.org/ver10/media/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">
                        <trt:Configuration token="$token">
                            <tt:Encoding>$encoding</tt:Encoding>
                            <tt:Quality>4</tt:Quality>
                            <tt:RateControl>
                                <tt:FrameRateLimit>25</tt:FrameRateLimit>
                                <tt:EncodingInterval>1</tt:EncodingInterval>
                                <tt:BitrateLimit>4096</tt:BitrateLimit>
                            </tt:RateControl>
                            $codecXml
                        </trt:Configuration>
                        <trt:ForcePersistence>true</trt:ForcePersistence>
                    </trt:SetVideoEncoderConfiguration>
                """.trimIndent())
                
                val res = sendSoapRequest(mediaUrl, setConfigSoap)
                if (res != null && res.contains("SetVideoEncoderConfigurationResponse")) totalSuccess++
            }
            return@withContext totalSuccess > 0
        } catch (e: Exception) { 
            Log.e(TAG, "Standardization failed", e)
            false 
        }
    }

    private suspend fun getStreamUri(token: String): String? {
        val soap = createSoapEnvelope("""
            <trt:GetStreamUri xmlns:trt="http://www.onvif.org/ver10/media/wsdl">
                <trt:StreamSetup>
                    <tt:Stream xmlns:tt="http://www.onvif.org/ver10/schema">RTP-Unicast</tt:Stream>
                    <tt:Transport xmlns:tt="http://www.onvif.org/ver10/schema"><tt:Protocol>RTSP</tt:Protocol></tt:Transport>
                </trt:StreamSetup>
                <trt:ProfileToken>$token</trt:ProfileToken>
            </trt:GetStreamUri>
        """.trimIndent())
        val res = sendSoapRequest(mediaUrl, soap)
        return extractXmlTag(res, "Uri")
    }

    private fun sendSoapRequest(urlStr: String, soap: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 4000
            conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
            OutputStreamWriter(conn.outputStream).use { it.write(soap) }
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: Exception) { null }
    }

    private fun extractXmlTag(xml: String?, tagName: String): String? {
        if (xml == null) return null
        val pattern = Pattern.compile("<(?:[a-zA-Z0-9]+:)?$tagName>(.*?)</(?:[a-zA-Z0-9]+:)?$tagName>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractXAddr(xml: String?, type: String): String? {
        if (xml == null) return null
        val pattern = Pattern.compile("<(?:[a-zA-Z0-9]+:)?$type>.*?<(?:[a-zA-Z0-9]+:)?XAddr>(.*?)</(?:[a-zA-Z0-9]+:)?XAddr>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) matcher.group(1) else null
    }

    private data class ProfileInfo(val token: String, val name: String, val pixels: Long)

    private fun extractProfiles(xml: String?): List<ProfileInfo> {
        if (xml == null) return emptyList()
        val profiles = mutableListOf<ProfileInfo>()
        val pattern = Pattern.compile(
            "<(?:[a-zA-Z0-9]+:)?Profiles\\b[^>]*token=['\"]([^'\"]+)['\"][^>]*>(.*?)</(?:[a-zA-Z0-9]+:)?Profiles>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val matcher = pattern.matcher(xml)
        while (matcher.find()) {
            val token = matcher.group(1) ?: continue
            val body = matcher.group(2).orEmpty()
            val name = extractXmlTag(body, "Name").orEmpty()
            val width = extractXmlTag(body, "Width")?.toLongOrNull() ?: 0
            val height = extractXmlTag(body, "Height")?.toLongOrNull() ?: 0
            if (profiles.none { it.token == token }) profiles.add(ProfileInfo(token, name, width * height))
        }
        return profiles
    }

    private fun createSoapEnvelope(body: String): String {
        val nonce = generateNonce()
        val created = getUtcNow()
        val digest = generatePasswordDigest(pass, nonce, created)
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                <s:Header>
                    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                        <wsse:UsernameToken>
                            <wsse:Username>$user</wsse:Username>
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

    private fun generateNonce(): String = Base64.encodeToString(ByteArray(16).apply { SecureRandom().nextBytes(this) }, Base64.NO_WRAP)
    private fun getUtcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
    private fun generatePasswordDigest(password: String, nonceBase64: String, created: String): String {
        return try {
            val nonce = Base64.decode(nonceBase64, Base64.DEFAULT)
            val combined = nonce + created.toByteArray() + password.toByteArray()
            Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest(combined), Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }
}
