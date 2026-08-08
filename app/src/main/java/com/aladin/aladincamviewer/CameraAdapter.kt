package com.aladin.aladincamviewer

import android.content.Intent
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.videolan.libvlc.util.VLCVideoLayout

class CameraAdapter(private val cameras: List<CameraModel>) : RecyclerView.Adapter<CameraAdapter.CameraViewHolder>() {
    private val activeHolders = mutableSetOf<CameraViewHolder>()

    class CameraViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val videoLayout: VLCVideoLayout = view.findViewById(R.id.player_view)
        val progressBar: ProgressBar = view.findViewById(R.id.loading_progress)
        val errorText: TextView = view.findViewById(R.id.error_text)
        val statusLed: View = view.findViewById(R.id.status_led)
        val cameraTitle: TextView = view.findViewById(R.id.camera_title)
        val emptyPlaceholder: View = view.findViewById(R.id.empty_placeholder)
        var playerManager: CctvPlayerManager? = null

        init {
            view.setOnFocusChangeListener { _, hasFocus ->
                view.scaleX = if (hasFocus) 1.05f else 1.0f
                view.scaleY = if (hasFocus) 1.05f else 1.0f
                playerManager?.setVolume(if (hasFocus) 1f else 0f)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_camera, parent, false)
        val layoutParams = view.layoutParams
        layoutParams.height = if (
            parent.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        ) {
            // Preserve the camera image instead of stretching it into a tall TV grid cell.
            ((parent.width - parent.paddingLeft - parent.paddingRight) * 9f / 16f).toInt()
        } else {
            parent.height / 2
        }
        view.layoutParams = layoutParams
        return CameraViewHolder(view)
    }

    override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
        activeHolders += holder
        val camera = cameras[position]
        holder.cameraTitle.text = camera.name
        holder.errorText.text = ""
        
        if (camera.subStreamUrl.isEmpty()) {
            holder.emptyPlaceholder.isVisible = true
            holder.progressBar.isVisible = false
            holder.playerManager?.releasePlayer()
            holder.playerManager = null
            return
        } else {
            holder.emptyPlaceholder.isVisible = false
        }

        if (holder.playerManager == null) {
            holder.playerManager = CctvPlayerManager(
                onStateChanged = { isLoading, error ->
                    holder.progressBar.isVisible = isLoading
                    holder.errorText.isVisible = error != null
                    holder.errorText.text = error.orEmpty()
                    holder.statusLed.setBackgroundResource(if (error != null) R.drawable.led_offline else R.drawable.led_online)
                }
            )
            holder.playerManager?.initializePlayer(isSubStream = true)
            holder.playerManager?.attachView(holder.videoLayout)
        }
        
        holder.playerManager?.playStream(camera.subStreamUrl)

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, FullScreenCameraActivity::class.java).apply {
                putExtra("camera_data", camera)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun onViewRecycled(holder: CameraViewHolder) {
        super.onViewRecycled(holder)
        release(holder)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        releaseAll()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    fun releaseAll() {
        activeHolders.toList().forEach(::release)
    }

    private fun release(holder: CameraViewHolder) {
        activeHolders -= holder
        holder.playerManager?.releasePlayer()
        holder.playerManager = null
    }

    override fun getItemCount(): Int = cameras.size
}
