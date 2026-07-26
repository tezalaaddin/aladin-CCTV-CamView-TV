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
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.appcompat.app.AlertDialog
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
    private lateinit var networkTracker: NetworkTracker
    private var recoveryDialog: AlertDialog? = null
    private var activeRecoveryProposal: RecoveryProposal? = null
    
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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleExitRequest()
        })
        TvFocusManager.install(this)

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
        val repository = CameraRepository(this, AppDatabase.getDatabase(this).cameraDao())
        networkTracker = NetworkTracker.getInstance(applicationContext, repository)
        observeRecoveryProposals()

        findViewById<View>(R.id.btn_settings_top).setOnClickListener { openSettings() }
        findViewById<View>(R.id.btn_tour_top).setOnClickListener { startTour() }

        checkLanguage()
        observeCameras()
    }

    private fun observeRecoveryProposals() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                networkTracker.recoveryProposals.collect { proposal ->
                    showRecoveryConfirmation(proposal)
                }
            }
        }
    }

    private fun showRecoveryConfirmation(proposal: RecoveryProposal) {
        activeRecoveryProposal?.let(networkTracker::rejectRecovery)
        recoveryDialog?.dismiss()
        activeRecoveryProposal = proposal
        recoveryDialog = AlertDialog.Builder(this)
            .setTitle(R.string.camera_ip_change_found)
            .setMessage(
                getString(
                    R.string.camera_ip_change_confirm,
                    proposal.cameraName,
                    proposal.oldIp,
                    proposal.newIp,
                    proposal.macAddress ?: getString(R.string.unknown_value)
                )
            )
            .setPositiveButton(R.string.use_new_camera_ip) { _, _ ->
                networkTracker.confirmRecovery(proposal)
                activeRecoveryProposal = null
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                networkTracker.rejectRecovery(proposal)
                activeRecoveryProposal = null
            }
            .setOnCancelListener {
                networkTracker.rejectRecovery(proposal)
                activeRecoveryProposal = null
            }
            .create()
        recoveryDialog?.show()
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
                Toast.makeText(this, R.string.access_denied, Toast.LENGTH_SHORT).show()
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
        pageIndicator.text = getString(R.string.page_format, currentPage + 1, totalPages)

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
        onvifUsername = onvifUsername,
        onvifPassword = onvifPassword,
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
        activeRecoveryProposal?.let(networkTracker::rejectRecovery)
        activeRecoveryProposal = null
        recoveryDialog?.dismiss()
        recoveryDialog = null
        recyclerView.adapter = null
        networkMonitor.stop()
    }

    private fun handleExitRequest() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            SecurityUtils.checkPin(this) { success ->
                if (success) finish()
            }
            return
        } else {
            Toast.makeText(this, R.string.press_back_exit, Toast.LENGTH_SHORT).show()
        }
        backPressedTime = System.currentTimeMillis()
    }
}
