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

package com.base.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.base.launcher.Const;
import com.base.launcher.util.DeviceInfoProvider;
import com.base.launcher.util.RemoteLogger;

public class SimChangedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // SIM card changed, log the new IMSI and number
        String phoneNumber = null;
        try {
            phoneNumber = DeviceInfoProvider.getPhoneNumber(context);
        } catch (Exception e) {
        }

        String simState = intent.getExtras().getString("ss");

        String message = null;
        if (simState.equals("LOADED")) {
            message = "SIM card loaded";
            if (phoneNumber != null && phoneNumber.length() > 0) {
                message += ". New phone number: " + phoneNumber;
            }
        } else if (simState.equals("ABSENT")) {
            message = "SIM card removed";
        }

        if (message != null) {
            RemoteLogger.log(context, Const.LOG_INFO, message);
        }
    }
}
