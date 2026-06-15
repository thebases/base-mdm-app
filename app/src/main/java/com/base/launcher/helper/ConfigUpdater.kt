package com.base.launcher.helper

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.db.DatabaseHelper
import com.base.launcher.db.DownloadTable
import com.base.launcher.db.RemoteFileTable
import com.base.launcher.json.Action
import com.base.launcher.json.Application
import com.base.launcher.json.DeviceInfo
import com.base.launcher.json.Download
import com.base.launcher.json.PushMessage
import com.base.launcher.json.RemoteFile
import com.base.launcher.json.ServerConfig
import com.base.launcher.pro.worker.DetailedInfoWorker
import com.base.launcher.server.ServerServiceKeeper
import com.base.launcher.service.PushLongPollingService
import com.base.launcher.task.ConfirmDeviceResetTask
import com.base.launcher.task.ConfirmPasswordResetTask
import com.base.launcher.task.ConfirmRebootTask
import com.base.launcher.task.GetRemoteLogConfigTask
import com.base.launcher.task.GetServerConfigTask
import com.base.launcher.util.DeviceInfoProvider
import com.base.launcher.util.InstallUtils
import com.base.launcher.util.PushNotificationMqttWrapper
import com.base.launcher.util.RemoteLogger
import com.base.launcher.util.SystemUtils
import com.base.launcher.util.Utils
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.concurrent.Executors

class ConfigUpdater {

    interface UINotifier {
        fun onConfigUpdateStart()
        fun onConfigUpdateServerError(errorText: String)
        fun onConfigUpdateNetworkError(errorText: String)
        fun onConfigLoaded()
        fun onPoliciesUpdated()
        fun onFileDownloading(remoteFile: RemoteFile)
        fun onDownloadProgress(progress: Int, total: Long, current: Long)
        fun onFileDownloadError(remoteFile: RemoteFile)
        fun onFileInstallError(remoteFile: RemoteFile)
        fun onAppUpdateStart()
        fun onAppRemoving(application: Application)
        fun onAppDownloading(application: Application)
        fun onAppInstalling(application: Application)
        fun onAppDownloadError(application: Application)
        fun onAppInstallError(packageName: String)
        fun onAppInstallComplete(packageName: String)
        fun onConfigUpdateComplete()
        fun onAllAppInstallComplete()
    }

    companion object {
        @JvmStatic
        fun notifyConfigUpdate(context: Context) {
            if (SettingsHelper.getInstance(context).isMainActivityRunning()) {
                Log.d(Const.LOG_TAG, "Main activity is running, using activity updater")
                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(Intent(Const.ACTION_UPDATE_CONFIGURATION))
            } else {
                Log.d(Const.LOG_TAG, "Main activity is not running, creating a new ConfigUpdater")
                ConfigUpdater().updateConfig(context, null, false)
            }
        }

        @JvmStatic
        fun forceConfigUpdate(context: Context) {
            forceConfigUpdate(context, null, false)
        }

        @JvmStatic
        fun forceConfigUpdate(context: Context, notifier: UINotifier?, userInteraction: Boolean) {
            ConfigUpdater().updateConfig(context, notifier, userInteraction)
        }

        @JvmStatic
        fun checkUpdateNetworkRestriction(config: ServerConfig?, context: Context): Boolean {
            if (config == null || "wifi" != config.downloadUpdates) {
                return true
            }
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
            return activeNetwork != null && activeNetwork.type != ConnectivityManager.TYPE_MOBILE
        }

        @JvmStatic
        fun checkAppUpdateTimeRestriction(config: ServerConfig?): Boolean {
            if (config == null) return true
            val appUpdateFrom = config.appUpdateFrom ?: return true
            val appUpdateTo = config.appUpdateTo ?: return true

            val calendar: Calendar = GregorianCalendar.getInstance()
            calendar.time = Date()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            var minute = calendar.get(Calendar.MINUTE)

            var appUpdateFromHour = 0
            try { appUpdateFromHour = appUpdateFrom.substring(0, 2).toInt() } catch (e: Exception) { e.printStackTrace() }
            var appUpdateFromMinute = 0
            try { appUpdateFromMinute = appUpdateFrom.substring(3).toInt() } catch (e: Exception) { e.printStackTrace() }

            var appUpdateToHour = 0
            try { appUpdateToHour = appUpdateTo.substring(0, 2).toInt() } catch (e: Exception) { e.printStackTrace() }
            var appUpdateToMinute = 0
            try { appUpdateToMinute = appUpdateTo.substring(3).toInt() } catch (e: Exception) { e.printStackTrace() }

            minute += 60 * hour
            appUpdateFromMinute += 60 * appUpdateFromHour
            appUpdateToMinute += 60 * appUpdateToHour

            if (appUpdateFromMinute == appUpdateToMinute) {
                // Incorrect config — treat as "24 hours"
                return true
            }

            return if (appUpdateFromMinute < appUpdateToMinute) {
                // Midnight not included
                appUpdateFromMinute <= minute && minute <= appUpdateToMinute
            } else {
                // Midnight included
                minute >= appUpdateFromMinute || minute <= appUpdateToMinute
            }
        }
    }

    // ---- Inner data classes ----

    class RemoteFileStatus {
        var remoteFile: RemoteFile? = null
        var downloaded: Boolean = false
        var installed: Boolean = false
    }

