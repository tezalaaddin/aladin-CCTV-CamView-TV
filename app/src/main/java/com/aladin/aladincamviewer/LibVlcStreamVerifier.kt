package com.aladin.aladincamviewer

import android.content.Context
import android.app.Activity
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.net.URI

/** Compatibility verifier for vendor RTSP servers whose Digest dialect rejects a raw DESCRIBE probe. */
object LibVlcStreamVerifier {
    suspend fun verify(context: Context, url: String, username: String, password: String): Boolean = withContext(Dispatchers.Main) {
        val activity = context as? Activity ?: return@withContext false
        val result = CompletableDeferred<Boolean>()
        val cleanUrl = runCatching {
            val uri = URI(url)
            URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, null).toASCIIString()
        }.getOrNull() ?: return@withContext false
        val libVlc = LibVLC(context.applicationContext, arrayListOf("--network-caching=700", "--no-audio", "--quiet"))
        val player = MediaPlayer(libVlc)
        val videoLayout = VLCVideoLayout(activity).apply { alpha = 0.01f }
        activity.addContentView(
            videoLayout,
            FrameLayout.LayoutParams(16, 16, Gravity.START or Gravity.BOTTOM)
        )
        try {
            player.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing, MediaPlayer.Event.Vout -> if (!result.isCompleted) result.complete(true)
                    MediaPlayer.Event.EncounteredError, MediaPlayer.Event.EndReached -> if (!result.isCompleted) result.complete(false)
                }
            }
            player.attachViews(videoLayout, null, false, false)
            // The app's normal player uses credential-bearing RTSP URIs as well. Quiet mode keeps
            // native VLC from printing that URI while this short compatibility check is active.
            val media = Media(libVlc, Uri.parse(url))
            media.addOption(":network-caching=700")
            media.addOption(":rtsp-tcp")
            player.media = media
            media.release()
            player.play()
            val playable = withTimeoutOrNull(8_000) { result.await() } ?: false
            Log.i("ALADIN_CAMERA_SETUP", "LibVLC verification endpoint=${RtspEndpointVerifier.safeEndpoint(cleanUrl)} playable=$playable")
            playable
        } catch (error: Exception) {
            Log.w("ALADIN_CAMERA_SETUP", "LibVLC verification failed endpoint=${RtspEndpointVerifier.safeEndpoint(cleanUrl)} reason=${error.javaClass.simpleName}")
            false
        } finally {
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            player.release()
            libVlc.release()
            (videoLayout.parent as? ViewGroup)?.removeView(videoLayout)
        }
    }
}
