package com.aladin.aladincamviewer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.*

/**
 * Ensures the app restarts daily or after crash to maintain 24/7 uptime.
 * Standard maintenance is scheduled for 04:00 AM.
 */
object CctvWatchdog {

    private const val TAG = "ALADIN_WATCHDOG"

    fun scheduleDailyRestart(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BootReceiver::class.java).apply {
            action = "ACTION_DAILY_RESTART"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1001, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set alarm for 04:00 AM (Maintenance Window)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        Log.i(TAG, "⏰ Daily Maintenance Scheduled: ${calendar.time}")

        // Daily restart at 04:00 AM doesn't need to be exact. 
        // Using setAndAllowWhileIdle to ensure it fires even in Doze mode on TVs
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}
