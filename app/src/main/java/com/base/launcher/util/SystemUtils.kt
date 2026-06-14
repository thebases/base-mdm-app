package com.base.launcher.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.helper.SettingsHelper
import java.io.BufferedReader
import java.io.InputStreamReader

object SystemUtils {
    @JvmStatic
    fun becomeDeviceOwnerByCommand(context: Context): Boolean {
        val command = "dpm set-device-owner ${context.packageName}/.AdminReceiver"
        val result = executeShellCommand(command, false)
        RemoteLogger.log(context, Const.LOG_INFO, "DPM command output: $result")
        return result.startsWith("Active admin component set")
    }

    @JvmStatic
    fun executeShellCommand(command: String, useShell: Boolean): String {
        val output = StringBuilder()
        try {
            val p = if (useShell) Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    else Runtime.getRuntime().exec(command)
            p.waitFor()
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            if (output.toString().trim().isEmpty()) {
                val errorReader = BufferedReader(InputStreamReader(p.errorStream))
                while (errorReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return output.toString()
    }

    @JvmStatic
    fun autoSetDeviceId(context: Context): Boolean {
        val deviceIdUse = SettingsHelper.getInstance(context).deviceIdUse
        var deviceId: String? = null
        Log.d(Const.LOG_TAG, "Device ID choice: $deviceIdUse")
        when {
            BuildConfig.DEVICE_ID_CHOICE == "imei" || "imei" == deviceIdUse ->
                deviceId = DeviceInfoProvider.getImei(context)
            BuildConfig.DEVICE_ID_CHOICE == "serial" || "serial" == deviceIdUse -> {
                deviceId = DeviceInfoProvider.getSerialNumber()
                if (deviceId == Build.UNKNOWN) deviceId = null
            }
            BuildConfig.DEVICE_ID_CHOICE == "mac" ->
                deviceId = DeviceInfoProvider.getMacAddress()
        }
        if (deviceId.isNullOrEmpty()) return false
        return SettingsHelper.getInstance(context.applicationContext).setDeviceId(deviceId)
    }

    @JvmStatic
    fun becomeDeviceOwnerByXmlFile(context: Context): Boolean {
        val cn = LegacyUtils.getAdminComponentName(context)

        val deviceOwnerFileName = "/data/system/device_owner_2.xml"
        val deviceOwnerFileContent = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                "<root>\n" +
                "<device-owner package=\"${cn.packageName}\" name=\"\" " +
                "component=\"${cn.packageName}/${cn.className}\" userRestrictionsMigrated=\"true\" canAccessDeviceIds=\"true\" />\n" +
                "<device-owner-context userId=\"0\" />\n" +
                "</root>"

        val devicePoliciesFileName = "/data/system/device_policies.xml"
        val devicePoliciesFileContent = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                "<policies setup-complete=\"true\" provisioning-state=\"3\">\n" +
                "<admin name=\"${cn.packageName}/${cn.className}\">\n" +
                "<policies flags=\"17\" />\n" +
                "<strong-auth-unlock-timeout value=\"0\" />\n" +
                "<user-restrictions no_add_managed_profile=\"true\" />\n" +
                "<default-enabled-user-restrictions>\n" +
                "<restriction value=\"no_add_managed_profile\" />\n" +
                "</default-enabled-user-restrictions>\n" +
                "<cross-profile-calendar-packages />\n" +
                "</admin>\n" +
                "<password-validity value=\"true\" />\n" +
                "<lock-task-features value=\"16\" />\n" +
                "</policies>"

        if (!Utils.writeStringToFile(deviceOwnerFileName, deviceOwnerFileContent, false)) {
            Log.e(Const.LOG_TAG, "Could not create device owner file $deviceOwnerFileName")
            return false
        }
        if (!Utils.writeStringToFile(devicePoliciesFileName, devicePoliciesFileContent, true)) {
            Log.e(Const.LOG_TAG, "Could not update device policies file $devicePoliciesFileName")
            return false
        }
        return true
    }

    @JvmStatic
    fun autoSetAccessibilityPermission(context: Context, packageName: String, className: String) {
        Settings.Secure.putString(context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "$packageName/$className")
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
    }

    const val OP_WRITE_SETTINGS = 23
    const val OP_SYSTEM_ALERT_WINDOW = 24
    const val OP_GET_USAGE_STATS = 43
    const val OP_MANAGE_EXTERNAL_STORAGE = 92

    @JvmStatic
    fun autoSetOverlayPermission(context: Context, packageName: String) =
        autoSetPermission(context, packageName, OP_SYSTEM_ALERT_WINDOW, "Overlay")

    @JvmStatic
    fun autoSetUsageStatsPermission(context: Context, packageName: String) =
        autoSetPermission(context, packageName, OP_GET_USAGE_STATS, "Usage history")

    @JvmStatic
    fun autoSetStoragePermission(context: Context, packageName: String) =
        autoSetPermission(context, packageName, OP_MANAGE_EXTERNAL_STORAGE, "Manage storage")

    @JvmStatic
    fun autoSetPermission(context: Context, packageName: String, permission: Int, permText: String): Boolean {
        val packageManager = context.packageManager
        val uid = try {
            packageManager.getApplicationInfo(packageName, 0).uid
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return false
        }
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return try {
            val clazz = AppOpsManager::class.java
            val method = clazz.getDeclaredMethod("setMode", Int::class.java, Int::class.java, String::class.java, Int::class.java)
            method.invoke(appOpsManager, permission, uid, packageName, AppOpsManager.MODE_ALLOWED)
            Log.d(Const.LOG_TAG, "$permText permission granted to $packageName")
            true
        } catch (e: Exception) {
            Log.e(Const.LOG_TAG, Log.getStackTraceString(e))
            false
        }
    }
}
