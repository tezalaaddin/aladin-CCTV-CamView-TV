package com.aladin.aladincamviewer

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Super-Simple Preference Helper to prevent startup crashes.
 * Uses regular SharedPreferences for maximum stability on all Android TV versions.
 */
class PreferenceHelper(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("aladin_prefs_v2", Context.MODE_PRIVATE)
    }

    val hasPin: Boolean get() = prefs.contains("app_pin_hash") || !prefs.getString("app_pin", "").isNullOrEmpty()

    fun setPin(value: String) {
        if (value.isBlank()) {
            prefs.edit().remove("app_pin").remove("app_pin_hash").remove("app_pin_salt").apply()
            return
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("app_pin_hash", hash(value, salt))
            .putString("app_pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .remove("app_pin")
            .apply()
    }

    fun verifyPin(value: String): Boolean {
        val legacy = prefs.getString("app_pin", "").orEmpty()
        if (legacy.isNotEmpty()) {
            val matches = constantTimeEquals(legacy, value)
            if (matches) setPin(value)
            return matches
        }
        val saltValue = prefs.getString("app_pin_salt", null) ?: return false
        val expected = prefs.getString("app_pin_hash", null) ?: return false
        val actual = hash(value, Base64.decode(saltValue, Base64.NO_WRAP))
        return constantTimeEquals(expected, actual)
    }

    var isOfflineAlarmEnabled: Boolean
        get() = prefs.getBoolean("offline_alarm", false)
        set(value) = prefs.edit().putBoolean("offline_alarm", value).apply()

    var appLanguage: String
        get() = prefs.getString("app_lang", "en") ?: "en"
        set(value) = prefs.edit().putString("app_lang", value).apply()

    var automaticNetworkRecovery: Boolean
        get() = prefs.getBoolean("automatic_network_recovery", false)
        set(value) = prefs.edit().putBoolean("automatic_network_recovery", value).apply()
    val hasAutomaticNetworkRecoveryChoice: Boolean
        get() = prefs.contains("automatic_network_recovery")

    var startOnBoot: Boolean
        get() = prefs.getBoolean("start_on_boot", false)
        set(value) = prefs.edit().putBoolean("start_on_boot", value).apply()

    var dailyMaintenance: Boolean
        get() = prefs.getBoolean("daily_maintenance", false)
        set(value) = prefs.edit().putBoolean("daily_maintenance", value).apply()

    var diagnosticLogging: Boolean
        get() = prefs.getBoolean("diagnostic_logging", false)
        set(value) = prefs.edit().putBoolean("diagnostic_logging", value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return Base64.encodeToString(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
            Base64.NO_WRAP
        )
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        val a = left.toByteArray()
        val b = right.toByteArray()
        var diff = a.size xor b.size
        for (index in 0 until maxOf(a.size, b.size)) {
            diff = diff or ((a.getOrElse(index) { 0 }).toInt() xor (b.getOrElse(index) { 0 }).toInt())
        }
        return diff == 0
    }
}
