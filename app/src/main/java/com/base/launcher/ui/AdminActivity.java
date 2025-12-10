/*
 * Base MDM: Open Source Android MDM Software
 * https://thebase.vn
 *
 * Copyright (C) 2025 The Base (https://thebase.vn)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.base.launcher.ui;

import static java.lang.Integer.parseInt;

import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.base.launcher.BuildConfig;
import com.base.launcher.Const;
import com.base.launcher.R;
import com.base.launcher.databinding.ActivityAdminBinding;
import com.base.launcher.helper.ConfigUpdater;
import com.base.launcher.helper.SettingsHelper;
import com.base.launcher.json.ServerConfig;
import com.base.launcher.pro.ProUtils;
import com.base.launcher.server.ServerServiceKeeper;
import com.base.launcher.util.AppInfo;
import com.base.launcher.util.LegacyUtils;
import com.base.launcher.util.PushNotificationMqttWrapper;
import com.base.launcher.util.RemoteLogger;
import com.base.launcher.util.Utils;

public class AdminActivity extends BaseActivity {

    private static final String KEY_APP_INFO = "info";
    private SettingsHelper settingsHelper;
    private ConfigUpdater configUpdater;

    @Nullable
    public static AppInfo getAppInfo(Intent intent){
        if (intent == null){
            return null;
        }
        return intent.getParcelableExtra(KEY_APP_INFO);
    }

    ActivityAdminBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_admin);
        binding.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        binding.toolbar.setTitle(ProUtils.getAppName(this));
        binding.toolbar.setSubtitle(ProUtils.getCopyright(this));

        // If QR code doesn't contain "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED":true
        // the system launcher is turned off, so it's not possible to exit and we must hide the exit button
        // Currently the QR code contains this parameter, so the button is always visible
        //binding.systemLauncherButton.setVisibility(Utils.isDeviceOwner(this) ? View.GONE : View.VISIBLE);

        if ( Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ) {
            binding.rebootButton.setVisibility(View.GONE);
        }

        settingsHelper = SettingsHelper.getInstance( this );
        configUpdater = new ConfigUpdater(); // TODO: should review this later
        binding.deviceId.setText(settingsHelper.getDeviceId());
        binding.deviceId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createAndShowInfoDialog();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    public void changeDeviceId(View view) {
        dismissDialog(enterDeviceIdDialog);
        createAndShowEnterDeviceIdDialog(false, settingsHelper.getDeviceId());
    }

    public void changeServerUrl(View view) {
        dismissDialog(enterServerDialog);
        createAndShowServerDialog(false, settingsHelper.getBaseUrl(), settingsHelper.getServerProject());
    }

    public void allowSettings(View view) {
        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_ENABLE_SETTINGS ) );
        Toast.makeText(this, R.string.settings_allowed, Toast.LENGTH_LONG).show();
        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        //finish();
    }

    public void clearRestrictions(View view) {
        String restrictions =
                UserManager.DISALLOW_SAFE_BOOT + "," +
                UserManager.DISALLOW_USB_FILE_TRANSFER + "," +
                UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA + "," +
                UserManager.DISALLOW_CONFIG_BRIGHTNESS + "," +
                UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT + "," +
                UserManager.DISALLOW_ADJUST_VOLUME;
        if (settingsHelper.getConfig() != null && settingsHelper.getConfig().getRestrictions() != null) {
            restrictions = "," + settingsHelper.getConfig().getRestrictions();
        }
        Utils.unlockUserRestrictions(this, restrictions);
        Utils.disableScreenshots(false, this);
        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_PERMISSIVE_MODE ) );
        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_STOP_CONTROL ) );
        Toast.makeText(this, R.string.permissive_mode_enabled, Toast.LENGTH_LONG).show();
        //finish();
    }
    @Override
    protected void updateSettingsFromQr(String qrcode) {
        super.updateSettingsFromQr(qrcode);
        dismissDialog(enterServerDialog);
        dismissDialog(enterDeviceIdDialog);
        binding.deviceId.setText(settingsHelper.getDeviceId());
    }

    public void saveServerUrl(View view ) {
        if (saveServerUrlBase()) {
            ServerServiceKeeper.resetServices();
            String pushOptions = null;
            if (settingsHelper != null && settingsHelper.getConfig() != null) {
                pushOptions = settingsHelper.getConfig().getPushOptions();
            }
            if (BuildConfig.ENABLE_PUSH && pushOptions != null && (pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_WORKER)
                    || pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_ALARM))) {
                PushNotificationMqttWrapper.getInstance().disconnect(this);
            }
            updateConfig(view);
        }
    }

    public void saveDeviceId(View view ) {
        String deviceId = enterDeviceIdDialogBinding.deviceId.getText().toString();
        if ( "".equals( deviceId ) ) {
            return;
        } else {
            settingsHelper.setDeviceId( deviceId );
            enterDeviceIdDialogBinding.setError( false );

            dismissDialog(enterDeviceIdDialog);

            Log.i(Const.LOG_TAG, "saveDeviceId(): calling updateConfig()");
            updateConfig(view);
        }
    }

    public void updateConfig( View view ) {
        LocalBroadcastManager.getInstance( this ).
                sendBroadcast( new Intent( Const.ACTION_UPDATE_CONFIGURATION ) );
        finish();
    }

    public void resetPermissions(View view) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
        SharedPreferences preferences = getSharedPreferences( Const.PREFERENCES, MODE_PRIVATE );
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(Const.PREFERENCES_UNKNOWN_SOURCES);
        editor.remove(Const.PREFERENCES_ADMINISTRATOR);
        editor.remove(Const.PREFERENCES_ACCESSIBILITY_SERVICE);
        editor.remove(Const.PREFERENCES_OVERLAY);
        editor.remove(Const.PREFERENCES_USAGE_STATISTICS);
        editor.remove(Const.PREFERENCES_DEVICE_OWNER);
        editor.remove(Const.PREFERENCES_MIUI_PERMISSIONS);
        editor.remove(Const.PREFERENCES_MIUI_OPTIMIZATION);
        editor.remove(Const.PREFERENCES_DEVICE_OWNER);
        editor.commit();
        RemoteLogger.log(this, Const.LOG_INFO, "Reset saved permissions state, will be refreshed at next start");
        Toast.makeText(this, R.string.permissions_reset_hint, Toast.LENGTH_LONG).show();
    }


    public void resetNetworkPolicy(View view) {
        ServerConfig config = settingsHelper.getConfig();
        if (config != null) {
            config.setWifi(null);
            config.setMobileData(null);
            settingsHelper.updateConfig(config);
        }
        RemoteLogger.log(this, Const.LOG_INFO, "Network policies are cleared");
        Toast.makeText(this, R.string.admin_reset_network_hint, Toast.LENGTH_LONG).show();
    }

    public void reboot(View view) {
        if ( Build.VERSION.SDK_INT > Build.VERSION_CODES.M ) {
            ComponentName deviceAdmin = LegacyUtils.getAdminComponentName(this);
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            try {
                devicePolicyManager.reboot(deviceAdmin);
            } catch (Exception e) {
                Toast.makeText(this, R.string.reboot_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    //========= The Base Code ==========
    public void changeMQTTServerUrl(View view) {
        dismissDialog(enterMqttServerDialog);
        createAndShowMqttServerDialog(false, settingsHelper.getMqttDomain(), settingsHelper.getMqttPort(), settingsHelper.getMqttTls(), settingsHelper.getMqttUsername(), settingsHelper.getMqttPassword());
    }

    public void connectMQTT(View view ) {
        // TODO: handle to save MQTT infomation
        Log.d(Const.LOG_TAG, "setupPushService() called");
        String pushOptions = null;
        int keepaliveTime = Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC;
        settingsHelper.setMqttUsername(dialogEnterMqttServerBinding.getUserName());
        settingsHelper.setMqttPassword(dialogEnterMqttServerBinding.getPassword());
        settingsHelper.setMqttDomain(dialogEnterMqttServerBinding.getServer());
        settingsHelper.setMqttPort(parseInt(dialogEnterMqttServerBinding.getPort()));
        settingsHelper.setMqttTls(dialogEnterMqttServerBinding.getUseTls());


        if (settingsHelper != null && settingsHelper.getConfig() != null) {
            pushOptions = settingsHelper.getConfig().getPushOptions();
            Integer newKeepaliveTime = settingsHelper.getConfig().getKeepaliveTime();
            if (newKeepaliveTime != null && newKeepaliveTime >= 30) {
                keepaliveTime = newKeepaliveTime;
            }
        }
        Runnable failRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(Const.LOG_TAG, "MQTT is failed to connect");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AdminActivity.this, "MQTT server connected failed", Toast.LENGTH_LONG).show();
                        dialogEnterMqttServerBinding.setError(true);
                        dialogEnterMqttServerBinding.setErrorText( getString(R.string.mqtt_connect_failed));
                    }
                });
            }
        };
        Runnable successRunnable = new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AdminActivity.this, "MQTT server connected", Toast.LENGTH_LONG).show();
                        dismissDialog(enterMqttServerDialog);
                    }
                });
            }
        };
        Toast.makeText(this, "MQTT server is connecting... please wait", Toast.LENGTH_SHORT).show();
        dialogEnterMqttServerBinding.setError(true);
        dialogEnterMqttServerBinding.setErrorText( getString(R.string.mqtt_connecting));
        PushNotificationMqttWrapper pushService = PushNotificationMqttWrapper.getInstance();
        pushService.disconnect(this);
        pushService.connect(this, dialogEnterMqttServerBinding.getServer(),
                parseInt(dialogEnterMqttServerBinding.getPort()),dialogEnterMqttServerBinding.getUseTls(), dialogEnterMqttServerBinding.getUserName(),
                dialogEnterMqttServerBinding.getPassword(), pushOptions, keepaliveTime,
                settingsHelper.getDeviceId(), successRunnable, failRunnable);

    }
    public void saveMqttServerUrl(View view ) {
        // TODO: handle to save MQTT infomation
        settingsHelper.setMqttUsername(dialogEnterMqttServerBinding.getUserName());
        settingsHelper.setMqttPassword(dialogEnterMqttServerBinding.getPassword());
        settingsHelper.setMqttDomain(dialogEnterMqttServerBinding.getServer());
        settingsHelper.setMqttPort(parseInt(dialogEnterMqttServerBinding.getPort()));
        settingsHelper.setMqttTls(dialogEnterMqttServerBinding.getUseTls());
        dialogEnterMqttServerBinding.setError(true);
        dialogEnterMqttServerBinding.setErrorText( getString(R.string.mqtt_save_data));
    }

    protected void createAndShowMqttServerDialog(boolean error, String serverDomain, Integer serverPort, Boolean useTLS, String userName, String password) {
        dismissDialog(enterMqttServerDialog);
        enterMqttServerDialog = new Dialog( this );
        dialogEnterMqttServerBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_enter_mqtt_server,
                null,
                false );
        dialogEnterMqttServerBinding.setError(error);
        enterMqttServerDialog.setCancelable(false);
        enterMqttServerDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        // set View Variables
        dialogEnterMqttServerBinding.setServer(serverDomain);
        dialogEnterMqttServerBinding.setPort(serverPort.toString());
        dialogEnterMqttServerBinding.setUseTls(useTLS);
        dialogEnterMqttServerBinding.setUserName(userName);
        dialogEnterMqttServerBinding.setPassword(password);


        enterMqttServerDialog.setContentView( dialogEnterMqttServerBinding.getRoot() );
        enterMqttServerDialog.setOnShowListener(dialog -> {
            dialogEnterMqttServerBinding.mqttServer.requestFocus();
        });
//        dialogEnterMqttServerBinding.mqttServerPort.setOnFocusChangeListener((view, hasFocus) -> {
//            if (hasFocus) {
//                InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
//                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
//            }
//        });
        enterMqttServerDialog.show();
    }

}
