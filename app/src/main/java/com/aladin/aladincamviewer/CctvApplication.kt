package com.aladin.aladincamviewer

import android.app.Application
import android.content.Context
import android.util.Log
import org.videolan.libvlc.LibVLC

/**
 * Custom Application class for 24/7 CCTV monitoring stability.
 * Reverted to most stable LibVLC initialization.
 */
class CctvApplication : Application() {

    companion object {
        private var _sharedLibVLC: LibVLC? = null
        val sharedLibVLC: LibVLC
            get() {
                if (_sharedLibVLC == null) {
                    // Fallback for unexpected lifecycle issues
                    Log.e("CctvApp", "LibVLC accessed before initialization!")
                }
                return _sharedLibVLC!!
            }
    }

    override fun attachBaseContext(base: Context) {
        // Safe language loading
        val lang = base.getSharedPreferences("aladin_prefs_v2", Context.MODE_PRIVATE)
            .getString("app_lang", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.setLocale(base, lang))
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            // Global VLC Engine initialization with hardware acceleration
            val options = arrayListOf(
                "--network-caching=1500",
                "--rtsp-tcp",
                "--no-audio",
                "--drop-late-frames",
                "--skip-frames",
                "--avcodec-hw=any"
            )
            _sharedLibVLC = LibVLC(this, options)
        } catch (e: Exception) {
            Log.e("CctvApp", "Failed to init LibVLC", e)
        }

        // Periodic maintenance and DHCP recovery
        try {
            CctvWatchdog.scheduleDailyRestart(this)
            val database = AppDatabase.getDatabase(this)
            val repository = CameraRepository(database.cameraDao())
            NetworkTracker.getInstance(this, repository).startTracking()
        } catch (e: Exception) {
            Log.e("CctvApp", "Failed to init services", e)
        }
    }
}
