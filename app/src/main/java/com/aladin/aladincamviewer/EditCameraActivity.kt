package com.aladin.aladincamviewer

import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class EditCameraActivity : AppCompatActivity() {

    private val brands = listOf("Hikvision", "Dahua", "Tiandy", "Uniview", "ONVIF", "AJCloud", "Custom")
    private var selectedBrand = "Custom"
    private var cameraId = 0
    private var displayOrder = 1
    private var prefilledUuid = ""

    private lateinit var etIp: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etMain: EditText
    private lateinit var etSub: EditText
    private lateinit var cbPtz: CheckBox
    private lateinit var btnBrand: Button

    private val viewModel: EditCameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_camera)

        cameraId = intent.getIntExtra("camera_id", 0)
        displayOrder = intent.getIntExtra("display_order", 1)
        
        val preIp = intent.getStringExtra("prefilled_ip")
        val preBrand = intent.getStringExtra("prefilled_brand")
        prefilledUuid = intent.getStringExtra("prefilled_uuid") ?: ""

        findViewById<TextView>(R.id.edit_title).text = getString(R.string.cam_label, displayOrder)

        btnBrand = findViewById(R.id.btn_select_brand)
        etIp = findViewById(R.id.et_ip)
        etUser = findViewById(R.id.et_user)
        etPass = findViewById(R.id.et_pass)
        etMain = findViewById(R.id.et_main_url)
        etSub = findViewById(R.id.et_sub_url)
        cbPtz = findViewById(R.id.cb_ptz)

        if (cameraId != 0) {
            lifecycleScope.launch {
                viewModel.getCameraById(cameraId)?.let { camera ->
                    selectedBrand = camera.brand
                    btnBrand.text = "Brand: $selectedBrand"
                    etIp.setText(camera.ipAddress)
                    etUser.setText(camera.username)
                    etPass.setText(camera.password)
                    etMain.setText(camera.mainStreamUrl)
                    etSub.setText(camera.subStreamUrl)
                    cbPtz.isChecked = camera.ptzSupported
                    prefilledUuid = camera.uuid
                }
            }
        } else if (preIp != null) {
            etIp.setText(preIp)
            selectedBrand = preBrand ?: "Custom"
            btnBrand.text = "Brand: $selectedBrand"
        }

        btnBrand.setOnClickListener { showBrandPicker() }
        findViewById<Button>(R.id.btn_apply_template).setOnClickListener { generateUrls() }
        findViewById<Button>(R.id.btn_save_camera).setOnClickListener { saveAndExit() }
        findViewById<Button>(R.id.btn_fix_camera).setOnClickListener { fixCameraViaOnvif() }
        
        findViewById<Button>(R.id.btn_apply_to_all)?.setOnClickListener { applyCommonSettingsToAll() }
    }

    private fun applyCommonSettingsToAll() {
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString().trim()
        
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Fill username/password to apply to all", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val allCams = viewModel.getAllCameras() // Need to add this to ViewModel
            val updatedCams = allCams.map { 
                it.copy(username = user, password = pass)
            }
            updatedCams.forEach { viewModel.saveCamera(it) }
            Toast.makeText(this@EditCameraActivity, "Credentials applied to all cameras", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fixCameraViaOnvif() {
        val ip = etIp.text.toString().trim()
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString().trim()

        if (ip.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Please fill IP, Username and Password first", Toast.LENGTH_SHORT).show()
            return
        }

        val camera = CameraModel("Temp", "", "", ip, false, user, pass, selectedBrand)
        CameraConfigManager(camera).optimizeForTV()
        Toast.makeText(this, "Optimization command sent via ONVIF", Toast.LENGTH_LONG).show()
    }

    private fun showBrandPicker() {
        AlertDialog.Builder(this)
            .setTitle("Select Brand")
            .setItems(brands.toTypedArray()) { _, which ->
                selectedBrand = brands[which]
                btnBrand.text = "Brand: $selectedBrand"
            }
            .show()
    }

    private fun generateUrls() {
        val ip = etIp.text.toString().trim()
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString().trim()

        if (ip.isEmpty()) {
            Toast.makeText(this, "Please enter IP address", Toast.LENGTH_SHORT).show()
            return
        }

        val (main, sub) = when (selectedBrand) {
            "Hikvision" -> {
                val base = "rtsp://$user:$pass@$ip:554/Streaming/Channels/"
                Pair("${base}101", "${base}102")
            }
            "Dahua" -> {
                val base = "rtsp://$user:$pass@$ip:554/cam/realmonitor?channel=1&subtype="
                Pair("${base}0", "${base}1")
            }
            "Tiandy" -> {
                val base = "rtsp://$user:$pass@$ip:554/1/"
                Pair("${base}1", "${base}2")
            }
            "Uniview" -> {
                val base = "rtsp://$user:$pass@$ip:554/unicast/c1/s"
                Pair("${base}0/live", "${base}1/live")
            }
            "AJCloud" -> {
                val base = "rtsp://$user:$pass@$ip:554/live/"
                Pair("${base}ch0", "${base}ch1")
            }
            else -> Pair(etMain.text.toString(), etSub.text.toString())
        }

        etMain.setText(main)
        etSub.setText(sub)
    }

    private fun saveAndExit() {
        lifecycleScope.launch {
            val camera = CameraEntity(
                id = cameraId,
                name = "Cam $displayOrder",
                ipAddress = etIp.text.toString().trim(),
                username = etUser.text.toString().trim(),
                password = etPass.text.toString().trim(),
                mainStreamUrl = etMain.text.toString().trim(),
                subStreamUrl = etSub.text.toString().trim(),
                brand = selectedBrand,
                ptzSupported = cbPtz.isChecked,
                displayOrder = displayOrder,
                uuid = prefilledUuid
            )
            viewModel.saveCamera(camera)
            
            Toast.makeText(this@EditCameraActivity, "Camera $displayOrder Updated", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
