package com.aladin.aladincamviewer

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object SnapshotUtils {

    @RequiresApi(Build.VERSION_CODES.O)
    fun takeSnapshot(view: View, cameraName: String) {
        val surfaceView = findSurfaceView(view) ?: return
        
        val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
        
        PixelCopy.request(surfaceView, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                saveBitmap(view.context, bitmap, cameraName)
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(view.context, "Snapshot failed: $result", Toast.LENGTH_SHORT).show()
                }
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun findSurfaceView(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = findSurfaceView(view.getChildAt(i))
                if (child != null) return child
            }
        }
        return null
    }

    private fun saveBitmap(context: android.content.Context, bitmap: Bitmap, cameraName: String) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ALADIN_${cameraName}_$timeStamp.jpg"
        
        val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val file = File(storageDir, fileName)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Snapshot saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
