package com.aladin.aladincamviewer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.parsers.DocumentBuilderFactory

data class HikvisionDeviceInfo(val name: String, val model: String, val serialNumber: String)
data class HikvisionChannel(val number: Int, val name: String, val enabled: Boolean)
data class RecordingSegment(val start: Instant, val end: Instant, val playbackUri: String)

object HikvisionNvrProfile {
    fun streamId(channel: Int, subStream: Boolean): Int {
        require(channel > 0) { "Channel must be positive" }
        return channel * 100 + if (subStream) 2 else 1
    }

    fun knownCapacity(model: String): Int? = when {
        model.contains("DS-7616NI-Q1", true) -> 16
        model.contains("DS-7104NI-Q1", true) -> 4
        else -> null
    }
}

/** Minimal Hikvision ISAPI client. URLs and credentials are deliberately excluded from logs. */
class HikvisionIsapiClient(private val connectTimeoutMs: Int = 3500, private val readTimeoutMs: Int = 7000) {
    private val nonceCount = AtomicInteger()

    suspend fun deviceInfo(host: String, port: Int, username: String, password: String): HikvisionDeviceInfo =
        withContext(Dispatchers.IO) {
            val xml = request(host, port, username, password, "GET", "/ISAPI/System/deviceInfo")
            HikvisionDeviceInfo(
                name = tag(xml, "deviceName").ifBlank { "Hikvision NVR" },
                model = tag(xml, "model"),
                serialNumber = tag(xml, "serialNumber")
            )
        }

    suspend fun channels(host: String, port: Int, username: String, password: String): List<HikvisionChannel> =
        withContext(Dispatchers.IO) {
            val candidates = listOf("/ISAPI/ContentMgmt/InputProxy/channels", "/ISAPI/System/Video/inputs/channels")
            val xml = candidates.firstNotNullOfOrNull { path -> runCatching { request(host, port, username, password, "GET", path) }.getOrNull() }
                ?: throw IllegalStateException("NVR channel list could not be read")
            val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder()
                .parse(xml.byteInputStream())
            val nodes = doc.getElementsByTagName("InputProxyChannel")
                .takeIf { it.length > 0 } ?: doc.getElementsByTagName("VideoInputChannel")
            (0 until nodes.length).mapNotNull { index ->
                val element = nodes.item(index) as? org.w3c.dom.Element ?: return@mapNotNull null
                val id = child(element, "id").toIntOrNull() ?: return@mapNotNull null
                val name = child(element, "name").ifBlank { "Kanal $id" }
                val enabled = child(element, "enable").lowercase() != "false"
                HikvisionChannel(id, name, enabled)
            }.distinctBy { it.number }.sortedBy { it.number }
        }

    suspend fun recordings(
        recorder: RecorderEntity,
        channel: Int,
        start: Instant,
        end: Instant,
        maxResults: Int = 200
    ): List<RecordingSegment> = withContext(Dispatchers.IO) {
        val trackId = channel * 100 + 1
        val searchId = UUID.randomUUID().toString()
        val body = """<?xml version="1.0" encoding="UTF-8"?>
            <CMSearchDescription><searchID>$searchId</searchID><trackList><trackID>$trackId</trackID></trackList>
            <timeSpanList><timeSpan><startTime>${iso(start)}</startTime><endTime>${iso(end)}</endTime></timeSpan></timeSpanList>
            <maxResults>$maxResults</maxResults><searchResultPostion>0</searchResultPostion>
            <metadataList><metadataDescriptor>//recordType.meta.std-cgi.com</metadataDescriptor></metadataList></CMSearchDescription>""".trimIndent()
        val xml = request(recorder.ipAddress, recorder.httpPort, recorder.username, recorder.password,
            "POST", "/ISAPI/ContentMgmt/search", body)
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder()
            .parse(xml.byteInputStream())
        val matches = doc.getElementsByTagName("searchMatchItem")
        (0 until matches.length).mapNotNull { index ->
            val element = matches.item(index) as? org.w3c.dom.Element ?: return@mapNotNull null
            val startText = descendant(element, "startTime")
            val endText = descendant(element, "endTime")
            val uri = descendant(element, "playbackURI")
            runCatching { RecordingSegment(Instant.parse(startText), Instant.parse(endText), uri) }.getOrNull()
        }
    }

