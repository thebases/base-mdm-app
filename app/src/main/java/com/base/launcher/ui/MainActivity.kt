package com.base.launcher.ui

import android.Manifest
import android.app.Dialog
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import com.base.launcher.IBeaconAdvertiser
import com.base.launcher.AdminReceiver
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.R
import com.base.launcher.databinding.ActivityMainBinding
import com.base.launcher.databinding.DialogAccessibilityServiceBinding
import com.base.launcher.databinding.DialogAdministratorModeBinding
import com.base.launcher.databinding.DialogEnterPasswordBinding
import com.base.launcher.databinding.DialogFileDownloadingFailedBinding
import com.base.launcher.databinding.DialogHistorySettingsBinding
import com.base.launcher.databinding.DialogManageStorageBinding
import com.base.launcher.databinding.DialogMiuiPermissionsBinding
import com.base.launcher.databinding.DialogOverlaySettingsBinding
import com.base.launcher.databinding.DialogPermissionsBinding
import com.base.launcher.databinding.DialogSystemSettingsBinding
import com.base.launcher.databinding.DialogUnknownSourcesBinding
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
import com.base.launcher.server.UnsafeOkHttpClient
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
import com.jakewharton.picasso.OkHttp3Downloader
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.Arrays
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
        private const val PERMISSIONS_REQUEST = 1000
        private const val TAG = "MainActivity"
        private const val BOOT_DURATION_SEC = 120
        private const val PAUSE_BETWEEN_AUTORUNS_SEC = 5
        private const val REQ_BT_PERMS = 1001
    }

    private lateinit var binding: ActivityMainBinding
    private var settingsHelper: SettingsHelper? = null

    private var fileNotDownloadedDialog: Dialog? = null
    private var dialogFileDownloadingFailedBinding: DialogFileDownloadingFailedBinding? = null

    private var enterPasswordDialog: Dialog? = null
    private var dialogEnterPasswordBinding: DialogEnterPasswordBinding? = null

    private var overlaySettingsDialog: Dialog? = null
    private var dialogOverlaySettingsBinding: DialogOverlaySettingsBinding? = null

    private var historySettingsDialog: Dialog? = null
    private var dialogHistorySettingsBinding: DialogHistorySettingsBinding? = null

    private var manageStorageDialog: Dialog? = null
    private var dialogManageStorageBinding: DialogManageStorageBinding? = null

    private var miuiPermissionsDialog: Dialog? = null
    private var dialogMiuiPermissionsBinding: DialogMiuiPermissionsBinding? = null

    private var unknownSourcesDialog: Dialog? = null
    private var dialogUnknownSourcesBinding: DialogUnknownSourcesBinding? = null

    private var administratorModeDialog: Dialog? = null
    private var dialogAdministratorModeBinding: DialogAdministratorModeBinding? = null

    private var accessibilityServiceDialog: Dialog? = null
    private var dialogAccessibilityServiceBinding: DialogAccessibilityServiceBinding? = null

    private var systemSettingsDialog: Dialog? = null
    private var dialogSystemSettingsBinding: DialogSystemSettingsBinding? = null

    private var permissionsDialog: Dialog? = null
    private var dialogPermissionsBinding: DialogPermissionsBinding? = null

    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var applicationNotAllowed: View? = null
    private var lockScreen: View? = null

    private var preferences: SharedPreferences? = null

    private var mainAppListAdapter: MainAppListAdapter? = null
    private var bottomAppListAdapter: BottomAppListAdapter? = null
    private var spanCount = 0
    private val statusBarUpdater = StatusBarUpdater()

    private var sendDeviceInfoScheduled = false
    // This flag notifies "download error" dialog what we're downloading: application or file
    // We cannot send this flag as the method parameter because dialog calls MainActivity methods
    private var downloadingFile = false

    private var configFault = false

    private var needSendDeviceInfoAfterReconfigure = false
    private var needRedrawContentAfterReconfigure = false
    private var orientationLocked = false

    private val REQUEST_CODE_GPS_STATE_CHANGE = 1

    // This flag is used by the broadcast receiver to determine what to do if it gets a policy violation report
    private var isBackground = false

    private var anrWatchDog: ANRWatchDog? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val configUpdater = ConfigUpdater()

    private var picasso: Picasso? = null

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
                        // Device is locked by the server administrator!
                        showLockScreen()
                    } else if (applicationNotAllowed != null) {
                        val textView = applicationNotAllowed!!.findViewById<TextView>(R.id.package_id)
                        textView.text = intent.getStringExtra(Const.PACKAGE_NAME)

                        applicationNotAllowed!!.visibility = View.VISIBLE
                        // This ensures requestFocus() happens after layout, when it's safe and guaranteed to work.
                        applicationNotAllowed!!.post {
                            val button = applicationNotAllowed!!.findViewById<View>(R.id.layout_application_not_allowed_continue)
                            button.requestFocus()
                        }
                        handler.postDelayed({
                            applicationNotAllowed?.visibility = View.GONE
                        }, 20000)
                    }
                }
                Const.ACTION_DISABLE_BLOCK_WINDOW -> {
                    applicationNotAllowed?.visibility = View.GONE
                }
                Const.ACTION_EXIT -> {
                    finish()
                }
                Const.ACTION_POLICY_VIOLATION -> {
                    if (isBackground) {
                        // If we're in the background, let's bring Base MDM to top and the notification will be raised in onResume
                        val restoreLauncherIntent = Intent(context, MainActivity::class.java)
                        restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(restoreLauncherIntent)
                    } else {
                        // Calling startActivity always calls onPause / onResume which is not what we want
                        // So just show dialog if it isn't already shown
                        if (systemSettingsDialog == null || !systemSettingsDialog!!.isShowing) {
                            notifyPolicyViolation(intent.getIntExtra(Const.POLICY_VIOLATION_CAUSE, 0))
                        }
                    }
                }
                Const.ACTION_ADMIN_PANEL -> {
                    openAdminPanel()
                }
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

    private val selectedManageButtonBorder = GradientDrawable()
    private var exitView: ImageView? = null
    private var exitFirstTapTime: Long = 0
    private var exitTapCount = 0
    private var infoView: ImageView? = null
    private var updateView: ImageView? = null

    private var statusBarView: View? = null
    private var rightToolbarView: View? = null

    private var firstStartAfterProvisioning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = getIntent()
        Log.d(Const.LOG_TAG, "MainActivity started" + if (intent != null && intent.action != null)
            ", action: ${intent.action}" else "")
        if (intent != null && "android.app.action.PROVISIONING_SUCCESSFUL".equals(intent.action, ignoreCase = true)) {
            firstStartAfterProvisioning = true
        }

        if (CrashLoopProtection.isCrashLoopDetected(this)) {
            Toast.makeText(this@MainActivity, R.string.fault_loop_detected, Toast.LENGTH_LONG).show()
            return
        }

        // Disable crashes to avoid "select a launcher" popup
        // Crashlytics will show an exception anyway!
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            e.printStackTrace()

            ProUtils.sendExceptionToCrashlytics(e)

            CrashLoopProtection.registerFault(this@MainActivity)
            // Restart launcher if there's a launcher restarter (and we're not in a crash loop)
            if (!CrashLoopProtection.isCrashLoopDetected(this@MainActivity)) {
                val launchIntent = packageManager.getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                finishAffinity()
            }
            System.exit(0)
        }

        if (BuildConfig.ANR_WATCHDOG) {
            anrWatchDog = ANRWatchDog()
            anrWatchDog!!.start()
        }

        // Prevent showing the lock screen during the app download/installation
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.setMessage(getString(R.string.main_start_preparations))
        binding.loading.visibility = View.VISIBLE

        settingsHelper = SettingsHelper.getInstance(this)
        preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE)

        if ("" == settingsHelper!!.getDeviceId() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AdminReceiver.updateSettingsFromFile(this)
        }

        settingsHelper!!.setAppStartTime(System.currentTimeMillis())

        Initializer.init(this) {

            // Try to start services in onCreate(), this may fail, we will try again on each onResume.
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
                        try {
                            applyEarlyPolicies(settingsHelper!!.getConfig())
                        } catch (e: Exception) {
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

        startBeaconWithPermissionCheck()
    }

    // On some Android firmwares, onResume is called before onCreate, so the fields are not initialized
    // Here we initialize all required fields to avoid crash at startup
    private fun reinitApp() {
        if (!::binding.isInitialized) {
            binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
            binding.setMessage(getString(R.string.main_start_preparations))
            binding.loading.visibility = View.VISIBLE
        }

        if (settingsHelper == null) {
            settingsHelper = SettingsHelper.getInstance(this)
        }
        if (preferences == null) {
            preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_GPS_STATE_CHANGE) {
            // User changed GPS state, let's update location service
            startLocationServiceWithRetry()
        }
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

        // On some Android firmwares, onResume is called before onCreate, so the fields are not initialized
        // Here we initialize all required fields to avoid crash at startup
        reinitApp()

        statusBarUpdater.startUpdating(this, binding.clock, binding.batteryState)

        startServicesWithRetry()

        if (!BuildConfig.SYSTEM_PRIVILEGES) {
            if (firstStartAfterProvisioning) {
                firstStartAfterProvisioning = false
                waitForProvisioning(10)
            } else {
                setDefaultLauncherEarly()
            }
        } else {
            setSelfAsDeviceOwner()
        }
    }

    private fun lockOrientation() {
        val orientation = resources.configuration.orientation
        val rotation = windowManager.defaultDisplay.rotation
        Log.d(Const.LOG_TAG, "Lock orientation: orientation=$orientation, rotation=$rotation")
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            requestedOrientation = if (rotation < Surface.ROTATION_180)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        } else {
            requestedOrientation = if (rotation < Surface.ROTATION_180)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (mainAppListAdapter != null && event.action == KeyEvent.ACTION_UP) {
            if (!mainAppListAdapter!!.onKey(keyCode)) {
                if (bottomAppListAdapter != null) {
                    return bottomAppListAdapter!!.onKey(keyCode)
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    // Workaround against crash "App is in background" on Android 9: this is an Android OS bug
    // https://stackoverflow.com/questions/52013545/android-9-0-not-allowed-to-start-service-app-is-in-background-after-onresume
    private fun startServicesWithRetry() {
        try {
            startServices()
        } catch (e: Exception) {
            // Android OS bug!!!
            e.printStackTrace()

            // Repeat an attempt to start services after one second
            handler.postDelayed({
                try {
                    startServices()
                } catch (e2: Exception) {
                    // Still failed, now give up!
                    // startService may fail after resuming, but the service may be already running (there's a WorkManager)
                    // So if we get an exception here, just ignore it and hope the app will work further
                    e2.printStackTrace()
                }
            }, 1000)
        }
    }

    private fun startAppsAtBoot() {
        // Let's assume that we start within two minutes after boot
        // This should work even for slow devices
        val uptimeMillis = SystemClock.uptimeMillis()
        if (uptimeMillis > BOOT_DURATION_SEC * 1000L) {
            return
        }
        val config = settingsHelper!!.getConfig() ?: return
        if (config.getApplications() == null) {
            // First start
            return
        }

        backgroundExecutor.execute {
            var appStarted = false
            for (application in config.getApplications()) {
                if (application.isRunAtBoot()) {
                    try {
                        Thread.sleep(PAUSE_BETWEEN_AUTORUNS_SEC * 1000L)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@execute
                    }
                    val launchIntent = packageManager.getLaunchIntentForPackage(application.getPkg())
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                        appStarted = true
                    }
                }
            }
            if (appStarted && !config.isAutostartForeground()) {
                try {
                    Thread.sleep(PAUSE_BETWEEN_AUTORUNS_SEC * 1000L)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
                val intent = Intent(this@MainActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                intent.putExtra(Const.RESTORED_ACTIVITY, true)
                startActivity(intent)
            }
        }
    }

    // Does not seem to work, though. See the comment to SystemUtils.becomeDeviceOwner()
    private fun setSelfAsDeviceOwner() {
        // We set self as device owner each time so we could trace errors if device owner setup fails
        if (Utils.isDeviceOwner(this)) {
            checkAndStartLauncher()
            return
        }

        backgroundExecutor.execute {
            if (!SystemUtils.becomeDeviceOwnerByCommand(this@MainActivity)) {
                SystemUtils.becomeDeviceOwnerByXmlFile(this@MainActivity)
            }
            handler.post { setDefaultLauncherEarly() }
        }
    }

    private fun startServices() {
        // Foreground apps checks are not available in a free version: services are the stubs
        if (preferences!!.getInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            startService(Intent(this@MainActivity, CheckForegroundApplicationService::class.java))
        }
        if (BuildConfig.USE_ACCESSIBILITY &&
            preferences!!.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            startService(Intent(this@MainActivity, CheckForegroundAppAccessibilityService::class.java))
        }
        startService(Intent(this@MainActivity, StatusControlService::class.java))

        // Moved to onResume!
        // https://stackoverflow.com/questions/51863600/java-lang-illegalstateexception-not-allowed-to-start-service-intent-from-activ
        startService(Intent(this@MainActivity, PluginApiService::class.java))

        // Send pending logs to server
        RemoteLogger.resetState()
        RemoteLogger.sendLogsToServer(this@MainActivity)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (Utils.isDeviceOwner(this)) {
                // Even in device owner mode, if "Ask for location" is requested by the admin,
                // let's ask permissions (so do nothing here, fall through)
                if (settingsHelper!!.getConfig() == null ||
                    !ServerConfig.APP_PERMISSIONS_ASK_ALL.equals(settingsHelper!!.getConfig().getAppPermissions()) &&
                    !ServerConfig.APP_PERMISSIONS_ASK_LOCATION.equals(settingsHelper!!.getConfig().getAppPermissions())) {
                    // This may be called on Android 10, not sure why; just continue the flow
                    Log.i(Const.LOG_TAG, "Called onRequestPermissionsResult: permissions=${Arrays.toString(permissions)}" +
                            ", grantResults=${Arrays.toString(grantResults)}")
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
                    return
                }
            }

            var locationDisabled = false
            for (n in permissions.indices) {
                if (permissions[n] == Manifest.permission.ACCESS_FINE_LOCATION) {
                    if (grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                        // The user didn't allow to determine location, this is not critical, just ignore it
                        preferences!!.edit().putInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_ON).commit()
                        locationDisabled = true
                    }
                }
            }

            var requestPermissions = false
            for (n in permissions.indices) {
                if (grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                    if (permissions[n] == Manifest.permission.ACCESS_BACKGROUND_LOCATION &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || locationDisabled)) {
                        // Background location is not available on Android 9 and below
                        // Also we don't need to grant background location permission if we don't grant location at all
                        continue
                    }

                    if (permissions[n] == Manifest.permission.ACCESS_FINE_LOCATION && locationDisabled) {
                        // Skip fine location permission if user intentionally disabled it
                        continue
                    }

                    // Let user know that he need to grant permissions
                    requestPermissions = true
                }
            }

            if (requestPermissions) {
                createAndShowPermissionsDialog()
            }
        }
        if (requestCode == REQ_BT_PERMS) {
            var granted = true
            for (r in grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    granted = false
                    break
                }
            }
            if (granted) {
                IBeaconAdvertiser.start(this)
            } else {
                Log.e("MainActivity", "Bluetooth permissions denied, cannot advertise")
            }
        }
    }

    // AdminReceiver may be called later than onCreate() and onResume()
    // so the launcher setup and other methods requiring device owner permissions may fail
    // Here we wait up to 10 seconds until the app gets the device owner permissions
    private fun waitForProvisioning(attempts: Int) {
        if (Utils.isDeviceOwner(this) || attempts <= 0) {
            setDefaultLauncherEarly()
        } else {
            handler.postDelayed({
                waitForProvisioning(attempts - 1)
            }, 1000)
        }
    }

    private fun setDefaultLauncherEarly() {
        val config = SettingsHelper.getInstance(this).getConfig()
        if (BuildConfig.SET_DEFAULT_LAUNCHER_EARLY && config == null && Utils.isDeviceOwner(this)) {
            // At first start, temporarily set Base MDM as a default launcher
            // to prevent the user from clicking Home to stop running Base MDM
            val defaultLauncher = Utils.getDefaultLauncher(this)

            // As per the documentation, setting the default preferred activity should not be done on the main thread
            backgroundExecutor.execute {
                if (!packageName.equals(defaultLauncher, ignoreCase = true)) {
                    Utils.setDefaultLauncher(this@MainActivity)
                }
                handler.post { checkAndStartLauncher() }
            }
            return
        }
        checkAndStartLauncher()
    }

    private fun checkAndStartLauncher() {
        val deviceOwner = Utils.isDeviceOwner(this)
        preferences!!.edit().putInt(Const.PREFERENCES_DEVICE_OWNER,
            if (deviceOwner) Const.PREFERENCES_ON else Const.PREFERENCES_OFF).commit()

        val miuiPermissionMode = preferences!!.getInt(Const.PREFERENCES_MIUI_PERMISSIONS, -1)
        if (miuiPermissionMode == -1) {
            preferences!!.edit().putInt(Const.PREFERENCES_MIUI_PERMISSIONS, Const.PREFERENCES_ON).commit()
            if (checkMiuiPermissions(Const.MIUI_PERMISSIONS)) {
                // Permissions dialog opened, break the flow!
                return
            }
        }

        val miuiDeveloperMode = preferences!!.getInt(Const.PREFERENCES_MIUI_DEVELOPER, -1)
        if (miuiDeveloperMode == -1) {
            preferences!!.edit().putInt(Const.PREFERENCES_MIUI_DEVELOPER, Const.PREFERENCES_ON).commit()
            if (checkMiuiPermissions(Const.MIUI_DEVELOPER)) {
                // Permissions dialog opened, break the flow!
                return
            }
        }

        val miuiOptimizationMode = preferences!!.getInt(Const.PREFERENCES_MIUI_OPTIMIZATION, -1)
        if (miuiOptimizationMode == -1) {
            preferences!!.edit().putInt(Const.PREFERENCES_MIUI_OPTIMIZATION, Const.PREFERENCES_ON).commit()
            if (checkMiuiPermissions(Const.MIUI_OPTIMIZATION)) {
                // Permissions dialog opened, break the flow!
                return
            }
        }

        val unknownSourceMode = preferences!!.getInt(Const.PREFERENCES_UNKNOWN_SOURCES, -1)
        if (!deviceOwner && unknownSourceMode == -1) {
            if (checkUnknownSources()) {
                preferences!!.edit().putInt(Const.PREFERENCES_UNKNOWN_SOURCES, Const.PREFERENCES_ON).commit()
            } else {
                return
            }
        }

        val administratorMode = preferences!!.getInt(Const.PREFERENCES_ADMINISTRATOR, -1)
        if (administratorMode == -1) {
            if (checkAdminMode()) {
                RemoteLogger.log(this, Const.LOG_DEBUG, "Saving device admin state as 1 (TRUE)")
                preferences!!.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ON).commit()
            } else {
                return
            }
        }

        val overlayMode = preferences!!.getInt(Const.PREFERENCES_OVERLAY, -1)
        if (ProUtils.isPro() && overlayMode == -1 && needRequestOverlay()) {
            if (checkAlarmWindow()) {
                preferences!!.edit().putInt(Const.PREFERENCES_OVERLAY, Const.PREFERENCES_ON).commit()
            } else {
                return
            }
        }

        val usageStatisticsMode = preferences!!.getInt(Const.PREFERENCES_USAGE_STATISTICS, -1)
        if (ProUtils.isPro() && usageStatisticsMode == -1 && needRequestUsageStats()) {
            if (checkUsageStatistics()) {
                preferences!!.edit().putInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_ON).commit()

                // If usage statistics is on, there's no need to turn on accessibility services
                preferences!!.edit().putInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF).commit()
            } else {
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manageStorageMode = preferences!!.getInt(Const.PREFERENCES_MANAGE_STORAGE, -1)
            if (manageStorageMode == -1) {
                if (checkManageStorage()) {
                    preferences!!.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_ON).commit()
                } else {
                    return
                }
            }
        }

        val accessibilityService = preferences!!.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, -1)
        // Check the same condition as for usage stats here
        // because accessibility is used as a secondary condition when usage stats is not available
        if (ProUtils.isPro() && BuildConfig.USE_ACCESSIBILITY && accessibilityService == -1 && needRequestUsageStats()) {
            if (checkAccessibilityService()) {
                preferences!!.edit().putInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_ON).commit()
            } else {
                createAndShowAccessibilityServiceDialog()
                return
            }
        }

        if (settingsHelper != null && settingsHelper!!.getConfig() != null &&
            settingsHelper!!.getConfig().getLockStatusBar() != null && settingsHelper!!.getConfig().getLockStatusBar()) {
            // If the admin requested status bar lock (may be required for some early Samsung devices), block the status bar and right bar (App list) expansion
            statusBarView = ProUtils.preventStatusBarExpansion(this)
            rightToolbarView = ProUtils.preventApplicationsList(this)
        }

        createApplicationNotAllowedScreen()
        createLockScreen()
        startLauncher()
    }

    private fun createAndShowPermissionsDialog() {
        dismissDialog(permissionsDialog)
        permissionsDialog = Dialog(this)
        dialogPermissionsBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_permissions,
            null,
            false
        )
        permissionsDialog!!.setCancelable(false)
        permissionsDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)

        permissionsDialog!!.setContentView(dialogPermissionsBinding!!.root)
        permissionsDialog!!.show()
    }

    fun permissionsRetryClicked(view: View) {
        dismissDialog(permissionsDialog)
        startLauncher()
    }

    fun permissionsExitClicked(view: View) {
        dismissDialog(permissionsDialog)
        finish()
    }

    private fun createAndShowAccessibilityServiceDialog() {
        dismissDialog(accessibilityServiceDialog)
        accessibilityServiceDialog = Dialog(this)
        dialogAccessibilityServiceBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_accessibility_service,
            null,
            false
        )
        dialogAccessibilityServiceBinding!!.hint.text =
            getString(R.string.dialog_accessibility_service_message, getString(R.string.white_app_name))
        accessibilityServiceDialog!!.setCancelable(false)
        accessibilityServiceDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)

        accessibilityServiceDialog!!.setContentView(dialogAccessibilityServiceBinding!!.root)
        accessibilityServiceDialog!!.show()
    }

    fun skipAccessibilityService(view: View) {
        try {
            accessibilityServiceDialog!!.dismiss()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        accessibilityServiceDialog = null

        preferences!!.edit().putInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF).commit()

        checkAndStartLauncher()
    }

    fun setAccessibilityService(view: View) {
        try {
            accessibilityServiceDialog!!.dismiss()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        accessibilityServiceDialog = null

        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivityForResult(intent, 0)
    }

    // Accessibility services are needed in the Pro-version only
    private fun checkAccessibilityService(): Boolean {
        return ProUtils.checkAccessibilityService(this)
    }

    private fun createLauncherButtons() {
        createExitButton()
        createInfoButton()
        createUpdateButton()
    }

    private fun createButtons() {
        createLauncherButtons()
    }

    private fun startLauncher() {
        createButtons()

        if (configUpdater.isPendingAppInstall()) {
            // Here we go after completing the user confirmed app installation
            configUpdater.repeatDownloadApps()
        } else if (!checkPermissions(true)) {
            // Permissions are requested inside checkPermissions, so do nothing here
            Log.i(Const.LOG_TAG, "startLauncher: requesting permissions")
        } else if (!settingsHelper!!.isBaseUrlSet() && BuildConfig.REQUEST_SERVER_URL) {
            // For common public version, here's an option to change the server
            createAndShowServerDialog(false, settingsHelper!!.getBaseUrl(), settingsHelper!!.getServerProject())
        } else if (settingsHelper!!.getDeviceId().isEmpty()) {
            Log.d(Const.LOG_TAG, "Device ID is empty")
            Utils.autoGrantPhonePermission(this)
            if (!SystemUtils.autoSetDeviceId(this)) {
                createAndShowEnterDeviceIdDialog(false, null)
            } else {
                // Retry after automatical setting of device ID
                // We shouldn't get looping here because autoSetDeviceId cannot return true if deviceId.length == 0
                startLauncher()
            }
        } else if (!settingsHelper!!.isConfigInitialized()) {
            Log.i(Const.LOG_TAG, "Updating configuration in startLauncher()")
            var userInteraction = true
            val integratedProvisioningFlow = settingsHelper!!.isIntegratedProvisioningFlow()
            if (integratedProvisioningFlow) {
                // InitialSetupActivity just started and this is the first start after
                // the admin integrated provisioning flow, we need to show the process of loading apps
                // Notice the config is not null because it's preloaded in InitialSetupActivity
                settingsHelper!!.setIntegratedProvisioningFlow(false)
            }
            if (settingsHelper!!.getConfig() != null && !integratedProvisioningFlow) {
                // If it's not the first start, let's update in the background, show the content first!
                showContent(settingsHelper!!.getConfig())
                userInteraction = false
            }
            updateConfig(userInteraction)
        } else {
            showContent(settingsHelper!!.getConfig())
        }
    }

    private fun checkAdminMode(): Boolean {
        if (!Utils.checkAdminMode(this)) {
            createAndShowAdministratorDialog()
            return false
        }
        return true
    }

    private fun needRequestUsageStats(): Boolean {
        val config = SettingsHelper.getInstance(this).getConfig()
        if (config == null) {
            // The app hasn't been properly provisioned because
            // config should be initialized in a setup activity.
            // So we request permissions anyway.
            return true
        }
        // Usage stats is only required to detect unwanted apps when permissive mode is off
        return !config.isPermissive()
    }

    // Access to usage statistics is required in the Pro-version only
    private fun checkUsageStatistics(): Boolean {
        if (!ProUtils.checkUsageStatistics(this)) {
            if (SystemUtils.autoSetUsageStatsPermission(this, packageName)) {
                // Permission auto granted, but we double check
                if (ProUtils.checkUsageStatistics(this)) {
                    return true
                }
            }
            createAndShowHistorySettingsDialog()
            return false
        }
        return true
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private fun checkManageStorage(): Boolean {
        if (!Environment.isExternalStorageManager()) {
            if (SystemUtils.autoSetStoragePermission(this, packageName)) {
                // Permission auto granted, but we double check
                if (Environment.isExternalStorageManager()) {
                    return true
                }
            }
            createAndShowManageStorageDialog()
            return false
        }
        return true
    }

    private fun needRequestOverlay(): Boolean {
        val config = SettingsHelper.getInstance(this).getConfig()
        if (config == null) {
            // The app hasn't been properly provisioned because
            // config should be initialized in a setup activity.
            // So we request permissions anyway.
            return true
        }
        if (!config.isPermissive()) {
            // Overlay window is required to block unwanted apps
            return true
        }
        return false
    }

    private fun checkAlarmWindow(): Boolean {
        if (ProUtils.isPro() && !Utils.canDrawOverlays(this)) {
            if (SystemUtils.autoSetOverlayPermission(this, packageName)) {
                // Permission auto granted, but we double check
                if (Utils.canDrawOverlays(this)) {
                    return true
                }
            }
            createAndShowOverlaySettingsDialog()
            return false
        } else {
            return true
        }
    }

    private fun checkMiuiPermissions(screen: Int): Boolean {
        // Permissions to open popup from background first appears in MIUI 11 (Android 9)
        // Also a workaround against https://qa.h-mdm.com/3119/
        if (Utils.isMiui(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_ENABLE_SETTINGS))
            createAndShowMiuiPermissionsDialog(screen)
            // It is not known how to check this setting programmatically, so return true
            return true
        }
        return false
    }

    private fun checkUnknownSources(): Boolean {
        if (!Utils.canInstallPackages(this)) {
            createAndShowUnknownSourcesDialog()
            return false
        } else {
            return true
        }
    }

    private fun overlayLockScreenParams(): WindowManager.LayoutParams {
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.type = Utils.OverlayWindowType()
        layoutParams.gravity = Gravity.RIGHT
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.format = PixelFormat.TRANSPARENT

        return layoutParams
    }

    private fun createApplicationNotAllowedScreen() {
        if (applicationNotAllowed != null) {
            return
        }
        val manager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        applicationNotAllowed = LayoutInflater.from(this).inflate(R.layout.layout_application_not_allowed, null)
        applicationNotAllowed!!.findViewById<View>(R.id.layout_application_not_allowed_continue).setOnClickListener {
            applicationNotAllowed!!.visibility = View.GONE
        }
        applicationNotAllowed!!.findViewById<View>(R.id.layout_application_not_allowed_admin).setOnClickListener {
            applicationNotAllowed!!.visibility = View.GONE
            createAndShowEnterPasswordDialog()
        }
        val tvPackageId = applicationNotAllowed!!.findViewById<TextView>(R.id.package_id)
        tvPackageId.setOnClickListener {
            try {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Package ID", tvPackageId.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@MainActivity, R.string.package_id_copied, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        applicationNotAllowed!!.visibility = View.GONE

        try {
            manager.addView(applicationNotAllowed, overlayLockScreenParams())
        } catch (e: Exception) {
            // No permission to show overlays; let's try to add view to main view
            try {
                val root = findViewById<RelativeLayout>(R.id.activity_main)
                root.addView(applicationNotAllowed)
            } catch (e1: Exception) {
                e1.printStackTrace()
            }
        }
    }

    private fun createLockScreen() {
        if (lockScreen != null) {
            return
        }

        val manager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Reuse existing "Application not allowed" screen but hide buttons
        lockScreen = LayoutInflater.from(this).inflate(R.layout.layout_application_not_allowed, null)
        lockScreen!!.findViewById<View>(R.id.layout_application_not_allowed_continue).visibility = View.GONE
        lockScreen!!.findViewById<View>(R.id.layout_application_not_allowed_admin).visibility = View.GONE
        lockScreen!!.findViewById<View>(R.id.package_id).visibility = View.GONE
        lockScreen!!.findViewById<View>(R.id.message2).visibility = View.GONE
        val textView = lockScreen!!.findViewById<TextView>(R.id.message)
        textView.text = getString(R.string.device_locked, SettingsHelper.getInstance(this).getDeviceId())

        lockScreen!!.visibility = View.GONE

        try {
            manager.addView(lockScreen, overlayLockScreenParams())
        } catch (e: Exception) {
            // No permission to show overlays; let's try to add view to main view
            try {
                val root = findViewById<RelativeLayout>(R.id.activity_main)
                root.addView(lockScreen)
            } catch (e1: Exception) {
                e1.printStackTrace()
            }
        }
    }

    private fun isDarkBackground(): Boolean {
        try {
            val config = settingsHelper!!.getConfig()
            if (config.getBackgroundColor() != null) {
                val color = Color.parseColor(config.getBackgroundColor())
                return !Utils.isLightColor(color)
            }
        } catch (e: Exception) {
            RemoteLogger.log(this, Const.LOG_WARN, "Failed to parse background color: ${e.message}")
        }
        return true
    }

    private fun createManageButton(imageResource: Int, imageResourceBlack: Int, offset: Int): ImageView {
        val layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutParams.addRule(RelativeLayout.CENTER_VERTICAL)
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)

        var offsetRight = 0
        if (settingsHelper != null && settingsHelper!!.getConfig() != null &&
            settingsHelper!!.getConfig().getLockStatusBar() != null && settingsHelper!!.getConfig().getLockStatusBar()) {
            // If we lock the right bar, let's shift buttons to avoid overlapping
            offsetRight = resources.getDimensionPixelOffset(R.dimen.prevent_applications_list_width)
        }

        val view = RelativeLayout(this)
        // Offset is multiplied by 2 because the view is centered. Yeah I know its an Induism)
        view.setPadding(0, offset * 2, offsetRight, 0)
        view.layoutParams = layoutParams

        val manageButton = ImageView(this)
        manageButton.setImageResource(if (isDarkBackground()) imageResource else imageResourceBlack)
        view.addView(manageButton)

        selectedManageButtonBorder.setColor(0) // transparent background
        selectedManageButtonBorder.setStroke(2, if (isDarkBackground()) 0xa0ffffff.toInt() else 0xa0000000.toInt()) // white or black border with some transparency
        manageButton.setOnFocusChangeListener { v, hasFocus ->
            v.background = if (hasFocus) selectedManageButtonBorder else null
        }

        try {
            val root = findViewById<RelativeLayout>(R.id.activity_main)
            root.addView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return manageButton
    }

    private fun createExitButton() {
        if (exitView != null) {
            return
        }
        exitView = createManageButton(R.drawable.ic_vpn_key_opaque_24dp, R.drawable.ic_vpn_key_black_24dp, 0)
        exitView!!.setOnClickListener { view ->
            if (view.hasFocus()) {
                // 6 subsequent taps within 3 secs open the hidden password view
                val now = System.currentTimeMillis()
                if (exitFirstTapTime < now - 3000) {
                    exitFirstTapTime = now
                    exitTapCount = 1
                } else {
                    exitTapCount++
                    if (exitTapCount >= 6) {
                        exitFirstTapTime = 0
                        exitTapCount = 0
                        createAndShowEnterPasswordDialog()
                    }
                }
            }
        }
        exitView!!.setOnLongClickListener(this)
    }

    private fun createInfoButton() {
        if (infoView != null) {
            return
        }
        infoView = createManageButton(
            R.drawable.ic_info_opaque_24dp,
            R.drawable.ic_info_black_24dp,
            resources.getDimensionPixelOffset(R.dimen.info_icon_margin)
        )
        infoView!!.setOnClickListener(this)
    }

    private fun createUpdateButton() {
        if (updateView != null) {
            return
        }
        updateView = createManageButton(
            R.drawable.ic_system_update_opaque_24dp,
            R.drawable.ic_system_update_black_24dp,
            (2.05f * resources.getDimensionPixelOffset(R.dimen.info_icon_margin)).toInt()
        )
        updateView!!.setOnClickListener(this)
    }

    // The userInteraction flag denotes whether the config has been updated from the UI or in the background
    // If this flag is set to true, network error dialog is displayed, and app update schedule is ignored
    private fun updateConfig(userInteraction: Boolean) {
        needSendDeviceInfoAfterReconfigure = true
        needRedrawContentAfterReconfigure = true
        if (!orientationLocked && !BuildConfig.DISABLE_ORIENTATION_LOCK) {
            lockOrientation()
            orientationLocked = true
        }
        configUpdater.updateConfig(this, this, userInteraction)
    }

    // Workaround against crash "App is in background" on Android 9: this is an Android OS bug
    // https://stackoverflow.com/questions/52013545/android-9-0-not-allowed-to-start-service-app-is-in-background-after-onresume
    private fun startLocationServiceWithRetry() {
        try {
            startLocationService()
        } catch (e: Exception) {
            // Android OS bug!!!
            e.printStackTrace()

            // Repeat an attempt to start service after one second
            handler.postDelayed({
                try {
                    startLocationService()
                } catch (e2: Exception) {
                    // Still failed, now give up!
                    e2.printStackTrace()
                }
            }, 1000)
        }
    }

    private fun startLocationService() {
        val config = settingsHelper!!.getConfig()
        val intent = Intent(this, LocationService::class.java)
        intent.action = config.getRequestUpdates() ?: LocationService.ACTION_STOP
        startService(intent)
    }

    override fun onConfigUpdateStart() {
        binding.setMessage(getString(R.string.main_activity_update_config))
    }

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
        // Do not show the reset button if the launcher is installed by scanning a QR code
        // Only show the reset button on manual setup at first start (when config is not yet loaded)
        createAndShowNetworkErrorDialog(
            settingsHelper!!.getBaseUrl(),
            settingsHelper!!.getServerProject(),
            errorText,
            settingsHelper!!.getConfig() == null && !settingsHelper!!.isQrProvisioning(),
            settingsHelper!!.getConfig() == null || settingsHelper!!.getConfig().isShowWifi()
        )
    }

    override fun onConfigLoaded() {
        applyEarlyPolicies(settingsHelper!!.getConfig())
    }

    override fun onPoliciesUpdated() {
        startLocationServiceWithRetry()
    }

    override fun onFileDownloading(remoteFile: RemoteFile) {
        handler.post {
            binding.setMessage(getString(R.string.main_file_downloading) + " " + remoteFile.getPath())
            binding.setDownloading(true)
        }
    }

    override fun onDownloadProgress(progress: Int, total: Long, current: Long) {
        handler.post {
            binding.progress.max = 100
            binding.progress.progress = progress

            binding.setFileLength(total)
            binding.setDownloadedLength(current)
        }
    }

    override fun onFileDownloadError(remoteFile: RemoteFile) {
        if (!isContentShown()) {
            downloadingFile = true
            createAndShowFileNotDownloadedDialog(remoteFile.getUrl())
            binding.setDownloading(false)
        } else {
            configUpdater.skipDownloadFiles()
        }
    }

    override fun onFileInstallError(remoteFile: RemoteFile) {
        if (!isContentShown()) {
            try {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(getString(R.string.file_create_error) + " " + remoteFile.getPath())
                    .setPositiveButton(R.string.dialog_administrator_mode_continue) { dialog, which ->
                        configUpdater.skipDownloadFiles()
                    }
                    .create()
                    .show()
            } catch (e: Exception) {
                // Activity closed before showing a dialog, just ignore this exception
                e.printStackTrace()
            }
        } else {
            configUpdater.skipDownloadFiles()
        }
    }

    override fun onAppUpdateStart() {
        binding.setMessage(getString(R.string.main_activity_applications_update))
        settingsHelper!!.setConfigInitialized(true)
    }

    override fun onAppInstalling(application: Application) {
        handler.post {
            binding.setMessage(getString(R.string.main_app_installing) + " " + application.getName())
            binding.setDownloading(false)
        }
    }

    override fun onAppDownloadError(application: Application) {
        if (!isContentShown()) {
            downloadingFile = false
            createAndShowFileNotDownloadedDialog(application.getName())
            binding.setDownloading(false)
        } else {
            configUpdater.skipDownloadApps()
        }
    }

    override fun onAppInstallError(packageName: String) {
        handler.post {
            if (!isContentShown()) {
                try {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(getString(R.string.install_error) + " " + packageName)
                        .setPositiveButton(R.string.dialog_administrator_mode_continue) { dialog, which ->
                            configUpdater.repeatDownloadApps()
                        }
                        .create()
                        .show()
                } catch (e: Exception) {
                    // Activity closed before showing a dialog, just ignore this exception
                    e.printStackTrace()
                }
            } else {
                configUpdater.repeatDownloadApps()
            }
        }
    }

    override fun onAppInstallComplete(packageName: String) {
    }

    override fun onConfigUpdateComplete() {
        val preferences = applicationContext.getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE)
        val deviceAdminLog = PreferenceLogger.getLogString(preferences)
        if (deviceAdminLog != null && deviceAdminLog != "") {
            RemoteLogger.log(this, Const.LOG_DEBUG, deviceAdminLog)
            PreferenceLogger.clearLogString(preferences)
        }
        Log.i(Const.LOG_TAG, "Showing content from setActions()")
        settingsHelper!!.refreshConfig(this)  // Avoid NPE in showContent()
        showContent(settingsHelper!!.getConfig())
    }

    override fun onAllAppInstallComplete() {
        Log.i(Const.LOG_TAG, "Refreshing content - new apps installed")
        settingsHelper!!.refreshConfig(this)  // Avoid NPE in showContent()
        handler.post {
            showContent(settingsHelper!!.getConfig())
        }
    }

    override fun onAppDownloading(application: Application) {
        handler.post {
            binding.setMessage(getString(R.string.main_app_downloading) + " " + application.getName())
            binding.setDownloading(true)
        }
    }

    override fun onAppRemoving(application: Application) {
        handler.post {
            binding.setMessage(getString(R.string.main_app_removing) + " " + application.getName())
            binding.setDownloading(false)
        }
    }

    private fun applyEarlyPolicies(config: ServerConfig): Boolean {
        Initializer.applyEarlyNonInteractivePolicies(this, config)
        return true
    }

    // Network policies are applied after getting all applications
    // These are interactive policies so can't be used when in background mode
    private fun applyLatePolicies(config: ServerConfig): Boolean {
        // To delay opening the settings activity
        var dialogWillShow = false

        if (config.getGps() != null) {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager?
            if (lm != null) {
                val enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                if (config.getGps() && !enabled) {
                    dialogWillShow = true
                    // System settings dialog should return result so we could re-initialize location service
                    postDelayedSystemSettingDialog(
                        getString(R.string.message_turn_on_gps),
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                        REQUEST_CODE_GPS_STATE_CHANGE
                    )
                } else if (!config.getGps() && enabled) {
                    dialogWillShow = true
                    postDelayedSystemSettingDialog(
                        getString(R.string.message_turn_off_gps),
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                        REQUEST_CODE_GPS_STATE_CHANGE
                    )
                }
            }
        }

        if (config.getMobileData() != null) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (cm != null && !dialogWillShow) {
                try {
                    val enabled = Utils.isMobileDataEnabled(this)
                    val mobileDataSettingsIntent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                    // Mobile data are turned on/off in the status bar! No settings (as the user can go back in settings and do something nasty)
                    if (config.getMobileData() && !enabled) {
                        postDelayedSystemSettingDialog(getString(R.string.message_turn_on_mobile_data), null)
                    } else if (!config.getMobileData() && enabled) {
                        postDelayedSystemSettingDialog(getString(R.string.message_turn_off_mobile_data), null)
                    }
                } catch (e: Exception) {
                    RemoteLogger.log(this, Const.LOG_WARN, "Failed to read mobile data state via private API: ${e.message}")
                }
            }
        }

        if (!Utils.setPasswordMode(config.getPasswordMode(), this)) {
            val updatePasswordIntent = Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)
            // Different Android versions/builds use different activities to setup password
            // So we have to enable temporary access to settings here (and only here!)
            postDelayedSystemSettingDialog(getString(R.string.message_set_password), updatePasswordIntent, null, true)
        }
        return true
    }

    private fun isContentShown(): Boolean {
        if (::binding.isInitialized) {
            return binding.getShowContent() != null && binding.getShowContent()
        }
        return false
    }

    private fun showContent(config: ServerConfig) {
        if (!applyEarlyPolicies(config)) {
            // Here we go when the settings window is opened;
            // Next time we're here after we returned from the Android settings through onResume()
            return
        }
        applyLatePolicies(config)

        sendDeviceInfoAfterReconfigure()
        scheduleDeviceInfoSending()
        scheduleInstalledAppsRun()

        if (config.getLock() != null && config.getLock()) {
            showLockScreen()
            return
        } else {
            hideLockScreen()
        }

        // Run default launcher option
        if (config.getRunDefaultLauncher() != null && config.getRunDefaultLauncher() &&
            !packageName.equals(Utils.getDefaultLauncher(this)) && !Utils.isLauncherIntent(intent)) {
            openDefaultLauncher()
            return
        }

        if (orientationLocked && !BuildConfig.DISABLE_ORIENTATION_LOCK) {
            Utils.setOrientation(this, config)
            orientationLocked = false
        }

        // TODO: Somehow binding is null here which causes a crash. Not sure why this could happen.
        if (config.getBackgroundColor() != null) {
            try {
                binding.activityMainContentWrapper.setBackgroundColor(Color.parseColor(config.getBackgroundColor()))
            } catch (e: Exception) {
                // Invalid color
                e.printStackTrace()
                binding.activityMainContentWrapper.setBackgroundColor(resources.getColor(R.color.defaultBackground))
            }
        } else {
            binding.activityMainContentWrapper.setBackgroundColor(resources.getColor(R.color.defaultBackground))
        }
        updateTitle(config)

        statusBarUpdater.updateControlsState(config.isDisplayStatus(), isDarkBackground())

        if (mainAppListAdapter == null || needRedrawContentAfterReconfigure) {
            needRedrawContentAfterReconfigure = false

            if (config.getBackgroundImageUrl() != null && config.getBackgroundImageUrl().isNotEmpty()) {
                if (picasso == null) {
                    // Initialize it once because otherwise it doesn't work offline
                    val builder = Picasso.Builder(this)
                    if (BuildConfig.TRUST_ANY_CERTIFICATE) {
                        builder.downloader(OkHttp3Downloader(UnsafeOkHttpClient.getUnsafeOkHttpClient()))
                    } else {
                        // Add signature to all requests to protect against unauthorized API calls
                        // For TRUST_ANY_CERTIFICATE, we won't add signatures because it's unsafe anyway
                        // and is just a workaround to use Base MDM on the LAN
                        val clientWithSignature = OkHttpClient.Builder()
                            .cache(Cache(File(application.cacheDir, "image_cache"), 1000000L))
                            .addInterceptor { chain ->
                                val requestBuilder = chain.request().newBuilder()
                                val signature = InstallUtils.getRequestSignature(chain.request().url().toString())
                                if (signature != null) {
                                    requestBuilder.addHeader("X-Request-Signature", signature)
                                }
                                chain.proceed(requestBuilder.build())
                            }
                            .build()
                        builder.downloader(OkHttp3Downloader(clientWithSignature))
                    }
                    builder.listener(object : Picasso.Listener {
                        override fun onImageLoadFailed(picasso: Picasso, uri: Uri, exception: Exception) {
                            // On fault, get the background image from the cache
                            // This is a workaround against a bug in Picasso: it doesn't display cached images by default!
                            picasso.load(config.getBackgroundImageUrl())
                                .networkPolicy(NetworkPolicy.OFFLINE)
                                .fit()
                                .centerCrop()
                                .into(binding.activityMainBackground)
                        }
                    })
                    picasso = builder.build()
                }

                picasso!!.load(config.getBackgroundImageUrl())
                    // fit and centerCrop is a workaround against a crash on too large images on some devices
                    .fit()
                    .centerCrop()
                    .into(binding.activityMainBackground)

            } else {
                binding.activityMainBackground.setImageDrawable(null)
            }

            val display = windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)

            val width = size.x
            val itemWidth = resources.getDimensionPixelSize(R.dimen.app_list_item_size)

            spanCount = (width * 1.0f / itemWidth).toInt()
            mainAppListAdapter = MainAppListAdapter(this, this, this)
            mainAppListAdapter!!.setSpanCount(spanCount)

            binding.activityMainContent.layoutManager = GridLayoutManager(this, spanCount)
            binding.activityMainContent.adapter = mainAppListAdapter
            mainAppListAdapter!!.notifyDataSetChanged()

            val bottomAppCount = AppShortcutManager.getInstance().getInstalledAppCount(this, true)
            if (bottomAppCount > 0) {
                bottomAppListAdapter = BottomAppListAdapter(this, this, this)
                bottomAppListAdapter!!.setSpanCount(spanCount)

                binding.activityBottomLayout.visibility = View.VISIBLE
                binding.activityBottomLine.layoutManager = GridLayoutManager(this,
                    if (bottomAppCount < spanCount) bottomAppCount else spanCount)
                binding.activityBottomLine.adapter = bottomAppListAdapter
                bottomAppListAdapter!!.notifyDataSetChanged()
            } else {
                bottomAppListAdapter = null
                binding.activityBottomLayout.visibility = View.GONE
            }
        }
        binding.loading.visibility = View.GONE
        binding.setShowContent(true)
        // We can now sleep, uh
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showLockScreen() {
        if (lockScreen == null) {
            createLockScreen()
            if (lockScreen == null) {
                // Why cannot we create the lock screen? Give up and return
                // The locked device will show the launcher, but still cannot run any application
                return
            }
        }
        val lockAdminMessage = settingsHelper!!.getConfig().getLockMessage()
        var lockMessage = getString(R.string.device_locked, SettingsHelper.getInstance(this).getDeviceId())
        if (lockAdminMessage != null) {
            lockMessage += " $lockAdminMessage"
        }
        val textView = lockScreen!!.findViewById<TextView>(R.id.message)
        textView.text = lockMessage
        lockScreen!!.visibility = View.VISIBLE
    }

    private fun hideLockScreen() {
        if (lockScreen != null && lockScreen!!.visibility == View.VISIBLE) {
            lockScreen!!.visibility = View.GONE
        }
    }

    private fun notifyPolicyViolation(cause: Int) {
        when (cause) {
            Const.GPS_ON_REQUIRED -> postDelayedSystemSettingDialog(
                getString(R.string.message_turn_on_gps),
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                REQUEST_CODE_GPS_STATE_CHANGE
            )
            Const.GPS_OFF_REQUIRED -> postDelayedSystemSettingDialog(
                getString(R.string.message_turn_off_gps),
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                REQUEST_CODE_GPS_STATE_CHANGE
            )
            Const.MOBILE_DATA_ON_REQUIRED -> createAndShowSystemSettingDialog(
                getString(R.string.message_turn_on_mobile_data), null, 0
            )
            Const.MOBILE_DATA_OFF_REQUIRED -> createAndShowSystemSettingDialog(
                getString(R.string.message_turn_off_mobile_data), null, 0
            )
        }
    }

    // Run default launcher (Base MDM) as if the user clicked Home button
    private fun openDefaultLauncher() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    // If we updated the configuration, let's send the final state to the server
    private fun sendDeviceInfoAfterReconfigure() {
        if (needSendDeviceInfoAfterReconfigure) {
            needSendDeviceInfoAfterReconfigure = false
            val sendDeviceInfoTask = SendDeviceInfoTask(this)
            val deviceInfo = DeviceInfoProvider.getDeviceInfo(this, true, true)
            sendDeviceInfoTask.execute(deviceInfo)
        }
    }

    private fun scheduleDeviceInfoSending() {
        if (sendDeviceInfoScheduled) {
            return
        }
        sendDeviceInfoScheduled = true
        SendDeviceInfoWorker.scheduleDeviceInfoSending(this)
    }

    private fun scheduleInstalledAppsRun() {
        val applicationsForRun = configUpdater.getApplicationsForRun()

        if (applicationsForRun.size == 0) {
            return
        }
        var pause = PAUSE_BETWEEN_AUTORUNS_SEC
        while (applicationsForRun.size > 0) {
            val application = applicationsForRun[0]
            applicationsForRun.removeAt(0)
            handler.postDelayed({
                val launchIntent = packageManager.getLaunchIntentForPackage(application.getPkg())
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }, (pause * 1000).toLong())
            pause += PAUSE_BETWEEN_AUTORUNS_SEC
        }
    }

    private fun updateTitle(config: ServerConfig) {
        val titleType = config.getTitle()
        if (titleType != null) {
            if (titleType == ServerConfig.TITLE_NONE) {
                binding.activityMainTitle.visibility = View.GONE
                return
            }
            if (config.getTextColor() != null) {
                try {
                    binding.activityMainTitle.setTextColor(Color.parseColor(settingsHelper!!.getConfig().getTextColor()))
                } catch (e: Exception) {
                    // Invalid color
                    e.printStackTrace()
                }
            }
            binding.activityMainTitle.visibility = View.VISIBLE
            var imei = DeviceInfoProvider.getImei(this) ?: ""
            var serial = DeviceInfoProvider.getSerialNumber() ?: ""
            var ip = SettingsHelper.getInstance(this).getExternalIp() ?: ""
            val titleText = titleType
                .replace(ServerConfig.TITLE_DEVICE_ID, SettingsHelper.getInstance(this).getDeviceId())
                .replace(ServerConfig.TITLE_DESCRIPTION, config.getDescription() ?: "")
                .replace(ServerConfig.TITLE_CUSTOM1, config.getCustom1() ?: "")
                .replace(ServerConfig.TITLE_CUSTOM2, config.getCustom2() ?: "")
                .replace(ServerConfig.TITLE_CUSTOM3, config.getCustom3() ?: "")
                .replace(ServerConfig.TITLE_IMEI, imei)
                .replace(ServerConfig.TITLE_SERIAL, serial)
                .replace(ServerConfig.TITLE_EXTERNAL_IP, ip)
                .replace("\\n", "\n")
            binding.activityMainTitle.text = titleText
        } else {
            binding.activityMainTitle.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        settingsHelper?.setMainActivityRunning(false)

        val manager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (applicationNotAllowed != null) {
            try {
                manager.removeView(applicationNotAllowed)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (statusBarView != null) {
            try {
                manager.removeView(statusBarView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (rightToolbarView != null) {
            try {
                manager.removeView(rightToolbarView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (exitView != null) {
            try {
                manager.removeView(exitView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (infoView != null) {
            try {
                manager.removeView(infoView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (updateView != null) {
            try {
                manager.removeView(updateView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (networkCallback != null) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            cm?.unregisterNetworkCallback(networkCallback!!)
        }

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
            unregisterReceiver(stateChangeReceiver)
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()

        isBackground = true

        statusBarUpdater.stopUpdating()

        dismissDialog(fileNotDownloadedDialog)
        dismissDialog(enterServerDialog)
        dismissDialog(enterDeviceIdDialog)
        dismissDialog(networkErrorDialog)
        dismissDialog(enterPasswordDialog)
        dismissDialog(historySettingsDialog)
        dismissDialog(unknownSourcesDialog)
        dismissDialog(overlaySettingsDialog)
        dismissDialog(administratorModeDialog)
        dismissDialog(deviceInfoDialog)
        dismissDialog(accessibilityServiceDialog)
        dismissDialog(systemSettingsDialog)
        dismissDialog(permissionsDialog)

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_SHOW_LAUNCHER))
    }

    private fun createAndShowAdministratorDialog() {
        dismissDialog(administratorModeDialog)
        administratorModeDialog = Dialog(this)
        dialogAdministratorModeBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_administrator_mode,
            null,
            false
        )
        dialogAdministratorModeBinding!!.hint.text =
            getString(R.string.dialog_administrator_mode_message, getString(R.string.white_app_name))
        administratorModeDialog!!.setCancelable(false)
        administratorModeDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)

        administratorModeDialog!!.setContentView(dialogAdministratorModeBinding!!.root)
        administratorModeDialog!!.show()
    }

    fun skipAdminMode(view: View) {
        dismissDialog(administratorModeDialog)

        RemoteLogger.log(this, Const.LOG_INFO, "Manually skipped the device admin permissions setup")
        preferences!!.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_OFF).commit()

        checkAndStartLauncher()
    }

    fun setAdminMode(view: View) {
        dismissDialog(administratorModeDialog)
        // Use a proxy activity because of an Android bug (see comment to AdminModeRequestActivity!)
        startActivity(Intent(this@MainActivity, AdminModeRequestActivity::class.java))
    }

    private fun createAndShowFileNotDownloadedDialog(fileName: String) {
        dismissDialog(fileNotDownloadedDialog)
        fileNotDownloadedDialog = Dialog(this)
        dialogFileDownloadingFailedBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_file_downloading_failed,
            null,
            false
        )
        val errorTextResource = if (downloadingFile) R.string.main_file_downloading_error else R.string.main_app_downloading_error
        dialogFileDownloadingFailedBinding!!.title.text = getString(errorTextResource) + " " + fileName
        fileNotDownloadedDialog!!.setCancelable(false)
        fileNotDownloadedDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)

        fileNotDownloadedDialog!!.setContentView(dialogFileDownloadingFailedBinding!!.root)
        try {
            fileNotDownloadedDialog!!.show()
        } catch (e: Exception) {
            // BadTokenException ignored
        }
    }

    fun repeatDownloadClicked(view: View) {
        dismissDialog(fileNotDownloadedDialog)
        if (downloadingFile) {
            configUpdater.repeatDownloadFiles()
        } else {
            configUpdater.repeatDownloadApps()
        }
    }

    fun confirmDownloadFailureClicked(view: View) {
        dismissDialog(fileNotDownloadedDialog)

        if (downloadingFile) {
            configUpdater.skipDownloadFiles()
        } else {
            configUpdater.skipDownloadApps()
        }
    }

    private fun createAndShowHistorySettingsDialog() {
        dismissDialog(historySettingsDialog)
        historySettingsDialog = Dialog(this)
        dialogHistorySettingsBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_history_settings,
            null,
            false
        )
        dialogHistorySettingsBinding!!.hint.text =
            getString(R.string.dialog_history_settings_title, getString(R.string.white_app_name))
        historySettingsDialog!!.setCancelable(false)
        historySettingsDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)

        historySettingsDialog!!.setContentView(dialogHistorySettingsBinding!!.root)
        historySettingsDialog!!.show()
    }

    fun historyWithoutPermission(view: View) {
        dismissDialog(historySettingsDialog)

        preferences!!.edit().putInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    fun continueHistory(view: View) {
        dismissDialog(historySettingsDialog)

        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun createAndShowManageStorageDialog() {
        dismissDialog(manageStorageDialog)
        manageStorageDialog = Dialog(this)
        dialogManageStorageBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_manage_storage,
            null,
            false
        )
        manageStorageDialog!!.setCancelable(false)
        manageStorageDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)

        manageStorageDialog!!.setContentView(dialogManageStorageBinding!!.root)
        manageStorageDialog!!.show()
    }

    fun storageWithoutPermission(view: View) {
        dismissDialog(manageStorageDialog)

        preferences!!.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    fun continueStorage(view: View) {
        dismissDialog(manageStorageDialog)
        try {
            val intent = Intent()
            intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivity(intent)
            } catch (e1: Exception) {
                Toast.makeText(this, R.string.manage_storage_not_supported, Toast.LENGTH_LONG).show()
                preferences!!.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_OFF).commit()
                checkAndStartLauncher()
            }
        }
    }

    private fun createAndShowOverlaySettingsDialog() {
        dismissDialog(overlaySettingsDialog)
        overlaySettingsDialog = Dialog(this)
        dialogOverlaySettingsBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_overlay_settings,
            null,
            false
        )
        dialogOverlaySettingsBinding!!.hint.text =
            getString(R.string.dialog_overlay_settings_title, getString(R.string.white_app_name))
        overlaySettingsDialog!!.setCancelable(false)
        overlaySettingsDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)

        overlaySettingsDialog!!.setContentView(dialogOverlaySettingsBinding!!.root)
        overlaySettingsDialog!!.show()
    }

    fun overlayWithoutPermission(view: View) {
        dismissDialog(overlaySettingsDialog)

        preferences!!.edit().putInt(Const.PREFERENCES_OVERLAY, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    fun continueOverlay(view: View) {
        dismissDialog(overlaySettingsDialog)

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            startActivityForResult(intent, 1001)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.overlays_not_supported, Toast.LENGTH_LONG).show()
            overlayWithoutPermission(view)
        }
    }

    fun saveDeviceId(view: View) {
        val deviceId = enterDeviceIdDialogBinding.deviceId.text.toString()
        if (deviceId == "") {
            return
        } else {
            settingsHelper!!.setDeviceId(deviceId)
            enterDeviceIdDialogBinding.setError(false)

            dismissDialog(enterDeviceIdDialog)

            if (checkPermissions(true)) {
                Log.i(Const.LOG_TAG, "saveDeviceId(): calling updateConfig()")
                updateConfig(true)
            }
        }
    }

    fun saveServerUrl(view: View) {
        if (saveServerUrlBase()) {
            ServerServiceKeeper.resetServices()
            checkAndStartLauncher()
        }
    }

    fun networkErrorRepeatClicked(view: View) {
        dismissDialog(networkErrorDialog)

        Log.i(Const.LOG_TAG, "networkErrorRepeatClicked(): calling updateConfig()")
        updateConfig(true)
    }

    fun networkErrorResetClicked(view: View) {
        dismissDialog(networkErrorDialog)

        Log.i(Const.LOG_TAG, "networkErrorResetClicked(): calling updateConfig()")
        settingsHelper!!.setDeviceId("")
        settingsHelper!!.setBaseUrl("")
        settingsHelper!!.setSecondaryBaseUrl("")
        settingsHelper!!.setServerProject("")
        createAndShowServerDialog(false, settingsHelper!!.getBaseUrl(), settingsHelper!!.getServerProject())
    }

    fun networkErrorWifiClicked(view: View) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_ENABLE_SETTINGS))
        handler.postDelayed({
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }, 500)
    }

    fun networkErrorCancelClicked(view: View) {
        dismissDialog(networkErrorDialog)

        if (configFault) {
            Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, quit")
            Toast.makeText(this, getString(R.string.critical_server_failure,
                getString(R.string.white_app_name)), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Log.i(Const.LOG_TAG, "networkErrorCancelClicked()")
        if (settingsHelper!!.getConfig() != null) {
            showContent(settingsHelper!!.getConfig())
            configUpdater.skipConfigLoad()
        } else {
            Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, retrying")
            Toast.makeText(this, R.string.empty_configuration, Toast.LENGTH_LONG).show()
            configFault = true
            updateConfig(false)
        }
    }

    fun networkErrorDetailsClicked(view: View) {
        ErrorDetailsActivity.display(this, networkErrorDetails, false)
    }

    private fun checkPermissions(startSettings: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        // If the user didn't grant permissions, let him know and do not request until he confirms he want to retry
        if (permissionsDialog != null && permissionsDialog!!.isShowing) {
            return false
        }

        if (Utils.isDeviceOwner(this)) {
            if (settingsHelper!!.getConfig() != null &&
                (ServerConfig.APP_PERMISSIONS_ASK_ALL.equals(settingsHelper!!.getConfig().getAppPermissions()) ||
                ServerConfig.APP_PERMISSIONS_ASK_LOCATION.equals(settingsHelper!!.getConfig().getAppPermissions()))) {
                // Even in device owner mode, if "Ask for location" is requested by the admin,
                // let's ask permissions (so do nothing here, fall through)
            } else {
                // Do not request permissions if we're the device owner
                // They are added automatically
                return true
            }
        }

        if (preferences!!.getInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

                if (startSettings) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        requestPermissions(arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_PHONE_STATE
                        ), PERMISSIONS_REQUEST)
                    } else {
                        requestPermissions(arrayOf(
                            Manifest.permission.READ_PHONE_STATE
                        ), PERMISSIONS_REQUEST)
                    }
                }
                return false
            } else {
                return true
            }
        } else {
            return checkLocationPermissions(startSettings)
        }
    }

    // Location permissions request on Android 10 and above is rather tricky (shame on Google for their stupid logic!!!)
    // So it's implemented in a separate method
    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun checkLocationPermissions(startSettings: Boolean): Boolean {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

            if (startSettings) {
                var activeModeLocation = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        activeModeLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
                    } catch (e: Exception) {
                        // On some older models:
                        // java.lang.IllegalArgumentException
                        // Unknown permission: android.permission.ACCESS_BACKGROUND_LOCATION
                        // Update: since there's the Android version check, we should never be here!
                        e.printStackTrace()
                    }
                }

                if (activeModeLocation) {
                    // The following flow happened
                    // The user has enabled locations, but when the app prompted for the background location,
                    // the user clicked "Locations only in active mode".
                    // In this case, requestPermissions won't show dialog any more!
                    // So we need to open the general permissions dialog
                    // Let's confirm with the user once again, then display the settings sheet
                    try {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_ENABLE_SETTINGS))
                        AlertDialog.Builder(this@MainActivity)
                            .setMessage(getString(R.string.background_location, getString(R.string.white_app_name)))
                            .setPositiveButton(R.string.background_location_continue) { dialog, which ->
                                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null)))
                            }
                            .setNegativeButton(R.string.location_disable) { dialog, which ->
                                preferences!!.edit().putInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_ON).commit()
                                // Continue the main flow!
                                startLauncher()
                            }
                            .create()
                            .show()
                    } catch (e: Exception) {
                        // Activity closed before showing a dialog, just ignore this exception
                        e.printStackTrace()
                    }
                } else {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        requestPermissions(arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            Manifest.permission.READ_PHONE_STATE
                        ), PERMISSIONS_REQUEST)
                    } else {
                        requestPermissions(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
// This location can't be requested here: the dialog fails to show when we use SDK 30+
// https://developer.android.com/develop/sensors-and-location/location/permissions#request-location-access-runtime
//                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            Manifest.permission.READ_PHONE_STATE
                        ), PERMISSIONS_REQUEST)
                    }
                }
            }
            return false
        } else {
            return true
        }
    }

    private fun createAndShowEnterPasswordDialog() {
        dismissDialog(enterPasswordDialog)
        enterPasswordDialog = Dialog(this)
        dialogEnterPasswordBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_enter_password,
            null,
            false
        )
        enterPasswordDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        enterPasswordDialog!!.setCancelable(false)

        enterPasswordDialog!!.setContentView(dialogEnterPasswordBinding!!.root)
        dialogEnterPasswordBinding!!.setLoading(false)
        try {
            enterPasswordDialog!!.show()
        } catch (e: Exception) {
            // Sometimes here we get a Fatal Exception: android.view.WindowManager$BadTokenException
            // Unable to add window -- token android.os.BinderProxy@f307de for displayid = 0 is not valid; is your activity running?
            Toast.makeText(applicationContext, R.string.internal_error, Toast.LENGTH_LONG).show()
        }
    }

    fun closeEnterPasswordDialog(view: View) {
        dismissDialog(enterPasswordDialog)
    }

    fun checkAdministratorPassword(view: View) {
        dialogEnterPasswordBinding!!.setLoading(true)
        GetServerConfigTask(this).execute { result ->
            dialogEnterPasswordBinding!!.setLoading(false)

            var masterPassword = CryptoHelper.getMD5String("12345678")
            if (settingsHelper!!.getConfig() != null && settingsHelper!!.getConfig().getPassword() != null) {
                masterPassword = settingsHelper!!.getConfig().getPassword()
            }

            if (CryptoHelper.getMD5String(dialogEnterPasswordBinding!!.password.text.toString())
                    .equals(masterPassword)) {
                dismissDialog(enterPasswordDialog)
                dialogEnterPasswordBinding!!.setError(false)
                openAdminPanel()
            } else {
                dialogEnterPasswordBinding!!.setError(true)
            }
        }
    }

    private fun openAdminPanel() {
        RemoteLogger.log(this@MainActivity, Const.LOG_INFO, "Administrator panel opened")
        startActivity(Intent(this@MainActivity, AdminActivity::class.java))
    }

    private fun createAndShowUnknownSourcesDialog() {
        dismissDialog(unknownSourcesDialog)
        unknownSourcesDialog = Dialog(this)
        dialogUnknownSourcesBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_unknown_sources,
            null,
            false
        )
        unknownSourcesDialog!!.setCancelable(false)
        unknownSourcesDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)

        unknownSourcesDialog!!.setContentView(dialogUnknownSourcesBinding!!.root)
        unknownSourcesDialog!!.show()
    }

    fun continueUnknownSources(view: View) {
        dismissDialog(unknownSourcesDialog)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
        } else {
            // In Android Oreo and above, permission to install packages are set per each app
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")))
        }
    }

    private fun createAndShowMiuiPermissionsDialog(screen: Int) {
        dismissDialog(miuiPermissionsDialog)
        miuiPermissionsDialog = Dialog(this)
        dialogMiuiPermissionsBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_miui_permissions,
            null,
            false
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

    fun continueMiuiPermissions(view: View) {
        val titleText = dialogMiuiPermissionsBinding!!.title.text.toString()
        dismissDialog(miuiPermissionsDialog)

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_ENABLE_SETTINGS))
        val intent: Intent
        if (titleText == getString(R.string.dialog_miui_permissions_title)) {
            intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
        } else if (titleText == getString(R.string.dialog_miui_developer_title)) {
            intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
        } else {
            // if (titleText == getString(R.string.dialog_miui_optimization_title))
            intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBackPressed() {}

    override fun onAppChoose(resolveInfo: AppInfo) {
    }

    override fun switchAppListAdapter(adapter: BaseAppListAdapter, direction: Int): Boolean {
        if (adapter == mainAppListAdapter && bottomAppListAdapter != null &&
            (direction == Const.DIRECTION_RIGHT || direction == Const.DIRECTION_DOWN)) {
            bottomAppListAdapter!!.setFocused(true)
            return true
        } else if (adapter == bottomAppListAdapter &&
            (direction == Const.DIRECTION_LEFT || direction == Const.DIRECTION_UP)) {
            mainAppListAdapter!!.setFocused(true)
            return true
        }
        return false
    }

    override fun onLongClick(v: View): Boolean {
        createAndShowEnterPasswordDialog()
        return true
    }

    override fun onClick(v: View) {
        if (v == infoView) {
            createAndShowInfoDialog()
        } else if (v == updateView) {
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

    private fun postDelayedSystemSettingDialog(message: String, settingsIntent: Intent?) {
        postDelayedSystemSettingDialog(message, settingsIntent, null)
    }

    private fun postDelayedSystemSettingDialog(message: String, settingsIntent: Intent?, requestCode: Int?) {
        postDelayedSystemSettingDialog(message, settingsIntent, requestCode, false)
    }

    private fun postDelayedSystemSettingDialog(
        message: String,
        settingsIntent: Intent?,
        requestCode: Int?,
        forceEnableSettings: Boolean
    ) {
        if (settingsIntent != null) {
            // If settings are controlled by usage stats, safe settings are allowed, so we need to enable settings in accessibility mode only
            // Accessibility mode is only enabled when usage stats is off
            if (preferences!!.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON || forceEnableSettings) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_ENABLE_SETTINGS))
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Const.ACTION_STOP_CONTROL))
        }
        // Delayed start prevents the race of ENABLE_SETTINGS handle and tapping "Next" button
        handler.postDelayed({
            createAndShowSystemSettingDialog(message, settingsIntent, requestCode)
        }, 5000)
    }

    private fun createAndShowSystemSettingDialog(message: String, settingsIntent: Intent?, requestCode: Int?) {
        dismissDialog(systemSettingsDialog)
        systemSettingsDialog = Dialog(this)
        dialogSystemSettingsBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.dialog_system_settings,
            null,
            false
        )
        systemSettingsDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        systemSettingsDialog!!.setCancelable(false)

        systemSettingsDialog!!.setContentView(dialogSystemSettingsBinding!!.root)

        dialogSystemSettingsBinding!!.setMessage(message)

        // Since we need to send Intent to the listener, here we don't use "event" attribute in XML resource as everywhere else
        systemSettingsDialog!!.findViewById<View>(R.id.continueButton).setOnClickListener {
            dismissDialog(systemSettingsDialog)
            if (settingsIntent == null) {
                return@setOnClickListener
            }
            // Enable settings once again, because the dialog may be shown more than 3 minutes
            // This is not necessary: the problem is resolved by clicking "Continue" in a popup window
            try {
                startActivityOptionalResult(settingsIntent, requestCode)
            } catch (e: Exception) {
                // Open settings by default
                startActivityOptionalResult(Intent(android.provider.Settings.ACTION_SETTINGS), requestCode)
            }
        }

        try {
            systemSettingsDialog!!.show()
        } catch (e: Exception) {
            // BadTokenException: activity closed before dialog is shown
            RemoteLogger.log(this, Const.LOG_WARN, "Failed to open a popup system dialog! ${e.message}")
            e.printStackTrace()
            systemSettingsDialog = null
        }
    }

    private fun startActivityOptionalResult(intent: Intent, requestCode: Int?) {
        if (requestCode != null) {
            startActivityForResult(intent, requestCode)
        } else {
            startActivity(intent)
        }
    }

    // The following algorithm of launcher restart works in EMUI:
    // Run EMUI_LAUNCHER_RESTARTER activity once and send the old version number to it.
    // The restarter application will check the launcher version each second, and restart it
    // when it is changed.
    private fun startLauncherRestarter() {
        // Sending an intent before updating, otherwise the launcher may be terminated at any time
        val intent = packageManager.getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID)
        if (intent == null) {
            Log.i("LauncherRestarter", "No restarter app, please add it in the config!")
            return
        }
        intent.putExtra(Const.LAUNCHER_RESTARTER_OLD_VERSION, BuildConfig.VERSION_NAME)
        startActivity(intent)
        Log.i("LauncherRestarter", "Calling launcher restarter from the launcher")
    }

    // Create a new file from the template file
    // (replace DEVICE_NUMBER, IMEI, CUSTOM* by their values)
    @Throws(IOException::class)
    private fun createFileFromTemplate(srcFile: File, dstFile: File, deviceId: String, config: ServerConfig) {
        // We are supposed to process only small text files
        // So here we are reading the whole file, replacing variables, and save the content
        // It is not optimal for large files - it would be better to replace in a stream (how?)
        var content = FileUtils.readFileToString(srcFile)
        content = content.replace("DEVICE_NUMBER", deviceId)
            .replace("CUSTOM1", config.getCustom1() ?: "")
            .replace("CUSTOM2", config.getCustom2() ?: "")
            .replace("CUSTOM3", config.getCustom3() ?: "")
        FileUtils.writeStringToFile(dstFile, content)
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                REQ_BT_PERMS
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQ_BT_PERMS
            )
        }
    }

    // Example when clicking "Start beacon" button
    private fun startBeaconWithPermissionCheck() {
        Log.d(TAG, "startBeaconWithPermissionCheck()")
        if (!hasBlePermissions()) {
            requestBlePermissions()
        } else {
            // Now safe to start advertising
            IBeaconAdvertiser.start(this)
            Log.d(TAG, "Started")
        }
    }
}
