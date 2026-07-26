package com.aladin.aladincamviewer

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** Lightweight RTSP DESCRIBE verifier with Basic and Digest authentication. */
object RtspEndpointVerifier {
    private const val TAG = "ALADIN_CAMERA_SETUP"

    data class Result(val playable: Boolean, val statusCode: Int?, val reason: String)

    suspend fun verify(url: String, username: String, password: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val uri = URI(url)
            require(uri.scheme.equals("rtsp", true) && !uri.host.isNullOrBlank())
            val cleanUri = URI("rtsp", null, uri.host, if (uri.port > 0) uri.port else 554, uri.path, uri.query, null)
            val first = request(cleanUri, null)
            if (first.code == 200) return@withContext Result(true, 200, "RTSP DESCRIBE accepted")
            if (first.code != 401) return@withContext Result(false, first.code, "RTSP status ${first.code}")

            val challenge = first.headers["www-authenticate"]
                ?: return@withContext Result(false, 401, "Authentication challenge missing")
            val authorization = when {
                challenge.startsWith("Basic", true) -> {
                    val token = Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
                    "Basic $token"
                }
                challenge.startsWith("Digest", true) -> digestAuthorization(challenge, cleanUri, username, password)
                else -> null
            } ?: return@withContext Result(false, 401, "Unsupported RTSP authentication")

            val authenticated = request(cleanUri, authorization)
            Result(
                authenticated.code == 200,
                authenticated.code,
                if (authenticated.code == 200) "Authenticated RTSP DESCRIBE accepted" else "Authentication or stream path rejected"
            )
        }.getOrElse {
            Log.d(TAG, "RTSP verification failed endpoint=${safeEndpoint(url)} reason=${it.javaClass.simpleName}")
            Result(false, null, it.javaClass.simpleName)
        }
    }

    private data class Response(val code: Int, val headers: Map<String, String>)

    private fun request(uri: URI, authorization: String?): Response {
        Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(uri.host, uri.port), 1800)
            socket.soTimeout = 2500
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            writer.write("DESCRIBE $uri RTSP/1.0\r\n")
            writer.write("CSeq: 1\r\n")
            writer.write("Accept: application/sdp\r\n")
            writer.write("User-Agent: AladinCCTV/1.2\r\n")
            if (authorization != null) writer.write("Authorization: $authorization\r\n")
            writer.write("Connection: close\r\n\r\n")
            writer.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val status = reader.readLine() ?: return Response(0, emptyMap())
            val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val separator = line.indexOf(':')
                if (separator > 0) headers[line.substring(0, separator).lowercase(Locale.US)] = line.substring(separator + 1).trim()
            }
            return Response(code, headers)
        }
    }

    private fun digestAuthorization(challenge: String, uri: URI, user: String, pass: String): String? {
        val values = Regex("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))")
            .findAll(challenge).associate { it.groupValues[1].lowercase() to (it.groupValues[2].ifBlank { it.groupValues[3] }) }
        val realm = values["realm"] ?: return null
        val nonce = values["nonce"] ?: return null
        val qop = values["qop"]?.split(',')?.map { it.trim() }?.firstOrNull { it == "auth" }
        Log.d(TAG, "RTSP Digest challenge endpoint=${safeEndpoint(uri.toString())} realm=$realm qop=${values["qop"] ?: "none"} algorithm=${values["algorithm"] ?: "MD5"}")
        // RTSP Digest uses the exact Request-URI from the DESCRIBE request (absolute URI),
        // unlike the origin-form path commonly used by HTTP servers.
        val requestUri = uri.toASCIIString()
        val ha1 = md5("$user:$realm:$pass")
        val ha2 = md5("DESCRIBE:$requestUri")
        val nc = "00000001"
        val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val response = if (qop != null) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")
        return buildString {
            append("Digest username=\"").append(user).append("\", realm=\"").append(realm)
            append("\", nonce=\"").append(nonce).append("\", uri=\"").append(requestUri)
            append("\", response=\"").append(response).append('"')
            values["opaque"]?.let { append(", opaque=\"").append(it).append('"') }
            values["algorithm"]?.let { append(", algorithm=").append(it) }
            if (qop != null) append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append('"')
        }
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    fun safeEndpoint(url: String): String = runCatching {
        val uri = URI(url)
        "${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else 554}${uri.path.orEmpty()}"
    }.getOrDefault("invalid_rtsp_url")
}
