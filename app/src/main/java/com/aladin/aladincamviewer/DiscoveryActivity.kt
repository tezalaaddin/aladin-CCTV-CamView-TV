package com.aladin.aladincamviewer

import android.os.Bundle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiscoveryActivity : AppCompatActivity() {

    private lateinit var hybridScanner: HybridScanner
    private lateinit var adapter: DiscoveryAdapter
    private lateinit var repository: CameraRepository
    private val devices = mutableListOf<DiscoveryDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discovery)

        val cameraDao = AppDatabase.getDatabase(this).cameraDao()
        repository = CameraRepository(cameraDao)

        hybridScanner = HybridScanner(this)
        setupRecycler()

        findViewById<MaterialButton>(R.id.btn_batch_add).setOnClickListener {
            showBatchAddDialog()
        }

        startScan()
    }

    private fun setupRecycler() {
        val recycler = findViewById<RecyclerView>(R.id.recycler_discovery)
        adapter = DiscoveryAdapter(devices) { device ->
            device.isSelected = !device.isSelected
            updateBatchButton()
        }
        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.adapter = adapter
    }

    private fun startScan() {
        lifecycleScope.launch {
            hybridScanner.startFullScan { updatedList ->
                runOnUiThread {
                    devices.clear()
                    devices.addAll(updatedList)
                    adapter.notifyDataSetChanged()
                    
                    if (devices.isNotEmpty()) {
                        findViewById<View>(R.id.radar_view).visibility = View.GONE
                        findViewById<View>(R.id.txt_scanning_status).visibility = View.GONE
                        findViewById<RecyclerView>(R.id.recycler_discovery).visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun updateBatchButton() {
        val btn = findViewById<MaterialButton>(R.id.btn_batch_add)
        val selectedCount = devices.count { it.isSelected }
        btn.visibility = if (selectedCount > 0) View.VISIBLE else View.GONE
        btn.text = "ADD SELECTED ($selectedCount)"
    }

    private fun showBatchAddDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_batch_credentials, null)
        val userEdit = dialogView.findViewById<EditText>(R.id.edit_username)
        val passEdit = dialogView.findViewById<EditText>(R.id.edit_password)

        AlertDialog.Builder(this)
            .setTitle("Batch Add Credentials")
            .setView(dialogView)
            .setPositiveButton("ADD") { _, _ ->
                val user = userEdit.text.toString()
                val pass = passEdit.text.toString()
                performBatchAdd(user, pass)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun performBatchAdd(user: String, pass: String) {
        val selectedDevices = devices.filter { it.isSelected && !it.isAdded }
        if (selectedDevices.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            val supervisor = SupervisorJob()
            selectedDevices.forEach { device ->
                launch(supervisor) {
                    runCatching {
                        // Fail-safe logic: If one camera fails, others continue
                        val camera = CameraEntity(
                            name = "${device.brand} ${device.ip.split(".").last()}",
                            ipAddress = device.ip,
                            username = user,
                            password = pass,
                            mainStreamUrl = "rtsp://$user:$pass@${device.ip}:554/live/ch1",
                            subStreamUrl = "rtsp://$user:$pass@${device.ip}:554/live/ch0",
                            brand = device.brand,
                            uuid = device.uuid ?: ""
                        )
                        repository.insert(camera)
                        
                        withContext(Dispatchers.Main) {
                            device.isAdded = true
                            device.isSelected = false
                        }
                    }.onFailure { e ->
                        android.util.Log.e("DiscoveryActivity", "Failed to add camera ${device.ip}: ${e.message}")
                    }
                }
            }
            
            // Wait for all to finish if needed, or just let them complete
            
            withContext(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
                Toast.makeText(this@DiscoveryActivity, "Batch processing initiated", Toast.LENGTH_SHORT).show()
                updateBatchButton()
            }
        }
    }

    inner class DiscoveryAdapter(
        private val list: List<DiscoveryDevice>,
        private val onToggle: (DiscoveryDevice) -> Unit
    ) : RecyclerView.Adapter<DiscoveryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view as MaterialCardView
            val brand: TextView = view.findViewById(R.id.txt_brand)
            val ip: TextView = view.findViewById(R.id.txt_ip)
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
            holder.ip.text = device.ip
            holder.protocols.text = device.protocols.joinToString(", ")
            holder.check.isChecked = device.isSelected
            holder.addedBadge.visibility = if (device.isAdded) View.VISIBLE else View.GONE
            
            holder.card.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                    holder.card.strokeWidth = 4
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    holder.card.strokeWidth = 0
                }
            }

            holder.card.setOnClickListener {
                onToggle(device)
                notifyItemChanged(position)
            }
        }

        override fun getItemCount() = list.size
    }
}
