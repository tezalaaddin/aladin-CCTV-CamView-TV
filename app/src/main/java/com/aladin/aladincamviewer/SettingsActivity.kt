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

class SettingsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var pinInput: EditText
    private lateinit var offlineAlarmCheck: CheckBox
    
    private val viewModel: SettingsViewModel by viewModels()
    private var currentCameras: List<CameraEntity> = emptyList()

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

        recyclerView = findViewById(R.id.settings_recycler_view)
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        recyclerView.layoutManager = GridLayoutManager(this, if (isLandscape) 4 else 2)

        pinInput = findViewById(R.id.app_pin)
        offlineAlarmCheck = findViewById(R.id.check_offline_alarm)

        findViewById<Button>(R.id.btn_scan).setOnClickListener { 
            startActivity(Intent(this, DiscoveryActivity::class.java)) 
        }
        findViewById<Button>(R.id.btn_export).setOnClickListener { exportLauncher.launch("cctv_config.json") }
        findViewById<Button>(R.id.btn_import).setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        findViewById<Button>(R.id.btn_language).setOnClickListener { showLanguagePicker() }

        setupObservers()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.allCameras.collect { cameras ->
                currentCameras = cameras
                setupGrid(cameras)
            }
        }
        pinInput.setText(viewModel.getPin())
        offlineAlarmCheck.isChecked = viewModel.isOfflineAlarmEnabled()
        offlineAlarmCheck.setOnCheckedChangeListener { _, isChecked -> viewModel.updateOfflineAlarm(isChecked) }
    }

    override fun onPause() {
        super.onPause()
        viewModel.updatePin(pinInput.text.toString())
    }

    private fun setupGrid(cameras: List<CameraEntity>) {
        val slots = (1..16).map { i ->
            cameras.find { it.displayOrder == i } ?: CameraEntity(name = "Empty", ipAddress = "", username = "", password = "", mainStreamUrl = "", subStreamUrl = "", displayOrder = i)
        }
        recyclerView.adapter = CameraSlotAdapter(slots) { camera ->
            val intent = Intent(this, EditCameraActivity::class.java)
            intent.putExtra("camera_id", camera.id)
            intent.putExtra("display_order", camera.displayOrder)
            startActivity(intent)
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
            viewModel.exportConfig(it, currentCameras)
            Toast.makeText(this, "Configuration Exported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importConfig(uri: Uri) {
        contentResolver.openInputStream(uri)?.let {
            viewModel.importConfig(it) {
                runOnUiThread {
                    Toast.makeText(this, "Import Successful", Toast.LENGTH_SHORT).show()
                    restartApp()
                }
            }
        }
    }

    private fun restartApp() {
        finishAffinity()
        startActivity(packageManager.getLaunchIntentForPackage(packageName))
    }

    private class CameraSlotAdapter(private val slots: List<CameraEntity>, private val onClick: (CameraEntity) -> Unit) :
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
            val context = holder.itemView.context
            holder.name.text = if (slot.name == "Empty") context.getString(R.string.slot_label, slot.displayOrder) else slot.name
            holder.status.text = if (slot.ipAddress.isEmpty()) context.getString(R.string.not_configured) else slot.ipAddress
            holder.itemView.setOnClickListener { onClick(slot) }
            
            if (slot.ipAddress.isEmpty()) {
                holder.indicator.setBackgroundResource(R.drawable.led_offline)
            } else {
                holder.indicator.setBackgroundResource(R.drawable.led_online)
            }
        }

        override fun getItemCount() = slots.size
    }
}