    fun liveUrl(recorder: RecorderEntity, channel: Int, subStream: Boolean): String {
        return credentialedRtsp(recorder, NvrStreamProfile.livePath(
            recorder.manufacturer, channel, subStream, recorder.username, recorder.password
        ))
    }

    fun authenticatedPlaybackUrl(recorder: RecorderEntity, uri: String): String {
        if (!uri.startsWith("rtsp://", true)) return uri
        val withoutScheme = uri.substringAfter("rtsp://").substringAfter('@')
        return "rtsp://${encodeUserInfo(recorder.username)}:${encodeUserInfo(recorder.password)}@$withoutScheme"
    }

    private fun credentialedRtsp(recorder: RecorderEntity, path: String) =
        "rtsp://${encodeUserInfo(recorder.username)}:${encodeUserInfo(recorder.password)}@${recorder.ipAddress}:${recorder.rtspPort}$path"

    private fun request(host: String, port: Int, user: String, password: String, method: String, path: String, body: String? = null): String {
        val url = URL("http", host, port, path)
        val first = open(url, method, body, null)
        if (first.first in 200..299) return first.second
        val challenge = first.third ?: throw IllegalStateException("ISAPI HTTP ${first.first}")
        if (first.first != 401 || !challenge.startsWith("Digest", true)) throw IllegalStateException("ISAPI HTTP ${first.first}")
        val authorization = digestAuthorization(challenge, method, path, user, password)
        val second = open(url, method, body, authorization)
        if (second.first !in 200..299) throw IllegalStateException("ISAPI HTTP ${second.first}")
        return second.second
    }

    private fun open(url: URL, method: String, body: String?, authorization: String?): Triple<Int, String, String?> {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/xml")
            authorization?.let { setRequestProperty("Authorization", it) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/xml; charset=UTF-8")
            }
        }
        if (body != null) OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val challenge = connection.getHeaderField("WWW-Authenticate")
        connection.disconnect()
        return Triple(code, response, challenge)
    }

    private fun digestAuthorization(challenge: String, method: String, uri: String, user: String, password: String): String {
        val values = Regex("(\\w+)=\"?([^,\"]+)\"?").findAll(challenge).associate { it.groupValues[1] to it.groupValues[2].trim() }
        val realm = values["realm"].orEmpty()
        val nonce = values["nonce"].orEmpty()
        val qop = values["qop"]?.split(',')?.map { it.trim() }?.firstOrNull { it == "auth" }
        val opaque = values["opaque"]
        val nc = "%08x".format(nonceCount.incrementAndGet())
        val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val ha1 = md5("$user:$realm:$password")
        val ha2 = md5("$method:$uri")
        val response = if (qop != null) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")
        return buildString {
            append("Digest username=\"").append(user).append("\", realm=\"").append(realm)
            append("\", nonce=\"").append(nonce).append("\", uri=\"").append(uri)
            append("\", response=\"").append(response).append('"')
            if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"").append(cnonce).append('"')
            opaque?.let { append(", opaque=\"").append(it).append('"') }
        }
    }

    private fun md5(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private fun tag(xml: String, name: String) = Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(xml)?.groupValues?.get(1)?.trim().orEmpty()
    private fun child(e: org.w3c.dom.Element, name: String) = e.getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
    private fun descendant(e: org.w3c.dom.Element, name: String) = child(e, name)
    private fun iso(value: Instant) = DateTimeFormatter.ISO_INSTANT.format(value)
    private fun encodeUserInfo(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
