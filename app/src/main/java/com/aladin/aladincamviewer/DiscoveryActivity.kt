package com.aladin.aladincamviewer

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
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
        val lang = newBase.getSharedPreferences("aladin_prefs_v2", Context.MODE_PRIVATE)
            .getString("app_lang", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discovery)
        TvFocusManager.install(this)

        val cameraDao = AppDatabase.getDatabase(this).cameraDao()
        repository = CameraRepository(this, cameraDao)
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
            findViewById<View>(R.id.txt_scanning_status).visibility = View.VISIBLE
            hybridScanner.startFullScan { discoveredList ->
                val cameraCandidates = discoveredList.filter { device ->
                    "ONVIF" in device.protocols ||
                        "RTSP" in device.protocols ||
                        device.protocols.any { it.startsWith("SDK-") }
                }
                devices.clear()
                devices.addAll(cameraCandidates)
                devices.forEach { it.isAdded = existingIps.contains(it.ip) }
                AppLog.i(
                    "ALADIN_DISCOVERY",
                    "Discovery UI candidates=${cameraCandidates.size} networkDevices=${discoveredList.size}"
                )
                
                findViewById<View>(R.id.radar_view).visibility = View.GONE
                findViewById<View>(R.id.txt_scanning_status).visibility = View.GONE
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
        val onvifSame = view.findViewById<CheckBox>(R.id.check_onvif_same)
        val onvifLayout = view.findViewById<View>(R.id.layout_onvif_credentials)
        val onvifUserEdit = view.findViewById<EditText>(R.id.edit_onvif_username)
        val onvifPassEdit = view.findViewById<EditText>(R.id.edit_onvif_password)
        onvifSame.setOnCheckedChangeListener { _, same -> onvifLayout.visibility = if (same) View.GONE else View.VISIBLE }

        AlertDialog.Builder(this)
            .setTitle(R.string.camera_credentials)
            .setView(view)
            .setPositiveButton(R.string.add) { _, _ ->
                performBatchAdd(
                    userEdit.text.toString(),
                    passEdit.text.toString(),
                    if (onvifSame.isChecked) null else onvifUserEdit.text.toString(),
                    if (onvifSame.isChecked) null else onvifPassEdit.text.toString()
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performBatchAdd(user: String, pass: String, onvifUser: String?, onvifPass: String?) {
        val selectedDevices = devices.filter { it.isSelected && !it.isAdded }
        if (selectedDevices.isEmpty()) return
        if (user.isBlank() || pass.isBlank()) {
            Toast.makeText(this, R.string.credentials_fill_warning, Toast.LENGTH_SHORT).show()
            return
        }

        val progress = AlertDialog.Builder(this)
            .setTitle(R.string.verifying_cameras)
            .setMessage(getString(R.string.verifying_camera_progress, selectedDevices.size))
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            val resolver = CameraConfigurationResolver(this@DiscoveryActivity)
            val resolved = selectedDevices.mapIndexed { index, device ->
                progress.setMessage(getString(R.string.verifying_camera_named, index + 1, selectedDevices.size, device.ip))
                val result = resolver.resolve(
                    CameraConfigurationResolver.Input(
                        ip = device.ip,
                        username = user,
                        password = pass,
                        onvifUsername = onvifUser,
                        onvifPassword = onvifPass,
                        brandHint = device.brand,
                        uuid = device.uuid,
                        mac = device.mac
                    )
                )
                device to result
            }
            progress.dismiss()
            showConfigurationConfirmation(resolved, user, pass, onvifUser, onvifPass)
        }
    }

    private fun showConfigurationConfirmation(
        resolved: List<Pair<DiscoveryDevice, CameraConfigurationResolver.Result>>,
        user: String,
        pass: String,
        onvifUser: String?,
        onvifPass: String?
    ) {
        val verifiedCount = resolved.count { it.second.verified }
        val summary = resolved.joinToString("\n\n") { (device, result) ->
            val marker = if (result.verified) "âœ“" else "âœ•"
            "$marker ${device.ip}  ${result.brand}\n${result.model ?: result.message}\n${result.source}"
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.camera_verification_summary, verifiedCount, resolved.size))
            .setMessage(summary)
            .setPositiveButton(R.string.add_verified) { _, _ -> saveVerifiedCameras(resolved, user, pass, onvifUser, onvifPass) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveVerifiedCameras(
        resolved: List<Pair<DiscoveryDevice, CameraConfigurationResolver.Result>>,
        user: String,
        pass: String,
        onvifUser: String?,
        onvifPass: String?
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val availableSlots = (1..16).filter { it !in occupiedSlots }.toMutableList()
            resolved.filter { it.second.verified }.forEach { (device, result) ->
                if (availableSlots.isEmpty()) return@forEach
                if (repository.isIpAlreadyUsed(device.ip)) {
                    AppLog.w("ALADIN_DISCOVERY", "Duplicate camera skipped ip=${device.ip}")
                    return@forEach
                }
                val nextSlot = availableSlots.removeAt(0)
                repository.insert(
                    CameraEntity(
                        name = "Cam $nextSlot",
                        ipAddress = device.ip,
                        username = user,
                        password = pass,
                        onvifUsername = onvifUser.orEmpty(),
                        onvifPassword = onvifPass.orEmpty(),
                        mainStreamUrl = result.mainUrl.orEmpty(),
                        subStreamUrl = result.subUrl ?: result.mainUrl.orEmpty(),
                        brand = result.brand,
                        ptzSupported = result.ptzSupported,
                        displayOrder = nextSlot,
                        uuid = result.uuid.orEmpty(),
                        macAddress = result.mac
                    )
                )
                withContext(Dispatchers.Main) {
                    device.isAdded = true
                    device.isSelected = false
                    adapter.notifyDataSetChanged()
                    updateBatchButton()
                }
            }
        }
    }

    inner class DiscoveryAdapter(private val list: List<DiscoveryDevice>, private val onToggle: (DiscoveryDevice) -> Unit) : 
        RecyclerView.Adapter<DiscoveryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view as MaterialCardView
            val brand: TextView = view.findViewById(R.id.txt_brand)
            val model: TextView = view.findViewById(R.id.txt_model)
            val ip: TextView = view.findViewById(R.id.txt_ip)
            val mac: TextView = view.findViewById(R.id.txt_mac)
            val firmware: TextView = view.findViewById(R.id.txt_firmware)
            val protocols: TextView = view.findViewById(R.id.txt_protocols)
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
            holder.model.text = getString(
                R.string.discovery_model_value,
                device.model?.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_value)
            )
            holder.ip.text = device.ip
            holder.mac.text = device.mac?.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_value)
            holder.firmware.text = device.firmware?.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_value)
            holder.protocols.text = device.protocols.sorted().joinToString(", ").ifBlank {
                getString(R.string.unknown_value)
            }
            holder.addedBadge.visibility = if (device.isAdded) View.VISIBLE else View.GONE
            holder.check.isEnabled = !device.isAdded
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = device.isSelected
            holder.check.setOnCheckedChangeListener { _, isChecked ->
                device.isSelected = isChecked
                onToggle(device)
            }
            holder.card.setOnClickListener {
                if (!device.isAdded) holder.check.isChecked = !holder.check.isChecked
            }
        }

        override fun getItemCount() = list.size
    }
}
