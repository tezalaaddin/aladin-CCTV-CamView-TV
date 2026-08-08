package com.aladin.aladincamviewer

import android.content.Intent
import android.os.Bundle
import android.app.TimePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

class RecordingsActivity : AppCompatActivity() {
    private val client = HikvisionIsapiClient()
    private val profileGClient = OnvifProfileGClient()
    private lateinit var repository: RecorderRepository
    private lateinit var recorder: RecorderEntity
    private var channels = emptyList<RecorderChannelEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordings)
        TvFocusManager.install(this)
        repository = RecorderRepository(this)
        findViewById<RecyclerView>(R.id.recordings_list).layoutManager = LinearLayoutManager(this)
        findViewById<EditText>(R.id.recording_date).setText(LocalDate.now().toString())
        findViewById<EditText>(R.id.recording_time).apply {
            setText("00:00")
            isFocusable = false
            setOnClickListener {
                val current = runCatching { LocalTime.parse(text) }.getOrDefault(LocalTime.MIDNIGHT)
                TimePickerDialog(this@RecordingsActivity, { _, hour, minute ->
                    setText(String.format("%02d:%02d", hour, minute))
                }, current.hour, current.minute, true).show()
            }
        }
        lifecycleScope.launch {
            val id = intent.getLongExtra("recorder_id", 0)
            recorder = repository.getRecorder(id) ?: return@launch finish()
            channels = repository.getChannels(id).filter { it.enabled }
            findViewById<TextView>(R.id.recordings_title).text = getString(R.string.recordings_for, recorder.name)
            findViewById<Spinner>(R.id.recording_channel).adapter = ArrayAdapter(this@RecordingsActivity,
                android.R.layout.simple_spinner_dropdown_item, channels.map { "${it.channelNumber}. ${it.name}" })
        }
        findViewById<View>(R.id.btn_search_recordings).setOnClickListener { search() }
    }

    private fun search() = lifecycleScope.launch {
        val index = findViewById<Spinner>(R.id.recording_channel).selectedItemPosition
        val channel = channels.getOrNull(index) ?: return@launch
        val date = runCatching { LocalDate.parse(findViewById<EditText>(R.id.recording_date).text) }.getOrElse {
            return@launch Toast.makeText(this@RecordingsActivity, R.string.invalid_date, Toast.LENGTH_SHORT).show()
        }
        val time = runCatching { LocalTime.parse(findViewById<EditText>(R.id.recording_time).text) }.getOrElse {
            return@launch Toast.makeText(this@RecordingsActivity, R.string.invalid_time, Toast.LENGTH_SHORT).show()
        }
        val zone = ZoneId.systemDefault()
        val start = date.atTime(time).atZone(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
        showProgress(true)
        runCatching {
            if (recorder.manufacturer.equals(NvrStreamProfile.HIKVISION, true)) {
                client.recordings(recorder, channel.channelNumber, start, end)
            } else {
                profileGClient.recordings(recorder, channel.channelNumber, start, end)
            }
        }
            .onSuccess { segments ->
                findViewById<RecyclerView>(R.id.recordings_list).adapter = SegmentAdapter(segments) { segment ->
                    val requestedStart = start.coerceIn(segment.start, segment.end)
                    val safeUrl = client.authenticatedPlaybackUrl(recorder, segment.playbackUri)
                    startActivity(Intent(this@RecordingsActivity, FullScreenCameraActivity::class.java)
                        .putExtra("playback_url", safeUrl)
                        .putExtra("playback_start_offset_ms", segment.startOffsetMs)
                        .putExtra("playback_start_epoch_ms", requestedStart.toEpochMilli())
                        .putExtra("playback_end_epoch_ms", segment.end.toEpochMilli())
                        .putExtra("playback_absolute_time", !recorder.manufacturer.equals(NvrStreamProfile.HIKVISION, true))
                        .putExtra("playback_time_in_url", recorder.manufacturer.equals(NvrStreamProfile.TIANDY, true))
                        .putExtra("playback_title", "${channel.name} • ${format(requestedStart)}"))
                }
                findViewById<TextView>(R.id.recordings_empty).apply {
                    visibility = if (segments.isEmpty()) View.VISIBLE else View.GONE
                    setText(R.string.no_recordings_found)
                }
                AppLog.i("ALADIN_REPLAY", "Recording search completed channel=${channel.channelNumber} resultCount=${segments.size}")
            }.onFailure {
                AppLog.e("ALADIN_REPLAY", "Recording search failed channel=${channel.channelNumber} reason=${it.message}")
                androidx.appcompat.app.AlertDialog.Builder(this@RecordingsActivity)
                    .setTitle(R.string.recording_search_unavailable)
                    .setMessage(getString(R.string.recording_search_failed, it.message.orEmpty()))
                    .setPositiveButton(R.string.open_nvr_web) { _, _ ->
                        startActivity(Intent(this@RecordingsActivity, WebPlaybackActivity::class.java)
                            .putExtra("camera_ip", recorder.ipAddress))
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        showProgress(false)
    }

    private fun showProgress(show: Boolean) { findViewById<View>(R.id.recordings_progress).visibility = if (show) View.VISIBLE else View.GONE }
    private fun format(value: Instant) = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(value)

    private inner class SegmentAdapter(private val items: List<RecordingSegment>, private val click: (RecordingSegment) -> Unit) : RecyclerView.Adapter<SegmentAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) { val time: TextView = view.findViewById(R.id.segment_time); val duration: TextView = view.findViewById(R.id.segment_duration) }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_recording_segment, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) { val item = items[position]; holder.time.text = "${format(item.start)} — ${format(item.end)}"; holder.duration.text = getString(R.string.minutes_format, Duration.between(item.start, item.end).toMinutes()); holder.itemView.setOnClickListener { click(item) } }
        override fun getItemCount() = items.size
    }
}
