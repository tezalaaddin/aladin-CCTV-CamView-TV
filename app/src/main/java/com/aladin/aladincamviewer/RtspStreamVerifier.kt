package com.aladin.aladincamviewer

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/** Lightweight, viewless LibVLC probe used only to identify a moved legacy camera. */
class RtspStreamVerifier {
    suspend fun canPlay(url: String, timeoutMs: Long = 6_000L): Boolean {
        val result = CompletableDeferred<Boolean>()
        val player = MediaPlayer(CctvApplication.sharedLibVLC)
        val endpoint = endpoint(url)
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> result.complete(true)
                MediaPlayer.Event.EncounteredError, MediaPlayer.Event.EndReached -> result.complete(false)
            }
        }

        return try {
            val media = Media(CctvApplication.sharedLibVLC, Uri.parse(url)).apply {
                setHWDecoderEnabled(false, false)
                addOption(":rtsp-tcp")
                addOption(":network-caching=300")
                addOption(":no-audio")
                addOption(":no-video")
            }
            player.media = media
            media.release()
            AppLog.d(TAG, "Legacy RTSP identity probe start endpoint=$endpoint timeoutMs=$timeoutMs")
            player.play()
            val playable = withTimeoutOrNull(timeoutMs) { result.await() } ?: false
            AppLog.i(TAG, "Legacy RTSP identity probe result endpoint=$endpoint playable=$playable")
            playable
        } catch (error: Exception) {
            AppLog.w(TAG, "Legacy RTSP identity probe failed endpoint=$endpoint", error)
            false
        } finally {
            player.setEventListener(null)
            runCatching { player.stop() }
            player.release()
        }
    }

    private fun endpoint(url: String): String = runCatching {
        val uri = Uri.parse(url)
        "${uri.host}:${if (uri.port == -1) 554 else uri.port}${uri.path.orEmpty()}"
    }.getOrDefault("<invalid>")

    private companion object {
        const val TAG = "ALADIN_NETWORK_TRACKER"
    }
}
