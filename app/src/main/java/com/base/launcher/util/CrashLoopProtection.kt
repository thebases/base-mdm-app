package com.base.launcher.util

import android.content.Context
import android.util.Log
import com.base.launcher.Const

object CrashLoopProtection {
    private const val LOOP_TIME_SPAN = 60000L
    private const val LOOP_CRASHES = 3L
    private const val FAULT_PREFERENCE_NAME = "com.base.launcher.fault"
    private const val LAST_FAULT_TIME_PREFERENCE = "last_fault_time"
    private const val FAULT_COUNTER_PREFERENCE = "fault_counter"

    @JvmStatic
    fun registerFault(context: Context) {
        val preferences = context.applicationContext.getSharedPreferences(FAULT_PREFERENCE_NAME, Context.MODE_PRIVATE)
        val faultTime = System.currentTimeMillis()
        val lastFaultTime = preferences.getLong(LAST_FAULT_TIME_PREFERENCE, 0)
        if (faultTime - lastFaultTime > LOOP_TIME_SPAN) {
            Log.i(Const.LOG_TAG, "Crash registered once")
            preferences.edit()
                .putInt(FAULT_COUNTER_PREFERENCE, 1)
                .putLong(LAST_FAULT_TIME_PREFERENCE, faultTime)
                .commit()
            return
        }
        val crashCounter = preferences.getInt(FAULT_COUNTER_PREFERENCE, 0) + 1
        Log.i(Const.LOG_TAG, "Crash registered $crashCounter times within ${LOOP_TIME_SPAN} ms")
        preferences.edit().putInt(FAULT_COUNTER_PREFERENCE, crashCounter).commit()
    }

    @JvmStatic
    fun isCrashLoopDetected(context: Context): Boolean {
        val preferences = context.applicationContext.getSharedPreferences(FAULT_PREFERENCE_NAME, Context.MODE_PRIVATE)
        val faultTime = System.currentTimeMillis()
        val lastFaultTime = preferences.getLong(LAST_FAULT_TIME_PREFERENCE, 0)
        if (lastFaultTime == 0L) return false
        if (faultTime - lastFaultTime > LOOP_TIME_SPAN) {
            Log.i(Const.LOG_TAG, "No recent crashes registered")
            preferences.edit()
                .putInt(FAULT_COUNTER_PREFERENCE, 0)
                .putLong(LAST_FAULT_TIME_PREFERENCE, 0)
                .commit()
            return false
        }
        val crashCounter = preferences.getInt(FAULT_COUNTER_PREFERENCE, 0)
        if (crashCounter > LOOP_CRASHES) {
            Log.i(Const.LOG_TAG, "Crash loop detected!")
            return true
        }
        return false
    }
}
