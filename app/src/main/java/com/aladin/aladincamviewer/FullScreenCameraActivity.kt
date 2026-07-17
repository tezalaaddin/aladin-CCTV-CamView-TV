package com.aladin.aladincamviewer

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.*
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Premium Full-screen viewer with joystick-style PTZ and touch-optimized controls.
 * Highly stable and crash-resistant version.
 */
@OptIn(UnstableApi::class)
class FullScreenCameraActivity : AppCompatActivity() {

    private var playerManager: CctvPlayerManager? = null
    private var playerView: PlayerView? = null
    private var progressBar: ProgressBar? = null
    private var errorText: TextView? = null
    private var clockText: TextView? = null
    private var tourIndicator: TextView? = null
    private var playbackIndicator: TextView? = null
    private var statsText: TextView? = null
    private var zoomOsd: TextView? = null
    private var ptzOverlay: View? = null
    private var controlPanel: View? = null
    private var topBar: View? = null
    private var networkErrorLayout: LinearLayout? = null
    private var networkMonitor: NetworkMonitor? = null

    private var currentCamera: CameraModel? = null
    private var ptzManager: PtzManager? = null
    private var isPtzMode = false
    private var isDigitalZoomMode = false
    private var isPlaybackMode = false
    private var currentZoom = 1.0f
    private var tourCameras: List<CameraModel>? = null
    private var currentTourIndex = 0
    private var isTourMode = false
    
    private val uiHandler = Handler(Looper.getMainLooper())
    private val autoHideControlsRunnable = Runnable { hideUi() }

    private val tourHandler = Handler(Looper.getMainLooper())
    private val tourRunnable = object : Runnable {
        override fun run() {
            cycleTour()
            tourHandler.postDelayed(this, 10000)
        }
    }

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            clockText?.text = sdf.format(Date())
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Bind views with null-safety
        playerView = findViewById(R.id.full_player_view)
        progressBar = findViewById(R.id.loading_progress)
        errorText = findViewById(R.id.error_text)
        clockText = findViewById(R.id.clock_text)
        tourIndicator = findViewById(R.id.tour_indicator)
        playbackIndicator = findViewById(R.id.playback_indicator)
        statsText = findViewById(R.id.stats_text)
        zoomOsd = findViewById(R.id.zoom_osd)
        ptzOverlay = findViewById(R.id.ptz_overlay)
        controlPanel = findViewById(R.id.control_panel)
        topBar = findViewById(R.id.top_bar_full)
        networkErrorLayout = findViewById(R.id.network_error_layout)

        setupButtons()

        currentCamera = intent.getParcelableExtra("camera_data")
        currentCamera?.let {
            findViewById<TextView>(R.id.cam_title_full)?.text = it.name
            if (it.ptzSupported) ptzManager = PtzManager(it)
        }

        isTourMode = intent.getBooleanExtra("tour_mode", false)
        tourCameras = intent.getParcelableArrayListExtra("camera_list")
        currentTourIndex = intent.getIntExtra("start_index", 0)

        if (isTourMode) tourIndicator?.visibility = View.VISIBLE

        playerManager = CctvPlayerManager(this, { isLoading, error ->
            progressBar?.isVisible = isLoading
            errorText?.text = error
            errorText?.isVisible = error != null
        }, { format ->
            format?.let {
                val bitrateStr = if (it.bitrate > 0) "${it.bitrate / 1000} kbps" else "N/A"
                statsText?.text = "${it.width}x${it.height} | ${it.frameRate.toInt()}fps | $bitrateStr"
            }
        })

        networkMonitor = NetworkMonitor(this) { isConnected ->
            if (isConnected) {
                networkErrorLayout?.visibility = View.GONE
                if (!isTourMode) currentCamera?.let { startStreaming(it) }
            } else {
                networkErrorLayout?.visibility = View.VISIBLE
            }
        }

