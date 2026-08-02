package com.aladin.aladincamviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("aladin_prefs_v2", Context.MODE_PRIVATE)
            .getString("app_lang", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        TvFocusManager.install(this)

        findViewById<TextView>(R.id.about_version).text =
            getString(R.string.about_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        findViewById<android.view.View>(R.id.btn_check_updates).setOnClickListener {
            Toast.makeText(this, R.string.opening_play_store_update, Toast.LENGTH_SHORT).show()
            openPlayStore()
        }
        findViewById<android.view.View>(R.id.btn_play_store).setOnClickListener { openPlayStore() }
        findViewById<android.view.View>(R.id.btn_website).setOnClickListener {
            openUrl("https://cctv.erisim.com.tr/")
        }
        findViewById<android.view.View>(R.id.btn_privacy).setOnClickListener {
            openUrl("https://cctv.erisim.com.tr/privacy-policy.html")
        }
        findViewById<android.view.View>(R.id.btn_github).setOnClickListener {
            openUrl("https://github.com/tezalaaddin/aladin-CCTV-CamView-TV")
        }
        findViewById<android.view.View>(R.id.btn_licenses).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.open_source_licenses)
                .setMessage(R.string.open_source_summary)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun openPlayStore() {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        try {
            startActivity(marketIntent)
        } catch (_: Exception) {
            openUrl("https://play.google.com/store/apps/details?id=$packageName")
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.no_browser_available, Toast.LENGTH_SHORT).show()
        }
    }
}
