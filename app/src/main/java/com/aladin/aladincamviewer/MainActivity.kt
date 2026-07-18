package com.aladin.aladincamviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var clockText: TextView
    private lateinit var pageIndicator: TextView
    private lateinit var networkErrorLayout: LinearLayout
    private lateinit var networkMonitor: NetworkMonitor
    
    private var backPressedTime: Long = 0
    private var currentCameras: List<CameraEntity> = emptyList()
    private var currentPage = 0
    private val pageSize = 4
    
    private val viewModel: MainViewModel by viewModels()
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            clockText.text = sdf.format(Date())
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("aladin_prefs_v2", Context.MODE_PRIVATE)
            .getString("app_lang", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AladinCamViewer)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        recyclerView = findViewById(R.id.camera_recycler_view)
        clockText = findViewById(R.id.clock_text)
        pageIndicator = findViewById(R.id.page_indicator)
        networkErrorLayout = findViewById(R.id.network_error_layout)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        networkMonitor = NetworkMonitor(this) { isConnected ->
            if (isConnected) {
                networkErrorLayout.visibility = View.GONE
                if (recyclerView.adapter == null) observeCameras()
            } else {
                networkErrorLayout.visibility = View.VISIBLE
            }
        }

        findViewById<View>(R.id.btn_settings_top).setOnClickListener { openSettings() }
        findViewById<View>(R.id.btn_tour_top).setOnClickListener { startTour() }

        checkLanguage()
        observeCameras()
    }

    private fun checkLanguage() {
        val prefs = PreferenceHelper(this)
        if (prefs.appLanguage.isEmpty()) {
            showLanguagePicker()
        }
    }

    private fun showLanguagePicker() {
        val languages = arrayOf("English", "Türkçe")
        val codes = arrayOf("en", "tr")
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Language / Dil Seçin")
            .setCancelable(false)
            .setItems(languages) { _, which ->
                val prefs = PreferenceHelper(this)
                prefs.appLanguage = codes[which]
                recreate()
            }
            .show()
    }

    private fun observeCameras() {
        lifecycleScope.launch {
            viewModel.allCameras.collect { cameras ->
                currentCameras = cameras
                displayCurrentPage()
            }
        }
    }

    private fun openSettings() {
        SecurityUtils.checkPin(this) { success ->
            if (success) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                Toast.makeText(this, "Access Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startTour() {
        recyclerView.adapter = null
        val intent = Intent(this, FullScreenCameraActivity::class.java).apply {
            putExtra("tour_mode", true)
            putParcelableArrayListExtra("camera_list", ArrayList(currentCameras.map { it.toModel() }))
        }
        startActivity(intent)
    }

    private fun displayCurrentPage(focusIndex: Int = -1) {
        if (currentCameras.isEmpty()) return
        
        val start = currentPage * pageSize
        val end = minOf(start + pageSize, currentCameras.size)
        val pageItems = currentCameras.subList(start, end).map { it.toModel() }
        
        recyclerView.adapter = CameraAdapter(pageItems)
        val totalPages = (currentCameras.size + pageSize - 1) / pageSize
        pageIndicator.text = "Page ${currentPage + 1}/$totalPages"

        if (focusIndex != -1) {
            recyclerView.post {
                recyclerView.layoutManager?.findViewByPosition(focusIndex)?.requestFocus()
            }
        }
    }

    private fun CameraEntity.toModel() = CameraModel(
        name = name,
        mainStreamUrl = mainStreamUrl,
        subStreamUrl = subStreamUrl,
        ipAddress = ipAddress,
        ptzSupported = ptzSupported,
        username = username,
        password = password,
        brand = brand
    )

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings()
            return true
        }

        val focusedView = currentFocus
        val position = if (focusedView != null) {
            val containingView = recyclerView.findContainingItemView(focusedView)
            if (containingView != null) recyclerView.getChildAdapterPosition(containingView) else -1
        } else -1

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if ((position == 1 || position == 3) && (currentPage + 1) * pageSize < currentCameras.size) {
                    currentPage++
                    displayCurrentPage(focusIndex = position - 1)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if ((position == 0 || position == 2) && currentPage > 0) {
                    currentPage--
                    displayCurrentPage(focusIndex = position + 1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
    }

    override fun onStart() {
        super.onStart()
        networkMonitor.start()
    }

    override fun onStop() {
        super.onStop()
        recyclerView.adapter = null
        networkMonitor.stop()
    }

    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            SecurityUtils.checkPin(this) { success ->
                if (success) super.onBackPressed()
            }
            return
        } else {
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
        backPressedTime = System.currentTimeMillis()
    }
}
