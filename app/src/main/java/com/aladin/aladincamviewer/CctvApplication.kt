package com.aladin.aladincamviewer

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * Custom Application class for 24/7 CCTV monitoring stability.
 * Implements a Crash Recovery Watchdog.
 */
class CctvApplication : Application() {

    private var lastCrashTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        setupWatchdog()
        CctvWatchdog.scheduleDailyRestart(this)
    }

    private fun setupWatchdog() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val currentTime = System.currentTimeMillis()
            
            // Eğer son 30 saniye içinde tekrar çöktüyse, bekleme süresini artır (Cihazı yormamak için)
            val restartDelay = if (currentTime - lastCrashTime < 30000) 10000L else 3000L
            lastCrashTime = currentTime

            throwable.printStackTrace()
            restartApp(restartDelay)
            
            // Orijinal işleyiciyi çağırmayalım ki döngüye girmesin, biz kendi restart'ımızı yapıyoruz
        }
    }

    private fun restartApp(delay: Long) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delay,
            pendingIntent
        )

        exitProcess(2)
    }
}
