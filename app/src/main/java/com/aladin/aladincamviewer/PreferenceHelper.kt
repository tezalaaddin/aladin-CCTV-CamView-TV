package com.aladin.aladincamviewer

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferenceHelper(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_aladin_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var appPin: String
        get() = prefs.getString("app_pin", "") ?: ""
        set(value) = prefs.edit().putString("app_pin", value).apply()

    var isOfflineAlarmEnabled: Boolean
        get() = prefs.getBoolean("offline_alarm", false)
        set(value) = prefs.edit().putBoolean("offline_alarm", value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
