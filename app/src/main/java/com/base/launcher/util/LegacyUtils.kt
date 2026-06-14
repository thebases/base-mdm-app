package com.base.launcher.util

import android.content.ComponentName
import android.content.Context
import com.base.launcher.AdminReceiver

object LegacyUtils {
    @JvmStatic
    fun getAdminComponentName(context: Context): ComponentName {
        return ComponentName(context.applicationContext, AdminReceiver::class.java)
    }
}
