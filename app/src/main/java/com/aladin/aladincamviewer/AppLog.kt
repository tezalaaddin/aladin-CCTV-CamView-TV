package com.aladin.aladincamviewer

import android.content.Context
import android.util.Log

/** Production logs are opt-in; debug builds remain verbose for development. */
object AppLog {
    @Volatile private var enabled = BuildConfig.DEBUG

    fun initialize(context: Context) {
        enabled = BuildConfig.DEBUG || PreferenceHelper(context.applicationContext).diagnosticLogging
    }

    fun d(tag: String, message: String, error: Throwable? = null) = write(Log.DEBUG, tag, message, error)
    fun i(tag: String, message: String, error: Throwable? = null) = write(Log.INFO, tag, message, error)
    fun w(tag: String, message: String, error: Throwable? = null) = write(Log.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write(Log.ERROR, tag, message, error)

    private fun write(priority: Int, tag: String, message: String, error: Throwable?): Int {
        if (!enabled) return 0
        return Log.println(priority, tag, if (error == null) message else "$message (${error.javaClass.simpleName})")
    }
}
