package com.aladin.aladincamviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import androidx.core.widget.doAfterTextChanged

class SettingsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var pinInput: EditText
    private lateinit var offlineAlarmCheck: CheckBox
    private var pinChanged = false
    
    private val viewModel: SettingsViewModel by viewModels()
    private var currentCameras: List<CameraEntity> = emptyList()
    private var currentRecorderChannels: List<RecorderChannelWithRecorder> = emptyList()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportConfig(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importConfig(it) }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("aladin_prefs_v2", Context.MODE_PRIVATE)
            .getString("app_lang", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        TvFocusManager.install(this)

        recyclerView = findViewById(R.id.settings_recycler_view)
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        recyclerView.layoutManager = GridLayoutManager(this, if (isLandscape) 4 else 2)

        pinInput = findViewById(R.id.app_pin)
        offlineAlarmCheck = findViewById(R.id.check_offline_alarm)

        findViewById<Button>(R.id.btn_scan).setOnClickListener { 
            startActivity(Intent(this, DiscoveryActivity::class.java)) 
        }
        findViewById<Button>(R.id.btn_export).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.export_configuration)
                .setMessage(R.string.export_security_notice)
                .setPositiveButton(R.string.export_btn) { _, _ -> exportLauncher.launch("aladin_cctv_config_v2.json") }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        findViewById<Button>(R.id.btn_import).setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        findViewById<Button>(R.id.btn_language).setOnClickListener { showLanguagePicker() }
        findViewById<Button>(R.id.btn_about).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<Button>(R.id.btn_recorders).setOnClickListener {
            startActivity(Intent(this, RecordersActivity::class.java))
        }

        setupObservers()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.allCameras.collect { cameras ->
                currentCameras = cameras
                setupGrid()
            }
        }
        lifecycleScope.launch {
            viewModel.recorderChannels.collect { channels ->
                currentRecorderChannels = channels
                setupGrid()
            }
        }
        pinInput.hint = if (viewModel.hasPin()) getString(R.string.pin_configured_hint) else getString(R.string.pin_hint)
        pinInput.doAfterTextChanged { pinChanged = true }
        offlineAlarmCheck.isChecked = viewModel.isOfflineAlarmEnabled()
        offlineAlarmCheck.setOnCheckedChangeListener { _, isChecked -> viewModel.updateOfflineAlarm(isChecked) }
        val prefs = PreferenceHelper(this)
        findViewById<CheckBox>(R.id.check_start_on_boot).apply {
            isChecked = prefs.startOnBoot
            setOnCheckedChangeListener { _, checked -> prefs.startOnBoot = checked }
        }
        findViewById<CheckBox>(R.id.check_network_recovery).apply {
            isChecked = prefs.automaticNetworkRecovery
            setOnCheckedChangeListener { _, checked ->
                prefs.automaticNetworkRecovery = checked
                val repository = CameraRepository(this@SettingsActivity, AppDatabase.getDatabase(this@SettingsActivity).cameraDao())
                val tracker = NetworkTracker.getInstance(this@SettingsActivity, repository)
                if (checked) tracker.startTracking() else tracker.stopTracking()
            }
        }
        findViewById<CheckBox>(R.id.check_daily_maintenance).apply {
            isChecked = prefs.dailyMaintenance
            setOnCheckedChangeListener { _, checked ->
                prefs.dailyMaintenance = checked
                if (checked) CctvWatchdog.scheduleDailyRestart(this@SettingsActivity)
                else CctvWatchdog.cancelDailyRestart(this@SettingsActivity)
            }
        }
        findViewById<CheckBox>(R.id.check_diagnostic_logging).apply {
            isChecked = prefs.diagnosticLogging
            setOnCheckedChangeListener { _, checked ->
                prefs.diagnosticLogging = checked
                AppLog.initialize(this@SettingsActivity)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (pinChanged) viewModel.updatePin(pinInput.text.toString())
    }

    private fun setupGrid() {
        val slotCount = maxOf(
            16,
            currentCameras.maxOfOrNull { it.displayOrder } ?: 0,
            currentCameras.size + currentRecorderChannels.size
        )
        val slots = (1..slotCount).associateWith { order ->
            currentCameras.find { it.displayOrder == order }?.let { camera ->
                SettingsSlot(order, camera.name, camera.ipAddress, cameraId = camera.id)
            }
        }.toMutableMap()
        val freeOrders = (1..slotCount).filter { slots[it] == null }.iterator()
        currentRecorderChannels.forEach { row ->
            if (!freeOrders.hasNext()) return@forEach
            val order = freeOrders.next()
            slots[order] = SettingsSlot(
                order = order,
                name = "${row.recorder.name} • ${row.channel.name}",
                status = "${row.recorder.manufacturer} • ${row.recorder.ipAddress}",
                recorderId = row.recorder.id
            )
        }
        recyclerView.adapter = CameraSlotAdapter((1..slotCount).map { order ->
            slots[order] ?: SettingsSlot(order, getString(R.string.slot_label, order), getString(R.string.not_configured))
        }) { slot ->
            if (slot.recorderId != 0L) {
                startActivity(Intent(this, RecordersActivity::class.java).putExtra("recorder_id", slot.recorderId))
            } else {
                startActivity(Intent(this, EditCameraActivity::class.java)
                    .putExtra("camera_id", slot.cameraId)
                    .putExtra("display_order", slot.order))
            }
        }
    }

    private fun showLanguagePicker() {
        val languages = arrayOf("English", "Türkçe")
        val codes = arrayOf("en", "tr")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Language")
            .setItems(languages) { _, which ->
                val prefs = PreferenceHelper(this)
                prefs.appLanguage = codes[which]
                restartApp()
            }
            .show()
    }

    private fun exportConfig(uri: Uri) {
        contentResolver.openOutputStream(uri)?.let {
            viewModel.exportConfig(it, currentCameras) { result -> runOnUiThread {
                Toast.makeText(this, if (result.isSuccess) R.string.config_exported else R.string.export_failed, Toast.LENGTH_SHORT).show()
            } }
        }
    }

    private fun importConfig(uri: Uri) {
        contentResolver.openInputStream(uri)?.let {
            viewModel.importConfig(it) { result ->
                runOnUiThread {
                    if (result.isSuccess) {
                        Toast.makeText(this, getString(R.string.import_success_count, result.getOrDefault(0)), Toast.LENGTH_SHORT).show()
                        restartApp()
                    } else {
                        Toast.makeText(this, getString(R.string.import_failed_detail, result.exceptionOrNull()?.message.orEmpty()), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun restartApp() {
        finishAffinity()
        startActivity(packageManager.getLaunchIntentForPackage(packageName))
    }

    private data class SettingsSlot(
        val order: Int,
        val name: String,
        val status: String,
        val cameraId: Int = 0,
        val recorderId: Long = 0
    )

    private class CameraSlotAdapter(private val slots: List<SettingsSlot>, private val onClick: (SettingsSlot) -> Unit) :
        RecyclerView.Adapter<CameraSlotAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.slot_name)
            val status: TextView = view.findViewById(R.id.slot_status)
            val indicator: View = view.findViewById(R.id.slot_indicator)
            val card: MaterialCardView = view as MaterialCardView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_camera_slot, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val slot = slots[position]
            holder.name.text = slot.name
            holder.status.text = slot.status
            holder.itemView.setOnClickListener { onClick(slot) }
            
            if (slot.cameraId == 0 && slot.recorderId == 0L) {
                holder.indicator.setBackgroundResource(R.drawable.led_offline)
            } else {
                holder.indicator.setBackgroundResource(R.drawable.led_online)
            }
        }

        override fun getItemCount() = slots.size
    }
}
