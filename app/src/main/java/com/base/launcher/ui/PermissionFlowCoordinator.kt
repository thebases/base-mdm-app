package com.base.launcher.ui

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.base.launcher.Const
import com.base.launcher.IBeaconAdvertiser
import com.base.launcher.R
import com.base.launcher.databinding.DialogAccessibilityServiceBinding
import com.base.launcher.databinding.DialogAdministratorModeBinding
import com.base.launcher.databinding.DialogHistorySettingsBinding
import com.base.launcher.databinding.DialogManageStorageBinding
import com.base.launcher.databinding.DialogMiuiPermissionsBinding
import com.base.launcher.databinding.DialogOverlaySettingsBinding
import com.base.launcher.databinding.DialogPermissionsBinding
import com.base.launcher.databinding.DialogUnknownSourcesBinding
import com.base.launcher.helper.SettingsHelper
import com.base.launcher.pro.ProUtils
import com.base.launcher.pro.service.CheckForegroundAppAccessibilityService
import com.base.launcher.util.RemoteLogger
import com.base.launcher.util.SystemUtils
import com.base.launcher.util.Utils
import java.util.concurrent.ExecutorService

class PermissionFlowCoordinator(
    private val activity: AppCompatActivity,
    private val settingsHelper: SettingsHelper,
    private val preferences: SharedPreferences,
    private val backgroundExecutor: ExecutorService,
    private val handler: Handler,
    private val onStartLauncher: () -> Unit
) {
    companion object {
        private const val PERMISSIONS_REQUEST = 1000
        private const val REQ_BT_PERMS = 1001
        private const val TAG = "PermissionFlow"
    }

    // Dialog references
    private var administratorModeDialog: Dialog? = null
    private var dialogAdministratorModeBinding: DialogAdministratorModeBinding? = null

    private var accessibilityServiceDialog: Dialog? = null
    private var dialogAccessibilityServiceBinding: DialogAccessibilityServiceBinding? = null

    private var historySettingsDialog: Dialog? = null
    private var dialogHistorySettingsBinding: DialogHistorySettingsBinding? = null

    private var manageStorageDialog: Dialog? = null
    private var dialogManageStorageBinding: DialogManageStorageBinding? = null

    private var overlaySettingsDialog: Dialog? = null
    private var dialogOverlaySettingsBinding: DialogOverlaySettingsBinding? = null

    private var unknownSourcesDialog: Dialog? = null
    private var dialogUnknownSourcesBinding: DialogUnknownSourcesBinding? = null

    private var miuiPermissionsDialog: Dialog? = null
    private var dialogMiuiPermissionsBinding: DialogMiuiPermissionsBinding? = null

    private var permissionsDialog: Dialog? = null
    private var dialogPermissionsBinding: DialogPermissionsBinding? = null

    val isPermissionsDialogShowing: Boolean
        get() = permissionsDialog?.isShowing == true

    fun dismissAll() {
        listOf(
            administratorModeDialog, accessibilityServiceDialog, historySettingsDialog,
            manageStorageDialog, overlaySettingsDialog, unknownSourcesDialog,
            miuiPermissionsDialog, permissionsDialog
        ).forEach { dismissDialog(it) }
    }

    private fun dismissDialog(dialog: Dialog?) {
        if (dialog != null) try { dialog.dismiss() } catch (e: Exception) { /* ignored */ }
    }

    // ---- Entry point ----

    fun waitForProvisioning(attempts: Int) {
        if (Utils.isDeviceOwner(activity) || attempts <= 0) {
            setDefaultLauncherEarly()
        } else {
            handler.postDelayed({ waitForProvisioning(attempts - 1) }, 1000)
        }
    }

    fun setDefaultLauncherEarly() {
        val config = SettingsHelper.getInstance(activity).getConfig()
        if (com.base.launcher.BuildConfig.SET_DEFAULT_LAUNCHER_EARLY && config == null && Utils.isDeviceOwner(activity)) {
            val defaultLauncher = Utils.getDefaultLauncher(activity)
            backgroundExecutor.execute {
                if (!activity.packageName.equals(defaultLauncher, ignoreCase = true)) {
                    Utils.setDefaultLauncher(activity)
                }
                handler.post { checkAndStartLauncher() }
            }
            return
        }
        checkAndStartLauncher()
    }

    fun setSelfAsDeviceOwner() {
        if (Utils.isDeviceOwner(activity)) {
            checkAndStartLauncher()
            return
        }
        backgroundExecutor.execute {
            if (!SystemUtils.becomeDeviceOwnerByCommand(activity)) {
                SystemUtils.becomeDeviceOwnerByXmlFile(activity)
            }
            handler.post { setDefaultLauncherEarly() }
        }
    }

    fun checkAndStartLauncher() {
        val deviceOwner = Utils.isDeviceOwner(activity)
        preferences.edit().putInt(
            Const.PREFERENCES_DEVICE_OWNER,
            if (deviceOwner) Const.PREFERENCES_ON else Const.PREFERENCES_OFF
        ).commit()

        val miuiPermissionMode = preferences.getInt(Const.PREFERENCES_MIUI_PERMISSIONS, -1)
        if (miuiPermissionMode == -1) {
            preferences.edit().putInt(Const.PREFERENCES_MIUI_PERMISSIONS, Const.PREFERENCES_ON).commit()
            if (checkMiuiPermissions(Const.MIUI_PERMISSIONS)) return
        }

        val miuiDeveloperMode = preferences.getInt(Const.PREFERENCES_MIUI_DEVELOPER, -1)
        if (miuiDeveloperMode == -1) {
            preferences.edit().putInt(Const.PREFERENCES_MIUI_DEVELOPER, Const.PREFERENCES_ON).commit()
            if (checkMiuiPermissions(Const.MIUI_DEVELOPER)) return
        }

        val miuiOptimizationMode = preferences.getInt(Const.PREFERENCES_MIUI_OPTIMIZATION, -1)
        if (miuiOptimizationMode == -1) {
            preferences.edit().putInt(Const.PREFERENCES_MIUI_OPTIMIZATION, Const.PREFERENCES_ON).commit()
            if (checkMiuiPermissions(Const.MIUI_OPTIMIZATION)) return
        }

        val unknownSourceMode = preferences.getInt(Const.PREFERENCES_UNKNOWN_SOURCES, -1)
        if (!deviceOwner && unknownSourceMode == -1) {
            if (checkUnknownSources()) {
                preferences.edit().putInt(Const.PREFERENCES_UNKNOWN_SOURCES, Const.PREFERENCES_ON).commit()
            } else {
                return
            }
        }

        val administratorMode = preferences.getInt(Const.PREFERENCES_ADMINISTRATOR, -1)
        if (administratorMode == -1) {
            if (checkAdminMode()) {
                RemoteLogger.log(activity, Const.LOG_DEBUG, "Saving device admin state as 1 (TRUE)")
                preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ON).commit()
            } else {
                return
            }
        }

        val overlayMode = preferences.getInt(Const.PREFERENCES_OVERLAY, -1)
        if (ProUtils.isPro() && overlayMode == -1 && needRequestOverlay()) {
            if (checkAlarmWindow()) {
                preferences.edit().putInt(Const.PREFERENCES_OVERLAY, Const.PREFERENCES_ON).commit()
            } else {
                return
            }
        }

        val usageStatisticsMode = preferences.getInt(Const.PREFERENCES_USAGE_STATISTICS, -1)
        if (ProUtils.isPro() && usageStatisticsMode == -1 && needRequestUsageStats()) {
            if (checkUsageStatistics()) {
                preferences.edit().putInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_ON).commit()
                preferences.edit().putInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF).commit()
            } else {
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manageStorageMode = preferences.getInt(Const.PREFERENCES_MANAGE_STORAGE, -1)
            if (manageStorageMode == -1) {
                if (checkManageStorage()) {
                    preferences.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_ON).commit()
                } else {
                    return
                }
            }
        }

        val accessibilityService = preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, -1)
        if (ProUtils.isPro() && com.base.launcher.BuildConfig.USE_ACCESSIBILITY &&
            accessibilityService == -1 && needRequestUsageStats()) {
            if (checkAccessibilityService()) {
                preferences.edit().putInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_ON).commit()
            } else {
                createAndShowAccessibilityServiceDialog()
                return
            }
        }

        onStartLauncher()
    }

    // ---- Check methods ----

    private fun needRequestUsageStats(): Boolean {
        val config = SettingsHelper.getInstance(activity).getConfig()
        return config == null || !config.isPermissive
    }

    private fun needRequestOverlay(): Boolean {
        val config = SettingsHelper.getInstance(activity).getConfig()
        return config == null || !config.isPermissive
    }

    private fun checkAdminMode(): Boolean {
        if (!Utils.checkAdminMode(activity)) {
            createAndShowAdministratorDialog()
            return false
        }
        return true
    }

    private fun checkUsageStatistics(): Boolean {
        if (!ProUtils.checkUsageStatistics(activity)) {
            if (SystemUtils.autoSetUsageStatsPermission(activity, activity.packageName)) {
                if (ProUtils.checkUsageStatistics(activity)) return true
            }
            createAndShowHistorySettingsDialog()
            return false
        }
        return true
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private fun checkManageStorage(): Boolean {
        if (!Environment.isExternalStorageManager()) {
            if (SystemUtils.autoSetStoragePermission(activity, activity.packageName)) {
                if (Environment.isExternalStorageManager()) return true
            }
            createAndShowManageStorageDialog()
            return false
        }
        return true
    }

    private fun checkAlarmWindow(): Boolean {
        if (ProUtils.isPro() && !Utils.canDrawOverlays(activity)) {
            if (SystemUtils.autoSetOverlayPermission(activity, activity.packageName)) {
                if (Utils.canDrawOverlays(activity)) return true
            }
            createAndShowOverlaySettingsDialog()
            return false
        }
        return true
    }

    private fun checkMiuiPermissions(screen: Int): Boolean {
        if (Utils.isMiui(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocalBroadcastManager.getInstance(activity).sendBroadcast(Intent(Const.ACTION_ENABLE_SETTINGS))
            createAndShowMiuiPermissionsDialog(screen)
            return true
        }
        return false
    }

    private fun checkUnknownSources(): Boolean {
        if (!Utils.canInstallPackages(activity)) {
            createAndShowUnknownSourcesDialog()
            return false
        }
        return true
    }

    fun checkAccessibilityService(): Boolean = ProUtils.checkAccessibilityService(activity)

    // ---- Runtime permissions ----

    fun checkPermissions(startSettings: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (isPermissionsDialogShowing) return false

        if (Utils.isDeviceOwner(activity)) {
            val config = settingsHelper.getConfig()
            if (config == null ||
                (!com.base.launcher.json.ServerConfig.APP_PERMISSIONS_ASK_ALL.equals(config.appPermissions) &&
                !com.base.launcher.json.ServerConfig.APP_PERMISSIONS_ASK_LOCATION.equals(config.appPermissions))) {
                return true
            }
        }

        return if (preferences.getInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            checkStorageAndPhonePermissions(startSettings)
        } else {
            checkLocationPermissions(startSettings)
        }
    }

    private fun checkStorageAndPhonePermissions(startSettings: Boolean): Boolean {
        val needStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
             activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        val needPhone = activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
        if (needStorage || needPhone) {
            if (startSettings) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    activity.requestPermissions(arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE
                    ), PERMISSIONS_REQUEST)
                } else {
                    activity.requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), PERMISSIONS_REQUEST)
                }
            }
            return false
        }
        return true
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun checkLocationPermissions(startSettings: Boolean): Boolean {
        val needStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
             activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        val needFineLocation = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        val needBgLocation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            activity.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        val needPhone = activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED

        if (needStorage || needFineLocation || needBgLocation || needPhone) {
            if (startSettings) {
                var activeModeLocation = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        activeModeLocation =
                            activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                            activity.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (activeModeLocation) {
                    try {
                        LocalBroadcastManager.getInstance(activity).sendBroadcast(Intent(Const.ACTION_ENABLE_SETTINGS))
                        AlertDialog.Builder(activity)
                            .setMessage(activity.getString(R.string.background_location, activity.getString(R.string.white_app_name)))
                            .setPositiveButton(R.string.background_location_continue) { _, _ ->
                                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", activity.packageName, null)))
                            }
                            .setNegativeButton(R.string.location_disable) { _, _ ->
                                preferences.edit().putInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_ON).commit()
                                onStartLauncher()
                            }
                            .create()
                            .show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        activity.requestPermissions(arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            Manifest.permission.READ_PHONE_STATE
                        ), PERMISSIONS_REQUEST)
                    } else {
                        activity.requestPermissions(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE
                        ), PERMISSIONS_REQUEST)
                    }
                }
            }
            return false
        }
        return true
    }

    fun handlePermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (Utils.isDeviceOwner(activity)) {
                val config = settingsHelper.getConfig()
                if (config == null ||
                    (!com.base.launcher.json.ServerConfig.APP_PERMISSIONS_ASK_ALL.equals(config.appPermissions) &&
                    !com.base.launcher.json.ServerConfig.APP_PERMISSIONS_ASK_LOCATION.equals(config.appPermissions))) {
                    return
                }
            }

            var locationDisabled = false
            for (n in permissions.indices) {
                if (permissions[n] == Manifest.permission.ACCESS_FINE_LOCATION &&
                    grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                    preferences.edit().putInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_ON).commit()
                    locationDisabled = true
                }
            }

            var requestPermissions = false
            for (n in permissions.indices) {
                if (grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                    if (permissions[n] == Manifest.permission.ACCESS_BACKGROUND_LOCATION &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || locationDisabled)) continue
                    if (permissions[n] == Manifest.permission.ACCESS_FINE_LOCATION && locationDisabled) continue
                    requestPermissions = true
                }
            }
            if (requestPermissions) createAndShowPermissionsDialog()
        }
        if (requestCode == REQ_BT_PERMS) {
            val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                IBeaconAdvertiser.start(activity)
            } else {
                Log.e(TAG, "Bluetooth permissions denied, cannot advertise")
            }
        }
    }

    // ---- Bluetooth / Beacon ----

    fun startBeaconWithPermissionCheck() {
        if (!hasBlePermissions()) {
            requestBlePermissions()
        } else {
            IBeaconAdvertiser.start(activity)
        }
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(activity, arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ), REQ_BT_PERMS)
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_BT_PERMS)
        }
    }

    // ---- Dialog creators ----

    private fun createAndShowPermissionsDialog() {
        dismissDialog(permissionsDialog)
        permissionsDialog = Dialog(activity)
        dialogPermissionsBinding = DataBindingUtil.inflate(
            LayoutInflater.from(activity), R.layout.dialog_permissions, null, false
        )
        permissionsDialog!!.setCancelable(false)
        permissionsDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        permissionsDialog!!.setContentView(dialogPermissionsBinding!!.root)
        permissionsDialog!!.show()
    }

    private fun createAndShowAccessibilityServiceDialog() {
        dismissDialog(accessibilityServiceDialog)
        accessibilityServiceDialog = Dialog(activity)
        dialogAccessibilityServiceBinding = DataBindingUtil.inflate(
            LayoutInflater.from(activity), R.layout.dialog_accessibility_service, null, false
        )
        dialogAccessibilityServiceBinding!!.hint.text =
            activity.getString(R.string.dialog_accessibility_service_message, activity.getString(R.string.white_app_name))
        accessibilityServiceDialog!!.setCancelable(false)
        accessibilityServiceDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        accessibilityServiceDialog!!.setContentView(dialogAccessibilityServiceBinding!!.root)
        accessibilityServiceDialog!!.show()
    }

    private fun createAndShowAdministratorDialog() {
        dismissDialog(administratorModeDialog)
        administratorModeDialog = Dialog(activity)
        dialogAdministratorModeBinding = DataBindingUtil.inflate(
            LayoutInflater.from(activity), R.layout.dialog_administrator_mode, null, false
        )
        dialogAdministratorModeBinding!!.hint.text =
            activity.getString(R.string.dialog_administrator_mode_message, activity.getString(R.string.white_app_name))
        administratorModeDialog!!.setCancelable(false)
        administratorModeDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        administratorModeDialog!!.setContentView(dialogAdministratorModeBinding!!.root)
        administratorModeDialog!!.show()
    }

    private fun createAndShowHistorySettingsDialog() {
        dismissDialog(historySettingsDialog)
        historySettingsDialog = Dialog(activity)
        dialogHistorySettingsBinding = DataBindingUtil.inflate(
            LayoutInflater.from(activity), R.layout.dialog_history_settings, null, false
        )
        dialogHistorySettingsBinding!!.hint.text =
            activity.getString(R.string.dialog_history_settings_title, activity.getString(R.string.white_app_name))
        historySettingsDialog!!.setCancelable(false)
        historySettingsDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        historySettingsDialog!!.setContentView(dialogHistorySettingsBinding!!.root)
        historySettingsDialog!!.show()
    }

    private fun createAndShowManageStorageDialog() {
        dismissDialog(manageStorageDialog)
        manageStorageDialog = Dialog(activity)
        dialogManageStorageBinding = DataBindingUtil.inflate(
            LayoutInflater.from(activity), R.layout.dialog_manage_storage, null, false
        )
        manageStorageDialog!!.setCancelable(false)
        manageStorageDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        manageStorageDialog!!.setContentView(dialogManageStorageBinding!!.root)
        manageStorageDialog!!.show()
    }

    private fun createAndShowOverlaySettingsDialog() {
        dismissDialog(overlaySettingsDialog)
        overlaySettingsDialog = Dialog(activity)
        dialogOverlaySettingsBinding = DataBindingUtil.inflate(
            LayoutInflater.from(activity), R.layout.dialog_overlay_settings, null, false
        )
        dialogOverlaySettingsBinding!!.hint.text =
            activity.getString(R.string.dialog_overlay_settings_title, activity.getString(R.string.white_app_name))
        overlaySettingsDialog!!.setCancelable(false)
        overlaySettingsDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        overlaySettingsDialog!!.setContentView(dialogOverlaySettingsBinding!!.root)
        overlaySettingsDialog!!.show()
    }

    private fun createAndShowUnknownSourcesDialog() {
        dismissDialog(unknownSourcesDialog)
        unknownSourcesDialog = Dialog(activity)
        dialogUnknownSourcesBinding = DataBindingUtil.inflate(
            LayoutInflater.from(activity), R.layout.dialog_unknown_sources, null, false
        )
        unknownSourcesDialog!!.setCancelable(false)
        unknownSourcesDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        unknownSourcesDialog!!.setContentView(dialogUnknownSourcesBinding!!.root)
        unknownSourcesDialog!!.show()
    }

    private fun createAndShowMiuiPermissionsDialog(screen: Int) {
        dismissDialog(miuiPermissionsDialog)
        miuiPermissionsDialog = Dialog(activity)
        dialogMiuiPermissionsBinding = DataBindingUtil.inflate(
            LayoutInflater.from(activity), R.layout.dialog_miui_permissions, null, false
        )
        miuiPermissionsDialog!!.setCancelable(false)
        miuiPermissionsDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        when (screen) {
            Const.MIUI_PERMISSIONS -> dialogMiuiPermissionsBinding!!.title.setText(R.string.dialog_miui_permissions_title)
            Const.MIUI_DEVELOPER -> dialogMiuiPermissionsBinding!!.title.setText(R.string.dialog_miui_developer_title)
            Const.MIUI_OPTIMIZATION -> dialogMiuiPermissionsBinding!!.title.setText(R.string.dialog_miui_optimization_title)
        }
        miuiPermissionsDialog!!.setContentView(dialogMiuiPermissionsBinding!!.root)
        miuiPermissionsDialog!!.show()
    }

    // ---- Dialog button handlers (called from Activity public stubs) ----

    fun permissionsRetry() {
        dismissDialog(permissionsDialog)
        onStartLauncher()
    }

    fun permissionsExit() {
        dismissDialog(permissionsDialog)
        activity.finish()
    }

    fun skipAccessibilityService() {
        try { accessibilityServiceDialog!!.dismiss() } catch (e: Exception) { e.printStackTrace() }
        accessibilityServiceDialog = null
        preferences.edit().putInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    fun setAccessibilityService() {
        try { accessibilityServiceDialog!!.dismiss() } catch (e: Exception) { e.printStackTrace() }
        accessibilityServiceDialog = null
        activity.startActivityForResult(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 0)
    }

    fun skipAdminMode() {
        dismissDialog(administratorModeDialog)
        RemoteLogger.log(activity, Const.LOG_INFO, "Manually skipped the device admin permissions setup")
        preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    fun setAdminMode() {
        dismissDialog(administratorModeDialog)
        activity.startActivity(Intent(activity, AdminModeRequestActivity::class.java))
    }

    fun historyWithoutPermission() {
        dismissDialog(historySettingsDialog)
        preferences.edit().putInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    fun continueHistory() {
        dismissDialog(historySettingsDialog)
        activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun storageWithoutPermission() {
        dismissDialog(manageStorageDialog)
        preferences.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    fun continueStorage() {
        dismissDialog(manageStorageDialog)
        try {
            val intent = Intent()
            intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            val uri = android.net.Uri.fromParts("package", activity.packageName, null)
            intent.data = uri
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                activity.startActivity(intent)
            } catch (e1: Exception) {
                Toast.makeText(activity, R.string.manage_storage_not_supported, Toast.LENGTH_LONG).show()
                preferences.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_OFF).commit()
                checkAndStartLauncher()
            }
        }
    }

    fun overlayWithoutPermission() {
        dismissDialog(overlaySettingsDialog)
        preferences.edit().putInt(Const.PREFERENCES_OVERLAY, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    fun continueOverlay(view: View) {
        dismissDialog(overlaySettingsDialog)
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${activity.packageName}")
        )
        try {
            activity.startActivityForResult(intent, 1001)
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.overlays_not_supported, Toast.LENGTH_LONG).show()
            overlayWithoutPermission()
        }
    }

    fun continueUnknownSources() {
        dismissDialog(unknownSourcesDialog)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } else {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${activity.packageName}")))
        }
    }

    fun continueMiuiPermissions() {
        val titleText = dialogMiuiPermissionsBinding!!.title.text.toString()
        dismissDialog(miuiPermissionsDialog)
        LocalBroadcastManager.getInstance(activity).sendBroadcast(Intent(Const.ACTION_ENABLE_SETTINGS))
        val intent: Intent = when (titleText) {
            activity.getString(R.string.dialog_miui_permissions_title) -> {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                i.data = android.net.Uri.fromParts("package", activity.packageName, null)
                i
            }
            activity.getString(R.string.dialog_miui_developer_title) -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
