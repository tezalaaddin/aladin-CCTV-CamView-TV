package com.aladin.aladincamviewer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RecordersActivity : AppCompatActivity() {
    private lateinit var repository: RecorderRepository
    private val client = HikvisionIsapiClient()
    private var editingId = 0L
    private var discovered = emptyList<HikvisionChannel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recorders)
        TvFocusManager.install(this)
        repository = RecorderRepository(this)
        findViewById<EditText>(R.id.recorder_http_port).setText("80")
        findViewById<EditText>(R.id.recorder_rtsp_port).setText("554")
        findViewById<EditText>(R.id.recorder_username).setText("admin")
        findViewById<EditText>(R.id.recorder_ip).setText(intent.getStringExtra("recorder_ip").orEmpty())
        findViewById<EditText>(R.id.recorder_name).setText(intent.getStringExtra("recorder_name").orEmpty())
        findViewById<View>(R.id.btn_discover_recorder).setOnClickListener { discover() }
        findViewById<View>(R.id.btn_save_recorder).setOnClickListener { save() }
        observeSaved()
    }

    private fun observeSaved() = lifecycleScope.launch {
        repository.recorders.collect { recorders ->
            val container = findViewById<LinearLayout>(R.id.saved_recorders)
            container.removeAllViews()
            recorders.forEach { recorder ->
                val button = com.google.android.material.button.MaterialButton(this@RecordersActivity).apply {
                    text = "${recorder.name}  •  ${recorder.ipAddress}  •  ${recorder.model.ifBlank { "NVR" }}"
                    isAllCaps = false
                    setOnClickListener { load(recorder) }
                    setOnLongClickListener {
                        startActivity(Intent(this@RecordersActivity, RecordingsActivity::class.java).putExtra("recorder_id", recorder.id))
                        true
                    }
                }
                container.addView(button)
            }
        }
    }

    private fun values(): RecorderEntity = RecorderEntity(
        id = editingId,
        name = text(R.id.recorder_name).ifBlank { "Hikvision NVR" },
        ipAddress = text(R.id.recorder_ip).trim(),
        httpPort = text(R.id.recorder_http_port).toIntOrNull() ?: 80,
        rtspPort = text(R.id.recorder_rtsp_port).toIntOrNull() ?: 554,
        username = text(R.id.recorder_username), password = text(R.id.recorder_password)
    )

    private fun discover() = lifecycleScope.launch {
        val recorder = values()
        if (recorder.ipAddress.isBlank()) return@launch toast(R.string.enter_recorder_ip)
        progress(true)
        runCatching {
            val info = client.deviceInfo(recorder.ipAddress, recorder.httpPort, recorder.username, recorder.password)
            val channels = client.channels(recorder.ipAddress, recorder.httpPort, recorder.username, recorder.password)
            info to channels
        }.onSuccess { (info, channels) ->
            discovered = channels
            findViewById<EditText>(R.id.recorder_name).setText(info.name)
            renderChannels(channels)
            findViewById<View>(R.id.btn_save_recorder).visibility = View.VISIBLE
            AppLog.i("ALADIN_NVR", "NVR verified model=${info.model} channelCount=${channels.size}")
        }.onFailure {
            AppLog.e("ALADIN_NVR", "NVR discovery failed endpoint=${recorder.ipAddress}:${recorder.httpPort} reason=${it.message}")
            Toast.makeText(this@RecordersActivity, getString(R.string.nvr_discovery_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
        progress(false)
    }

    private fun renderChannels(channels: List<HikvisionChannel>, selected: Set<Int> = channels.map { it.number }.toSet()) {
        val container = findViewById<LinearLayout>(R.id.discovered_channels)
        container.removeAllViews()
        channels.forEach { channel -> container.addView(CheckBox(this).apply {
            tag = channel.number; text = "${channel.number}. ${channel.name}"; isChecked = channel.number in selected
            setTextColor(getColor(R.color.white)); isFocusable = true
        }) }
    }

    private fun save() = lifecycleScope.launch {
        val base = values()
        val selected = findViewById<LinearLayout>(R.id.discovered_channels).childrenChecks()
        if (selected.isEmpty()) return@launch toast(R.string.select_at_least_one_channel)
        val info = runCatching { client.deviceInfo(base.ipAddress, base.httpPort, base.username, base.password) }.getOrNull()
        val recorder = base.copy(model = info?.model.orEmpty(), serialNumber = info?.serialNumber.orEmpty())
        val channels = discovered.filter { it.number in selected }.map { channel ->
            RecorderChannelEntity(recorderId = recorder.id, channelNumber = channel.number, name = channel.name,
                mainStreamUrl = client.liveUrl(recorder, channel.number, false), subStreamUrl = client.liveUrl(recorder, channel.number, true))
        }
        runCatching { repository.save(recorder, channels) }.onSuccess {
            toast(R.string.recorder_saved); clearForm()
        }.onFailure { Toast.makeText(this@RecordersActivity, it.message, Toast.LENGTH_LONG).show() }
    }

    private fun load(recorder: RecorderEntity) = lifecycleScope.launch {
        editingId = recorder.id
        set(R.id.recorder_name, recorder.name); set(R.id.recorder_ip, recorder.ipAddress)
        set(R.id.recorder_http_port, recorder.httpPort.toString()); set(R.id.recorder_rtsp_port, recorder.rtspPort.toString())
        set(R.id.recorder_username, recorder.username); set(R.id.recorder_password, recorder.password)
        val saved = repository.getChannels(recorder.id)
        discovered = saved.map { HikvisionChannel(it.channelNumber, it.name, it.enabled) }
        renderChannels(discovered, saved.filter { it.enabled }.map { it.channelNumber }.toSet())
        findViewById<View>(R.id.btn_save_recorder).visibility = View.VISIBLE
    }

    private fun clearForm() { editingId = 0; discovered = emptyList(); findViewById<LinearLayout>(R.id.discovered_channels).removeAllViews() }
    private fun text(id: Int) = findViewById<EditText>(id).text.toString()
    private fun set(id: Int, value: String) = findViewById<EditText>(id).setText(value)
    private fun progress(active: Boolean) { findViewById<View>(R.id.recorder_progress).visibility = if (active) View.VISIBLE else View.GONE }
    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()
    private fun LinearLayout.childrenChecks(): Set<Int> = (0 until childCount).mapNotNull { (getChildAt(it) as? CheckBox)?.takeIf(CheckBox::isChecked)?.tag as? Int }.toSet()
}