    private inner class ApplicationStatus {
        var application: Application? = null
        var installed: Boolean = false
    }

    // ---- Instance fields ----

    private var configInitializing: Boolean = false
    private lateinit var context: Context
    private var uiNotifier: UINotifier? = null
    private lateinit var settingsHelper: SettingsHelper
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val filesForInstall: MutableList<RemoteFile> = mutableListOf()
    private val applicationsForInstall: MutableList<Application> = mutableListOf()
    private val applicationsForRun: MutableList<Application> = mutableListOf()
    private val pendingInstallations: MutableMap<String, File> = mutableMapOf()
    private var appInstallReceiver: BroadcastReceiver? = null
    private var retry: Boolean = true
    private var loadOnly: Boolean = false
    private var userInteraction: Boolean = false

    fun getApplicationsForRun(): List<Application> = applicationsForRun

    fun setLoadOnly(loadOnly: Boolean) {
        this.loadOnly = loadOnly
    }

    // ---- Main update flow ----

    fun updateConfig(context: Context, uiNotifier: UINotifier?, userInteraction: Boolean) {
        if (configInitializing) {
            Log.i(Const.LOG_TAG, "updateConfig(): configInitializing=true, exiting")
            return
        }

        Log.i(Const.LOG_TAG, "updateConfig(): set configInitializing=true")
        configInitializing = true
        DetailedInfoWorker.requestConfigUpdate(context)
        this.context = context
        this.uiNotifier = uiNotifier
        this.userInteraction = userInteraction

        // Work around a strange bug with stale SettingsHelper instance: re-read its value
        settingsHelper = SettingsHelper.getInstance(context.applicationContext)

        val cfg = settingsHelper.config
        if (cfg != null) {
            cfg.restrictions?.let { Utils.releaseUserRestrictions(context, it) }
            // Explicitly release restrictions of installing/uninstalling apps
            Utils.releaseUserRestrictions(context, "no_install_apps,no_uninstall_apps")
        }

        uiNotifier?.onConfigUpdateStart()

        val serverConfigTask = GetServerConfigTask(context)
        serverConfigTask.execute { result ->
            configInitializing = false
            Log.i(Const.LOG_TAG, "updateConfig(): set configInitializing=false after getting config")

            when (result) {
                Const.TASK_SUCCESS -> {
                    RemoteLogger.log(context, Const.LOG_INFO, "Configuration updated")
                    updateRemoteLogConfig()
                }
                Const.TASK_ERROR -> {
                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to update config: server error")
                    uiNotifier?.onConfigUpdateServerError(serverConfigTask.errorText ?: "")
                }
                Const.TASK_NETWORK_ERROR -> {
                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to update config: network error")
                    if (retry) {
                        // Retry the request once because WiFi may not yet be initialized
                        retry = false
                        handler.postDelayed({ updateConfig(context, uiNotifier, userInteraction) }, 15000)
                    } else {
                        val config = settingsHelper.config
                        if (config != null && !userInteraction) {
                            if (uiNotifier != null && config.isShowWifi) {
                                // Show network error dialog with Wi-Fi settings
                                // if it is required by the web panel
                                // so the user can set up WiFi even in kiosk mode
                                uiNotifier.onConfigUpdateNetworkError(serverConfigTask.errorText ?: "")
                            } else {
                                updateRemoteLogConfig()
                            }
                        } else {
                            uiNotifier?.onConfigUpdateNetworkError(serverConfigTask.errorText ?: "")
                        }
                    }
                }
            }
        }
    }

    fun skipConfigLoad() {
        updateRemoteLogConfig()
    }

