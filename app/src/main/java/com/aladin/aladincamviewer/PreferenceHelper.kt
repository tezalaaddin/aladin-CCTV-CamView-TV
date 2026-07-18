package com.aladin.aladincamviewer

import android.content.Context
import android.content.SharedPreferences

/**
 * Super-Simple Preference Helper to prevent startup crashes.
 * Uses regular SharedPreferences for maximum stability on all Android TV versions.
 */
class PreferenceHelper(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("aladin_prefs_v2", Context.MODE_PRIVATE)
    }

    var appPin: String
        get() = prefs.getString("app_pin", "") ?: ""
        set(value) = prefs.edit().putString("app_pin", value).apply()

    var isOfflineAlarmEnabled: Boolean
        get() = prefs.getBoolean("offline_alarm", false)
        set(value) = prefs.edit().putBoolean("offline_alarm", value).apply()

    var appLanguage: String
        get() = prefs.getString("app_lang", "en") ?: "en"
        set(value) = prefs.edit().putString("app_lang", value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
