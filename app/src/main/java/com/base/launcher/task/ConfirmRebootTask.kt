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

package com.base.launcher.task

import android.content.Context
import com.base.launcher.Const
import com.base.launcher.helper.SettingsHelper
import com.base.launcher.json.DeviceInfo
import com.base.launcher.server.ServerServiceKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfirmRebootTask(private val context: Context) {

    private val settingsHelper = SettingsHelper.getInstance(context)

    @JvmOverloads
    fun execute(deviceInfo: DeviceInfo, onComplete: TaskCallback = TaskCallback {}) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) { doInBackground(deviceInfo) }
            onComplete.onComplete(result)
        }
    }

    private fun doInBackground(deviceInfo: DeviceInfo): Int {
        val serverService = ServerServiceKeeper.getServerServiceInstance(context)
        val secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context)
        var response = try {
            serverService.confirmReboot(
                settingsHelper.serverProject, deviceInfo.deviceId, deviceInfo
            ).execute()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        return try {
            if (response == null) {
                response = secondaryServerService.confirmReboot(
                    settingsHelper.serverProject, deviceInfo.deviceId, deviceInfo
                ).execute()
            }
            if (response!!.isSuccessful) Const.TASK_SUCCESS else Const.TASK_ERROR
        } catch (e: Exception) {
            e.printStackTrace()
            Const.TASK_ERROR
        }
    }
}
