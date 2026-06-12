package com.base.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.base.launcher.Const;
import com.base.launcher.helper.Initializer;
import com.base.launcher.helper.SettingsHelper;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(Const.LOG_TAG, "Got the BOOT_RECEIVER broadcast");

        SettingsHelper settingsHelper = SettingsHelper.getInstance(context.getApplicationContext());
        if (!settingsHelper.isBaseUrlSet()) {
            // We're here before initializing after the factory reset! Let's ignore this call
            return;
        }

        long lastAppStartTime = settingsHelper.getAppStartTime();
        long bootTime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        Log.d(Const.LOG_TAG, "appStartTime=" + lastAppStartTime + ", bootTime=" + bootTime);
        if (lastAppStartTime < bootTime) {
            Log.i(Const.LOG_TAG, "Base MDM wasn't started since boot, start initializing services");
        } else {
            Log.i(Const.LOG_TAG, "Base MDM is already started, ignoring BootReceiver");
            return;
        }

        Initializer.init(context, () -> {
            Initializer.startServicesAndLoadConfig(context);
            SettingsHelper.getInstance(context).setMainActivityRunning(false);
        });
    }
}
