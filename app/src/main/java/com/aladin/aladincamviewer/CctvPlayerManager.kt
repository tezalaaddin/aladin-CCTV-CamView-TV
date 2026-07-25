package com.aladin.aladincamviewer

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class CctvPlayerManager(
    private val onStateChanged: (Boolean, String?) -> Unit
) {
    private val tag = "ALADIN_VLC"
    private val libVLC: LibVLC = CctvApplication.sharedLibVLC
    private val mainHandler = Handler(Looper.getMainLooper())
    private val retryPolicy = RetryPolicy()
    private var mediaPlayer: MediaPlayer? = MediaPlayer(libVLC)
    private var currentUrl: String? = null
    private var isSubStream = false
    private var retryAttempt = 0
    private var playGeneration = 0
    private var released = false
    private var retryScheduled = false

    init {
        mediaPlayer?.videoScale = MediaPlayer.ScaleType.SURFACE_FILL
    }

    fun initializePlayer(isSubStream: Boolean = false) {
        this.isSubStream = isSubStream
    }

    fun attachView(videoLayout: VLCVideoLayout) {
        mediaPlayer?.let {
            it.attachViews(videoLayout, null, false, false)
            it.videoScale = MediaPlayer.ScaleType.SURFACE_FILL
        }
    }

    fun playStream(url: String) {
        if (url.isBlank()) {
            Log.w(tag, "RTSP start ignored: empty URL")
            notifyState(false, "Yayın adresi boş")
            return
        }
        if (url == currentUrl && mediaPlayer?.isPlaying == true) return

        playGeneration++
        retryAttempt = 0
        currentUrl = url
        released = false
        retryScheduled = false
        startPlayback(url, playGeneration)
    }

    private fun startPlayback(url: String, generation: Int) {
        if (released || generation != playGeneration) return
        val endpoint = describeEndpoint(url)
        val cacheMs = if (isSubStream) 800 else 1500
        Log.i(tag, "RTSP start endpoint=$endpoint transport=tcp profile=${if (isSubStream) "grid" else "fullscreen"} cacheMs=$cacheMs attempt=${retryAttempt + 1}")
        notifyState(true, null)

        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    Log.i(tag, "RTSP playing endpoint=$endpoint retries=$retryAttempt")
                    retryAttempt = 0
                    retryScheduled = false
                    notifyState(false, null)
                    mediaPlayer?.videoScale = MediaPlayer.ScaleType.SURFACE_FILL
                }
                MediaPlayer.Event.Buffering ->
                    Log.d(tag, "RTSP buffering endpoint=$endpoint percent=${event.buffering}")
                MediaPlayer.Event.EncounteredError ->
                    scheduleRetry(url, generation, endpoint, "decoder_or_network_error")
                MediaPlayer.Event.EndReached ->
                    scheduleRetry(url, generation, endpoint, "stream_ended")
            }
        }

        try {
            val media = Media(libVLC, Uri.parse(url)).apply {
                setHWDecoderEnabled(true, true)
                addOption(":network-caching=$cacheMs")
                addOption(":rtsp-tcp")
                addOption(":no-audio")
                addOption(":clock-jitter=0")
            }
            mediaPlayer?.media = media
            media.release()
            mediaPlayer?.play()
        } catch (error: Exception) {
            Log.e(tag, "RTSP start failure endpoint=$endpoint type=${error.javaClass.simpleName}", error)
            scheduleRetry(url, generation, endpoint, "start_failure")
        }
    }

    private fun scheduleRetry(url: String, generation: Int, endpoint: String, reason: String) {
        if (released || generation != playGeneration || retryScheduled) return
        val delayMs = retryPolicy.delayForAttempt(retryAttempt)
        if (delayMs == null) {
            Log.e(tag, "RTSP retry exhausted endpoint=$endpoint reason=$reason attempts=$retryAttempt")
            notifyState(false, "Bağlantı kurulamadı")
            return
        }
        retryScheduled = true
        retryAttempt++
        Log.w(tag, "RTSP retry scheduled endpoint=$endpoint reason=$reason attempt=$retryAttempt delayMs=$delayMs")
        notifyState(false, "Yeniden bağlanılıyor ($retryAttempt/5)")
        mainHandler.postDelayed({
            if (!released && generation == playGeneration) {
                retryScheduled = false
                runCatching { mediaPlayer?.stop() }
                startPlayback(url, generation)
            }
        }, delayMs)
    }

    private fun describeEndpoint(url: String): String = runCatching {
        val uri = Uri.parse(url)
        "${uri.scheme}://${uri.host}:${if (uri.port == -1) 554 else uri.port}${uri.path.orEmpty()}"
    }.getOrDefault("<invalid-url>")

    private fun notifyState(loading: Boolean, error: String?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onStateChanged(loading, error)
        } else {
            mainHandler.post { if (!released) onStateChanged(loading, error) }
        }
    }

    fun setVolume(volume: Float) {
        mediaPlayer?.volume = (volume * 100).toInt()
    }

    fun releasePlayer() {
        released = true
        playGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        mediaPlayer?.let {
            it.setEventListener(null)
            if (it.isPlaying) it.stop()
            it.detachViews()
            it.release()
        }
        mediaPlayer = null
        Log.d(tag, "RTSP player released endpoint=${currentUrl?.let(::describeEndpoint)}")
    }

    val player: Any? get() = null
}
