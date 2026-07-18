package com.aladin.aladincamviewer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiscoveryActivity : AppCompatActivity() {

    private lateinit var hybridScanner: HybridScanner
    private lateinit var adapter: DiscoveryAdapter
    private lateinit var repository: CameraRepository
    private val devices = mutableListOf<DiscoveryDevice>()
    private var existingIps = setOf<String>()
    private var occupiedSlots = setOf<Int>()

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("aladin_settings", Context.MODE_PRIVATE)
            .getString("app_lang", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discovery)

        val cameraDao = AppDatabase.getDatabase(this).cameraDao()
        repository = CameraRepository(cameraDao)
        hybridScanner = HybridScanner(this)
        
        setupRecycler()
        loadExistingCameras()
        startScan()
    }

    private fun loadExistingCameras() {
        lifecycleScope.launch {
            repository.allCameras.collect { cameras ->
                existingIps = cameras.map { it.ipAddress }.toSet()
                occupiedSlots = cameras.map { it.displayOrder }.toSet()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupRecycler() {
        val rv = findViewById<RecyclerView>(R.id.recycler_discovery)
        adapter = DiscoveryAdapter(devices) { updateBatchButton() }
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = adapter
    }

    private fun startScan() {
        lifecycleScope.launch {
            findViewById<View>(R.id.radar_view).visibility = View.VISIBLE
            hybridScanner.startFullScan { discoveredList ->
                devices.clear()
                devices.addAll(discoveredList)
                devices.forEach { it.isAdded = existingIps.contains(it.ip) }
                
                findViewById<View>(R.id.radar_view).visibility = View.GONE
                findViewById<View>(R.id.recycler_discovery).visibility = View.VISIBLE
                findViewById<View>(R.id.btn_batch_add).visibility = View.VISIBLE
                
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun updateBatchButton() {
        val selectedCount = devices.count { it.isSelected && !it.isAdded }
        val btn = findViewById<MaterialButton>(R.id.btn_batch_add)
        btn.isEnabled = selectedCount > 0
        btn.text = getString(R.string.add_selected_btn, selectedCount)
        btn.setOnClickListener { showBatchAddDialog() }
    }

    private fun showBatchAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_batch_credentials, null)
        val userEdit = view.findViewById<EditText>(R.id.edit_username)
        val passEdit = view.findViewById<EditText>(R.id.edit_password)

        AlertDialog.Builder(this)
            .setTitle(R.string.camera_credentials)
            .setView(view)
            .setPositiveButton(R.string.add) { _, _ ->
                performBatchAdd(userEdit.text.toString(), passEdit.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performBatchAdd(user: String, pass: String) {
        val selectedDevices = devices.filter { it.isSelected && !it.isAdded }
        if (selectedDevices.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            val supervisor = SupervisorJob()
            val availableSlots = (1..16).filter { it !in occupiedSlots }.toMutableList()

            selectedDevices.forEach { device ->
                if (availableSlots.isEmpty()) return@forEach
                val nextSlot = availableSlots.removeAt(0)

                launch(supervisor) {
                    runCatching {
                        val camera = CameraEntity(
                            name = "Cam $nextSlot",
                            ipAddress = device.ip,
                            username = user,
                            password = pass,
                            mainStreamUrl = "rtsp://$user:$pass@${device.ip}:554/live/ch1",
                            subStreamUrl = "rtsp://$user:$pass@${device.ip}:554/live/ch0",
                            brand = device.brand,
                            ptzSupported = device.protocols.contains("ONVIF"),
                            displayOrder = nextSlot,
                            uuid = device.uuid ?: "",
                            macAddress = device.mac
                        )
                        repository.insert(camera)
                        withContext(Dispatchers.Main) { 
                            device.isAdded = true 
                            device.isSelected = false
                        }
                    }
                }
            }
        }
    }

    inner class DiscoveryAdapter(private val list: List<DiscoveryDevice>, private val onToggle: (DiscoveryDevice) -> Unit) : 
        RecyclerView.Adapter<DiscoveryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val brand: TextView = view.findViewById(R.id.txt_brand)
            val ip: TextView = view.findViewById(R.id.txt_ip)
            val check: CheckBox = view.findViewById(R.id.check_select)
            val addedBadge: View = view.findViewById(R.id.badge_added)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_discovery_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = list[position]
            holder.brand.text = device.brand
            holder.ip.text = device.ip
            holder.addedBadge.visibility = if (device.isAdded) View.VISIBLE else View.GONE
            holder.check.isEnabled = !device.isAdded
            holder.check.isChecked = device.isSelected
            holder.check.setOnCheckedChangeListener { _, isChecked ->
                device.isSelected = isChecked
                onToggle(device)
            }
        }

        override fun getItemCount() = list.size
    }
}