        playerView?.setOnClickListener { toggleUi() }
        showUi()
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btn_ptz_toggle)?.setOnClickListener { togglePtz(); resetUiHideTimer() }
        findViewById<View>(R.id.btn_zoom_toggle)?.setOnClickListener { toggleDigitalZoom(); resetUiHideTimer() }
        findViewById<View>(R.id.btn_snapshot)?.setOnClickListener { takeSnapshot(); resetUiHideTimer() }
        findViewById<View>(R.id.btn_playback)?.setOnClickListener { showPlaybackDatePicker(); resetUiHideTimer() }

        // Joystick with Release-to-Stop
        val joystickMap = mapOf(
            R.id.ptz_up to { ptzManager?.moveUp() },
            R.id.ptz_down to { ptzManager?.moveDown() },
            R.id.ptz_left to { ptzManager?.moveLeft() },
            R.id.ptz_right to { ptzManager?.moveRight() }
        )

        joystickMap.forEach { (id, action) ->
            findViewById<View>(id)?.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> { action(); true }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { ptzManager?.stop(); true }
                    else -> false
                }
            }
        }
    }

    private fun toggleUi() {
        if (controlPanel?.visibility == View.VISIBLE) hideUi() else showUi()
    }

    private fun showUi() {
        controlPanel?.visibility = View.VISIBLE
        topBar?.visibility = View.VISIBLE
        resetUiHideTimer()
    }

    private fun hideUi() {
        if (isPtzMode) return 
        controlPanel?.visibility = View.GONE
        topBar?.visibility = View.GONE
    }

    private fun resetUiHideTimer() {
        uiHandler.removeCallbacks(autoHideControlsRunnable)
        uiHandler.postDelayed(autoHideControlsRunnable, 5000)
    }

    private fun togglePtz() {
        if (ptzManager != null) {
            isPtzMode = !isPtzMode
            isDigitalZoomMode = false
            updateUiModes()
            Toast.makeText(this, if (isPtzMode) "PTZ ACTIVE" else "PTZ OFF", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "PTZ Not Supported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleDigitalZoom() {
        isDigitalZoomMode = !isDigitalZoomMode
        isPtzMode = false
        updateUiModes()
        if (isDigitalZoomMode) {
            updateZoomOsd()
            Toast.makeText(this, "Digital Zoom Active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUiModes() {
        ptzOverlay?.visibility = if (isPtzMode) View.VISIBLE else View.GONE
        zoomOsd?.visibility = if (isDigitalZoomMode && currentZoom > 1.0f) View.VISIBLE else View.GONE
        playbackIndicator?.visibility = if (isPlaybackMode) View.VISIBLE else View.GONE
    }

    private fun startStreaming(camera: CameraModel) {
        playerManager?.initializePlayer()
        playerView?.player = playerManager?.player
        playerManager?.playStream(camera.mainStreamUrl)
    }

    private fun cycleTour() {
        tourCameras?.let { cameras ->
            if (cameras.isNotEmpty()) {
                val camera = cameras[currentTourIndex]
                startStreaming(camera)
                currentTourIndex = (currentTourIndex + 1) % cameras.size
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        resetUiHideTimer()
        when (keyCode) {
            KeyEvent.KEYCODE_PROG_RED -> { togglePtz(); return true }
            KeyEvent.KEYCODE_PROG_GREEN -> { toggleDigitalZoom(); return true }
            KeyEvent.KEYCODE_PROG_YELLOW -> { takeSnapshot(); return true }
            KeyEvent.KEYCODE_PROG_BLUE -> { showPlaybackDatePicker(); return true }
            KeyEvent.KEYCODE_INFO -> { statsText?.isVisible = !(statsText?.isVisible ?: true); return true }
        }
        
        if (isPtzMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { ptzManager?.moveUp(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { ptzManager?.moveDown(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { ptzManager?.moveLeft(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { ptzManager?.moveRight(); return true }
            }
        }

        if (isDigitalZoomMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP -> { adjustZoom(0.2f); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { adjustZoom(-0.2f); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isPtzMode) ptzManager?.stop()
        return super.onKeyUp(keyCode, event)
    }

    private fun adjustZoom(delta: Float) {
        currentZoom = (currentZoom + delta).coerceIn(1.0f, 4.0f)
        playerView?.scaleX = currentZoom
        playerView?.scaleY = currentZoom
        updateZoomOsd()
    }

    private fun updateZoomOsd() {
        zoomOsd?.text = String.format("ZOOM: %.1fx", currentZoom)
        zoomOsd?.visibility = View.VISIBLE
    }

    private fun takeSnapshot() {
        val surfaceView = playerView?.videoSurfaceView as? SurfaceView ?: return
        val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(surfaceView, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) saveBitmapToStorage(bitmap)
        }, Handler(Looper.getMainLooper()))
    }

    private fun saveBitmapToStorage(bitmap: Bitmap) {
        val filename = "CCTV_${System.currentTimeMillis()}.jpg"
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AladinCam")
            }
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            contentResolver.openOutputStream(imageUri!!)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            Toast.makeText(this, "Snapshot Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error Saving Image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlaybackDatePicker() {
        val calendar = Calendar.getInstance()
        android.app.DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)
            android.app.TimePickerDialog(this, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                startPlayback(calendar.time)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun startPlayback(time: Date) {
        val playbackUrl = generatePlaybackUrl(currentCamera!!, time)
        isPlaybackMode = true
        updateUiModes()
        playerManager?.playStream(playbackUrl)
    }

    private fun generatePlaybackUrl(camera: CameraModel, time: Date): String {
        val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.getDefault())
        val startTime = sdf.format(time)
        return "rtsp://${camera.username}:${camera.password}@${camera.ipAddress}:554/Streaming/tracks/101/?starttime=$startTime"
    }

    override fun onStart() {
        super.onStart()
        clockHandler.post(clockRunnable)
        networkMonitor?.start()
        if (isTourMode) tourHandler.post(tourRunnable) else currentCamera?.let { startStreaming(it) }
    }

    override fun onStop() {
        super.onStop()
        playerManager?.releasePlayer()
        clockHandler.removeCallbacks(clockRunnable)
        tourHandler.removeCallbacks(tourRunnable)
        uiHandler.removeCallbacks(autoHideControlsRunnable)
        networkMonitor?.stop()
    }
}
