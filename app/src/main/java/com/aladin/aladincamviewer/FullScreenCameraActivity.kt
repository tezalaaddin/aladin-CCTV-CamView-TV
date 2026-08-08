package com.aladin.aladincamviewer

import android.content.Context
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.text.SimpleDateFormat
import java.util.*

class FullScreenCameraActivity : AppCompatActivity() {

    private var videoLayout: VLCVideoLayout? = null
    private var playerManager: CctvPlayerManager? = null
    private var ptzManager: PtzManager? = null
    
    private var progressBar: ProgressBar? = null
    private var errorText: TextView? = null
    private var camTitle: TextView? = null
    private var clockText: TextView? = null
    private var ptzOverlay: View? = null
    
    private var isPtzMode = false
    private var tourJob: Job? = null
    private var isTourMode = false
    private var tourCameras: ArrayList<CameraModel>? = null
    private var currentTourIndex = 0
    private var playbackStartEpochMs = 0L
    private var playbackEndEpochMs = 0L
    private var playbackUsesAbsoluteTime = false
    private var playbackUsesUrlTime = false
    private var playbackUrl: String? = null
    private var isSeekingPlayback = false
    private var playbackRateIndex = 1
    private val playbackRates = floatArrayOf(0.5f, 1f, 2f, 4f)
    private var playbackChromeVisible = true
    private val hidePlaybackChrome = Runnable { setPlaybackChromeVisible(false) }

    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            if (playbackStartEpochMs == 0L) {
                clockText?.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            } else {
                updatePlaybackPosition()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("aladin_prefs_v2", Context.MODE_PRIVATE)
            .getString("app_lang", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_full_screen)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isPtzMode) togglePtzMode(false) else finish()
            }
        })
        TvFocusManager.install(this)

        videoLayout = findViewById(R.id.full_player_view)
        progressBar = findViewById(R.id.loading_progress)
        errorText = findViewById(R.id.error_text)
        camTitle = findViewById(R.id.cam_title_full)
        clockText = findViewById(R.id.clock_text)
        ptzOverlay = findViewById(R.id.ptz_overlay)

        val currentCamera: CameraModel? = intent.getParcelableExtra("camera_data")
        playbackUrl = intent.getStringExtra("playback_url")
        isTourMode = intent.getBooleanExtra("tour_mode", false)
        tourCameras = intent.getParcelableArrayListExtra("camera_list")
        currentTourIndex = intent.getIntExtra("start_index", 0)

        playerManager = CctvPlayerManager(
            onStateChanged = { isLoading, error ->
                runOnUiThread {
                    progressBar?.isVisible = isLoading
                    errorText?.text = error
                    errorText?.isVisible = error != null
                }
            }
        )

        videoLayout?.let { playerManager?.attachView(it) }
        
        startClock()
        
        if (!playbackUrl.isNullOrBlank()) {
            camTitle?.text = intent.getStringExtra("playback_title") ?: getString(R.string.recordings_title)
            setupPlaybackControls()
            playerManager?.initializePlayer(forceSoftwareDecoder = true)
            playerManager?.playStream(playbackUrl!!, intent.getLongExtra("playback_start_offset_ms", 0L))
            playerManager?.fitVideoToScreen(true)
            if (playbackUsesAbsoluteTime && !playbackUsesUrlTime) {
                playerManager?.seekTo(playbackStartEpochMs)
            }
        } else if (isTourMode) {
            startTour()
        } else {
            currentCamera?.let { playCamera(it) }
        }

        if (currentCamera?.recorderId ?: 0 > 0) {
            findViewById<View>(R.id.btn_recordings)?.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    playerManager?.releasePlayer()
                    startActivity(android.content.Intent(this@FullScreenCameraActivity, RecordingsActivity::class.java)
                        .putExtra("recorder_id", currentCamera!!.recorderId))
                    finish()
                }
            }
        }
        
        setupPtzButtons()
        
        // Initial focus for side buttons
        findViewById<View>(R.id.btn_ptz_toggle)?.requestFocus()
    }

    private fun playCamera(camera: CameraModel) {
        ptzManager?.close()
        camTitle?.text = camera.name
        ptzManager = PtzManager(camera)
        playerManager?.playStream(camera.mainStreamUrl)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        if (isPtzMode) {
            // High-reliability D-pad redirection for PTZ
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> ptzManager?.moveUp()
                            KeyEvent.KEYCODE_DPAD_DOWN -> ptzManager?.moveDown()
                            KeyEvent.KEYCODE_DPAD_LEFT -> ptzManager?.moveLeft()
                            KeyEvent.KEYCODE_DPAD_RIGHT -> ptzManager?.moveRight()
                        }
                    } else if (action == KeyEvent.ACTION_UP) {
                        ptzManager?.stop()
                    }
                    return true
                }
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                    AppLog.d("ALADIN_PTZ", "Remote zoom key action=${if (action == KeyEvent.ACTION_DOWN) "in" else "stop"} keyCode=$keyCode")
                    if (action == KeyEvent.ACTION_DOWN) ptzManager?.zoomIn() else ptzManager?.stop()
                    return true
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                    AppLog.d("ALADIN_PTZ", "Remote zoom key action=${if (action == KeyEvent.ACTION_DOWN) "out" else "stop"} keyCode=$keyCode")
                    if (action == KeyEvent.ACTION_DOWN) ptzManager?.zoomOut() else ptzManager?.stop()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupPtzButtons() {
        findViewById<View>(R.id.btn_ptz_toggle)?.setOnClickListener {
            togglePtzMode(!isPtzMode)
        }

        findViewById<View>(R.id.btn_snapshot)?.setOnClickListener {
            val playerView = videoLayout ?: return@setOnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                SnapshotUtils.takeSnapshot(playerView, camTitle?.text?.toString().orEmpty())
            } else {
                Toast.makeText(this, R.string.snapshot_not_supported, Toast.LENGTH_SHORT).show()
            }
        }

        // On-screen touch mapping (4 directions + zoom)
        mapPtz(R.id.ptz_up) { ptzManager?.moveUp() }
        mapPtz(R.id.ptz_down) { ptzManager?.moveDown() }
        mapPtz(R.id.ptz_left) { ptzManager?.moveLeft() }
        mapPtz(R.id.ptz_right) { ptzManager?.moveRight() }
        mapPtz(R.id.ptz_zoom_in) { ptzManager?.zoomIn() }
        mapPtz(R.id.ptz_zoom_out) { ptzManager?.zoomOut() }
    }

    private fun mapPtz(id: Int, action: () -> Unit) {
        findViewById<View>(id)?.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { action(); true }
                android.view.MotionEvent.ACTION_UP -> { ptzManager?.stop(); view.performClick(); true }
                android.view.MotionEvent.ACTION_CANCEL -> { ptzManager?.stop(); true }
                else -> false
            }
        }
    }

    private fun togglePtzMode(enabled: Boolean) {
        isPtzMode = enabled
        findViewById<View>(R.id.btn_ptz_toggle)?.isSelected = enabled
        ptzOverlay?.visibility = if (isPtzMode) View.VISIBLE else View.GONE
        
        if (isPtzMode) {
            currentFocus?.clearFocus()
            Toast.makeText(this, R.string.ptz_on, Toast.LENGTH_SHORT).show()
        } else {
            findViewById<View>(R.id.btn_ptz_toggle)?.requestFocus()
            Toast.makeText(this, R.string.ptz_off, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTour() {
        tourJob = lifecycleScope.launch {
            while (true) {
                val cam = tourCameras?.get(currentTourIndex) ?: break
                playCamera(cam)
                delay(10000)
                currentTourIndex = (currentTourIndex + 1) % (tourCameras?.size ?: 1)
            }
        }
    }

    private fun startClock() {
        handler.post(clockRunnable)
    }

    private fun setupPlaybackControls() {
        playbackStartEpochMs = intent.getLongExtra("playback_start_epoch_ms", 0L)
        playbackEndEpochMs = intent.getLongExtra("playback_end_epoch_ms", playbackStartEpochMs + 86_399_000L)
        playbackUsesAbsoluteTime = intent.getBooleanExtra("playback_absolute_time", false)
        playbackUsesUrlTime = intent.getBooleanExtra("playback_time_in_url", false)

        findViewById<View>(R.id.control_panel).visibility = View.GONE
        findViewById<View>(R.id.playback_controls).visibility = View.VISIBLE
        findViewById<TextView>(R.id.stream_status_text).setText(R.string.playback_recorded)

        (videoLayout?.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            params.width = 0
            params.height = 0
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToBottom = R.id.top_bar_full
            params.bottomToTop = R.id.playback_controls
            videoLayout?.layoutParams = params
        }

        val seekBar = findViewById<SeekBar>(R.id.playback_seekbar)
        seekBar.max = ((playbackEndEpochMs - playbackStartEpochMs) / 1000L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeekingPlayback = true
                handler.removeCallbacks(hidePlaybackChrome)
            }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) showPlaybackTime(playbackStartEpochMs + progress * 1000L)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val targetEpoch = playbackStartEpochMs + seekBar.progress * 1000L
                seekPlaybackTo(targetEpoch)
                isSeekingPlayback = false
                schedulePlaybackChromeHide()
            }
        })

        findViewById<View>(R.id.playback_rewind).setOnClickListener { skipPlayback(-30_000L); schedulePlaybackChromeHide() }
        findViewById<View>(R.id.playback_forward).setOnClickListener { skipPlayback(30_000L); schedulePlaybackChromeHide() }
        findViewById<Button>(R.id.playback_pause).setOnClickListener { button ->
            val paused = playerManager?.togglePause() ?: false
            (button as Button).setText(if (paused) R.string.playback_resume else R.string.playback_pause)
            schedulePlaybackChromeHide()
        }
        findViewById<TextView>(R.id.playback_speed).setOnClickListener {
            playbackRateIndex = (playbackRateIndex + 1) % playbackRates.size
            val rate = playbackRates[playbackRateIndex]
            if (playerManager?.setPlaybackRate(rate) == true) {
                (it as TextView).text = "${rate.toString().removeSuffix(".0")}x"
            }
            schedulePlaybackChromeHide()
        }
        videoLayout?.setOnClickListener { setPlaybackChromeVisible(!playbackChromeVisible) }
        showPlaybackTime(playbackStartEpochMs)
        seekBar.requestFocus()
        schedulePlaybackChromeHide()
    }

    private fun schedulePlaybackChromeHide() {
        handler.removeCallbacks(hidePlaybackChrome)
        handler.postDelayed(hidePlaybackChrome, 6_000L)
    }

    private fun setPlaybackChromeVisible(visible: Boolean) {
        playbackChromeVisible = visible
        findViewById<View>(R.id.top_bar_full).visibility = if (visible) View.VISIBLE else View.GONE
        findViewById<View>(R.id.playback_controls).visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) schedulePlaybackChromeHide()
    }

    private fun skipPlayback(deltaMs: Long) {
        val raw = playerManager?.playbackTimeMs() ?: return
        val currentEpoch = if (playbackUsesAbsoluteTime) raw else playbackStartEpochMs + raw
        val targetEpoch = (currentEpoch + deltaMs).coerceIn(playbackStartEpochMs, playbackEndEpochMs)
        seekPlaybackTo(targetEpoch)
    }

    private fun seekPlaybackTo(targetEpoch: Long) {
        if (!playbackUsesUrlTime) {
            playerManager?.seekTo(if (playbackUsesAbsoluteTime) targetEpoch else targetEpoch - playbackStartEpochMs)
            return
        }
        val current = playbackUrl ?: return
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(java.time.ZoneOffset.UTC)
        val startValue = formatter.format(java.time.Instant.ofEpochMilli(targetEpoch))
        val endValue = formatter.format(java.time.Instant.ofEpochMilli(playbackEndEpochMs))
        val withoutTimes = current
            .replace(Regex("(?i)&?starttime=[^&]*"), "")
            .replace(Regex("(?i)&?endtime=[^&]*"), "")
            .trimEnd('?', '&')
        val separator = if ('?' in withoutTimes) "&" else "?"
        playbackUrl = "$withoutTimes${separator}starttime=$startValue&endtime=$endValue"
        playerManager?.playStream(playbackUrl!!)
        playerManager?.fitVideoToScreen(true)
        showPlaybackTime(targetEpoch)
    }

    private fun updatePlaybackPosition() {
        val raw = playerManager?.playbackTimeMs() ?: return
        val epoch = if (playbackUsesAbsoluteTime) raw else playbackStartEpochMs + raw
        if (epoch !in playbackStartEpochMs..playbackEndEpochMs) return
        showPlaybackTime(epoch)
        if (!isSeekingPlayback) {
            findViewById<SeekBar>(R.id.playback_seekbar).progress = ((epoch - playbackStartEpochMs) / 1000L).toInt()
        }
    }

    private fun showPlaybackTime(epochMs: Long) {
        val formatted = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
        findViewById<TextView>(R.id.playback_datetime).text = formatted
        clockText?.text = formatted
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(hidePlaybackChrome)
        ptzManager?.close()
        playerManager?.releasePlayer()
        tourJob?.cancel()
    }
}
