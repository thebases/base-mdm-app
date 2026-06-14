package com.base.launcher.util

import android.content.SharedPreferences
import android.util.Log
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date

object PreferenceLogger {
    private val DEBUG = BuildConfig.DEVICE_ADMIN_DEBUG
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private fun _log(preferences: SharedPreferences, message: String) {
        Log.d(Const.LOG_TAG, message)
        if (DEBUG) {
            val logString = preferences.getString(Const.PREFERENCES_LOG_STRING, "") +
                    sdf.format(Date()) + " " + message + "\n"
            preferences.edit().putString(Const.PREFERENCES_LOG_STRING, logString).commit()
        }
    }

    @JvmStatic
    @Synchronized
    fun log(preferences: SharedPreferences, message: String) {
        _log(preferences, message)
    }

    @JvmStatic
    @Synchronized
    fun getLogString(preferences: SharedPreferences): String {
        return if (DEBUG) preferences.getString(Const.PREFERENCES_LOG_STRING, "") ?: "" else ""
    }

    @JvmStatic
    @Synchronized
    fun clearLogString(preferences: SharedPreferences) {
        if (DEBUG) {
            preferences.edit().putString(Const.PREFERENCES_LOG_STRING, "").commit()
        }
    }

    @JvmStatic
    @Synchronized
    fun printStackTrace(preferences: SharedPreferences, e: Exception) {
        val errors = StringWriter()
        e.printStackTrace(PrintWriter(errors))
        _log(preferences, errors.toString())
    }
}