    private fun updateRemoteLogConfig() {
        Log.i(Const.LOG_TAG, "updateRemoteLogConfig(): get logging configuration")

        GetRemoteLogConfigTask(context).execute { result ->
            Log.i(Const.LOG_TAG, "updateRemoteLogConfig(): result=$result")
            val deviceOwner = Utils.isDeviceOwner(context)
            RemoteLogger.log(context, Const.LOG_INFO, "Device owner: $deviceOwner")
            if (deviceOwner) {
                setSelfPermissions(settingsHelper.config?.appPermissions)
            }
            try {
                if (settingsHelper.config != null && uiNotifier != null) {
                    uiNotifier!!.onConfigLoaded()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (!loadOnly) {
                checkServerMigration()
            } else {
                Log.d(Const.LOG_TAG, "LoadOnly flag set, finishing the update flow")
            }
        }
    }

    private fun setSelfPermissions(appPermissionStrategy: String?) {
        Utils.autoGrantRequestedPermissions(context, context.packageName, appPermissionStrategy, true)
    }

    private fun checkServerMigration() {
        val newServerUrl = settingsHelper.config?.newServerUrl?.trim()
        if (!newServerUrl.isNullOrEmpty()) {
            try {
                val migrationHelper = MigrationHelper(newServerUrl)
                if (migrationHelper.needMigrating(context)) {
                    // Before migration, test that new URL is working well
                    migrationHelper.tryNewServer(context, object : MigrationHelper.CompletionHandler {
                        override fun onSuccess() {
                            // Everything is OK, migrate!
                            RemoteLogger.log(context, Const.LOG_INFO, "Migrated to $newServerUrl")
                            settingsHelper.setBaseUrl(migrationHelper.baseUrl)
                            settingsHelper.setSecondaryBaseUrl(migrationHelper.baseUrl)
                            settingsHelper.setServerProject(migrationHelper.serverProject)
                            ServerServiceKeeper.resetServices()
                            configInitializing = false
                            updateConfig(context, uiNotifier, false)
                        }

                        override fun onError(cause: String) {
                            RemoteLogger.log(context, Const.LOG_WARN, "Failed to migrate to $newServerUrl: $cause")
                            setupPushService()
                        }
                    })
                    return
                }
            } catch (e: Exception) {
                // Malformed URL
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to migrate to $newServerUrl: malformed URL")
            }
        }
        setupPushService()
    }

    fun setupPushService() {
        Log.d(Const.LOG_TAG, "setupPushService() called")
        var pushOptions: String? = null
        var keepaliveTime = Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC
        val cfg = settingsHelper.config
        if (cfg != null) {
            pushOptions = cfg.pushOptions
            val newKeepaliveTime: Int? = cfg.keepaliveTime
            if (newKeepaliveTime != null && newKeepaliveTime >= 30) {
                keepaliveTime = newKeepaliveTime
            }
        }
        if (BuildConfig.ENABLE_PUSH && pushOptions != null) {
            if (pushOptions == ServerConfig.PUSH_OPTIONS_MQTT_WORKER
                || pushOptions == ServerConfig.PUSH_OPTIONS_MQTT_ALARM
            ) {
                try {
                    val nextRunnable = Runnable { checkFactoryReset() }
                    PushNotificationMqttWrapper.getInstance().connect(
                        context,
                        settingsHelper.mqttDomain,
                        settingsHelper.mqttPort,
                        settingsHelper.mqttTls,
                        settingsHelper.mqttUsername,
                        settingsHelper.mqttPassword,
                        pushOptions,
                        keepaliveTime,
                        settingsHelper.deviceId,
                        nextRunnable,
                        nextRunnable
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    checkFactoryReset()
                }
            } else {
                try {
                    val serviceStartIntent = Intent(context, PushLongPollingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceStartIntent)
                    } else {
                        context.startService(serviceStartIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                checkFactoryReset()
            }
        } else {
            checkFactoryReset()
        }
    }

    private fun checkFactoryReset() {
        Log.d(Const.LOG_TAG, "checkFactoryReset() called")
        val config: ServerConfig? = settingsHelper.config
        if (config != null && config.factoryReset == true) {
            // We got a factory reset request, let's confirm and erase everything!
            RemoteLogger.log(context, Const.LOG_INFO, "Device reset by server request")
            val deviceInfo: DeviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true)
            deviceInfo.factoryReset = Utils.checkAdminMode(context)
            ConfirmDeviceResetTask(context).execute(deviceInfo) { result ->
                // Do a factory reset if we can
                if (result != Const.TASK_SUCCESS) {
                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to confirm device reset on server")
                } else if (Utils.checkAdminMode(context)) {
                    // no_factory_reset restriction doesn't prevent against admin's reset action
                    // So we do not need to release this restriction prior to resetting the device
                    if (!Utils.factoryReset(context)) {
                        RemoteLogger.log(context, Const.LOG_WARN, "Device reset failed")
                    }
                } else {
                    RemoteLogger.log(context, Const.LOG_WARN, "Device reset failed: no permissions")
                }
                // If we can't, proceed the initialization flow
                checkRemoteReboot()
            }
        } else {
            checkRemoteReboot()
        }
    }

    private fun checkRemoteReboot() {
        val config: ServerConfig? = settingsHelper.config
        if (config != null && config.reboot != null && config.reboot == true) {
            // Log and confirm reboot before rebooting
            RemoteLogger.log(context, Const.LOG_INFO, "Rebooting by server request")
            val deviceInfo: DeviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true)
            ConfirmRebootTask(context).execute(deviceInfo) { result ->
                if (result != Const.TASK_SUCCESS) {
                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to confirm reboot on server")
                } else if (Utils.checkAdminMode(context)) {
                    if (!Utils.reboot(context)) {
                        RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed")
                    }
                } else {
                    RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed: no permissions")
                }
                checkPasswordReset()
            }
        } else {
            checkPasswordReset()
        }
    }

    private fun checkPasswordReset() {
        val config: ServerConfig? = settingsHelper.config
        val passwordReset = config?.passwordReset
        if (config != null && passwordReset != null) {
            if (Utils.passwordReset(context, passwordReset)) {
                RemoteLogger.log(context, Const.LOG_INFO, "Password successfully changed")
            } else {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to reset password")
            }

            val deviceInfo: DeviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true)
            ConfirmPasswordResetTask(context).execute(deviceInfo) { _ -> setDefaultLauncher() }
        } else {
            setDefaultLauncher()
        }
    }

    private fun setDefaultLauncher() {
        val config: ServerConfig? = settingsHelper.config
        if (Utils.isDeviceOwner(context) && config != null) {
            // "Run default launcher" means we should not set Base MDM as a default launcher
            // and clear the setting if it has been already set
            val runDefaultLauncher = config.runDefaultLauncher
            val needSetLauncher = (runDefaultLauncher == null || !runDefaultLauncher)
            val defaultLauncher = Utils.getDefaultLauncher(context)

            // As per the documentation, setting the default preferred activity should not be done on the main thread
            backgroundExecutor.execute {
                if (needSetLauncher && !context.packageName.equals(defaultLauncher, ignoreCase = true)) {
                    Utils.setDefaultLauncher(context)
                } else if (!needSetLauncher && context.packageName.equals(defaultLauncher, ignoreCase = true)) {
                    Utils.clearDefaultLauncher(context)
                }
                handler.post { updatePolicies() }
            }
            return
        }
        updatePolicies()
    }

    private fun updatePolicies() {
        // Update miscellaneous device policies here

        // Set up a proxy server
        val settingsHelper = SettingsHelper.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Utils.isDeviceOwner(context)) {
            var proxyUrl = settingsHelper.getAppPreference(context.packageName, "proxy")
            if (proxyUrl != null) {
                proxyUrl = proxyUrl.trim()
                if (proxyUrl == "0") {
                    // null stays for "no changes" (most users won't even know about an option to set up a proxy)
                    // "0" stays for "clear the proxy previously set up"
                    proxyUrl = null
                }
                Utils.setProxy(context, proxyUrl)
            }
        }

        uiNotifier?.onPoliciesUpdated()
        Log.d(Const.LOG_TAG, "updatePolicies(): proceed to updating files")
        checkAndUpdateFiles()
    }

    private fun checkAndUpdateFiles() {
        backgroundExecutor.execute {
            val config = settingsHelper.config
            InstallUtils.generateFilesForInstallList(context, config.files, filesForInstall)
            handler.post { loadAndInstallFiles() }
        }
    }

    private fun loadAndInstallFiles() {
        val isGoodNetworkForUpdate = userInteraction || checkUpdateNetworkRestriction(settingsHelper.config, context)
        if (filesForInstall.size > 0 && !isGoodNetworkForUpdate) {
            RemoteLogger.log(context, Const.LOG_DEBUG, "Updating files not enabled: waiting for WiFi connection")
        }
        if (filesForInstall.size > 0 && isGoodNetworkForUpdate) {
            val remoteFile = filesForInstall.removeAt(0)

            backgroundExecutor.execute {
                var fileStatus: RemoteFileStatus? = null

                if (remoteFile.isRemove) {
                    val path = remoteFile.path ?: return@execute
                    RemoteLogger.log(context, Const.LOG_DEBUG, "Removing file: $path")
                    val file = InstallUtils.getFileByPath(path)
                    try {
                        if (file.exists()) {
                            file.delete()
                        }
                        RemoteFileTable.deleteByPath(
                            DatabaseHelper.instance(context).writableDatabase,
                            path
                        )
                    } catch (e: Exception) {
                        RemoteLogger.log(
                            context, Const.LOG_WARN,
                            "Failed to remove file: $path: ${e.message}"
                        )
                        e.printStackTrace()
                    }
                } else if (remoteFile.url != null) {
                    val url = remoteFile.url!!
                    val path = remoteFile.path ?: return@execute
                    uiNotifier?.let { handler.post { it.onFileDownloading(remoteFile) } }

                    fileStatus = RemoteFileStatus()
                    fileStatus.remoteFile = remoteFile

                    val dbHelper = DatabaseHelper.instance(context)
                    val lastDownload = DownloadTable.selectByPath(dbHelper.readableDatabase, path)
                    if (!canDownload(lastDownload, path)) {
                        val finalFileStatus = fileStatus
                        handler.post { handleFileStatus(finalFileStatus) }
                        return@execute
                    }

                    var file: File? = null
                    try {
                        RemoteLogger.log(context, Const.LOG_DEBUG, "Downloading file: $path")
                        file = InstallUtils.downloadFile(context, url) { progress, total, current ->
                            uiNotifier?.onDownloadProgress(progress, total, current)
                        }
                    } catch (e: Exception) {
                        RemoteLogger.log(
                            context, Const.LOG_WARN,
                            "Failed to download file $path: ${e.message}"
                        )
                        e.printStackTrace()
                        saveFailedAttempt(context, lastDownload, url, path, false, false)
                    }

                    if (file != null) {
                        fileStatus.downloaded = true
                        val finalFile = InstallUtils.getFileByPath(path)
                        try {
                            if (finalFile.exists()) {
                                finalFile.delete()
                            }
                            if (!remoteFile.isVarContent) {
                                FileUtils.moveFile(file, finalFile)
                            } else {
                                var imei = DeviceInfoProvider.getImei(context, 0)
                                val cfg = settingsHelper.config
                                if (imei == null || imei.isEmpty()) {
                                    imei = cfg?.imei
                                }
                                if (cfg != null) {
                                    createFileFromTemplate(
                                        file, finalFile,
                                        settingsHelper.deviceId, imei,
                                        cfg
                                    )
                                }
                            }
                            RemoteFileTable.insert(dbHelper.writableDatabase, remoteFile)
                            fileStatus.installed = true
                            if (lastDownload != null) {
                                DownloadTable.deleteByPath(dbHelper.writableDatabase, path)
                            }
                        } catch (e: Exception) {
                            RemoteLogger.log(
                                context, Const.LOG_WARN,
                                "Failed to create file $path: ${e.message}"
                            )
                            e.printStackTrace()
                            try {
                                if (file.exists()) {
                                    file.delete()
                                }
                            } catch (e1: Exception) {
                                e1.printStackTrace()
                            }
                            fileStatus.installed = false
                            saveFailedAttempt(context, lastDownload, url, path, true, false)
                        }
                    } else {
                        fileStatus.downloaded = false
                        fileStatus.installed = false
                    }
                }

                val finalFileStatus = fileStatus
                handler.post { handleFileStatus(finalFileStatus) }
            }
        } else {
            Log.i(Const.LOG_TAG, "loadAndInstallFiles(): Proceed to certificate installation")
            installCertificates()
        }
    }

    private fun handleFileStatus(fileStatus: RemoteFileStatus?) {
        if (fileStatus != null && !fileStatus.installed) {
            filesForInstall.add(0, fileStatus.remoteFile!!)
            if (uiNotifier != null) {
                if (!fileStatus.downloaded) {
                    uiNotifier!!.onFileDownloadError(fileStatus.remoteFile!!)
                } else {
                    uiNotifier!!.onFileInstallError(fileStatus.remoteFile!!)
                }
            }
            return
        }
        Log.i(Const.LOG_TAG, "loadAndInstallFiles(): proceed to next file")
        loadAndInstallFiles()
    }

    // Save failed attempt to download or install a file or an app in the database to avoid infinite loops
    private fun saveFailedAttempt(
        context: Context,
        lastDownload: Download?,
        url: String,
        path: String,
        downloaded: Boolean,
        installed: Boolean
    ) {
        val download = lastDownload ?: Download().also {
            it.url = url
            it.path = path
            it.attempts = 0
        }
        if (!downloaded) {
            download.attempts = download.attempts + 1
            download.lastAttemptTime = System.currentTimeMillis()
        }
        download.isDownloaded = downloaded
        download.isInstalled = installed
        val dbHelper = DatabaseHelper.instance(context)
        DownloadTable.insert(dbHelper.writableDatabase, download)
    }

    // In background mode, we do not attempt to download files or apps in two cases:
    // 1. Installation failed
    // 2. Downloading in a mobile network is limited
    private fun canDownload(lastDownload: Download?, objectId: String): Boolean {
        if (userInteraction || lastDownload == null) {
            return true
        }
        if (lastDownload.isDownloaded && !lastDownload.isInstalled) {
            RemoteLogger.log(context, Const.LOG_INFO, "Skip download due to previous install failure: $objectId")
            return false
        }
        val config = settingsHelper.config ?: return true
        if ("limited" == config.downloadUpdates) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
            if (activeNetwork == null) {
                RemoteLogger.log(context, Const.LOG_INFO, "Skip downloading $objectId: no active network")
                return false
            }
            Log.d(Const.LOG_TAG, "Active network; ${activeNetwork.typeName}, download attempts: ${lastDownload.attempts}")
            if (activeNetwork.type == ConnectivityManager.TYPE_MOBILE &&
                !lastDownload.isDownloaded && lastDownload.attempts > 3
            ) {
                RemoteLogger.log(context, Const.LOG_INFO, "Skip download due to previous download failures: $objectId")
                return false
            }
        }
        return true
    }

