package com.aladin.aladincamviewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver that catches the BOOT_COMPLETED intent and starts the app automatically.
 * Useful for dedicated CCTV TVs.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "ACTION_DAILY_RESTART") {
            val i = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        }
    }
}
