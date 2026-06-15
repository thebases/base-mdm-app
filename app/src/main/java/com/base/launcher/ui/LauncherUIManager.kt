package com.base.launcher.ui

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.R
import com.base.launcher.databinding.ActivityMainBinding
import com.base.launcher.helper.ConfigUpdater
import com.base.launcher.helper.SettingsHelper
import com.base.launcher.json.ServerConfig
import com.base.launcher.pro.ProUtils
import com.base.launcher.ui.custom.StatusBarUpdater
import com.base.launcher.util.DeviceInfoProvider
import com.base.launcher.util.InstallUtils
import com.base.launcher.util.RemoteLogger
import com.base.launcher.util.Utils
import com.jakewharton.picasso.OkHttp3Downloader
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

class LauncherUIManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val settingsHelper: SettingsHelper,
    private val configUpdater: ConfigUpdater,
    private val preferences: SharedPreferences,
    private val handler: Handler,
    private val lockScreenManager: LockScreenManager,
    private val statusBarUpdater: StatusBarUpdater,
    private val onSendDeviceInfoNeeded: () -> Unit,
    private val onScheduleDeviceInfoNeeded: () -> Unit,
    private val onScheduleInstalledApps: () -> Unit,
    private val onPasswordDialogRequested: () -> Unit,
    private val onUpdateConfigRequested: () -> Unit,
    private val onPostDelayedSystemSettingDialog: (String, Intent?, Int?, Boolean) -> Unit
) {
    private val selectedManageButtonBorder = GradientDrawable()
    var exitView: ImageView? = null
        private set
    var infoView: ImageView? = null
        private set
    var updateView: ImageView? = null
        private set
    var exitFirstTapTime: Long = 0
    var exitTapCount = 0
    var orientationLocked = false
    var needRedrawContentAfterReconfigure = false
    private var picasso: Picasso? = null

    var mainAppListAdapter: MainAppListAdapter? = null
        private set
    var bottomAppListAdapter: BottomAppListAdapter? = null
        private set

    fun isDarkBackground(): Boolean {
        return try {
            val config = settingsHelper.config
            if (config?.backgroundColor != null) {
                val color = Color.parseColor(config.backgroundColor)
                !Utils.isLightColor(color)
            } else true
        } catch (e: Exception) {
            RemoteLogger.log(activity, Const.LOG_WARN, "Failed to parse background color: ${e.message}")
            true
        }
    }

    fun createManageButton(imageResource: Int, imageResourceBlack: Int, offset: Int): ImageView {
        val layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.addRule(RelativeLayout.CENTER_VERTICAL)
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)

        var offsetRight = 0
        if (settingsHelper.config?.lockStatusBar == true) {
            offsetRight = activity.resources.getDimensionPixelOffset(R.dimen.prevent_applications_list_width)
        }

        val view = RelativeLayout(activity)
        view.setPadding(0, offset * 2, offsetRight, 0)
        view.layoutParams = layoutParams

        val manageButton = ImageView(activity)
        manageButton.setImageResource(if (isDarkBackground()) imageResource else imageResourceBlack)
        view.addView(manageButton)

        selectedManageButtonBorder.setColor(0)
        selectedManageButtonBorder.setStroke(
            2,
            if (isDarkBackground()) 0xa0ffffff.toInt() else 0xa0000000.toInt()
        )
        manageButton.setOnFocusChangeListener { v, hasFocus ->
            v.background = if (hasFocus) selectedManageButtonBorder else null
        }
        try {
            val root = activity.findViewById<RelativeLayout>(R.id.activity_main)
            root.addView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return manageButton
    }

    fun createExitButton() {
        if (exitView != null) return
        exitView = createManageButton(R.drawable.ic_vpn_key_opaque_24dp, R.drawable.ic_vpn_key_black_24dp, 0)
        exitView!!.setOnClickListener { view ->
            if (view.hasFocus()) {
                val now = System.currentTimeMillis()
                if (exitFirstTapTime < now - 3000) {
                    exitFirstTapTime = now
                    exitTapCount = 1
                } else {
                    exitTapCount++
                    if (exitTapCount >= 6) {
                        exitFirstTapTime = 0
                        exitTapCount = 0
                        onPasswordDialogRequested()
                    }
                }
            }
        }
        exitView!!.setOnLongClickListener {
            onPasswordDialogRequested()
            true
        }
    }

    fun createInfoButton() {
        if (infoView != null) return
        infoView = createManageButton(
            R.drawable.ic_info_opaque_24dp,
            R.drawable.ic_info_black_24dp,
            activity.resources.getDimensionPixelOffset(R.dimen.info_icon_margin)
        )
        infoView!!.setOnClickListener { v ->
            // handled in MainActivity.onClick
            (activity as? View.OnClickListener)?.onClick(v)
        }
    }

    fun createUpdateButton() {
        if (updateView != null) return
        updateView = createManageButton(
            R.drawable.ic_system_update_opaque_24dp,
            R.drawable.ic_system_update_black_24dp,
            (2.05f * activity.resources.getDimensionPixelOffset(R.dimen.info_icon_margin)).toInt()
        )
        updateView!!.setOnClickListener { v ->
            (activity as? View.OnClickListener)?.onClick(v)
        }
    }

    fun createButtons() {
        createExitButton()
        createInfoButton()
        createUpdateButton()
    }

    fun removeManageViews(manager: WindowManager) {
        listOf(exitView, infoView, updateView).forEach { v ->
            if (v != null) try { manager.removeView(v) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun applyLatePolicies(config: ServerConfig): Boolean {
        var dialogWillShow = false

        if (config.gps != null) {
            val lm = activity.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager?
            if (lm != null) {
                val enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                if (config.gps == true && !enabled) {
                    dialogWillShow = true
                    onPostDelayedSystemSettingDialog(
                        activity.getString(R.string.message_turn_on_gps),
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                        1, false  // REQUEST_CODE_GPS_STATE_CHANGE = 1
                    )
                } else if (config.gps == false && enabled) {
                    dialogWillShow = true
                    onPostDelayedSystemSettingDialog(
                        activity.getString(R.string.message_turn_off_gps),
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                        1, false
                    )
                }
            }
        }

        if (config.mobileData != null) {
            val cm = activity.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (cm != null && !dialogWillShow) {
                try {
                    val enabled = Utils.isMobileDataEnabled(activity)
                    if (config.mobileData == true && !enabled) {
                        onPostDelayedSystemSettingDialog(
                            activity.getString(R.string.message_turn_on_mobile_data), null, null, false
                        )
                    } else if (config.mobileData == false && enabled) {
                        onPostDelayedSystemSettingDialog(
                            activity.getString(R.string.message_turn_off_mobile_data), null, null, false
                        )
                    }
                } catch (e: Exception) {
                    RemoteLogger.log(activity, Const.LOG_WARN, "Failed to read mobile data state via private API: ${e.message}")
                }
            }
        }

        if (!Utils.setPasswordMode(config.passwordMode, activity)) {
            val updatePasswordIntent = Intent(android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD)
            onPostDelayedSystemSettingDialog(
                activity.getString(R.string.message_set_password), updatePasswordIntent, null, true
            )
        }
        return true
    }

    fun showContent(config: ServerConfig, applyEarlyPolicies: (ServerConfig) -> Boolean) {
        if (!applyEarlyPolicies(config)) return
        applyLatePolicies(config)

        onSendDeviceInfoNeeded()
        onScheduleDeviceInfoNeeded()
        onScheduleInstalledApps()

        if (config.lock == true) {
            lockScreenManager.showLockScreen()
            return
        } else {
            lockScreenManager.hideLockScreen()
        }

        if (config.runDefaultLauncher == true &&
            !activity.packageName.equals(Utils.getDefaultLauncher(activity)) &&
            !Utils.isLauncherIntent(activity.intent)) {
            openDefaultLauncher()
            return
        }

        if (orientationLocked && !BuildConfig.DISABLE_ORIENTATION_LOCK) {
            Utils.setOrientation(activity, config)
            orientationLocked = false
        }

        if (config.backgroundColor != null) {
            try {
                binding.activityMainContentWrapper.setBackgroundColor(Color.parseColor(config.backgroundColor))
            } catch (e: Exception) {
                e.printStackTrace()
                binding.activityMainContentWrapper.setBackgroundColor(
                    activity.resources.getColor(R.color.defaultBackground)
                )
            }
        } else {
            binding.activityMainContentWrapper.setBackgroundColor(
                activity.resources.getColor(R.color.defaultBackground)
            )
        }
        updateTitle(config)

        statusBarUpdater.updateControlsState(config.isDisplayStatus, isDarkBackground())

        if (mainAppListAdapter == null || needRedrawContentAfterReconfigure) {
            needRedrawContentAfterReconfigure = false

            if (!config.backgroundImageUrl.isNullOrEmpty()) {
                if (picasso == null) {
                    val builder = Picasso.Builder(activity)
                    if (BuildConfig.TRUST_ANY_CERTIFICATE) {
                        builder.downloader(OkHttp3Downloader(com.base.launcher.server.UnsafeOkHttpClient.getUnsafeOkHttpClient()))
                    } else {
                        val clientWithSignature = OkHttpClient.Builder()
                            .cache(Cache(File(activity.application.cacheDir, "image_cache"), 1000000L))
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
                        override fun onImageLoadFailed(picasso: Picasso, uri: android.net.Uri, exception: Exception) {
                            config.backgroundImageUrl?.let {
                                picasso.load(it)
                                    .networkPolicy(NetworkPolicy.OFFLINE)
                                    .fit()
                                    .centerCrop()
                                    .into(binding.activityMainBackground)
                            }
                        }
                    })
                    picasso = builder.build()
                }
                picasso!!.load(config.backgroundImageUrl)
                    .fit()
                    .centerCrop()
                    .into(binding.activityMainBackground)
            } else {
                binding.activityMainBackground.setImageDrawable(null)
            }

            val display = activity.windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)
            val width = size.x
            val itemWidth = activity.resources.getDimensionPixelSize(R.dimen.app_list_item_size)
            val spanCount = (width * 1.0f / itemWidth).toInt()

            mainAppListAdapter = MainAppListAdapter(
                activity,
                activity as BaseAppListAdapter.OnAppChooseListener,
                activity as BaseAppListAdapter.SwitchAdapterListener
            )
            mainAppListAdapter!!.setSpanCount(spanCount)
            binding.activityMainContent.layoutManager = GridLayoutManager(activity, spanCount)
            binding.activityMainContent.adapter = mainAppListAdapter
            mainAppListAdapter!!.notifyDataSetChanged()

            val bottomAppCount = AppShortcutManager.getInstance().getInstalledAppCount(activity, true)
            if (bottomAppCount > 0) {
                bottomAppListAdapter = BottomAppListAdapter(
                    activity,
                    activity as BaseAppListAdapter.OnAppChooseListener,
                    activity as BaseAppListAdapter.SwitchAdapterListener
                )
                bottomAppListAdapter!!.setSpanCount(spanCount)
                binding.activityBottomLayout.visibility = View.VISIBLE
                binding.activityBottomLine.layoutManager = GridLayoutManager(
                    activity,
                    if (bottomAppCount < spanCount) bottomAppCount else spanCount
                )
                binding.activityBottomLine.adapter = bottomAppListAdapter
                bottomAppListAdapter!!.notifyDataSetChanged()
            } else {
                bottomAppListAdapter = null
                binding.activityBottomLayout.visibility = View.GONE
            }
        }
        binding.loading.visibility = View.GONE
        binding.setShowContent(true)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun updateTitle(config: ServerConfig) {
        val titleType = config.title
        if (titleType != null) {
            if (titleType == ServerConfig.TITLE_NONE) {
                binding.activityMainTitle.visibility = View.GONE
                return
            }
            if (config.textColor != null) {
                try {
                    binding.activityMainTitle.setTextColor(
                        Color.parseColor(config.textColor)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            binding.activityMainTitle.visibility = View.VISIBLE
            val imei = DeviceInfoProvider.getImei(activity) ?: ""
            val serial = DeviceInfoProvider.getSerialNumber() ?: ""
            val ip = settingsHelper.getExternalIp() ?: ""
            val titleText = titleType
                .replace(ServerConfig.TITLE_DEVICE_ID, settingsHelper.deviceId)
                .replace(ServerConfig.TITLE_DESCRIPTION, config.description ?: "")
                .replace(ServerConfig.TITLE_CUSTOM1, config.custom1 ?: "")
                .replace(ServerConfig.TITLE_CUSTOM2, config.custom2 ?: "")
                .replace(ServerConfig.TITLE_CUSTOM3, config.custom3 ?: "")
                .replace(ServerConfig.TITLE_IMEI, imei)
                .replace(ServerConfig.TITLE_SERIAL, serial)
                .replace(ServerConfig.TITLE_EXTERNAL_IP, ip)
                .replace("\\n", "\n")
            binding.activityMainTitle.text = titleText
        } else {
            binding.activityMainTitle.visibility = View.GONE
        }
    }

    private fun openDefaultLauncher() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
    }
}
