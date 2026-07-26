package com.aladin.aladincamviewer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class EditCameraActivity : AppCompatActivity() {

    private val brands = CameraBrandProfiles.knownBrands()
    private var selectedBrand = "Custom"
    private var cameraId = 0
    private var displayOrder = 1
    private var prefilledUuid = ""
    private var prefilledMacAddress: String? = null

    private lateinit var etIp: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etMain: EditText
    private lateinit var etSub: EditText
    private lateinit var cbPtz: CheckBox
    private lateinit var cbOnvifSame: CheckBox
    private lateinit var etOnvifUser: EditText
    private lateinit var etOnvifPass: EditText
    private lateinit var onvifCredentialsLayout: View
    private lateinit var btnBrand: Button
    private lateinit var btnDelete: Button
    private lateinit var setupStatus: TextView

    private val viewModel: EditCameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_camera)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        TvFocusManager.install(this)

        cameraId = intent.getIntExtra("camera_id", 0)
        displayOrder = intent.getIntExtra("display_order", 1)
        
        val preIp = intent.getStringExtra("prefilled_ip")
        val preBrand = intent.getStringExtra("prefilled_brand")
        prefilledUuid = intent.getStringExtra("prefilled_uuid") ?: ""

        findViewById<TextView>(R.id.edit_title).text = getString(R.string.slot_setup_title, displayOrder)

        btnBrand = findViewById(R.id.btn_select_brand)
        btnDelete = findViewById(R.id.btn_delete_camera)
        etIp = findViewById(R.id.et_ip)
        etUser = findViewById(R.id.et_user)
        etPass = findViewById(R.id.et_pass)
        etMain = findViewById(R.id.et_main_url)
        etSub = findViewById(R.id.et_sub_url)
        cbPtz = findViewById(R.id.cb_ptz)
        cbOnvifSame = findViewById(R.id.cb_onvif_same)
        etOnvifUser = findViewById(R.id.et_onvif_user)
        etOnvifPass = findViewById(R.id.et_onvif_pass)
        onvifCredentialsLayout = findViewById(R.id.layout_edit_onvif_credentials)
        setupStatus = findViewById(R.id.txt_setup_status)
        cbOnvifSame.setOnCheckedChangeListener { _, same ->
            onvifCredentialsLayout.visibility = if (same) View.GONE else View.VISIBLE
        }

        if (cameraId != 0) {
            btnDelete.visibility = View.VISIBLE
            lifecycleScope.launch {
                viewModel.getCameraById(cameraId)?.let { camera ->
                    selectedBrand = camera.brand
                    btnBrand.text = getString(R.string.brand_label, selectedBrand)
                    etIp.setText(camera.ipAddress)
                    etUser.setText(camera.username)
                    etPass.setText(camera.password)
                    val sameOnvifCredentials = camera.onvifUsername.isBlank() && camera.onvifPassword.isBlank()
                    cbOnvifSame.isChecked = sameOnvifCredentials
                    if (!sameOnvifCredentials) {
                        etOnvifUser.setText(camera.onvifUsername)
                        etOnvifPass.setText(camera.onvifPassword)
                    }
                    etMain.setText(camera.mainStreamUrl)
                    etSub.setText(camera.subStreamUrl)
                    cbPtz.isChecked = camera.ptzSupported
                    prefilledUuid = camera.uuid
                    prefilledMacAddress = camera.macAddress
                }
            }
        } else if (preIp != null) {
            etIp.setText(preIp)
            selectedBrand = preBrand ?: "Custom"
            btnBrand.text = getString(R.string.brand_label, selectedBrand)
        }

        btnBrand.setOnClickListener { showBrandPicker() }
        btnDelete.setOnClickListener { confirmDelete() }
        findViewById<Button>(R.id.btn_apply_template).setOnClickListener { generateUrls() }
        findViewById<Button>(R.id.btn_save_camera).setOnClickListener { verifyAndSave() }
        findViewById<Button>(R.id.btn_fix_camera).setOnClickListener { fixCameraViaOnvif() }
        
        findViewById<Button>(R.id.btn_apply_to_all)?.setOnClickListener { applyCommonSettingsToAll() }
        btnBrand.requestFocus()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_camera)
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.delete_camera) { _, _ ->
                lifecycleScope.launch {
                    val camera = viewModel.getCameraById(cameraId)
                    if (camera != null) {
                        viewModel.deleteCamera(camera)
                        Toast.makeText(this@EditCameraActivity, getString(R.string.camera_deleted), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyCommonSettingsToAll() {
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString().trim()
        
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, getString(R.string.credentials_fill_warning), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val allCams = viewModel.getAllCameras()
            val updatedCams = allCams.map { 
                it.copy(username = user, password = pass)
            }
            updatedCams.forEach { viewModel.saveCamera(it) }
            Toast.makeText(this@EditCameraActivity, getString(R.string.credentials_applied_msg), Toast.LENGTH_SHORT).show()
        }
    }

    private fun fixCameraViaOnvif() {
        val ip = etIp.text.toString().trim()
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString().trim()

        if (ip.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, getString(R.string.fill_details_warning), Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setMessage(R.string.onvif_scanning_msg)
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            val result = CameraConfigurationResolver(this@EditCameraActivity).resolve(configurationInput(ip, user, pass))
            progressDialog.dismiss()
            if (result.verified) {
                selectedBrand = result.brand
                btnBrand.text = getString(R.string.brand_label, selectedBrand)
                etMain.setText(result.mainUrl)
                etSub.setText(result.subUrl)
                cbPtz.isChecked = result.ptzSupported
                prefilledUuid = result.uuid.orEmpty()
                prefilledMacAddress = result.mac
                setupStatus.text = getString(R.string.camera_verified_status, result.source, result.model ?: result.brand)
                setupStatus.setTextColor(getColor(R.color.status_green))
                val msg = getString(R.string.onvif_success_msg, result.brand)
                Toast.makeText(this@EditCameraActivity, msg, Toast.LENGTH_LONG).show()
            } else {
                setupStatus.text = result.message
                setupStatus.setTextColor(getColor(R.color.status_red))
                Toast.makeText(this@EditCameraActivity, getString(R.string.onvif_fail_msg), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBrandPicker() {
        AlertDialog.Builder(this)
            .setTitle(R.string.select_brand)
            .setItems(brands.toTypedArray()) { _, which ->
                selectedBrand = brands[which]
                btnBrand.text = getString(R.string.brand_label, selectedBrand)
            }
            .show()
    }

    private fun generateUrls() {
        val ip = etIp.text.toString().trim()
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString().trim()

        if (ip.isEmpty()) {
            Toast.makeText(this, getString(R.string.enter_ip_warning), Toast.LENGTH_SHORT).show()
            return
        }

        val (main, sub) = CameraBrandProfiles.candidates(selectedBrand, ip, user, pass).firstOrNull()
            ?: (etMain.text.toString() to etSub.text.toString())

        etMain.setText(main)
        etSub.setText(sub)
    }

    private fun verifyAndSave() {
        val ip = etIp.text.toString().trim()
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString()
        if (ip.isBlank() || user.isBlank() || pass.isBlank()) {
            Toast.makeText(this, R.string.fill_details_warning, Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            val userInfo = Regex("^rtsp://([^@]+)@", RegexOption.IGNORE_CASE)
                .find(etMain.text.toString())?.groupValues?.get(1).orEmpty().split(':', limit = 2)
            val urlUser = java.net.URLDecoder.decode(userInfo.getOrNull(0).orEmpty(), "UTF-8")
            val urlPass = java.net.URLDecoder.decode(userInfo.getOrNull(1).orEmpty(), "UTF-8")
            Log.d("ALADIN_CAMERA_SETUP", "Stored fields match stream credentials user=${urlUser == user} password=${urlPass == pass}")
        }
        val progress = AlertDialog.Builder(this).setMessage(R.string.verifying_camera_configuration).setCancelable(false).show()
        lifecycleScope.launch {
            var mainUrl = etMain.text.toString().trim()
            var subUrl = etSub.text.toString().trim()
            var verified = if (mainUrl.isNotBlank()) RtspEndpointVerifier.verify(mainUrl, user, pass).playable else false
            if (!verified) {
                val result = CameraConfigurationResolver(this@EditCameraActivity).resolve(configurationInput(ip, user, pass))
                verified = result.verified
                if (verified) {
                    selectedBrand = result.brand
                    mainUrl = result.mainUrl.orEmpty()
                    subUrl = result.subUrl ?: mainUrl
                    cbPtz.isChecked = result.ptzSupported
                    prefilledUuid = result.uuid.orEmpty()
                    prefilledMacAddress = result.mac
                    etMain.setText(mainUrl)
                    etSub.setText(subUrl)
                }
            }
            progress.dismiss()
            if (!verified) {
                setupStatus.text = getString(R.string.camera_verification_failed)
                setupStatus.setTextColor(getColor(R.color.status_red))
                Toast.makeText(this@EditCameraActivity, R.string.camera_verification_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            val camera = CameraEntity(
                id = cameraId,
                name = "Cam $displayOrder",
                ipAddress = etIp.text.toString().trim(),
                username = etUser.text.toString().trim(),
                password = etPass.text.toString().trim(),
                onvifUsername = if (cbOnvifSame.isChecked) "" else etOnvifUser.text.toString().trim(),
                onvifPassword = if (cbOnvifSame.isChecked) "" else etOnvifPass.text.toString(),
                mainStreamUrl = mainUrl,
                subStreamUrl = subUrl.ifBlank { mainUrl },
                brand = selectedBrand,
                ptzSupported = cbPtz.isChecked,
                displayOrder = displayOrder,
                uuid = prefilledUuid,
                macAddress = prefilledMacAddress
            )
            if (viewModel.saveCameraChecked(camera)) {
                setupStatus.text = getString(R.string.camera_ready_to_save)
                Toast.makeText(this@EditCameraActivity, getString(R.string.cam_slot_saved, displayOrder), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@EditCameraActivity, R.string.duplicate_camera_ip, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun configurationInput(ip: String, user: String, pass: String) =
        CameraConfigurationResolver.Input(
            ip = ip,
            username = user,
            password = pass,
            onvifUsername = if (cbOnvifSame.isChecked) null else etOnvifUser.text.toString().trim(),
            onvifPassword = if (cbOnvifSame.isChecked) null else etOnvifPass.text.toString(),
            brandHint = selectedBrand,
            uuid = prefilledUuid,
            mac = prefilledMacAddress
        )
}
