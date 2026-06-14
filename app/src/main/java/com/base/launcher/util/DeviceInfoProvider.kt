package com.base.launcher.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.db.DatabaseHelper
import com.base.launcher.db.RemoteFileTable
import com.base.launcher.helper.SettingsHelper
import com.base.launcher.json.Application
import com.base.launcher.json.DeviceInfo
import com.base.launcher.json.RemoteFile
import com.base.launcher.pro.ProUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.reflect.Method

object DeviceInfoProvider {
    @JvmStatic
    fun getDeviceInfo(context: Context, queryPermissions: Boolean, queryApps: Boolean): DeviceInfo {
        val deviceInfo = DeviceInfo()
        val permissions = deviceInfo.permissions
        val applications = deviceInfo.applications
        val files = deviceInfo.files

        deviceInfo.model = Build.MODEL

        if (queryPermissions) {
            permissions.add(if (Utils.checkAdminMode(context)) 1 else 0)
            permissions.add(if (Utils.canDrawOverlays(context)) 1 else 0)
            permissions.add(if (ProUtils.checkUsageStatistics(context)) 1 else 0)
            permissions.add(if (!BuildConfig.USE_ACCESSIBILITY || !ProUtils.checkAccessibilityService(context)) 0 else 1)
        }

        val config = SettingsHelper.getInstance(context)
        if (queryApps) {
            val packageManager = context.packageManager
            if (config.config != null) {
                val requiredApps = SettingsHelper.getInstance(context).config.applications
                for (application in requiredApps) {
                    if (application.isRemove) continue
                    try {
                        val packageInfo = packageManager.getPackageInfo(application.pkg, 0)
                        val installedApp = Application().apply {
                            name = application.name
                            pkg = packageInfo.packageName
                            version = packageInfo.versionName
                        }
                        val appPresents = applications.any { it.pkg.equals(installedApp.pkg, ignoreCase = true) }
                        if (!appPresents) applications.add(installedApp)
                    } catch (_: PackageManager.NameNotFoundException) {}
                }

                val requiredFiles = SettingsHelper.getInstance(context).config.files
                for (remoteFile in requiredFiles) {
                    val file = File(Environment.getExternalStorageDirectory(), remoteFile.path)
                    if (file.exists()) {
                        val remoteFileDb = RemoteFileTable.selectByPath(
                            DatabaseHelper.instance(context).readableDatabase, remoteFile.path)
                        if (remoteFileDb != null) {
                            files.add(remoteFileDb)
                        } else {
                            try {
                                val copy = RemoteFile(remoteFile).apply {
                                    checksum = CryptoUtils.calculateChecksum(FileInputStream(file))
                                }
                                files.add(copy)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        deviceInfo.deviceId = SettingsHelper.getInstance(context).deviceId

        var phone = getPhoneNumber(context, 0)
        if (phone.isNullOrEmpty()) phone = config.config?.phone
        deviceInfo.phone = phone

        var imei = getImei(context, 0)
        if (imei.isNullOrEmpty()) imei = config.config?.imei
        deviceInfo.imei = imei

        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.registerReceiver(null, ifilter, Context.RECEIVER_EXPORTED)
        else
            context.registerReceiver(null, ifilter)

        val status = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
            when (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                BatteryManager.BATTERY_PLUGGED_USB -> deviceInfo.batteryCharging = Const.DEVICE_CHARGING_USB
                BatteryManager.BATTERY_PLUGGED_AC  -> deviceInfo.batteryCharging = Const.DEVICE_CHARGING_AC
            }
        } else {
            deviceInfo.batteryCharging = ""
        }

        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        deviceInfo.batteryLevel = level * 100 / scale

        deviceInfo.androidVersion = Build.VERSION.RELEASE
        deviceInfo.location = getLocation(context)
        deviceInfo.isMdmMode = Utils.isDeviceOwner(context)
        deviceInfo.setKioskMode(ProUtils.isKioskModeRunning(context))
        deviceInfo.launcherType = Utils.getLauncherVariant()
        deviceInfo.cpu = Build.CPU_ABI
        deviceInfo.serial = getSerialNumber()

        deviceInfo.imsi = getImsi(context, 0)
        deviceInfo.iccid = getIccid(context, 0)
        deviceInfo.imei2 = getImei(context, 1)
        deviceInfo.imsi2 = getImsi(context, 1)
        deviceInfo.phone2 = getPhoneNumber(context, 1)
        deviceInfo.iccid2 = getIccid(context, 1)

        val launcherPackage = Utils.getDefaultLauncher(context)
        deviceInfo.launcherPackage = launcherPackage ?: ""
        deviceInfo.isDefaultLauncher = context.packageName == launcherPackage

        deviceInfo.custom1 = config.userCustom1
        deviceInfo.custom2 = config.userCustom2
        deviceInfo.custom3 = config.userCustom3

        return deviceInfo
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun getLocation(context: Context): DeviceInfo.Location? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (lastGps == null && lastNetwork == null) return null

            val lastLocation = when {
                lastGps == null || (lastGps.latitude == 0.0 && lastGps.longitude == 0.0) -> lastNetwork!!
                lastNetwork == null || (lastNetwork.latitude == 0.0 && lastNetwork.longitude == 0.0) -> lastGps
                else -> if (lastGps.time >= lastNetwork.time) lastGps else lastNetwork
            }

            if (lastLocation.latitude == 0.0 && lastLocation.longitude == 0.0) return null

            DeviceInfo.Location().apply {
                lat = lastLocation.latitude
                lon = lastLocation.longitude
                ts = lastLocation.time
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun getSerialNumber(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return Build.getSerial().also { Log.d(Const.LOG_TAG, "Serial number: $it") }
            } catch (e: SecurityException) {
                Log.w(Const.LOG_TAG, "Failed to get serial number from Build.getSerial()")
                e.printStackTrace()
            }
        }
        var serialNumber: String? = null
        try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            serialNumber = get.invoke(c, "ril.serialnumber") as? String
        } catch (e: Exception) {
            Log.w(Const.LOG_TAG, "Failed to get serial number from ril.serialnumber")
            e.printStackTrace()
        }
        if (!serialNumber.isNullOrEmpty()) return serialNumber
        Log.d(Const.LOG_TAG, "Build.SERIAL=${Build.SERIAL}")
        @Suppress("DEPRECATION")
        return Build.SERIAL
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun getPhoneNumber(context: Context): String? {
        return try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.line1Number
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun getIccid(context: Context): String? {
        return try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.simSerialNumber
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun getImsi(context: Context): String? {
        return try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.subscriberId
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun getImsi(context: Context, slot: Int): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val c = Class.forName("android.telephony.TelephonyManager")
            val m = c.getMethod("getSubscriberId", Int::class.java)
            m.invoke(telephonyManager, slot) as? String
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun getPhoneNumber(context: Context, slot: Int): String? {
        return try {
            Utils.autoGrantPhonePermission(context)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                return if (slot == 0) getPhoneNumber(context) else null
            }
            val subscriptionManager = SubscriptionManager.from(context)
            val subscriptionList = subscriptionManager.activeSubscriptionInfoList
            if (subscriptionList == null || slot >= subscriptionList.size) return null
            subscriptionList[slot].number
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun getIccid(context: Context, slot: Int): String? {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                return if (slot == 0) getPhoneNumber(context) else null
            }
            val subscriptionManager = SubscriptionManager.from(context)
            val subscriptionList = subscriptionManager.activeSubscriptionInfoList
            if (subscriptionList == null || slot >= subscriptionList.size) return null
            subscriptionList[slot].iccId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun getImei(context: Context): String? {
        return try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.deviceId
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    @JvmStatic
    fun getImei(context: Context, slot: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return if (slot == 0) getImei(context) else null
        }
        return try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.getDeviceId(slot)
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun getMacAddress(): String? {
        return try {
            Utils.loadFileAsString("/sys/class/net/eth0/address").uppercase().substring(0, 17)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
