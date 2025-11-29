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

package com.base.launcher.task;

import android.content.Context;
import android.os.AsyncTask;

import com.base.launcher.Const;
import com.base.launcher.helper.SettingsHelper;
import com.base.launcher.json.DeviceInfo;
import com.base.launcher.server.ServerService;
import com.base.launcher.server.ServerServiceKeeper;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class SendDeviceInfoTask extends AsyncTask< DeviceInfo, Integer, Integer > {

    private Context context;
    private SettingsHelper settingsHelper;

    public SendDeviceInfoTask( Context context ) {
        this.context = context;
        this.settingsHelper = SettingsHelper.getInstance( context );
    }

    @Override
    protected Integer doInBackground( DeviceInfo... info ) {
        ServerService serverService = ServerServiceKeeper.getServerServiceInstance(context);
        ServerService secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
        Response< ResponseBody > response = null;

        try {
            response = serverService.sendDevice(settingsHelper.getServerProject(), info[ 0 ]).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (response == null) {
                response = secondaryServerService.sendDevice(settingsHelper.getServerProject(), info[0]).execute();
            }
            if ( response.isSuccessful() ) {
                return Const.TASK_SUCCESS;
            }
        }
        catch ( Exception e ) { e.printStackTrace(); }

        return Const.TASK_ERROR;
    }
}