    private fun installCertificates() {
        val certPaths = settingsHelper.getAppPreference(context.packageName, "certificates")
        if (certPaths != null) {
            backgroundExecutor.execute {
                CertInstaller.installCertificatesFromFiles(context, certPaths.trim())
                handler.post { checkAndUpdateApplications() }
            }
        } else {
            checkAndUpdateApplications()
        }
    }

    private fun checkAndUpdateApplications() {
        Log.i(Const.LOG_TAG, "checkAndUpdateApplications(): starting update applications")
        uiNotifier?.onAppUpdateStart()
        configInitializing = false

        val config = settingsHelper.config
        InstallUtils.generateApplicationsForInstallList(context, config.applications, applicationsForInstall, pendingInstallations)

        Log.i(Const.LOG_TAG, "checkAndUpdateApplications(): list size=${applicationsForInstall.size}")

        registerAppInstallReceiver(config?.appPermissions)
        loadAndInstallApplications()
    }

    // Here we avoid ConcurrentModificationException by executing all operations with applicationForInstall list in a main thread
    private fun loadAndInstallApplications() {
        val config = settingsHelper.config
        val isGoodTimeForAppUpdate = userInteraction || checkAppUpdateTimeRestriction(config)
        if (applicationsForInstall.size > 0 && !isGoodTimeForAppUpdate) {
            RemoteLogger.log(context, Const.LOG_DEBUG, "Application update not enabled. Scheduled time: ${config?.appUpdateFrom}")
        }
        val isGoodNetworkForUpdate = userInteraction || checkUpdateNetworkRestriction(config ?: return, context)
        if (applicationsForInstall.size > 0 && !isGoodNetworkForUpdate) {
            RemoteLogger.log(context, Const.LOG_DEBUG, "Application update not enabled: waiting for WiFi connection")
        }
        if (applicationsForInstall.size > 0 && isGoodTimeForAppUpdate && isGoodNetworkForUpdate) {
            val application = applicationsForInstall.removeAt(0)

            backgroundExecutor.execute {
                var applicationStatus: ApplicationStatus? = null

                if (application.isRemove) {
                    val pkg = application.pkg ?: return@execute
                    RemoteLogger.log(context, Const.LOG_DEBUG, "Removing app: $pkg")
                    uiNotifier?.let { handler.post { it.onAppRemoving(application) } }
                    uninstallApplication(pkg)

                } else if (application.url == null) {
                    handler.post {
                        Log.i(Const.LOG_TAG, "loadAndInstallApplications(): proceed to next app")
                        loadAndInstallApplications()
                    }
                    return@execute

                } else if (application.url!!.startsWith("market://details")) {
                    val url = application.url!!
                    val pkg = application.pkg ?: return@execute
                    RemoteLogger.log(context, Const.LOG_INFO, "Installing app $pkg from Google Play")
                    installApplicationFromPlayMarket(url, pkg)
                    applicationStatus = ApplicationStatus()
                    applicationStatus.application = application
                    applicationStatus.installed = true

                } else if (application.url!!.startsWith("file:///")) {
                    val url = application.url!!
                    val pkg = application.pkg ?: return@execute
                    RemoteLogger.log(context, Const.LOG_INFO, "Installing app $pkg from SD card")
                    applicationStatus = ApplicationStatus()
                    applicationStatus.application = application
                    try {
                        Log.d(Const.LOG_TAG, "URL: $url")
                        val file = File(URL(url).toURI())
                        Log.d(Const.LOG_TAG, "Path: ${file.absolutePath}")
                        uiNotifier?.let { handler.post { it.onAppInstalling(application) } }
                        installApplication(file, pkg, application.version)
                        applicationStatus.installed = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        applicationStatus.installed = false
                    }

                } else {
                    val url = application.url!!
                    val pkg = application.pkg ?: return@execute
                    uiNotifier?.let { handler.post { it.onAppDownloading(application) } }

                    applicationStatus = ApplicationStatus()
                    applicationStatus.application = application

                    val dbHelper = DatabaseHelper.instance(context)
                    val tempPath = InstallUtils.getAppTempPath(context, url)
                    val lastDownload = DownloadTable.selectByPath(dbHelper.readableDatabase, tempPath)
                    if (!canDownload(lastDownload, pkg)) {
                        applicationStatus.installed = false
                        val finalApplicationStatus = applicationStatus
                        handler.post { handleApplicationStatus(finalApplicationStatus) }
                        return@execute
                    }

                    var file: File? = null
                    try {
                        RemoteLogger.log(context, Const.LOG_DEBUG, "Downloading app: $pkg")
                        file = InstallUtils.downloadFile(context, url) { progress, total, current ->
                            uiNotifier?.onDownloadProgress(progress, total, current)
                        }
                    } catch (e: Exception) {
                        RemoteLogger.log(context, Const.LOG_WARN, "Failed to download app $pkg: ${e.message}")
                        e.printStackTrace()
                        saveFailedAttempt(context, lastDownload, url, tempPath, false, false)
                    }

                    if (file != null) {
                        uiNotifier?.let { handler.post { it.onAppInstalling(application) } }
                        installApplication(file, pkg, application.version)
                        applicationStatus.installed = true
                        if (lastDownload != null) {
                            DownloadTable.deleteByPath(dbHelper.writableDatabase, lastDownload.path)
                        }
                    } else {
                        applicationStatus.installed = false
                    }
                }

                val finalApplicationStatus = applicationStatus
                handler.post { handleApplicationStatus(finalApplicationStatus) }
            }
        } else {
            // App install receiver is unregistered after all apps are installed or a timeout happens
            lockRestrictions()
        }
    }

