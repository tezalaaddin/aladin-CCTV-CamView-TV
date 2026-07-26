package com.aladin.aladincamviewer

import android.app.Application
import android.content.Context
import android.util.Log
import org.videolan.libvlc.LibVLC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
                    AppLog.e("CctvApp", "LibVLC accessed before initialization!")
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
        AppLog.initialize(this)
        
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
            AppLog.e("CctvApp", "Failed to init LibVLC", e)
        }

        // Periodic maintenance and DHCP recovery
        try {
            if (PreferenceHelper(this).dailyMaintenance) CctvWatchdog.scheduleDailyRestart(this)
            val database = AppDatabase.getDatabase(this)
            val repository = CameraRepository(this, database.cameraDao())
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                repository.migrateLegacySecrets()
                val preferences = PreferenceHelper(this@CctvApplication)
                if (!preferences.hasAutomaticNetworkRecoveryChoice && repository.getAllOnce().isNotEmpty()) {
                    preferences.automaticNetworkRecovery = true
                }
                if (preferences.automaticNetworkRecovery) {
                    NetworkTracker.getInstance(this@CctvApplication, repository).startTracking()
                }
            }
        } catch (e: Exception) {
            AppLog.e("CctvApp", "Failed to init services", e)
        }
    }
}
