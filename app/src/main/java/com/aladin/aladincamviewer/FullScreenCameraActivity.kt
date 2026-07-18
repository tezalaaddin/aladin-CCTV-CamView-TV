package com.aladin.aladincamviewer

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
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

    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            clockText?.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("aladin_settings", Context.MODE_PRIVATE)
            .getString("app_lang", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_full_screen)

        videoLayout = findViewById(R.id.full_player_view)
        progressBar = findViewById(R.id.loading_progress)
        errorText = findViewById(R.id.error_text)
        camTitle = findViewById(R.id.cam_title_full)
        clockText = findViewById(R.id.clock_text)
        ptzOverlay = findViewById(R.id.ptz_overlay)

        val currentCamera: CameraModel? = intent.getParcelableExtra("camera_data")
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
        
        if (isTourMode) {
            startTour()
        } else {
            currentCamera?.let { playCamera(it) }
        }
        
        setupPtzButtons()
        
        // Initial focus for side buttons
        findViewById<View>(R.id.btn_ptz_toggle)?.requestFocus()
    }

    private fun playCamera(camera: CameraModel) {
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
                KeyEvent.KEYCODE_BACK -> {
                    if (action == KeyEvent.ACTION_UP) {
                        togglePtzMode(false)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupPtzButtons() {
        findViewById<View>(R.id.btn_ptz_toggle)?.setOnClickListener {
            togglePtzMode(!isPtzMode)
        }

        // On-screen touch/click mapping (8 directions + zoom)
        mapPtz(R.id.ptz_up) { ptzManager?.moveUp() }
        mapPtz(R.id.ptz_down) { ptzManager?.moveDown() }
        mapPtz(R.id.ptz_left) { ptzManager?.moveLeft() }
        mapPtz(R.id.ptz_right) { ptzManager?.moveRight() }
        mapPtz(R.id.ptz_up_left) { ptzManager?.moveUpLeft() }
        mapPtz(R.id.ptz_up_right) { ptzManager?.moveUpRight() }
        mapPtz(R.id.ptz_down_left) { ptzManager?.moveDownLeft() }
        mapPtz(R.id.ptz_down_right) { ptzManager?.moveDownRight() }
        mapPtz(R.id.ptz_zoom_in) { ptzManager?.zoomIn() }
        mapPtz(R.id.ptz_zoom_out) { ptzManager?.zoomOut() }
    }

    private fun mapPtz(id: Int, action: () -> Unit) {
        findViewById<View>(id)?.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { action(); true }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { ptzManager?.stop(); true }
                else -> false
            }
        }
    }

    private fun togglePtzMode(enabled: Boolean) {
        isPtzMode = enabled
        ptzOverlay?.visibility = if (isPtzMode) View.VISIBLE else View.GONE
        
        if (isPtzMode) {
            currentFocus?.clearFocus()
            Toast.makeText(this, "PTZ Mode ACTIVE - Use D-Pad", Toast.LENGTH_SHORT).show()
        } else {
            findViewById<View>(R.id.btn_ptz_toggle)?.requestFocus()
            Toast.makeText(this, "PTZ OFF", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
        playerManager?.releasePlayer()
        tourJob?.cancel()
    }
}
