package com.base.launcher.ui

import android.app.Dialog
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.Surface
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.R
import com.base.launcher.databinding.ActivityMainBinding
import com.base.launcher.databinding.DialogEnterPasswordBinding
import com.base.launcher.databinding.DialogSystemSettingsBinding
import com.base.launcher.helper.ConfigUpdater
import com.base.launcher.helper.CryptoHelper
import com.base.launcher.helper.Initializer
import com.base.launcher.helper.SettingsHelper
import com.base.launcher.json.Application
import com.base.launcher.json.DeviceInfo
import com.base.launcher.json.RemoteFile
import com.base.launcher.json.ServerConfig
import com.base.launcher.pro.ProUtils
import com.base.launcher.pro.service.CheckForegroundAppAccessibilityService
import com.base.launcher.pro.service.CheckForegroundApplicationService
import com.base.launcher.receiver.ScreenOffReceiver
import com.base.launcher.server.ServerServiceKeeper
import com.base.launcher.service.LocationService
import com.base.launcher.service.PluginApiService
import com.base.launcher.service.StatusControlService
import com.base.launcher.task.GetServerConfigTask
import com.base.launcher.task.SendDeviceInfoTask
import com.base.launcher.ui.custom.StatusBarUpdater
import com.base.launcher.util.AppInfo
import com.base.launcher.util.CrashLoopProtection
import com.base.launcher.util.DeviceInfoProvider
import com.base.launcher.util.InstallUtils
import com.base.launcher.util.PreferenceLogger
import com.base.launcher.util.RemoteLogger
import com.base.launcher.util.SystemUtils
import com.base.launcher.util.Utils
import com.base.launcher.worker.SendDeviceInfoWorker
import com.github.anrwatchdog.ANRWatchDog
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity :
    BaseActivity(),
    View.OnLongClickListener,
    BaseAppListAdapter.OnAppChooseListener,
    BaseAppListAdapter.SwitchAdapterListener,
    View.OnClickListener,
    ConfigUpdater.UINotifier {

    companion object {
        private const val TAG = "MainActivity"
        private const val BOOT_DURATION_SEC = 120
        private const val PAUSE_BETWEEN_AUTORUNS_SEC = 5
        private const val REQUEST_CODE_GPS_STATE_CHANGE = 1
    }

    private lateinit var binding: ActivityMainBinding
    private var settingsHelper: SettingsHelper? = null
    private var preferences: SharedPreferences? = null

    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val configUpdater = ConfigUpdater()
    private val statusBarUpdater = StatusBarUpdater()

    private var sendDeviceInfoScheduled = false
    private var downloadingFile = false
    private var configFault = false
    private var needSendDeviceInfoAfterReconfigure = false
    private var isBackground = false
    private var anrWatchDog: ANRWatchDog? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var firstStartAfterProvisioning = false

    // Status bar / right toolbar overlay views (held here for onDestroy cleanup)
    private var statusBarView: View? = null
    private var rightToolbarView: View? = null

    // Password dialog
    private var enterPasswordDialog: Dialog? = null
    private var dialogEnterPasswordBinding: DialogEnterPasswordBinding? = null

    // System settings dialog
    private var systemSettingsDialog: Dialog? = null
    private var dialogSystemSettingsBinding: DialogSystemSettingsBinding? = null

    // Delegates
    private lateinit var lockScreenManager: LockScreenManager
    private lateinit var permissionCoordinator: PermissionFlowCoordinator
    private lateinit var appInstallDelegate: AppInstallDelegate
    private lateinit var launcherUIManager: LauncherUIManager

    // ---- BroadcastReceivers ----

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Const.ACTION_UPDATE_CONFIGURATION -> {
                    RemoteLogger.log(context, Const.LOG_DEBUG, "Update configuration by MainActivity")
                    updateConfig(false)
                }
                Const.ACTION_HIDE_SCREEN -> {
                    val serverConfig = SettingsHelper.getInstance(this@MainActivity).getConfig()
                    if (serverConfig.getLock() != null && serverConfig.getLock()) {
                        lockScreenManager.showLockScreen()
                    } else if (lockScreenManager.applicationNotAllowed != null) {
                        val v = lockScreenManager.applicationNotAllowed!!
                        v.findViewById<android.widget.TextView>(R.id.package_id).text =
                            intent.getStringExtra(Const.PACKAGE_NAME)
                        v.visibility = View.VISIBLE
                        v.post {
                            v.findViewById<View>(R.id.layout_application_not_allowed_continue).requestFocus()
                        }
                        handler.postDelayed({ v.visibility = View.GONE }, 20000)
                    }
                }
                Const.ACTION_DISABLE_BLOCK_WINDOW -> lockScreenManager.applicationNotAllowed?.visibility = View.GONE
                Const.ACTION_EXIT -> finish()
                Const.ACTION_POLICY_VIOLATION -> {
                    if (isBackground) {
                        val restoreLauncherIntent = Intent(context, MainActivity::class.java)
                        restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(restoreLauncherIntent)
                    } else {
                        if (systemSettingsDialog == null || !systemSettingsDialog!!.isShowing) {
                            notifyPolicyViolation(intent.getIntExtra(Const.POLICY_VIOLATION_CAUSE, 0))
                        }
                    }
                }
                Const.ACTION_ADMIN_PANEL -> openAdminPanel()
            }
        }
    }

    private val screenOffReceiver: BroadcastReceiver = ScreenOffReceiver()

    private val stateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                applyEarlyPolicies(settingsHelper!!.getConfig())
            } catch (e: Exception) {
                RemoteLogger.log(this@MainActivity, Const.LOG_WARN, "Non-fatal in stateChangeReceiver: ${e.message}")
            }
        }
    }

    // ---- Lifecycle ----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = getIntent()
        Log.d(Const.LOG_TAG, "MainActivity started" + if (intent?.action != null) ", action: ${intent.action}" else "")
        if (intent != null && "android.app.action.PROVISIONING_SUCCESSFUL".equals(intent.action, ignoreCase = true)) {
            firstStartAfterProvisioning = true
        }

        if (CrashLoopProtection.isCrashLoopDetected(this)) {
            Toast.makeText(this, R.string.fault_loop_detected, Toast.LENGTH_LONG).show()
            return
        }

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            ProUtils.sendExceptionToCrashlytics(e)
            CrashLoopProtection.registerFault(this)
            if (!CrashLoopProtection.isCrashLoopDetected(this)) {
                val launchIntent = packageManager.getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID)
                if (launchIntent != null) startActivity(launchIntent)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) finishAffinity()
            System.exit(0)
        }

        if (BuildConfig.ANR_WATCHDOG) {
            anrWatchDog = ANRWatchDog().also { it.start() }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.setMessage(getString(R.string.main_start_preparations))
        binding.loading.visibility = View.VISIBLE

        settingsHelper = SettingsHelper.getInstance(this)
        preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE)

        if ("" == settingsHelper!!.getDeviceId() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            com.base.launcher.AdminReceiver.updateSettingsFromFile(this)
        }

        settingsHelper!!.setAppStartTime(System.currentTimeMillis())

        // Init delegates (binding must be set first)
        lockScreenManager = LockScreenManager(this, settingsHelper!!)
        permissionCoordinator = PermissionFlowCoordinator(
            this, settingsHelper!!, preferences!!, backgroundExecutor, handler,
            onStartLauncher = { startLauncher() }
        )
        appInstallDelegate = AppInstallDelegate(
            this, binding, handler, configUpdater,
            isContentShown = { isContentShown() },
            onAllInstallComplete = {
                Log.i(Const.LOG_TAG, "Refreshing content - new apps installed")
                settingsHelper!!.refreshConfig(this)
                handler.post { showContent(settingsHelper!!.getConfig()) }
            }
        )
        launcherUIManager = LauncherUIManager(
            this, binding, settingsHelper!!, configUpdater, preferences!!, handler,
            lockScreenManager, statusBarUpdater,
            onSendDeviceInfoNeeded = { sendDeviceInfoAfterReconfigure() },
            onScheduleDeviceInfoNeeded = { scheduleDeviceInfoSending() },
            onScheduleInstalledApps = { scheduleInstalledAppsRun() },
            onPasswordDialogRequested = { createAndShowEnterPasswordDialog() },
            onUpdateConfigRequested = { updateConfig(true) },
            onPostDelayedSystemSettingDialog = { msg, settingsIntent, requestCode, forceEnable ->
                postDelayedSystemSettingDialog(msg, settingsIntent, requestCode, forceEnable)
            }
        )

        Initializer.init(this) {
            startServicesWithRetry()
            initReceiver()

            var intentFilter = IntentFilter()
            intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(stateChangeReceiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(stateChangeReceiver, intentFilter)
            }

            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    RemoteLogger.log(this@MainActivity, Const.LOG_DEBUG, "Network connection available")
                    runOnUiThread {
                        try { applyEarlyPolicies(settingsHelper!!.getConfig()) }
                        catch (e: Exception) {
                            RemoteLogger.log(this@MainActivity, Const.LOG_WARN, "Non-fatal in networkCallback: ${e.message}")
                        }
                    }
                }
                override fun onLost(network: Network) {
                    RemoteLogger.log(this@MainActivity, Const.LOG_DEBUG, "Network connection lost")
                }
            }
            cm?.registerDefaultNetworkCallback(networkCallback!!)

            intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(screenOffReceiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenOffReceiver, intentFilter)
            }

            if (!getIntent().getBooleanExtra(Const.RESTORED_ACTIVITY, false)) {
                startAppsAtBoot()
            }
            settingsHelper!!.setMainActivityRunning(true)
        }

        permissionCoordinator.startBeaconWithPermissionCheck()
    }

    private fun reinitApp() {
        if (!::binding.isInitialized) {
            binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
            binding.setMessage(getString(R.string.main_start_preparations))
            binding.loading.visibility = View.VISIBLE
        }
        if (settingsHelper == null) settingsHelper = SettingsHelper.getInstance(this)
        if (preferences == null) preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_GPS_STATE_CHANGE) startLocationServiceWithRetry()
    }

    private fun initReceiver() {
        val intentFilter = IntentFilter(Const.ACTION_UPDATE_CONFIGURATION)
        intentFilter.addAction(Const.ACTION_HIDE_SCREEN)
        intentFilter.addAction(Const.ACTION_EXIT)
        intentFilter.addAction(Const.ACTION_POLICY_VIOLATION)
        intentFilter.addAction(Const.ACTION_ADMIN_PANEL)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter)
    }

    override fun onResume() {
        super.onResume()
        isBackground = false
        reinitApp()
        statusBarUpdater.startUpdating(this, binding.clock, binding.batteryState)
        startServicesWithRetry()
        if (!BuildConfig.SYSTEM_PRIVILEGES) {
            if (firstStartAfterProvisioning) {
                firstStartAfterProvisioning = false
                permissionCoordinator.waitForProvisioning(10)
            } else {
                permissionCoordinator.setDefaultLauncherEarly()
            }
        } else {
            permissionCoordinator.setSelfAsDeviceOwner()
        }
    }

    override fun onPause() {
        super.onPause()
        isBackground = true
        statusBarUpdater.stopUpdating()
        appInstallDelegate.dismissFileNotDownloadedDialog()
        dismissDialog(enterServerDialog)
        dismissDialog(enterDeviceIdDialog)
        dismissDialog(networkErrorDialog)
        dismissDialog(enterPasswordDialog)
        permissionCoordinator.dismissAll()
        dismissDialog(deviceInfoDialog)
        dismissDialog(systemSettingsDialog)
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_SHOW_LAUNCHER))
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsHelper?.setMainActivityRunning(false)

        val manager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lockScreenManager.removeViews(manager)
        launcherUIManager.removeManageViews(manager)

        listOf(statusBarView, rightToolbarView).forEach { v ->
            if (v != null) try { manager.removeView(v) } catch (e: Exception) { e.printStackTrace() }
        }

        networkCallback?.let {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?)?.unregisterNetworkCallback(it)
        }

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
            unregisterReceiver(stateChangeReceiver)
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (launcherUIManager.mainAppListAdapter != null && event.action == KeyEvent.ACTION_UP) {
            if (!launcherUIManager.mainAppListAdapter!!.onKey(keyCode)) {
                if (launcherUIManager.bottomAppListAdapter != null) {
                    return launcherUIManager.bottomAppListAdapter!!.onKey(keyCode)
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {}

    // ---- Service management ----

    private fun startServicesWithRetry() {
        try {
            startServices()
        } catch (e: Exception) {
            e.printStackTrace()
            handler.postDelayed({
                try { startServices() } catch (e2: Exception) { e2.printStackTrace() }
            }, 1000)
        }
    }

    private fun startServices() {
        if (preferences!!.getInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            startService(Intent(this, CheckForegroundApplicationService::class.java))
        }
        if (BuildConfig.USE_ACCESSIBILITY &&
            preferences!!.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            startService(Intent(this, CheckForegroundAppAccessibilityService::class.java))
        }
        startService(Intent(this, StatusControlService::class.java))
        startService(Intent(this, PluginApiService::class.java))
        RemoteLogger.resetState()
        RemoteLogger.sendLogsToServer(this)
    }

    private fun startAppsAtBoot() {
        if (SystemClock.uptimeMillis() > BOOT_DURATION_SEC * 1000L) return
        val config = settingsHelper!!.getConfig() ?: return
        if (config.getApplications() == null) return

        backgroundExecutor.execute {
            var appStarted = false
            for (application in config.getApplications()) {
                if (application.isRunAtBoot()) {
                    try { Thread.sleep(PAUSE_BETWEEN_AUTORUNS_SEC * 1000L) }
                    catch (e: InterruptedException) { Thread.currentThread().interrupt(); return@execute }
                    val launchIntent = packageManager.getLaunchIntentForPackage(application.getPkg())
                    if (launchIntent != null) { startActivity(launchIntent); appStarted = true }
                }
            }
            if (appStarted && !config.isAutostartForeground()) {
                try { Thread.sleep(PAUSE_BETWEEN_AUTORUNS_SEC * 1000L) }
                catch (e: InterruptedException) { Thread.currentThread().interrupt(); return@execute }
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                intent.putExtra(Const.RESTORED_ACTIVITY, true)
                startActivity(intent)
            }
        }
    }

    // ---- Launcher flow ----

    private fun startLauncher() {
        createScreensIfNeeded()
        launcherUIManager.createButtons()

        if (configUpdater.isPendingAppInstall()) {
            configUpdater.repeatDownloadApps()
        } else if (!permissionCoordinator.checkPermissions(true)) {
            Log.i(Const.LOG_TAG, "startLauncher: requesting permissions")
        } else if (!settingsHelper!!.isBaseUrlSet() && BuildConfig.REQUEST_SERVER_URL) {
            createAndShowServerDialog(false, settingsHelper!!.getBaseUrl(), settingsHelper!!.getServerProject())
        } else if (settingsHelper!!.getDeviceId().isEmpty()) {
            Utils.autoGrantPhonePermission(this)
            if (!SystemUtils.autoSetDeviceId(this)) {
                createAndShowEnterDeviceIdDialog(false, null)
            } else {
                startLauncher()
            }
        } else if (!settingsHelper!!.isConfigInitialized()) {
            Log.i(Const.LOG_TAG, "Updating configuration in startLauncher()")
            var userInteraction = true
            val integratedProvisioningFlow = settingsHelper!!.isIntegratedProvisioningFlow()
            if (integratedProvisioningFlow) settingsHelper!!.setIntegratedProvisioningFlow(false)
            if (settingsHelper!!.getConfig() != null && !integratedProvisioningFlow) {
                showContent(settingsHelper!!.getConfig())
                userInteraction = false
            }
            updateConfig(userInteraction)
        } else {
            showContent(settingsHelper!!.getConfig())
        }
    }

    private fun showContent(config: ServerConfig) {
        launcherUIManager.showContent(config) { c -> applyEarlyPolicies(c) }
    }

    private fun applyEarlyPolicies(config: ServerConfig): Boolean {
        Initializer.applyEarlyNonInteractivePolicies(this, config)
        return true
    }

    private fun isContentShown(): Boolean {
        if (::binding.isInitialized) {
            return binding.getShowContent() != null && binding.getShowContent()
        }
        return false
    }

    private fun updateConfig(userInteraction: Boolean) {
        needSendDeviceInfoAfterReconfigure = true
        launcherUIManager.needRedrawContentAfterReconfigure = true
        if (!launcherUIManager.orientationLocked && !BuildConfig.DISABLE_ORIENTATION_LOCK) {
            lockOrientation()
            launcherUIManager.orientationLocked = true
        }
        configUpdater.updateConfig(this, this, userInteraction)
    }

    private fun lockOrientation() {
        val orientation = resources.configuration.orientation
        val rotation = windowManager.defaultDisplay.rotation
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            requestedOrientation = if (rotation < Surface.ROTATION_180)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        } else {
            requestedOrientation = if (rotation < Surface.ROTATION_180)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }

    private fun sendDeviceInfoAfterReconfigure() {
        if (needSendDeviceInfoAfterReconfigure) {
            needSendDeviceInfoAfterReconfigure = false
            val task = SendDeviceInfoTask(this)
            val deviceInfo = DeviceInfoProvider.getDeviceInfo(this, true, true)
            task.execute(deviceInfo)
        }
    }

    private fun scheduleDeviceInfoSending() {
        if (!sendDeviceInfoScheduled) {
            sendDeviceInfoScheduled = true
            SendDeviceInfoWorker.scheduleDeviceInfoSending(this)
        }
    }

    private fun scheduleInstalledAppsRun() {
        val applicationsForRun = configUpdater.getApplicationsForRun().toMutableList()
        if (applicationsForRun.isEmpty()) return
        var pause = PAUSE_BETWEEN_AUTORUNS_SEC
        while (applicationsForRun.isNotEmpty()) {
            val application = applicationsForRun.removeAt(0)
            handler.postDelayed({
                val launchIntent = packageManager.getLaunchIntentForPackage(application.getPkg())
                if (launchIntent != null) startActivity(launchIntent)
            }, (pause * 1000).toLong())
            pause += PAUSE_BETWEEN_AUTORUNS_SEC
        }
    }

    private fun startLocationServiceWithRetry() {
        try { startLocationService() }
        catch (e: Exception) {
            e.printStackTrace()
            handler.postDelayed({
                try { startLocationService() } catch (e2: Exception) { e2.printStackTrace() }
            }, 1000)
        }
    }

    private fun startLocationService() {
        val config = settingsHelper!!.getConfig()
        val intent = Intent(this, LocationService::class.java)
        intent.action = config.getRequestUpdates() ?: LocationService.ACTION_STOP
        startService(intent)
    }

    // ---- ConfigUpdater.UINotifier ----

    override fun onConfigUpdateStart() = appInstallDelegate.onConfigUpdateStart()

    override fun onConfigUpdateServerError(errorText: String) {
        if (enterDeviceIdDialog != null) {
            enterDeviceIdDialogBinding.setError(true)
            enterDeviceIdDialog!!.show()
        } else {
            networkErrorDetails = errorText
            createAndShowEnterDeviceIdDialog(true, settingsHelper!!.getDeviceId())
        }
    }

    override fun onConfigUpdateNetworkError(errorText: String) {
        createAndShowNetworkErrorDialog(
            settingsHelper!!.getBaseUrl(),
            settingsHelper!!.getServerProject(),
            errorText,
            settingsHelper!!.getConfig() == null && !settingsHelper!!.isQrProvisioning(),
            settingsHelper!!.getConfig() == null || settingsHelper!!.getConfig().isShowWifi()
        )
    }

    override fun onConfigLoaded() = applyEarlyPolicies(settingsHelper!!.getConfig()).let {}

    override fun onPoliciesUpdated() = startLocationServiceWithRetry()

    override fun onFileDownloading(remoteFile: RemoteFile) = appInstallDelegate.onFileDownloading(remoteFile)
    override fun onDownloadProgress(progress: Int, total: Long, current: Long) =
        appInstallDelegate.onDownloadProgress(progress, total, current)
    override fun onFileDownloadError(remoteFile: RemoteFile) = appInstallDelegate.onFileDownloadError(remoteFile)
    override fun onFileInstallError(remoteFile: RemoteFile) = appInstallDelegate.onFileInstallError(remoteFile)

    override fun onAppUpdateStart() {
        appInstallDelegate.onAppUpdateStart()
        settingsHelper!!.setConfigInitialized(true)
    }

    override fun onAppRemoving(application: Application) = appInstallDelegate.onAppRemoving(application)
    override fun onAppDownloading(application: Application) = appInstallDelegate.onAppDownloading(application)
    override fun onAppInstalling(application: Application) = appInstallDelegate.onAppInstalling(application)
    override fun onAppDownloadError(application: Application) = appInstallDelegate.onAppDownloadError(application)
    override fun onAppInstallError(packageName: String) = appInstallDelegate.onAppInstallError(packageName)
    override fun onAppInstallComplete(packageName: String) {}

    override fun onConfigUpdateComplete() {
        val prefs = applicationContext.getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE)
        val deviceAdminLog = PreferenceLogger.getLogString(prefs)
        if (!deviceAdminLog.isNullOrEmpty()) {
            RemoteLogger.log(this, Const.LOG_DEBUG, deviceAdminLog)
            PreferenceLogger.clearLogString(prefs)
        }
        Log.i(Const.LOG_TAG, "Showing content from setActions()")
        settingsHelper!!.refreshConfig(this)
        showContent(settingsHelper!!.getConfig())
    }

    override fun onAllAppInstallComplete() = appInstallDelegate.onAllAppInstallComplete()

    // ---- Permission result ----

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionCoordinator.handlePermissionsResult(requestCode, permissions, grantResults)
    }

    // ---- Click handlers ----

    override fun onAppChoose(resolveInfo: AppInfo) {}

    override fun switchAppListAdapter(adapter: BaseAppListAdapter, direction: Int): Boolean {
        if (adapter == launcherUIManager.mainAppListAdapter && launcherUIManager.bottomAppListAdapter != null &&
            (direction == Const.DIRECTION_RIGHT || direction == Const.DIRECTION_DOWN)) {
            launcherUIManager.bottomAppListAdapter!!.setFocused(true)
            return true
        } else if (adapter == launcherUIManager.bottomAppListAdapter &&
            (direction == Const.DIRECTION_LEFT || direction == Const.DIRECTION_UP)) {
            launcherUIManager.mainAppListAdapter!!.setFocused(true)
            return true
        }
        return false
    }

    override fun onLongClick(v: View): Boolean {
        createAndShowEnterPasswordDialog()
        return true
    }

    override fun onClick(v: View) {
        if (v == launcherUIManager.infoView) {
            createAndShowInfoDialog()
        } else if (v == launcherUIManager.updateView) {
            if (enterDeviceIdDialog != null && enterDeviceIdDialog!!.isShowing) {
                Log.i(Const.LOG_TAG, "Occasional update request when device info is entered, ignoring!")
                return
            }
            Log.i(Const.LOG_TAG, "updating config on request")
            binding.loading.visibility = View.VISIBLE
            binding.setShowContent(false)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            updateConfig(true)
        }
    }

    // ---- Password dialog ----

    private fun createAndShowEnterPasswordDialog() {
        dismissDialog(enterPasswordDialog)
        enterPasswordDialog = Dialog(this)
        dialogEnterPasswordBinding = DataBindingUtil.inflate(
            android.view.LayoutInflater.from(this), R.layout.dialog_enter_password, null, false
        )
        enterPasswordDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        enterPasswordDialog!!.setCancelable(false)
        enterPasswordDialog!!.setContentView(dialogEnterPasswordBinding!!.root)
        dialogEnterPasswordBinding!!.setLoading(false)
        try {
            enterPasswordDialog!!.show()
        } catch (e: Exception) {
            Toast.makeText(applicationContext, R.string.internal_error, Toast.LENGTH_LONG).show()
        }
    }

    fun closeEnterPasswordDialog(view: View) = dismissDialog(enterPasswordDialog)

    fun checkAdministratorPassword(view: View) {
        dialogEnterPasswordBinding!!.setLoading(true)
        GetServerConfigTask(this).execute { _ ->
            dialogEnterPasswordBinding!!.setLoading(false)
            var masterPassword = CryptoHelper.getMD5String("12345678")
            if (settingsHelper!!.getConfig() != null && settingsHelper!!.getConfig().getPassword() != null) {
                masterPassword = settingsHelper!!.getConfig().getPassword()
            }
            if (CryptoHelper.getMD5String(dialogEnterPasswordBinding!!.password.text.toString()) == masterPassword) {
                dismissDialog(enterPasswordDialog)
                dialogEnterPasswordBinding!!.setError(false)
                openAdminPanel()
            } else {
                dialogEnterPasswordBinding!!.setError(true)
            }
        }
    }

    private fun openAdminPanel() {
        RemoteLogger.log(this, Const.LOG_INFO, "Administrator panel opened")
        startActivity(Intent(this, AdminActivity::class.java))
    }

    // ---- System settings dialog ----

    private fun notifyPolicyViolation(cause: Int) {
        when (cause) {
            Const.GPS_ON_REQUIRED -> postDelayedSystemSettingDialog(
                getString(R.string.message_turn_on_gps), Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                REQUEST_CODE_GPS_STATE_CHANGE, false)
            Const.GPS_OFF_REQUIRED -> postDelayedSystemSettingDialog(
                getString(R.string.message_turn_off_gps), Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                REQUEST_CODE_GPS_STATE_CHANGE, false)
            Const.MOBILE_DATA_ON_REQUIRED -> createAndShowSystemSettingDialog(
                getString(R.string.message_turn_on_mobile_data), null, 0)
            Const.MOBILE_DATA_OFF_REQUIRED -> createAndShowSystemSettingDialog(
                getString(R.string.message_turn_off_mobile_data), null, 0)
        }
    }

    private fun postDelayedSystemSettingDialog(message: String, settingsIntent: Intent?, requestCode: Int?, forceEnableSettings: Boolean) {
        if (settingsIntent != null) {
            if (preferences!!.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON || forceEnableSettings) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_ENABLE_SETTINGS))
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_STOP_CONTROL))
        }
        handler.postDelayed({ createAndShowSystemSettingDialog(message, settingsIntent, requestCode) }, 5000)
    }

    private fun createAndShowSystemSettingDialog(message: String, settingsIntent: Intent?, requestCode: Int?) {
        dismissDialog(systemSettingsDialog)
        systemSettingsDialog = Dialog(this)
        dialogSystemSettingsBinding = DataBindingUtil.inflate(
            android.view.LayoutInflater.from(this), R.layout.dialog_system_settings, null, false
        )
        systemSettingsDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        systemSettingsDialog!!.setCancelable(false)
        systemSettingsDialog!!.setContentView(dialogSystemSettingsBinding!!.root)
        dialogSystemSettingsBinding!!.setMessage(message)
        systemSettingsDialog!!.findViewById<View>(R.id.continueButton).setOnClickListener {
            dismissDialog(systemSettingsDialog)
            if (settingsIntent == null) return@setOnClickListener
            try {
                startActivityOptionalResult(settingsIntent, requestCode)
            } catch (e: Exception) {
                startActivityOptionalResult(Intent(android.provider.Settings.ACTION_SETTINGS), requestCode)
            }
        }
        try {
            systemSettingsDialog!!.show()
        } catch (e: Exception) {
            RemoteLogger.log(this, Const.LOG_WARN, "Failed to open a popup system dialog! ${e.message}")
            e.printStackTrace()
            systemSettingsDialog = null
        }
    }

    private fun startActivityOptionalResult(intent: Intent, requestCode: Int?) {
        if (requestCode != null) startActivityForResult(intent, requestCode) else startActivity(intent)
    }

    // ---- Network error handlers ----

    fun saveDeviceId(view: View) {
        val deviceId = enterDeviceIdDialogBinding.deviceId.text.toString()
        if (deviceId.isEmpty()) return
        settingsHelper!!.setDeviceId(deviceId)
        enterDeviceIdDialogBinding.setError(false)
        dismissDialog(enterDeviceIdDialog)
        if (permissionCoordinator.checkPermissions(true)) {
            Log.i(Const.LOG_TAG, "saveDeviceId(): calling updateConfig()")
            updateConfig(true)
        }
    }

    fun saveServerUrl(view: View) {
        if (saveServerUrlBase()) {
            ServerServiceKeeper.resetServices()
            permissionCoordinator.checkAndStartLauncher()
        }
    }

    fun networkErrorRepeatClicked(view: View) {
        dismissDialog(networkErrorDialog)
        Log.i(Const.LOG_TAG, "networkErrorRepeatClicked(): calling updateConfig()")
        updateConfig(true)
    }

    fun networkErrorResetClicked(view: View) {
        dismissDialog(networkErrorDialog)
        settingsHelper!!.setDeviceId("")
        settingsHelper!!.setBaseUrl("")
        settingsHelper!!.setSecondaryBaseUrl("")
        settingsHelper!!.setServerProject("")
        createAndShowServerDialog(false, settingsHelper!!.getBaseUrl(), settingsHelper!!.getServerProject())
    }

    fun networkErrorWifiClicked(view: View) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_ENABLE_SETTINGS))
        handler.postDelayed({ startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }, 500)
    }

    fun networkErrorCancelClicked(view: View) {
        dismissDialog(networkErrorDialog)
        if (configFault) {
            Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, quit")
            Toast.makeText(this, getString(R.string.critical_server_failure, getString(R.string.white_app_name)), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.i(Const.LOG_TAG, "networkErrorCancelClicked()")
        if (settingsHelper!!.getConfig() != null) {
            showContent(settingsHelper!!.getConfig())
            configUpdater.skipConfigLoad()
        } else {
            Toast.makeText(this, R.string.empty_configuration, Toast.LENGTH_LONG).show()
            configFault = true
            updateConfig(false)
        }
    }

    fun networkErrorDetailsClicked(view: View) = ErrorDetailsActivity.display(this, networkErrorDetails, false)

    fun repeatDownloadClicked(view: View) = appInstallDelegate.repeatDownload()
    fun confirmDownloadFailureClicked(view: View) = appInstallDelegate.confirmDownloadFailure()

    // ---- Permission coordinator stubs (called from XML onClick) ----

    fun permissionsRetryClicked(view: View) = permissionCoordinator.permissionsRetry()
    fun permissionsExitClicked(view: View) = permissionCoordinator.permissionsExit()
    fun skipAccessibilityService(view: View) = permissionCoordinator.skipAccessibilityService()
    fun setAccessibilityService(view: View) = permissionCoordinator.setAccessibilityService()
    fun skipAdminMode(view: View) = permissionCoordinator.skipAdminMode()
    fun setAdminMode(view: View) = permissionCoordinator.setAdminMode()
    fun historyWithoutPermission(view: View) = permissionCoordinator.historyWithoutPermission()
    fun continueHistory(view: View) = permissionCoordinator.continueHistory()
    fun storageWithoutPermission(view: View) = permissionCoordinator.storageWithoutPermission()
    fun continueStorage(view: View) = permissionCoordinator.continueStorage()
    fun overlayWithoutPermission(view: View) = permissionCoordinator.overlayWithoutPermission()
    fun continueOverlay(view: View) = permissionCoordinator.continueOverlay(view)
    fun continueUnknownSources(view: View) = permissionCoordinator.continueUnknownSources()
    fun continueMiuiPermissions(view: View) = permissionCoordinator.continueMiuiPermissions()

    // LockScreen creation is triggered from checkAndStartLauncher (called by permissionCoordinator.onStartLauncher → startLauncher)
    fun createScreensIfNeeded() {
        lockScreenManager.createApplicationNotAllowedScreen { createAndShowEnterPasswordDialog() }
        lockScreenManager.createLockScreen()
        if (settingsHelper?.getConfig()?.getLockStatusBar() == true) {
            statusBarView = ProUtils.preventStatusBarExpansion(this)
            rightToolbarView = ProUtils.preventApplicationsList(this)
        }
    }

    // ---- Misc helpers ----

    private fun startLauncherRestarter() {
        val intent = packageManager.getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID) ?: run {
            Log.i("LauncherRestarter", "No restarter app, please add it in the config!")
            return
        }
        intent.putExtra(Const.LAUNCHER_RESTARTER_OLD_VERSION, BuildConfig.VERSION_NAME)
        startActivity(intent)
        Log.i("LauncherRestarter", "Calling launcher restarter from the launcher")
    }

    @Throws(IOException::class)
    private fun createFileFromTemplate(srcFile: File, dstFile: File, deviceId: String, config: ServerConfig) {
        var content = FileUtils.readFileToString(srcFile)
        content = content.replace("DEVICE_NUMBER", deviceId)
            .replace("CUSTOM1", config.getCustom1() ?: "")
            .replace("CUSTOM2", config.getCustom2() ?: "")
            .replace("CUSTOM3", config.getCustom3() ?: "")
        FileUtils.writeStringToFile(dstFile, content)
    }
}
