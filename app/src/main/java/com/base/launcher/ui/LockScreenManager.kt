package com.base.launcher.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.base.launcher.R
import com.base.launcher.helper.SettingsHelper

class LockScreenManager(
    private val activity: AppCompatActivity,
    private val settingsHelper: SettingsHelper
) {
    var applicationNotAllowed: View? = null
        private set
    var lockScreen: View? = null
        private set

    fun overlayLockScreenParams(): WindowManager.LayoutParams {
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.type = com.base.launcher.util.Utils.OverlayWindowType()
        layoutParams.gravity = Gravity.RIGHT
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.format = PixelFormat.TRANSPARENT
        return layoutParams
    }

    fun createApplicationNotAllowedScreen(onPasswordRequested: () -> Unit) {
        if (applicationNotAllowed != null) return
        val manager = activity.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        applicationNotAllowed = LayoutInflater.from(activity).inflate(R.layout.layout_application_not_allowed, null)
        applicationNotAllowed!!.findViewById<View>(R.id.layout_application_not_allowed_continue).setOnClickListener {
            applicationNotAllowed!!.visibility = View.GONE
        }
        applicationNotAllowed!!.findViewById<View>(R.id.layout_application_not_allowed_admin).setOnClickListener {
            applicationNotAllowed!!.visibility = View.GONE
            onPasswordRequested()
        }
        val tvPackageId = applicationNotAllowed!!.findViewById<TextView>(R.id.package_id)
        tvPackageId.setOnClickListener {
            try {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Package ID", tvPackageId.text.toString())
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(activity, R.string.package_id_copied, android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        applicationNotAllowed!!.visibility = View.GONE
        try {
            manager.addView(applicationNotAllowed, overlayLockScreenParams())
        } catch (e: Exception) {
            try {
                val root = activity.findViewById<RelativeLayout>(R.id.activity_main)
                root.addView(applicationNotAllowed)
            } catch (e1: Exception) {
                e1.printStackTrace()
            }
        }
    }

    fun createLockScreen() {
        if (lockScreen != null) return
        val manager = activity.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lockScreen = LayoutInflater.from(activity).inflate(R.layout.layout_application_not_allowed, null)
        lockScreen!!.findViewById<View>(R.id.layout_application_not_allowed_continue).visibility = View.GONE
        lockScreen!!.findViewById<View>(R.id.layout_application_not_allowed_admin).visibility = View.GONE
        lockScreen!!.findViewById<View>(R.id.package_id).visibility = View.GONE
        lockScreen!!.findViewById<View>(R.id.message2).visibility = View.GONE
        val textView = lockScreen!!.findViewById<TextView>(R.id.message)
        textView.text = activity.getString(R.string.device_locked, SettingsHelper.getInstance(activity).getDeviceId())
        lockScreen!!.visibility = View.GONE
        try {
            manager.addView(lockScreen, overlayLockScreenParams())
        } catch (e: Exception) {
            try {
                val root = activity.findViewById<RelativeLayout>(R.id.activity_main)
                root.addView(lockScreen)
            } catch (e1: Exception) {
                e1.printStackTrace()
            }
        }
    }

    fun showLockScreen() {
        if (lockScreen == null) {
            createLockScreen()
            if (lockScreen == null) return
        }
        val lockAdminMessage = settingsHelper.getConfig().getLockMessage()
        var lockMessage = activity.getString(R.string.device_locked, SettingsHelper.getInstance(activity).getDeviceId())
        if (lockAdminMessage != null) {
            lockMessage += " $lockAdminMessage"
        }
        val textView = lockScreen!!.findViewById<TextView>(R.id.message)
        textView.text = lockMessage
        lockScreen!!.visibility = View.VISIBLE
    }

    fun hideLockScreen() {
        if (lockScreen != null && lockScreen!!.visibility == View.VISIBLE) {
            lockScreen!!.visibility = View.GONE
        }
    }

    fun removeViews(manager: WindowManager) {
        applicationNotAllowed?.let {
            try { manager.removeView(it) } catch (e: Exception) { e.printStackTrace() }
        }
        lockScreen?.let {
            try { manager.removeView(it) } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
