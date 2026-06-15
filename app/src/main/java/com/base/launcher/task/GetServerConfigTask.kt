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
import android.os.Build
import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.helper.CryptoHelper
import com.base.launcher.helper.SettingsHelper
import com.base.launcher.json.DeviceEnrollOptions
import com.base.launcher.json.ServerConfig
import com.base.launcher.json.ServerConfigResponse
import com.base.launcher.server.ServerService
import com.base.launcher.server.ServerServiceKeeper
import com.base.launcher.util.PushNotificationMqttWrapper
import com.base.launcher.util.RemoteLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response

class GetServerConfigTask(private val context: Context) {

    private val settingsHelper = SettingsHelper.getInstance(context)

    private lateinit var serverService: ServerService
    private lateinit var secondaryServerService: ServerService

    private var serverHost: String = ""
    private val urlTemplate = "{project}/rest/public/sync/configuration/{number}"
    private var isDeviceNotFound = false
    private val notFoundError = "error.notfound.device"

    var errorText: String? = null
        private set

    @JvmOverloads
    fun execute(onComplete: TaskCallback = TaskCallback {}) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) { doInBackground() }
            onComplete.onComplete(result)
        }
    }

    private fun doInBackground(): Int {
        val enrollOptions: DeviceEnrollOptions? = if (settingsHelper?.config == null) {
            DeviceEnrollOptions().apply {
                customer = settingsHelper?.enrollOptionCustomer
                configuration = settingsHelper?.enrollOptionConfigName
                groups = settingsHelper?.enrollOptionGroup?.toList()
            }
        } else null

        try {
            serverService = ServerServiceKeeper.getServerServiceInstance(context)
            secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context)
        } catch (e: Exception) {
            errorText = "Exception: ${e.message}"
            return Const.TASK_NETWORK_ERROR
        }

        val deviceId = settingsHelper.deviceId
        val signature = try {
            CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + deviceId)
        } catch (e: Exception) {
            ""
        }

        isDeviceNotFound = false
        return try {
            val serverConfig: ServerConfig? = if (enrollOptions == null) {
                if (BuildConfig.CHECK_SIGNATURE) getServerConfigSecure(deviceId, signature)
                else getServerConfigPlain(deviceId, signature)
            } else {
                if (BuildConfig.CHECK_SIGNATURE) enrollSecure(deviceId, enrollOptions, signature)
                else enrollPlain(deviceId, enrollOptions, signature)
            }

            if (serverConfig != null) {
                if (serverConfig.newNumber != null) {
                    RemoteLogger.log(
                        context, Const.LOG_INFO,
                        "Device number changed from ${settingsHelper.deviceId} to ${serverConfig.newNumber}"
                    )
                    settingsHelper.setDeviceId(serverConfig.newNumber)
                    serverConfig.newNumber = null
                    try {
                        PushNotificationMqttWrapper.getInstance().disconnect(context)
                    } catch (_: Exception) {}
                }

                settingsHelper.updateConfig(serverConfig)
                settingsHelper.setDeviceIdUse(null)
                settingsHelper.setEnrollOptionCustomer(null)
                settingsHelper.setEnrollOptionConfigName(null)
                settingsHelper.setEnrollOptionGroup(null)

                Const.TASK_SUCCESS
            } else {
                if (isDeviceNotFound) Const.TASK_ERROR else Const.TASK_NETWORK_ERROR
            }
        } catch (e: Exception) {
            e.printStackTrace()
            buildNetworkErrorText(e.message)
            Const.TASK_NETWORK_ERROR
        }
    }

    private fun getServerConfigPlain(deviceId: String, signature: String): ServerConfig? {
        var response: Response<ServerConfigResponse>? = null
        try {
            serverHost = settingsHelper.baseUrl
            response = serverService.getServerConfig(
                settingsHelper.serverProject, deviceId, signature, Build.CPU_ABI
            ).execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (response == null) {
            serverHost = settingsHelper.secondaryBaseUrl
            response = secondaryServerService.getServerConfig(
                settingsHelper.serverProject, deviceId, signature, Build.CPU_ABI
            ).execute()
        }

        return if (response!!.isSuccessful &&
            Const.STATUS_OK == response.body()?.status &&
            response.body()?.data != null
        ) {
            SettingsHelper.getInstance(context).setExternalIp(response.headers()[Const.HEADER_IP_ADDRESS])
            response.body()!!.data
        } else {
            isDeviceNotFound = response.body() != null && notFoundError == response.body()?.message
            buildTaskErrorText(response)
            null
        }
    }

    private fun getServerConfigSecure(deviceId: String, signature: String): ServerConfig? {
        var response: Response<ResponseBody>? = null
        try {
            serverHost = settingsHelper.baseUrl
            response = serverService.getServerConfigRaw(
                settingsHelper.serverProject, deviceId, signature, Build.CPU_ABI
            ).execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (response == null) {
            serverHost = settingsHelper.secondaryBaseUrl
            response = secondaryServerService.getServerConfigRaw(
                settingsHelper.serverProject, deviceId, signature, Build.CPU_ABI
            ).execute()
        }

        if (!response!!.isSuccessful) {
            buildTaskErrorTextSecure(response, null)
            return null
        }

        val serverResponse: String = response.body()?.string() ?: ""
        val serverConfigResponse = try {
            ObjectMapper().readValue(java.lang.String.valueOf(serverResponse), ServerConfigResponse::class.java)
        } catch (e: Exception) {
            errorText = "Failed to parse JSON"
            Log.e(Const.LOG_TAG, errorText)
            buildTaskErrorTextSecure(response, serverResponse)
            return null
        }

        if (Const.STATUS_OK != serverConfigResponse.status) {
            isDeviceNotFound = notFoundError == serverConfigResponse.message
            buildTaskErrorTextSecure(response, serverResponse)
            return null
        }

        val serverSignature = response.headers()[Const.HEADER_RESPONSE_SIGNATURE]
        if (serverSignature == null) {
            errorText = "Missing ${Const.HEADER_RESPONSE_SIGNATURE} flag, dropping response"
            Log.e(Const.LOG_TAG, errorText!!)
            buildTaskErrorTextSecure(response, serverResponse)
            return null
        }

        val dataMarker = "\"data\":"
        val pos = serverResponse.indexOf(dataMarker)
        if (pos == -1) {
            errorText = "Wrong server response, missing data"
            Log.e(Const.LOG_TAG, "$errorText: $serverResponse")
            buildTaskErrorTextSecure(response, serverResponse)
            return null
        }

        val serverData: String = serverResponse.substring(pos + dataMarker.length, serverResponse.length - 1)
        val calculatedSignature = CryptoHelper.getSHA1String(
            ((BuildConfig.REQUEST_SIGNATURE ?: "") + serverData.replace("\\s".toRegex(), ""))!!
        )
        if (!calculatedSignature.equals(serverSignature, ignoreCase = true)) {
            errorText = "Server signature $serverSignature doesn't match calculated signature $calculatedSignature, dropping response"
            Log.e(Const.LOG_TAG, errorText!!)
            buildTaskErrorTextSecure(response, serverResponse)
            return null
        }

        return ObjectMapper().readValue(java.lang.String.valueOf(serverData), ServerConfig::class.java)
    }

    private fun enrollPlain(
        deviceId: String,
        createOptions: DeviceEnrollOptions,
        signature: String
    ): ServerConfig? {
        var response: Response<ServerConfigResponse>? = null
        try {
            serverHost = settingsHelper.baseUrl
            response = serverService.enrollAndGetServerConfig(
                settingsHelper.serverProject, deviceId, signature, Build.CPU_ABI, createOptions
            ).execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (response == null) {
            serverHost = settingsHelper.secondaryBaseUrl
            response = secondaryServerService.enrollAndGetServerConfig(
                settingsHelper.serverProject, deviceId, signature, Build.CPU_ABI, createOptions
            ).execute()
        }

        return if (response!!.isSuccessful &&
            Const.STATUS_OK == response.body()?.status &&
            response.body()?.data != null
        ) {
            SettingsHelper.getInstance(context).setExternalIp(response.headers()[Const.HEADER_IP_ADDRESS])
            response.body()!!.data
        } else {
            isDeviceNotFound = response.body() != null && notFoundError == response.body()?.message
            buildTaskErrorText(response)
            null
        }
    }

    private fun enrollSecure(
        deviceId: String,
        createOptions: DeviceEnrollOptions,
        signature: String
    ): ServerConfig? {
        var response: Response<ResponseBody>? = null
        try {
            serverHost = settingsHelper.baseUrl
            response = serverService.enrollAndGetServerConfigRaw(
                settingsHelper.serverProject, deviceId, signature, Build.CPU_ABI, createOptions
            ).execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (response == null) {
            serverHost = settingsHelper.secondaryBaseUrl
            response = secondaryServerService.enrollAndGetServerConfigRaw(
                settingsHelper.serverProject, deviceId, signature, Build.CPU_ABI, createOptions
            ).execute()
        }

        if (!response!!.isSuccessful) {
            buildTaskErrorTextSecure(response, null)
            return null
        }

        val serverResponse: String = response.body()?.string() ?: ""
        val serverConfigResponse = try {
            ObjectMapper().readValue(java.lang.String.valueOf(serverResponse), ServerConfigResponse::class.java)
        } catch (e: Exception) {
            errorText = "Failed to parse JSON"
            Log.e(Const.LOG_TAG, errorText)
            buildTaskErrorTextSecure(response, serverResponse)
            return null
        }

        if (Const.STATUS_OK != serverConfigResponse.status) {
            isDeviceNotFound = notFoundError == serverConfigResponse.message
            buildTaskErrorTextSecure(response, serverResponse)
            return null
        }

        val serverSignature = response.headers()[Const.HEADER_RESPONSE_SIGNATURE]
        if (serverSignature == null) {
            errorText = "Missing ${Const.HEADER_RESPONSE_SIGNATURE} flag, dropping response"
            Log.e(Const.LOG_TAG, errorText!!)
            buildTaskErrorTextSecure(response, serverResponse)
        }

        val dataMarker = "\"data\":"
        val pos = serverResponse.indexOf(dataMarker)
        if (pos == -1) {
            errorText = "Wrong server response, missing data"
            Log.e(Const.LOG_TAG, "$errorText: $serverResponse")
            buildTaskErrorTextSecure(response, serverResponse)
            return null
        }

        val serverData: String = serverResponse.substring(pos + dataMarker.length, serverResponse.length - 1)
        val calculatedSignature = CryptoHelper.getSHA1String(
            ((BuildConfig.REQUEST_SIGNATURE ?: "") + serverData.replace("\\s".toRegex(), ""))!!
        )
        if (!calculatedSignature.equals(serverSignature, ignoreCase = true)) {
            errorText = "Server signature $serverSignature doesn't match calculated signature $calculatedSignature, dropping response"
            Log.e(Const.LOG_TAG, errorText!!)
            buildTaskErrorTextSecure(response, serverResponse)
            return null
        }

        return ObjectMapper().readValue(java.lang.String.valueOf(serverData), ServerConfig::class.java)
    }

    private fun buildTaskErrorText(response: Response<ServerConfigResponse>) {
        var message = "HTTP status: ${response.code()}"
        if (response.isSuccessful) {
            message += "\nJSON status: ${response.body()?.status}\nJSON message: ${response.body()?.message}"
        }
        buildNetworkErrorText(message)
    }

    private fun buildTaskErrorTextSecure(response: Response<ResponseBody>, body: String?) {
        val reason = errorText
        var message = "HTTP status: ${response.code()}"
        if (response.isSuccessful) {
            message += "\nBody: $body"
        }
        buildNetworkErrorText(message)
        if (!reason.isNullOrEmpty()) {
            errorText = "$reason\n\n$errorText"
        }
    }

    private fun buildNetworkErrorText(message: String?) {
        val url = serverHost + urlTemplate
            .replace("{project}", settingsHelper.serverProject)
            .replace("{number}", settingsHelper.deviceId)

        errorText = "$url\n\n$message"

        val tag = queryTag(message)
        if (tag != null) {
            errorText += "\n\nError tag: $tag"
        }
    }

    private fun queryTag(message: String?): String? {
        return if (message?.contains("Trust anchor") == true) "trust_anchor" else null
    }
}
