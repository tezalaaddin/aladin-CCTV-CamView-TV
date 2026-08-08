package com.aladin.aladincamviewer

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private var currentStartTimeMs = 0L
    private var isSubStream = false
    private var forceSoftwareDecoder = false
    private var hardwareDecoderEnabled = false
    private var retryAttempt = 0
    private var playGeneration = 0
    private var released = false
    private var retryScheduled = false
    private var lastBufferBucket = -1
    private var pendingSeekTimeMs: Long? = null
    private val stallDetector = PlaybackStallDetector()
    private var watchdogGeneration = -1
    private var watchdogTicks = 0
    private val playbackWatchdog = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return
            val generation = watchdogGeneration
            if (released || generation != playGeneration) return

            val stats = runCatching { player.media?.stats }.getOrNull()
            val displayedFrames = stats?.displayedPictures?.toLong()
            val progress = displayedFrames ?: player.time
            watchdogTicks++
            if (watchdogTicks % 6 == 0) {
                AppLog.d(
                    tag,
                    "RTSP health endpoint=${currentUrl?.let(::describeEndpoint)} " +
                        "displayed=${stats?.displayedPictures} decoded=${stats?.decodedVideo} " +
                        "demuxBytes=${stats?.demuxReadBytes} mediaTimeMs=${player.time}"
                )
            }
            if (player.isPlaying && stallDetector.isStalled(SystemClock.elapsedRealtime(), progress)) {
                val url = currentUrl ?: return
                val endpoint = describeEndpoint(url)
                AppLog.w(
                    tag,
                    "RTSP video frames stalled endpoint=$endpoint displayed=${stats?.displayedPictures} " +
                        "decoded=${stats?.decodedVideo} demuxBytes=${stats?.demuxReadBytes} " +
                        "mediaTimeMs=${player.time}; restarting"
                )
                stopPlaybackWatchdog()
                scheduleRetry(url, generation, endpoint, "video_frames_stalled")
                return
            }
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    companion object {
        private const val WATCHDOG_INTERVAL_MS = 5_000L
    }

    init {
        mediaPlayer?.videoScale = MediaPlayer.ScaleType.SURFACE_FILL
    }

    fun initializePlayer(isSubStream: Boolean = false, forceSoftwareDecoder: Boolean = false) {
        this.isSubStream = isSubStream
        this.forceSoftwareDecoder = forceSoftwareDecoder
        hardwareDecoderEnabled = !isSubStream && !forceSoftwareDecoder
    }

    fun attachView(videoLayout: VLCVideoLayout) {
        mediaPlayer?.let {
            it.attachViews(videoLayout, null, false, false)
            it.videoScale = MediaPlayer.ScaleType.SURFACE_FILL
        }
    }

    fun fitVideoToScreen(fit: Boolean) {
        mediaPlayer?.videoScale = if (fit) MediaPlayer.ScaleType.SURFACE_BEST_FIT else MediaPlayer.ScaleType.SURFACE_FILL
    }

    fun seekTo(timeMs: Long) {
        val target = timeMs.coerceAtLeast(0L)
        val player = mediaPlayer
        if (player?.isPlaying == true) {
            player.time = target
            pendingSeekTimeMs = null
            AppLog.d(tag, "RTSP seek applied endpoint=${currentUrl?.let(::describeEndpoint)} targetMs=$target")
        } else {
            pendingSeekTimeMs = target
            AppLog.d(tag, "RTSP seek queued endpoint=${currentUrl?.let(::describeEndpoint)} targetMs=$target")
        }
    }

    fun skipBy(deltaMs: Long) {
        val player = mediaPlayer ?: return
        seekTo(player.time + deltaMs)
    }

    fun setPlaybackRate(rate: Float): Boolean {
        val player = mediaPlayer ?: return false
        player.setRate(rate)
        return true
    }

    fun togglePause(): Boolean {
        val player = mediaPlayer ?: return false
        if (player.isPlaying) player.pause() else player.play()
        return !player.isPlaying
    }

    fun playbackTimeMs(): Long = mediaPlayer?.time ?: 0L

    fun playStream(url: String, startTimeMs: Long = 0L) {
        if (url.isBlank()) {
            AppLog.w(tag, "RTSP start ignored: empty URL")
            notifyState(false, "YayÄ±n adresi boÅŸ")
            return
        }
        if (url == currentUrl && mediaPlayer?.isPlaying == true) return

        val isNewStream = url != currentUrl
        playGeneration++
        retryAttempt = 0
        currentUrl = url
        currentStartTimeMs = startTimeMs.coerceAtLeast(0L)
        if (isNewStream) hardwareDecoderEnabled = !isSubStream && !forceSoftwareDecoder
        released = false
        retryScheduled = false
        lastBufferBucket = -1
        startPlayback(url, playGeneration)
    }

    private fun startPlayback(url: String, generation: Int) {
        if (released || generation != playGeneration) return
        val endpoint = describeEndpoint(url)
        val cacheMs = if (isSubStream) 800 else 1500
        val decoder = if (hardwareDecoderEnabled) "hardware" else "software"
        AppLog.i(tag, "RTSP start endpoint=$endpoint transport=tcp profile=${if (isSubStream) "grid" else "fullscreen"} decoder=$decoder cacheMs=$cacheMs attempt=${retryAttempt + 1}")
        notifyState(true, null)

        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    AppLog.i(tag, "RTSP playing endpoint=$endpoint retries=$retryAttempt")
                    retryAttempt = 0
                    retryScheduled = false
                    notifyState(false, null)
                    pendingSeekTimeMs?.let { target ->
                        mainHandler.postDelayed({
                            if (!released && generation == playGeneration && mediaPlayer?.isPlaying == true) {
                                mediaPlayer?.time = target
                                pendingSeekTimeMs = null
                                AppLog.d(tag, "RTSP queued seek applied endpoint=$endpoint targetMs=$target")
                            }
                        }, 250L)
                    }
                    startPlaybackWatchdog(generation)
                }
                MediaPlayer.Event.Buffering -> {
                    val bucket = (event.buffering.toInt().coerceIn(0, 100) / 25) * 25
                    if (bucket != lastBufferBucket) {
                        lastBufferBucket = bucket
                        AppLog.d(tag, "RTSP buffering endpoint=$endpoint percent=$bucket")
                    }
                }
                MediaPlayer.Event.EncounteredError ->
                    scheduleRetry(url, generation, endpoint, "decoder_or_network_error")
                MediaPlayer.Event.EndReached ->
                    scheduleRetry(url, generation, endpoint, "stream_ended")
            }
        }

        try {
            val media = Media(libVLC, Uri.parse(url)).apply {
                // Some Android TV chipsets lock both MediaCodec sessions when two
                // RTSP feeds decode concurrently. Grid sub-streams are small enough
                // for software decoding; retain hardware decoding for fullscreen.
                setHWDecoderEnabled(hardwareDecoderEnabled, hardwareDecoderEnabled)
                addOption(":network-caching=$cacheMs")
                addOption(":rtsp-tcp")
                addOption(":no-audio")
                if (currentStartTimeMs > 0L) addOption(":start-time=${currentStartTimeMs / 1000.0}")
            }
            mediaPlayer?.media = media
            media.release()
            mediaPlayer?.play()
        } catch (error: Exception) {
            AppLog.e(tag, "RTSP start failure endpoint=$endpoint type=${error.javaClass.simpleName}", error)
            scheduleRetry(url, generation, endpoint, "start_failure")
        }
    }

    private fun scheduleRetry(url: String, generation: Int, endpoint: String, reason: String) {
        if (released || generation != playGeneration || retryScheduled) return
        stopPlaybackWatchdog()
        if (pendingSeekTimeMs == null) {
            mediaPlayer?.time?.takeIf { it > 0L }?.let { pendingSeekTimeMs = it }
        }
        if (hardwareDecoderEnabled && (reason == "decoder_or_network_error" || reason == "video_frames_stalled")) {
            hardwareDecoderEnabled = false
            AppLog.w(tag, "RTSP decoder fallback endpoint=$endpoint hardware=failed next=software reason=$reason")
        }
        val delayMs = retryPolicy.delayForAttempt(retryAttempt)
        if (delayMs == null) {
            AppLog.e(tag, "RTSP retry exhausted endpoint=$endpoint reason=$reason attempts=$retryAttempt")
            notifyState(false, "BaÄŸlantÄ± kurulamadÄ±")
            return
        }
        retryScheduled = true
        retryAttempt++
        AppLog.w(tag, "RTSP retry scheduled endpoint=$endpoint reason=$reason attempt=$retryAttempt delayMs=$delayMs")
        notifyState(false, "Yeniden baÄŸlanÄ±lÄ±yor ($retryAttempt/5)")
        mainHandler.postDelayed({
            if (!released && generation == playGeneration) {
                retryScheduled = false
                runCatching { mediaPlayer?.stop() }
                startPlayback(url, generation)
            }
        }, delayMs)
    }

    private fun startPlaybackWatchdog(generation: Int) {
        mainHandler.removeCallbacks(playbackWatchdog)
        watchdogGeneration = generation
        watchdogTicks = 0
        val initialProgress = runCatching { mediaPlayer?.media?.stats?.displayedPictures?.toLong() }
            .getOrNull() ?: mediaPlayer?.time ?: -1L
        stallDetector.reset(SystemClock.elapsedRealtime(), initialProgress)
        AppLog.d(tag, "RTSP stall watchdog started endpoint=${currentUrl?.let(::describeEndpoint)} thresholdMs=25000")
        mainHandler.postDelayed(playbackWatchdog, WATCHDOG_INTERVAL_MS)
    }

    private fun stopPlaybackWatchdog() {
        watchdogGeneration = -1
        mainHandler.removeCallbacks(playbackWatchdog)
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
        stopPlaybackWatchdog()
        mainHandler.removeCallbacksAndMessages(null)
        mediaPlayer?.let {
            it.setEventListener(null)
            if (it.isPlaying) it.stop()
            it.detachViews()
            it.release()
        }
        mediaPlayer = null
        AppLog.d(tag, "RTSP player released endpoint=${currentUrl?.let(::describeEndpoint)}")
    }

    val player: Any? get() = null
}
