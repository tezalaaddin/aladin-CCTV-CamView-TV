package com.aladin.aladincamviewer

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi

class CameraAdapter(private val cameras: List<CameraModel>) : RecyclerView.Adapter<CameraAdapter.CameraViewHolder>() {

    @OptIn(UnstableApi::class)
    class CameraViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val playerView: PlayerView = view.findViewById(R.id.player_view)
        val cardView: MaterialCardView = view as MaterialCardView
        val progressBar: ProgressBar = view.findViewById(R.id.loading_progress)
        val errorText: TextView = view.findViewById(R.id.error_text)
        val tvResFps: TextView = view.findViewById(R.id.tv_res_fps)
        val tvBitrateCodec: TextView = view.findViewById(R.id.tv_bitrate_codec)
        val statusLed: View = view.findViewById(R.id.status_led)
        var playerManager: CctvPlayerManager? = null

        init {
            view.setOnFocusChangeListener { _, hasFocus ->
                cardView.strokeWidth = if (hasFocus) 6 else 0
                cardView.cardElevation = if (hasFocus) 15f else 2f
                view.scaleX = if (hasFocus) 1.02f else 1.0f
                view.scaleY = if (hasFocus) 1.02f else 1.0f
                
                // Smart Audio: Focus on this camera enables audio
                playerManager?.setVolume(if (hasFocus) 1f else 0f)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_camera, parent, false)
        val layoutParams = view.layoutParams
        layoutParams.height = parent.height / 2
        view.layoutParams = layoutParams
        return CameraViewHolder(view)
    }

    override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
        val camera = cameras[position]
        
        // Boş slot kontrolü: Eğer URL yoksa hiçbir şey yapma ve görünümleri temizle
        if (camera.subStreamUrl.isEmpty()) {
            holder.progressBar.isVisible = false
            holder.errorText.isVisible = false
            holder.playerManager?.releasePlayer()
            holder.playerManager = null
            holder.playerView.player = null
            return
        }

        if (holder.playerManager == null) {
            holder.playerManager = CctvPlayerManager(
                context = holder.itemView.context,
                onStateChanged = { isLoading, error ->
                    holder.progressBar.isVisible = isLoading
                    holder.errorText.text = error
                    holder.errorText.isVisible = error != null
                    
                    // Update Status LED
                    holder.statusLed.setBackgroundResource(
                        if (error != null) android.R.drawable.presence_offline 
                        else if (isLoading) android.R.drawable.presence_away 
                        else android.R.drawable.presence_online
                    )
                },
                onFormatChanged = { format ->
                    format?.let {
                        holder.tvResFps.text = "${it.width}x${it.height} | ${it.frameRate.toInt()} FPS"
                        holder.tvBitrateCodec.text = "${(it.bitrate / 1024)} Kbps | ${it.sampleMimeType?.substringAfterLast("/")}"
                    }
                }
            )
            holder.playerManager?.initializePlayer(isSubStream = true)
            // Initial volume based on focus
            holder.playerManager?.setVolume(if (holder.itemView.hasFocus()) 1f else 0f)
            holder.playerView.player = holder.playerManager?.player
        }
        
        // Play Sub Stream in Grid
        holder.playerManager?.playStream(camera.subStreamUrl)

        holder.itemView.setOnClickListener {
            // Stop players globally before transition
            (holder.itemView.context as? MainActivity)?.findViewById<RecyclerView>(R.id.camera_recycler_view)?.adapter = null

            val intent = Intent(holder.itemView.context, FullScreenCameraActivity::class.java).apply {
                putExtra("camera_data", camera)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun onViewRecycled(holder: CameraViewHolder) {
        super.onViewRecycled(holder)
        holder.playerManager?.releasePlayer()
        holder.playerManager = null
        holder.playerView.player = null
    }

    override fun getItemCount(): Int = cameras.size
}
