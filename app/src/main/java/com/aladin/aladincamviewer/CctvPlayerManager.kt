package com.aladin.aladincamviewer

import android.net.Uri
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * High-Performance LibVLC Player Manager.
 * Optimized for Android TV stability and "Stretch-to-Fill" display mode.
 * Uses Shared LibVLC instance to prevent resource-heavy freezes.
 */
class CctvPlayerManager(
    private val onStateChanged: (Boolean, String?) -> Unit
) {

    private val TAG = "ALADIN_VLC"
    private val libVLC: LibVLC = CctvApplication.sharedLibVLC
    private var mediaPlayer: MediaPlayer? = null
    private var currentUrl: String? = null
    private var isSubStream = false

    init {
        mediaPlayer = MediaPlayer(libVLC)
        // 🚀 FORCE FILL MODE: This stretches the video to fill the entire container
        mediaPlayer?.videoScale = MediaPlayer.ScaleType.SURFACE_FILL
    }

    /**
     * Optimizes player for Grid view (Lower latency) or Fullscreen.
     */
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
        if (url.isEmpty()) return
        if (url == currentUrl && mediaPlayer?.isPlaying == true) return
        
        currentUrl = url
        Log.d(TAG, "▶️ LibVLC Playing: $url")
        
        onStateChanged(true, null)

        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    onStateChanged(false, null)
                    mediaPlayer?.videoScale = MediaPlayer.ScaleType.SURFACE_FILL
                }
                MediaPlayer.Event.EncounteredError -> onStateChanged(false, "Bağlantı Hatası")
                MediaPlayer.Event.EndReached -> onStateChanged(false, "Yayın Kesildi")
            }
        }

        try {
            val media = Media(libVLC, Uri.parse(url)).apply {
                setHWDecoderEnabled(true, true)
                addOption(":network-caching=${if (isSubStream) 800 else 1500}")
                addOption(":rtsp-tcp")
                addOption(":no-audio")
                addOption(":clock-jitter=0")
            }

            mediaPlayer?.media = media
            media.release()
            mediaPlayer?.play()
        } catch (e: Exception) {
            Log.e(TAG, "❌ LibVLC Error: ${e.message}")
            onStateChanged(false, "Başlatma Hatası")
        }
    }

    fun setVolume(volume: Float) {
        mediaPlayer?.volume = (volume * 100).toInt()
    }

    fun releasePlayer() {
        mediaPlayer?.let {
            it.setEventListener(null)
            if (it.isPlaying) it.stop()
            it.detachViews()
            it.release()
        }
        mediaPlayer = null
    }

    // Compatibility getter
    val player: Any? get() = null
}
