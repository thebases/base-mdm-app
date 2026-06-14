package com.base.launcher.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.base.launcher.Const

object ConnectionWaiter {
    private val handler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun waitForConnect(context: Context, uiCallback: Runnable): Boolean {
        for (n in 0 until 10) {
            if (isNetworkAvailable(context)) {
                if (n > 0) Thread.sleep(2000)
                Log.d(Const.LOG_TAG, "Network is available, resuming flow")
                handler.post(uiCallback)
                return true
            }
            Log.d(Const.LOG_TAG, "Network is unavailable, waiting, attempts: ${9 - n}")
            Thread.sleep(2000)
        }
        Log.d(Const.LOG_TAG, "Proceed without network!")
        handler.post(uiCallback)
        return false
    }

    @JvmStatic
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val activeNetwork = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            return activeNetwork != null && activeNetwork.isConnected
        }
    }
}
