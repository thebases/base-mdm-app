package com.base.launcher.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.ProxyInfo
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.json.Action
import com.base.launcher.json.ServerConfig
import com.base.launcher.ui.MainActivity
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object Utils {
    @JvmStatic
    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return dpm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                dpm.isDeviceOwnerApp(context.packageName)
    }

    @JvmStatic
    fun getLauncherVariant(): String {
        return if (BuildConfig.FLAVOR.isNullOrEmpty()) "opensource" else BuildConfig.FLAVOR
    }

    @SuppressLint("NewApi")
    @JvmStatic
    fun autoGrantPhonePermission(context: Context): Boolean {
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponentName = LegacyUtils.getAdminComponentName(context)

            if (devicePolicyManager.getPermissionGrantState(adminComponentName,
                    context.packageName, Manifest.permission.READ_PHONE_STATE) !=
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) {
                if (!devicePolicyManager.setPermissionGrantState(adminComponentName,
                        context.packageName, Manifest.permission.READ_PHONE_STATE,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)) return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (devicePolicyManager.getPermissionGrantState(adminComponentName,
                        context.packageName, Manifest.permission.READ_PHONE_NUMBERS) !=
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) {
                    if (!devicePolicyManager.setPermissionGrantState(adminComponentName,
                            context.packageName, Manifest.permission.READ_PHONE_NUMBERS,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)) return false
                }
            }
            Log.i(Const.LOG_TAG, "READ_PHONE_STATE automatically granted")
            true
        } catch (e: NoSuchMethodError) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @SuppressLint("NewApi")
    @JvmStatic
    fun autoGrantRequestedPermissions(
        context: Context, packageName: String,
        @Nullable appPermissionStrategy: String?,
        forceSdCardPermissions: Boolean
    ): Boolean {
        var locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        var otherPermissionsState = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED

        when (appPermissionStrategy) {
            ServerConfig.APP_PERMISSIONS_ASK_LOCATION ->
                locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            ServerConfig.APP_PERMISSIONS_DENY_LOCATION ->
                locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
            ServerConfig.APP_PERMISSIONS_ASK_ALL -> {
                locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
                if (packageName != context.packageName)
                    otherPermissionsState = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            }
        }

        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)

        return try {
            val permissions = getRuntimePermissions(context.packageManager, packageName).toMutableList()

            if (forceSdCardPermissions) {
                if (!permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (!permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            // Android 11+: if MANAGE_EXTERNAL_STORAGE is requested, skip WRITE/READ_EXTERNAL_STORAGE
            if (permissions.contains(Manifest.permission.MANAGE_EXTERNAL_STORAGE) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                permissions.removeAll { it == Manifest.permission.WRITE_EXTERNAL_STORAGE ||
                        it == Manifest.permission.READ_EXTERNAL_STORAGE }
            }

            for (permission in permissions) {
                val permissionState = if (isLocationPermission(permission)) locationPermissionState else otherPermissionsState
                if (devicePolicyManager.getPermissionGrantState(adminComponentName, packageName, permission) != permissionState) {
                    if (!devicePolicyManager.setPermissionGrantState(adminComponentName, packageName, permission, permissionState)) {
                        Log.w(Const.LOG_TAG, "Failed to grant permission $permission")
                        return false
                    } else {
                        Log.d(Const.LOG_TAG, "Permission $permission granted to package $packageName")
                    }
                }
            }
            Log.i(Const.LOG_TAG, "Permissions automatically granted")
            true
        } catch (e: NoSuchMethodError) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun isLocationPermission(permission: String): Boolean {
        return Manifest.permission.ACCESS_COARSE_LOCATION == permission ||
               Manifest.permission.ACCESS_FINE_LOCATION == permission ||
               Manifest.permission.ACCESS_BACKGROUND_LOCATION == permission
    }

    private fun getRuntimePermissions(packageManager: PackageManager, packageName: String): List<String> {
        val permissions = mutableListOf<String>()
        val packageInfo = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (_: PackageManager.NameNotFoundException) {
            return permissions
        }
        var manageStorage = false
        if (packageInfo != null && packageInfo.requestedPermissions != null) {
            for (requestedPerm in packageInfo.requestedPermissions) {
                if (requestedPerm == Manifest.permission.MANAGE_EXTERNAL_STORAGE) manageStorage = true
                if (isRuntimePermission(packageManager, requestedPerm)) permissions.add(requestedPerm)
            }
            if (manageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                permissions.removeAll { it == Manifest.permission.WRITE_EXTERNAL_STORAGE ||
                        it == Manifest.permission.READ_EXTERNAL_STORAGE }
            }
        }
        return permissions
    }

    private fun isRuntimePermission(packageManager: PackageManager, permission: String): Boolean {
        return try {
            val pInfo = packageManager.getPermissionInfo(permission, 0)
            pInfo != null && (pInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun OverlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
    }

    @JvmStatic
    fun isLightColor(color: Int): Boolean {
        val threshold = 0xA0
        return Color.red(color) >= threshold && Color.green(color) >= threshold && Color.blue(color) >= threshold
    }

    @SuppressLint("NewApi")
    @JvmStatic
    fun setSystemUpdatePolicy(context: Context, systemUpdateType: Int, scheduledFrom: String?, scheduledTo: String?) {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val deviceAdmin = LegacyUtils.getAdminComponentName(context)

        val currentPolicy = try {
            devicePolicyManager.systemUpdatePolicy
        } catch (e: NoSuchMethodError) {
            Log.e(Const.LOG_TAG, "Failed to set system update policy: ${e.message}")
            return
        }

        if (currentPolicy != null) {
            if ((systemUpdateType == ServerConfig.SYSTEM_UPDATE_INSTANT && currentPolicy.policyType == SystemUpdatePolicy.TYPE_INSTALL_AUTOMATIC) ||
                (systemUpdateType == ServerConfig.SYSTEM_UPDATE_MANUAL && currentPolicy.policyType == SystemUpdatePolicy.TYPE_POSTPONE)) {
                return
            }
        }

        val newPolicy = when (systemUpdateType) {
            ServerConfig.SYSTEM_UPDATE_INSTANT -> SystemUpdatePolicy.createAutomaticInstallPolicy()
            ServerConfig.SYSTEM_UPDATE_SCHEDULE -> {
                if (scheduledFrom != null && scheduledTo != null) {
                    val windowStart = getMinutesFromString(scheduledFrom)
                    val windowEnd = getMinutesFromString(scheduledTo)
                    if (windowStart == -1) { Log.e(Const.LOG_TAG, "Wrong start time: $scheduledFrom"); return }
                    if (windowEnd == -1) { Log.e(Const.LOG_TAG, "Wrong end time: $scheduledTo"); return }
                    SystemUpdatePolicy.createWindowedInstallPolicy(windowStart, windowEnd)
                } else {
                    Log.e(Const.LOG_TAG, "Ignoring scheduled system update policy: update window is not set on server")
                    return
                }
            }
            ServerConfig.SYSTEM_UPDATE_MANUAL -> SystemUpdatePolicy.createPostponeInstallPolicy()
            else -> null
        }
        try {
            devicePolicyManager.setSystemUpdatePolicy(deviceAdmin, newPolicy)
        } catch (e: Exception) {
            Log.e(Const.LOG_TAG, "Failed to set system update policy: ${e.message}")
        }
    }

    private fun getMinutesFromString(s: String): Int {
        return try {
            s.substring(0, 2).toInt() * 60 + s.substring(3, 5).toInt()
        } catch (_: Exception) {
            -1
        }
    }

    @JvmStatic
    fun canInstallPackages(context: Context): Boolean {
        if (BuildConfig.SYSTEM_PRIVILEGES) return true
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS) == 1
            } catch (_: Settings.SettingNotFoundException) {
                true
            }
        } else {
            context.packageManager.canRequestPackageInstalls()
        }
    }

    @JvmStatic
    fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    @JvmStatic
    fun checkAdminMode(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isAdminActive(LegacyUtils.getAdminComponentName(context))
        } catch (_: Exception) {
            true
        }
    }

    @JvmStatic
    fun factoryReset(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION") dpm.wipeData(0)
            } else {
                dpm.wipeDevice(0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun reboot(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.reboot(LegacyUtils.getAdminComponentName(context))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getDataToken(context: Context): String {
        val prefs = context.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE)
        return prefs.getString(Const.PREFERENCES_DATA_TOKEN, null) ?: run {
            val token = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(Const.PREFERENCES_DATA_TOKEN, token).commit()
            token
        }
    }

    @JvmStatic
    fun initPasswordReset(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val token = getDataToken(context)
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponentName = LegacyUtils.getAdminComponentName(context)
                if (dpm.setResetPasswordToken(adminComponentName, token.toByteArray())) {
                    if (!dpm.isResetPasswordTokenActive(adminComponentName)) {
                        RemoteLogger.log(context, Const.LOG_WARN, "Password reset token will be activated once the user enters the current password next time.")
                    }
                } else {
                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to setup password reset token, password reset requests will fail")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun passwordReset(context: Context, password: String): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val adminComponentName = LegacyUtils.getAdminComponentName(context)
                if (!dpm.isResetPasswordTokenActive(adminComponentName)) return false
                dpm.resetPasswordWithToken(adminComponentName, password, getDataToken(context).toByteArray(), 0)
            } else {
                @Suppress("DEPRECATION") dpm.resetPassword(password, 0)
            }
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun isMobileDataEnabled(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return try {
            val clazz = Class.forName(cm.javaClass.name)
            val method = clazz.getDeclaredMethod("getMobileDataEnabled")
            method.isAccessible = true
            method.invoke(cm) as Boolean
        } catch (_: Exception) {
            true
        }
    }

    @JvmStatic
    fun isPackageInstalled(context: Context, targetPackage: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(targetPackage, PackageManager.GET_META_DATA)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun isMiui(context: Context): Boolean {
        return isPackageInstalled(context, "com.miui.home") ||
               isPackageInstalled(context, "com.miui.securitycenter")
    }

    @JvmStatic
    fun lockSafeBoot(context: Context): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        return try {
            devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_SAFE_BOOT)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun lockUsbStorage(lock: Boolean, context: Context): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return try {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1)
                    Settings.Secure.putInt(context.contentResolver, Settings.Secure.USB_MASS_STORAGE_ENABLED, 0)
                else
                    Settings.Global.putInt(context.contentResolver, Settings.Global.USB_MASS_STORAGE_ENABLED, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        return try {
            if (lock) {
                devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_USB_FILE_TRANSFER)
                devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            } else {
                devicePolicyManager.clearUserRestriction(adminComponentName, UserManager.DISALLOW_USB_FILE_TRANSFER)
                devicePolicyManager.clearUserRestriction(adminComponentName, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun setBrightnessPolicy(auto: Boolean?, brightness: Int?, context: Context): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        return try {
            if (auto == null) {
                devicePolicyManager.clearUserRestriction(adminComponentName, UserManager.DISALLOW_CONFIG_BRIGHTNESS)
            } else {
                devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_CONFIG_BRIGHTNESS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (auto) {
                        devicePolicyManager.setSystemSetting(adminComponentName, Settings.System.SCREEN_BRIGHTNESS_MODE, "1")
                    } else {
                        devicePolicyManager.setSystemSetting(adminComponentName, Settings.System.SCREEN_BRIGHTNESS_MODE, "0")
                        if (brightness != null)
                            devicePolicyManager.setSystemSetting(adminComponentName, Settings.System.SCREEN_BRIGHTNESS, "$brightness")
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun setScreenTimeoutPolicy(lock: Boolean?, timeout: Int?, context: Context): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        return try {
            if (lock == null || !lock) {
                devicePolicyManager.clearUserRestriction(adminComponentName, UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT)
            } else {
                devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && timeout != null)
                    devicePolicyManager.setSystemSetting(adminComponentName, Settings.System.SCREEN_OFF_TIMEOUT, "${timeout * 1000}")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun lockVolume(lock: Boolean?, context: Context): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        return try {
            if (lock == null || !lock) {
                Log.d(Const.LOG_TAG, "Unlocking volume")
                devicePolicyManager.clearUserRestriction(adminComponentName, UserManager.DISALLOW_ADJUST_VOLUME)
            } else {
                Log.d(Const.LOG_TAG, "Locking volume")
                devicePolicyManager.addUserRestriction(adminComponentName, UserManager.DISALLOW_ADJUST_VOLUME)
            }
            true
        } catch (e: Exception) {
            Log.w(Const.LOG_TAG, "Failed to lock/unlock volume: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun setVolume(percent: Int, context: Context): Boolean {
        val streams = intArrayOf(
            AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING, AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM
        )
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            for (s in streams) {
                val maxVolume = audioManager.getStreamMaxVolume(s)
                val volume = (maxVolume * percent) / 100
                audioManager.setStreamVolume(s, volume, 0)
            }
            true
        } catch (e: Exception) {
            Log.w(Const.LOG_TAG, "Failed to set volume: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun disableScreenshots(disabled: Boolean?, context: Context): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        return try {
            devicePolicyManager.setScreenCaptureDisabled(adminComponentName, disabled ?: false)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun setPasswordMode(passwordMode: String?, context: Context): Boolean {
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponentName = LegacyUtils.getAdminComponentName(context)
            when (passwordMode) {
                null -> devicePolicyManager.setPasswordQuality(adminComponentName, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED)
                Const.PASSWORD_QUALITY_PRESENT -> {
                    devicePolicyManager.setPasswordQuality(adminComponentName, DevicePolicyManager.PASSWORD_QUALITY_NUMERIC)
                    @Suppress("DEPRECATION") devicePolicyManager.setPasswordMinimumLength(adminComponentName, 1)
                }
                Const.PASSWORD_QUALITY_EASY -> {
                    devicePolicyManager.setPasswordQuality(adminComponentName, DevicePolicyManager.PASSWORD_QUALITY_NUMERIC)
                    @Suppress("DEPRECATION") devicePolicyManager.setPasswordMinimumLength(adminComponentName, 6)
                }
                Const.PASSWORD_QUALITY_MODERATE -> {
                    devicePolicyManager.setPasswordQuality(adminComponentName, DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC)
                    @Suppress("DEPRECATION") devicePolicyManager.setPasswordMinimumLength(adminComponentName, 8)
                }
                Const.PASSWORD_QUALITY_STRONG -> {
                    devicePolicyManager.setPasswordQuality(adminComponentName, DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)
                    @Suppress("DEPRECATION") devicePolicyManager.setPasswordMinimumLowerCase(adminComponentName, 1)
                    @Suppress("DEPRECATION") devicePolicyManager.setPasswordMinimumUpperCase(adminComponentName, 1)
                    @Suppress("DEPRECATION") devicePolicyManager.setPasswordMinimumNumeric(adminComponentName, 1)
                    @Suppress("DEPRECATION") devicePolicyManager.setPasswordMinimumSymbols(adminComponentName, 1)
                    @Suppress("DEPRECATION") devicePolicyManager.setPasswordMinimumLength(adminComponentName, 8)
                }
            }
            val result = devicePolicyManager.isActivePasswordSufficient
            if (passwordMode != null) RemoteLogger.log(context, Const.LOG_DEBUG, "Active password quality sufficient: $result")
            result
        } catch (e: Exception) {
            e.printStackTrace()
            if (passwordMode != null) RemoteLogger.log(context, Const.LOG_WARN, "Failed to update password quality: ${e.message}")
            true
        }
    }

    @JvmStatic
    fun setTimeZone(timeZone: String?, context: Context): Boolean {
        if (!isDeviceOwner(context) || timeZone == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponentName = LegacyUtils.getAdminComponentName(context)
            if (timeZone == "auto") {
                devicePolicyManager.setGlobalSetting(adminComponentName, Settings.Global.AUTO_TIME_ZONE, "1")
            } else {
                devicePolicyManager.setGlobalSetting(adminComponentName, Settings.Global.AUTO_TIME_ZONE, "0")
                devicePolicyManager.setTimeZone(adminComponentName, timeZone)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            true
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @JvmStatic
    fun setOrientation(activity: Activity, config: ServerConfig) {
        var loggedOrientation = "unspecified"
        if (config.orientation != null && config.orientation != 0) {
            when (config.orientation) {
                Const.SCREEN_ORIENTATION_PORTRAIT -> {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    loggedOrientation = "portrait"
                }
                Const.SCREEN_ORIENTATION_LANDSCAPE -> {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    loggedOrientation = "landscape"
                }
                else -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        Log.i(Const.LOG_TAG, "Set orientation: $loggedOrientation")
    }

    @JvmStatic
    fun isLauncherIntent(intent: Intent?): Boolean {
        val categories = intent?.categories ?: return false
        return categories.contains(Intent.CATEGORY_LAUNCHER)
    }

    @JvmStatic
    fun getDefaultLauncher(context: Context): String? {
        return getDefaultLauncherInfo(context)?.packageName
    }

    @JvmStatic
    fun getDefaultLauncherInfo(context: Context): ActivityInfo? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo
    }

    @JvmStatic
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == serviceClass.name }
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun setDefaultLauncher(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        setPreferredActivity(context, filter, ComponentName(context, MainActivity::class.java), "Set Base MDM as default launcher")
    }

    @JvmStatic
    fun clearDefaultLauncher(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        setPreferredActivity(context, filter, null, "Reset default launcher")
    }

    @JvmStatic
    fun setAction(context: Context, action: Action) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        try {
            val filter = IntentFilter("android.intent.action.${action.action}")

            if (!action.categories.isNullOrEmpty()) {
                action.categories!!.split(",").forEach { filter.addCategory("android.intent.category.$it") }
            }
            if (!action.mimeTypes.isNullOrEmpty()) {
                action.mimeTypes!!.split(",").forEach {
                    try { filter.addDataType(it) } catch (_: IntentFilter.MalformedMimeTypeException) {}
                }
            }
            if (!action.schemes.isNullOrEmpty()) {
                action.schemes!!.split(",").forEach { filter.addDataScheme(it) }
                if (!action.hosts.isNullOrEmpty()) {
                    action.hosts!!.split(",").forEach { host ->
                        val hostport = host.split(":")
                        when (hostport.size) {
                            1 -> filter.addDataAuthority(hostport[0], null)
                            2 -> filter.addDataAuthority(hostport[0], hostport[1])
                        }
                    }
                }
            }
            val activity = ComponentName(action.packageId!!, action.activity!!)
            setPreferredActivity(context, filter, activity, "Set ${action.packageId}/${action.activity} as default for ${action.action}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setPreferredActivity(context: Context, filter: IntentFilter, activity: ComponentName?, logMessage: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            if (activity != null) {
                dpm.addPersistentPreferredActivity(adminComponentName, filter, activity)
            } else {
                dpm.clearPackagePersistentPreferredActivities(adminComponentName, context.packageName)
            }
            RemoteLogger.log(context, Const.LOG_DEBUG, "$logMessage - success")
        } catch (e: Exception) {
            e.printStackTrace()
            RemoteLogger.log(context, Const.LOG_WARN, "$logMessage - failure: ${e.message}")
        }
    }

    @JvmStatic
    fun releaseUserRestrictions(context: Context, restrictions: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        restrictions.split(",").forEach {
            try { dpm.clearUserRestriction(adminComponentName, it.trim()) } catch (_: Exception) {}
        }
    }

    @JvmStatic
    fun lockUserRestrictions(context: Context, restrictions: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        restrictions.split(",").forEach {
            try { dpm.addUserRestriction(adminComponentName, it.trim()) } catch (_: Exception) {}
        }
    }

    @JvmStatic
    fun unlockUserRestrictions(context: Context, restrictions: String) {
        releaseUserRestrictions(context, restrictions)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @JvmStatic
    fun setProxy(context: Context, proxyUrl: String?): Boolean {
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return try {
            val proxyInfo = if (proxyUrl != null) {
                val parts = proxyUrl.split(":")
                if (parts.size != 2) { Log.d(Const.LOG_TAG, "Invalid proxy URL: $proxyUrl"); return false }
                ProxyInfo.buildDirectProxy(parts[0], parts[1].toInt())
            } else null
            dpm.setRecommendedGlobalProxy(adminComponentName, proxyInfo)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun loadFileAsString(filePath: String): String {
        val fileData = StringBuilder()
        BufferedReader(FileReader(filePath)).use { reader ->
            val buf = CharArray(1024)
            var numRead: Int
            while (reader.read(buf).also { numRead = it } != -1) {
                fileData.append(String(buf, 0, numRead))
            }
        }
        return fileData.toString()
    }

    @JvmStatic
    fun loadStreamAsString(inputStreamReader: InputStreamReader): String? {
        return try {
            val sb = StringBuilder()
            BufferedReader(inputStreamReader).use { reader ->
                var s: String?
                while (reader.readLine().also { s = it } != null) sb.append(s).append("\n")
            }
            sb.toString()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun writeStringToFile(fileName: String, fileContent: String, overwrite: Boolean): Boolean {
        return try {
            val file = File(fileName)
            if (file.exists()) {
                if (overwrite) file.delete() else return false
            }
            file.createNewFile()
            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos).use { writer -> writer.append(fileContent) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun startStableForegroundService(service: Service, notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (isDeviceOwner(service))
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            else
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            try {
                service.startForeground(notificationId, notification, serviceType)
            } catch (_: Exception) {
                service.startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        } else {
            service.startForeground(notificationId, notification)
        }
    }
}