    private fun handleApplicationStatus(applicationStatus: ApplicationStatus?) {
        if (applicationStatus != null) {
            if (applicationStatus.installed) {
                if (applicationStatus.application!!.isRunAfterInstall) {
                    applicationsForRun.add(applicationStatus.application!!)
                }
            } else {
                applicationsForInstall.add(0, applicationStatus.application!!)
                uiNotifier?.onAppDownloadError(applicationStatus.application!!)
            }
        }
    }

    private fun lockRestrictions() {
        val restrictions = settingsHelper.config?.restrictions
        if (restrictions != null) {
            Utils.lockUserRestrictions(context, restrictions)
        }
        notifyThreads()
    }

    private fun notifyThreads() {
        val config = settingsHelper.config
        if (config != null) {
            val intent = Intent(Const.ACTION_TOGGLE_PERMISSIVE)
            intent.putExtra(Const.EXTRA_ENABLED, config.isPermissive)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
        setActions()
    }

    private fun setActions() {
        val config = settingsHelper.config
        // As per the documentation, setting the default preferred activity should not be done on the main thread
        backgroundExecutor.execute {
            if (Utils.isDeviceOwner(context) && config?.actions != null && config.actions!!.isNotEmpty()) {
                for (action: Action in config.actions!!) {
                    Utils.setAction(context, action)
                }
            }
            handler.post {
                uiNotifier?.onConfigUpdateComplete()

                val intent = Intent(Const.INTENT_PUSH_NOTIFICATION_PREFIX + PushMessage.TYPE_CONFIG_UPDATED)
                context.sendBroadcast(intent)

                RemoteLogger.log(context, Const.LOG_VERBOSE, "Update flow completed")
                if (pendingInstallations.size > 0) {
                    waitForInstallComplete()
                } else {
                    unregisterAppInstallReceiver()
                }
            }
        }
    }

    private fun waitForInstallComplete() {
        backgroundExecutor.execute {
            for (n in 0 until 60) {
                if (pendingInstallations.size == 0) {
                    break
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            handler.post {
                unregisterAppInstallReceiver()
                uiNotifier?.onAllAppInstallComplete()
            }
        }
    }

    @SuppressLint("WrongConstant,UnspecifiedRegisterReceiverFlag")
    private fun registerAppInstallReceiver(appPermissionStrategy: String?) {
        // Here we handle the completion of the silent app installation in the device owner mode
        // These intents are not delivered to LocalBroadcastManager
        if (appInstallReceiver == null) {
            Log.d(Const.LOG_TAG, "Install completion receiver prepared")
            appInstallReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Const.ACTION_INSTALL_COMPLETE) {
                        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)
                        when (status) {
                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                RemoteLogger.log(context, Const.LOG_INFO, "Request user confirmation to install")
                                val confirmationIntent: Intent? = intent.getParcelableExtra(Intent.EXTRA_INTENT)

                                // Fix the Intent Redirection vulnerability
                                // https://support.google.com/faqs/answer/9267555
                                if (confirmationIntent != null) {
                                    val name: ComponentName? = confirmationIntent.resolveActivity(context.packageManager)
                                    val flags = confirmationIntent.flags
                                    if (name != null && name.packageName != context.packageName &&
                                        (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0 &&
                                        (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0
                                    ) {
                                        confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(confirmationIntent)
                                        } catch (e: Exception) {
                                            // ignore
                                        }
                                    } else {
                                        Log.e(Const.LOG_TAG, "Intent redirection detected, ignoring the fault intent!")
                                    }
                                }
                            }
                            PackageInstaller.STATUS_SUCCESS -> {
                                var packageName: String? = intent.getStringExtra(Const.PACKAGE_NAME)
                                if (packageName != null) {
                                    RemoteLogger.log(context, Const.LOG_DEBUG, "App $packageName installed successfully")
                                    Log.i(Const.LOG_TAG, "Install complete: $packageName")
                                    val file = pendingInstallations[packageName]
                                    if (file != null) {
                                        pendingInstallations.remove(packageName)
                                        InstallUtils.deleteTempApk(file)
                                    }
                                    if (BuildConfig.SYSTEM_PRIVILEGES || Utils.isDeviceOwner(context)) {
                                        // Always grant all dangerous rights to the app
                                        Utils.autoGrantRequestedPermissions(context, packageName, appPermissionStrategy, false)
                                        if (BuildConfig.SYSTEM_PRIVILEGES && packageName == Const.APUPPET_PACKAGE_NAME) {
                                            // Automatically grant required permissions to aPuppet if we can
                                            // Note: device owner can only grant permissions to self, not to other apps!
                                            try {
                                                SystemUtils.autoSetAccessibilityPermission(
                                                    context,
                                                    Const.APUPPET_PACKAGE_NAME,
                                                    Const.APUPPET_SERVICE_CLASS_NAME
                                                )
                                                SystemUtils.autoSetOverlayPermission(context, Const.APUPPET_PACKAGE_NAME)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                    uiNotifier?.onAppInstallComplete(packageName)
                                } else {
                                    RemoteLogger.log(context, Const.LOG_DEBUG, "App installed successfully")
                                }
                            }
                            else -> {
                                // Installation failure
                                val extraMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                val statusMessage = InstallUtils.getPackageInstallerStatusMessage(status)
                                val packageName = intent.getStringExtra(Const.PACKAGE_NAME)
                                var logRecord = "Install failed: $statusMessage"
                                if (packageName != null) {
                                    logRecord = "$packageName $logRecord"
                                }
                                if (!extraMessage.isNullOrEmpty()) {
                                    logRecord += ", extra: $extraMessage"
                                }
                                RemoteLogger.log(context, Const.LOG_ERROR, logRecord)
                                if (packageName != null) {
                                    val file = pendingInstallations[packageName]
                                    if (file != null) {
                                        pendingInstallations.remove(packageName)
                                        InstallUtils.deleteTempApk(file)
                                        // Save failed install attempt to prevent next downloads
                                        saveFailedAttempt(context, null, "", file.absolutePath, true, false)
                                    }
                                }
                            }
                        }
                        loadAndInstallApplications()
                    }
                }
            }
        } else {
            // Renewed the configuration multiple times?
            unregisterAppInstallReceiver()
        }

        try {
            Log.d(Const.LOG_TAG, "Install completion receiver registered")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.registerReceiver(appInstallReceiver, IntentFilter(Const.ACTION_INSTALL_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(appInstallReceiver, IntentFilter(Const.ACTION_INSTALL_COMPLETE))
            }
        } catch (e: Exception) {
            // On earlier Android versions (4, 5):
            // Fatal Exception: android.content.ReceiverCallNotAllowedException
            // BroadcastReceiver components are not allowed to register to receive intents
            e.printStackTrace()
        }
    }

    private fun unregisterAppInstallReceiver() {
        if (appInstallReceiver != null) {
            try {
                Log.d(Const.LOG_TAG, "Install completion receiver unregistered")
                context.unregisterReceiver(appInstallReceiver)
            } catch (e: Exception) {
                // Receiver not registered
                e.printStackTrace()
            }
            appInstallReceiver = null
        }
    }

    private fun installApplicationFromPlayMarket(uri: String, packageName: String) {
        RemoteLogger.log(context, Const.LOG_DEBUG, "Asking user to install app $packageName")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(uri)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            RemoteLogger.log(context, Const.LOG_DEBUG, "Failed to run app install activity for $packageName")
        }
    }

    // This function is called from a background thread
    private fun installApplication(file: File, packageName: String, version: String?) {
        if (packageName == context.packageName &&
            context.packageManager.getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID) != null
        ) {
            // Restart self in EMUI: there's no auto restart after update in EMUI, we must use a helper app
            startLauncherRestarter()
        }
        val versionData = if (version == null || version == "0") "" else " $version"
        if (Utils.isDeviceOwner(context) || BuildConfig.SYSTEM_PRIVILEGES) {
            pendingInstallations[packageName] = file
            RemoteLogger.log(context, Const.LOG_INFO, "Silently installing app $packageName$versionData")
            InstallUtils.silentInstallApplication(context, file, packageName, object : InstallUtils.InstallErrorHandler {
                override fun onInstallError(msg: String?) {
                    Log.i(Const.LOG_TAG, "installApplication(): error installing app $packageName")
                    pendingInstallations.remove(packageName)
                    if (file.exists()) {
                        file.delete()
                    }
                    uiNotifier?.onAppInstallError(packageName)
                    if (msg != null) {
                        RemoteLogger.log(context, Const.LOG_WARN, "Failed to install app $packageName: $msg")
                    }
                    // Save failed install attempt to prevent next downloads
                    saveFailedAttempt(context, null, "", file.absolutePath, true, false)
                }
            })
        } else {
            RemoteLogger.log(context, Const.LOG_INFO, "Asking user to install app $packageName$versionData")
            InstallUtils.requestInstallApplication(context, file, object : InstallUtils.InstallErrorHandler {
                override fun onInstallError(msg: String?) {
                    pendingInstallations.remove(packageName)
                    if (file.exists()) {
                        file.delete()
                    }
                    if (msg != null) {
                        RemoteLogger.log(context, Const.LOG_WARN, "Failed to install app $packageName: $msg")
                    }
                    // Save failed install attempt to prevent next downloads
                    saveFailedAttempt(context, null, "", file.absolutePath, true, false)
                    handler.post { loadAndInstallApplications() }
                }
            })
        }
    }

    private fun uninstallApplication(packageName: String?) {
        if (packageName == null) return
        if (Utils.isDeviceOwner(context) || BuildConfig.SYSTEM_PRIVILEGES) {
            RemoteLogger.log(context, Const.LOG_INFO, "Silently uninstall app $packageName")
            InstallUtils.silentUninstallApplication(context, packageName)
        } else {
            RemoteLogger.log(context, Const.LOG_INFO, "Asking user to uninstall app $packageName")
            InstallUtils.requestUninstallApplication(context, packageName)
        }
    }

    // The following algorithm of launcher restart works in EMUI:
    // Run EMUI_LAUNCHER_RESTARTER activity once and send the old version number to it.
    // The restarter application will check the launcher version each second, and restart it
    // when it is changed.
    private fun startLauncherRestarter() {
        // Sending an intent before updating, otherwise the launcher may be terminated at any time
        val intent = context.packageManager.getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID)
        if (intent == null) {
            Log.i("LauncherRestarter", "No restarter app, please add it in the config!")
            return
        }
        intent.putExtra(Const.LAUNCHER_RESTARTER_OLD_VERSION, BuildConfig.VERSION_NAME)
        context.startActivity(intent)
        Log.i("LauncherRestarter", "Calling launcher restarter from the launcher")
    }

