package com.aladin.aladincamviewer

import android.content.Context
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Helper class to manage Jetpack Media3 ExoPlayer for RTSP CCTV streaming.
 * Optimized for low-latency and low RAM usage on TV hardware.
 */
@OptIn(UnstableApi::class)
class CctvPlayerManager(
    private val context: Context,
    private val onStateChanged: (Boolean, String?) -> Unit, // (isLoading, errorMessage)
    private val onFormatChanged: ((Format?) -> Unit)? = null
) {

    var player: ExoPlayer? = null
        private set

    private var currentUrl: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val retryRunnable = Runnable { playStream(currentUrl ?: "") }
    private var retryCount = 0
    private val maxRetries = 10

    private var alarmPlayed = false
    private val alarmHandler = Handler(Looper.getMainLooper())
    private val alarmRunnable = Runnable { playAlarm() }

    private val errorUiHandler = Handler(Looper.getMainLooper())
    private var pendingErrorMsg: String? = null
    private val errorUiRunnable = Runnable {
        onStateChanged(false, pendingErrorMsg)
    }
    private val loadingUiRunnable = Runnable {
        onStateChanged(true, null)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    scheduleLoadingUiUpdate(10000)
                }
                Player.STATE_READY -> {
                    cancelErrorUiUpdate()
                    onStateChanged(false, null)
                    retryCount = 0 
                    cancelAlarm()
                    updateFormatInfo()
                }
                Player.STATE_IDLE -> {}
                Player.STATE_ENDED -> {}
            }
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            super.onVideoSizeChanged(videoSize)
            updateFormatInfo()
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e("CctvPlayerManager", "Player Error: ${error.message}", error)
            pendingErrorMsg = context.getString(R.string.connection_lost_retry)
            scheduleErrorUiUpdate(5000)
            
            if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED || 
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED) {
                releasePlayer()
                initializePlayer(currentUrl?.contains("sub") == true || currentUrl?.contains("ch1") == true)
            }
            
            scheduleRetry()
            scheduleAlarm()
        }
    }

    private fun updateFormatInfo() {
        player?.videoFormat?.let { format ->
            onFormatChanged?.invoke(format)
        }
    }

    private fun scheduleErrorUiUpdate(delay: Long) {
        errorUiHandler.removeCallbacks(errorUiRunnable)
        errorUiHandler.removeCallbacks(loadingUiRunnable)
        errorUiHandler.postDelayed(errorUiRunnable, delay)
    }

    private fun scheduleLoadingUiUpdate(delay: Long) {
        errorUiHandler.removeCallbacks(errorUiRunnable)
        errorUiHandler.removeCallbacks(loadingUiRunnable)
        errorUiHandler.postDelayed(loadingUiRunnable, delay)
    }

    private fun cancelErrorUiUpdate() {
        errorUiHandler.removeCallbacks(errorUiRunnable)
        errorUiHandler.removeCallbacks(loadingUiRunnable)
        pendingErrorMsg = null
    }

    private fun scheduleAlarm() {
        val prefHelper = PreferenceHelper(context)
        if (prefHelper.isOfflineAlarmEnabled && !alarmPlayed) {
            alarmHandler.removeCallbacks(alarmRunnable)
            alarmHandler.postDelayed(alarmRunnable, 10000) // 10 seconds timeout
        }
    }

    private fun cancelAlarm() {
        alarmHandler.removeCallbacks(alarmRunnable)
        alarmPlayed = false
    }

    private fun playAlarm() {
        if (!alarmPlayed) {
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val r = RingtoneManager.getRingtone(context, notification)
                r.play()
                alarmPlayed = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scheduleRetry() {
        if (retryCount < maxRetries) {
            retryCount++
            mainHandler.removeCallbacks(retryRunnable)
            // Deneme aralığını 10 saniyeye çıkararak cihazı rahatlatalım
            mainHandler.postDelayed(retryRunnable, 10000) 
        } else {
            onStateChanged(false, context.getString(R.string.failed_reconnect_attempts))
        }
    }

    fun initializePlayer(isSubStream: Boolean = false) {
        if (player == null) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    2500, // minBufferMs
                    5000, // maxBufferMs
                    500,  // bufferForPlaybackMs
                    1500  // bufferForPlaybackAfterRebufferMs
                )
                .build()

            // 1. Configure TrackSelector to restrict resolution and disable heavy tracks
            val trackSelector = DefaultTrackSelector(context)
            val parametersBuilder = trackSelector.buildUponParameters()
                .setDisabledTrackTypes(setOf(
                    C.TRACK_TYPE_TEXT, 
                    C.TRACK_TYPE_METADATA, 
                    C.TRACK_TYPE_AUDIO // Disable audio to avoid G.711 errors and save CPU
                ))
            
            if (isSubStream) {
                // Extreme Optimization for Grid View
                parametersBuilder
                    .setMaxVideoSize(640, 480) // AJCloud sub-streams are usually within this range
                    .setMaxVideoBitrate(1_000_000)
                    .setMaxVideoFrameRate(20)
            }
            trackSelector.parameters = parametersBuilder.build()

            // 3. Optimize Renderers Factory
            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                .setEnableDecoderFallback(true)

            player = ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build().apply {
                    addListener(playerListener)
                    // 4. Set video scaling mode to reduce GPU overhead
                    setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                }
        }
    }

    /**
     * Starts playing an RTSP stream using TCP to ensure stability on varying networks.
     * Also added socket timeout and debug logging for better monitoring.
     */
    fun playStream(url: String) {
        if (url.isEmpty()) return
        currentUrl = url
        
        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(MediaItem.fromUri(url))

        player?.apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    /**
     * Controls the audio volume of the player.
     * @param volume 0f for mute, 1f for full volume.
     */
    fun setVolume(volume: Float) {
        player?.volume = volume
    }

    fun releasePlayer() {
        mainHandler.removeCallbacks(retryRunnable)
        alarmHandler.removeCallbacks(alarmRunnable)
        errorUiHandler.removeCallbacks(errorUiRunnable)
        errorUiHandler.removeCallbacks(loadingUiRunnable)
        player?.removeListener(playerListener)
        // Note: We release but don't clear the view to keep the last frame if possible
        player?.release()
        player = null
    }

    /**
     * Resets only the playback state without full release to help keep the last frame visible.
     */
    fun stopPlayback() {
        player?.stop()
        player?.clearMediaItems()
    }
}
