package com.aladin.aladincamviewer

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.parsers.DocumentBuilderFactory

/** ONVIF Profile G recording discovery and replay client. */
class OnvifProfileGClient(
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 12000
) {
    private val nonceCount = AtomicInteger()
    private data class Services(val recording: String, val search: String, val replay: String)
    private data class Recording(val token: String, val sourceId: String, val name: String, val earliest: Instant?, val latest: Instant?)
    private data class Response(val code: Int, val body: String, val challenge: String?)

    suspend fun recordings(recorder: RecorderEntity, channel: Int, start: Instant, end: Instant): List<RecordingSegment> =
        withContext(Dispatchers.IO) {
            val deviceUrl = "http://${recorder.ipAddress}:${recorder.httpPort}/onvif/device_service"
            val services = services(recorder, deviceUrl)
            val available = getRecordings(recorder, services.recording)
            check(available.isNotEmpty()) { "ONVIF Profile G returned no recordings" }
            val selected = selectRecording(available, channel)
                ?: throw IllegalStateException("ONVIF recording token was not found for channel $channel")
            val channelUri = channelReplayUri(recorder, replayUri(recorder, services.replay, selected.token), channel)
            val rangeStart = maxOf(start, selected.earliest ?: start)
            val rangeEnd = minOf(end, selected.latest ?: end)
            if (rangeEnd <= rangeStart) return@withContext emptyList()
            val uri = timedReplayUri(recorder, channelUri, rangeStart, rangeEnd)
            val offset = selected.earliest?.let { Duration.between(it, rangeStart).toMillis().coerceAtLeast(0L) } ?: 0L
            AppLog.i("ALADIN_PROFILE_G", "Replay resolved channel=$channel token=${selected.token.take(12)} offsetMs=$offset")
            listOf(RecordingSegment(rangeStart, rangeEnd, uri, offset))
        }

    internal fun channelReplayUri(recorder: RecorderEntity, uri: String, channel: Int): String {
        if (!recorder.manufacturer.equals(NvrStreamProfile.TIANDY, true)) return uri
        return uri.replace(Regex("(?i)(/replay/)\\d+(/)"), "$1$channel$2")
    }

    internal fun timedReplayUri(recorder: RecorderEntity, uri: String, start: Instant, end: Instant): String {
        if (!recorder.manufacturer.equals(NvrStreamProfile.TIANDY, true)) return uri
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC)
        val separator = if ('?' in uri) "&" else "?"
        return "$uri${separator}starttime=${formatter.format(start)}&endtime=${formatter.format(end)}"
    }

    private fun services(recorder: RecorderEntity, deviceUrl: String): Services {
        val response = request(recorder, deviceUrl, "http://www.onvif.org/ver10/device/wsdl/GetServices", """
            <tds:GetServices xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
              <tds:IncludeCapability>true</tds:IncludeCapability>
            </tds:GetServices>
        """.trimIndent())
        val serviceElements = elements(response, "Service")
        fun find(namespace: String) = serviceElements.firstNotNullOfOrNull { element ->
            child(element, "XAddr").takeIf { child(element, "Namespace").contains(namespace, true) }
        }
        val search = find("/search/wsdl") ?: throw IllegalStateException("ONVIF Profile G Search service is unavailable")
        val replay = find("/replay/wsdl") ?: throw IllegalStateException("ONVIF Profile G Replay service is unavailable")
        val recording = find("/recording/wsdl") ?: throw IllegalStateException("ONVIF Profile G Recording service is unavailable")
        AppLog.i("ALADIN_PROFILE_G", "Services discovered recording=true search=true replay=true")
        return Services(normalize(recording, recorder), normalize(search, recorder), normalize(replay, recorder))
    }

    private fun getRecordings(recorder: RecorderEntity, recordingUrl: String): List<Recording> {
        val response = request(recorder, recordingUrl, "http://www.onvif.org/ver10/recording/wsdl/GetRecordings",
            "<trc:GetRecordings xmlns:trc=\"http://www.onvif.org/ver10/recording/wsdl\"/>")
        val entries = elements(response, "RecordingItem").ifEmpty { elements(response, "RecordingInformation") }
        AppLog.i("ALADIN_PROFILE_G", "Recording catalog count=${entries.size}")
        return entries.mapNotNull { element ->
            val token = child(element, "RecordingToken")
            if (token.isBlank()) return@mapNotNull null
            Recording(token, descendant(element, "SourceId"), descendant(element, "Name"),
                instant(descendant(element, "EarliestRecording")), instant(descendant(element, "LatestRecording")))
        }
    }

    private fun replayUri(recorder: RecorderEntity, replayUrl: String, token: String): String {
        val response = request(recorder, replayUrl, "http://www.onvif.org/ver10/replay/wsdl/GetReplayUri", """
            <trp:GetReplayUri xmlns:trp="http://www.onvif.org/ver10/replay/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema">
              <trp:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream><tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></trp:StreamSetup>
              <trp:RecordingToken>${xml(token)}</trp:RecordingToken>
            </trp:GetReplayUri>
        """.trimIndent())
        val uri = first(response, "Uri")
        check(uri.startsWith("rtsp://", true)) { "ONVIF Replay service returned no RTSP URI" }
        return uri
    }

    private fun selectRecording(items: List<Recording>, channel: Int): Recording? {
        val exact = items.firstOrNull { item ->
            listOf(item.sourceId, item.name, item.token).any { Regex("(?:^|\\D)0*$channel(?:\\D|$)").containsMatchIn(it) }
        }
        return exact ?: items.getOrNull(channel - 1)
    }

    private fun request(recorder: RecorderEntity, url: String, action: String, body: String): String {
        val envelope = envelope(recorder.username, recorder.password, body)
        val first = open(url, action, envelope, null)
        if (first.code in 200..299) return first.body
        val challenge = first.challenge.orEmpty()
        if (first.code != 401 || !challenge.startsWith("Digest", true))
            throw IllegalStateException("ONVIF HTTP ${first.code}: ${soapFault(first.body)}")
        val parsed = URL(url)
        val uri = parsed.path + (parsed.query?.let { "?$it" } ?: "")
        val second = open(url, action, envelope, digest(challenge, "POST", uri, recorder.username, recorder.password))
        if (second.code !in 200..299) throw IllegalStateException("ONVIF HTTP ${second.code}: ${soapFault(second.body)}")
        return second.body
    }

    private fun open(url: String, action: String, body: String, authorization: String?): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"; connection.doOutput = true
        connection.connectTimeout = connectTimeoutMs; connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8; action=\"$action\"")
        connection.setRequestProperty("SOAPAction", "\"$action\"")
        authorization?.let { connection.setRequestProperty("Authorization", it) }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val challenge = connection.getHeaderField("WWW-Authenticate")
        connection.disconnect()
        return Response(code, response, challenge)
    }

    private fun envelope(user: String, password: String, body: String): String {
        val nonce = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val created = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val passwordDigest = MessageDigest.getInstance("SHA-1").digest(nonce + created.toByteArray() + password.toByteArray())
        return """<?xml version="1.0" encoding="UTF-8"?>
          <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"><s:Header>
          <wsse:Security s:mustUnderstand="1" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
          <wsse:UsernameToken><wsse:Username>${xml(user)}</wsse:Username><wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">${Base64.encodeToString(passwordDigest, Base64.NO_WRAP)}</wsse:Password><wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">${Base64.encodeToString(nonce, Base64.NO_WRAP)}</wsse:Nonce><wsu:Created>$created</wsu:Created></wsse:UsernameToken>
          </wsse:Security></s:Header><s:Body>$body</s:Body></s:Envelope>""".trimIndent()
    }

    private fun digest(challenge: String, method: String, uri: String, user: String, password: String): String {
        val values = Regex("(\\w+)=\"?([^,\"]+)\"?").findAll(challenge).associate { it.groupValues[1] to it.groupValues[2].trim() }
        val realm = values["realm"].orEmpty(); val nonce = values["nonce"].orEmpty()
        val qop = values["qop"]?.split(',')?.map(String::trim)?.firstOrNull { it == "auth" }
        val nc = "%08x".format(nonceCount.incrementAndGet()); val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val ha1 = md5("$user:$realm:$password"); val ha2 = md5("$method:$uri")
        val response = if (qop != null) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")
        return buildString {
            append("Digest username=\"").append(user).append("\", realm=\"").append(realm).append("\", nonce=\"").append(nonce)
            append("\", uri=\"").append(uri).append("\", response=\"").append(response).append('"')
            if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"").append(cnonce).append('"')
            values["opaque"]?.let { append(", opaque=\"").append(it).append('"') }
        }
    }

    private fun document(value: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(value.byteInputStream())
    private fun elements(value: String, localName: String): List<Element> {
        val nodes = document(value).getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }
    private fun first(value: String, localName: String) = elements(value, localName).firstOrNull()?.textContent?.trim().orEmpty()
    private fun child(element: Element, localName: String): String = (0 until element.childNodes.length)
        .mapNotNull { element.childNodes.item(it) as? Element }.firstOrNull { it.localName.equals(localName, true) }
        ?.textContent?.trim().orEmpty()
    private fun descendant(element: Element, localName: String) = element.getElementsByTagNameNS("*", localName).item(0)?.textContent?.trim().orEmpty()
    private fun instant(value: String) = runCatching { Instant.parse(value) }.getOrNull()
    private fun normalize(value: String, recorder: RecorderEntity) = runCatching {
        val parsed = URL(value); URL(parsed.protocol, recorder.ipAddress, if (parsed.port >= 0) parsed.port else recorder.httpPort, parsed.file).toString()
    }.getOrDefault(value)
    private fun soapFault(value: String) = runCatching { first(value, "Text").ifBlank { first(value, "Reason") } }.getOrDefault("").ifBlank { "request failed" }
    private fun md5(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