    // Create a new file from the template file
    // (replace DEVICE_NUMBER, IMEI, CUSTOM* by their values)
    @Throws(IOException::class)
    private fun createFileFromTemplate(srcFile: File, dstFile: File, deviceId: String, imei: String?, config: ServerConfig) {
        // We are supposed to process only small text files
        // So here we are reading the whole file, replacing variables, and save the content
        // It is not optimal for large files - it would be better to replace in a stream (how?)
        var content = FileUtils.readFileToString(srcFile)
        content = content
            .replace("DEVICE_NUMBER", deviceId)
            .replace("IMEI", imei ?: "")
            .replace("CUSTOM1", config.custom1 ?: "")
            .replace("CUSTOM2", config.custom2 ?: "")
            .replace("CUSTOM3", config.custom3 ?: "")
        FileUtils.writeStringToFile(dstFile, content)
    }

    fun isPendingAppInstall(): Boolean = applicationsForInstall.size > 0

    fun repeatDownloadFiles() {
        loadAndInstallFiles()
    }

    fun repeatDownloadApps() {
        loadAndInstallApplications()
    }

    fun skipDownloadFiles() {
        Log.d(Const.LOG_TAG, "File download skipped, continue updating files")
        if (filesForInstall.size > 0) {
            val remoteFile = filesForInstall.removeAt(0)
            settingsHelper.removeRemoteFile(remoteFile)
        }
        loadAndInstallFiles()
    }

    fun skipDownloadApps() {
        Log.d(Const.LOG_TAG, "App download skipped, continue updating applications")
        if (applicationsForInstall.size > 0) {
            val application = applicationsForInstall.removeAt(0)
            // Mark this app not to download any more until the config is refreshed
            // But we should not remove the app from a list because it may be
            // already installed!
            settingsHelper.removeApplicationUrl(application)
        }
        loadAndInstallApplications()
    }
}
