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

package com.base.launcher.worker

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.db.DatabaseHelper
import com.base.launcher.db.DownloadTable
import com.base.launcher.helper.ConfigUpdater
import com.base.launcher.helper.SettingsHelper
import com.base.launcher.json.Application
import com.base.launcher.json.PushMessage
import com.base.launcher.kozen.KozenCommandHandler
import com.base.launcher.util.InstallUtils
import com.base.launcher.util.RemoteLogger
import com.base.launcher.util.SystemUtils
import com.base.launcher.util.Utils
import com.kozen.terminalmanager.TerminalManager
import com.kozen.terminalmanager.resource.OnUpdateOTAListener
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object PushNotificationProcessor {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    // Single shared OkHttpClient for OTA downloads
    private val OTA_HTTP_CLIENT: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)   // large file -> long timeout
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    private fun showToast(context: Context, msg: String) {
        handler.post {
            Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun process(message: PushMessage, context: Context) {
        RemoteLogger.log(context, Const.LOG_INFO, "Got Push Message, type ${message.messageType}")
        val kCommand = KozenCommandHandler(context)

        when (message.messageType) {
            PushMessage.TYPE_CONFIG_UPDATED -> {
                // Update local configuration; broadcast happens inside ConfigUpdater after update completes
                ConfigUpdater.notifyConfigUpdate(context)
                return
            }

            PushMessage.TYPE_RUN_APP -> {
                // Run application — do not broadcast this message to other apps
                runApplication(context, message.payloadJSON)
                return
            }

            PushMessage.TYPE_UNINSTALL_APP -> {
                executor.execute {
                    val jsonObject = JSONObject().apply {
                        put("action", "resourceUninstall")
                        put("payload", message.payloadJSON)
                    }
                    RemoteLogger.log(context, Const.LOG_INFO, "jsonObject: $jsonObject")
                    kCommand.execute(jsonObject)
                }
                return
            }

            PushMessage.TYPE_DELETE_FILE -> {
                executor.execute { deleteFile(context, message.payloadJSON) }
                return
            }

            PushMessage.TYPE_DELETE_DIR -> {
                executor.execute { deleteDir(context, message.payloadJSON) }
                return
            }

            PushMessage.TYPE_PURGE_DIR -> {
                executor.execute { purgeDir(context, message.payloadJSON) }
                return
            }

            PushMessage.TYPE_PERMISSIVE_MODE -> {
                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(Intent(Const.ACTION_PERMISSIVE_MODE))
                return
            }

            PushMessage.TYPE_RUN_COMMAND -> {
                executor.execute { runCommand(context, message.payloadJSON) }
                return
            }

            PushMessage.TYPE_REBOOT -> {
                executor.execute { reboot(context) }
                return
            }

            PushMessage.TYPE_ADMIN_PANEL -> {
                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(Intent(Const.ACTION_ADMIN_PANEL))
                return
            }

            PushMessage.TYPE_CLEAR_DOWNLOADS -> {
                executor.execute { clearDownloads(context) }
                return
            }

            PushMessage.TYPE_INTENT -> {
                executor.execute { callIntent(context, message.payloadJSON) }
                return
            }

            PushMessage.TYPE_GRANT_PERMISSIONS -> {
                executor.execute { grantPermissions(context, message.payloadJSON) }
                return
            }

            PushMessage.TYPE_DEVICE_ACTION -> {
                RemoteLogger.log(context, Const.LOG_INFO, "Received TYPE_DEVICE_ACTION push message")
                kCommand.execute(message.payloadJSON)
                return
            }

            PushMessage.TYPE_DEVICE_BROADCAST -> {
                RemoteLogger.log(context, Const.LOG_INFO, "Received TYPE_DEVICE_BROADCAST push message")
                val jsonObject = message.payloadJSON
                sendBroadCast(context, jsonObject.getString("title"), jsonObject.getString("content"))
                return
            }

            PushMessage.TYPE_DEVICE_FACTORY_RESET -> {
                resetFactory(context)
                // Falls through to broadcast below
            }

            PushMessage.TYPE_UPDATEOTA -> {
                RemoteLogger.log(context, Const.LOG_INFO, "Received TYPE_UPDATEOTA push message")
                val jsonObject = message.payloadJSON
                if (jsonObject != null) {
                    jsonObject.put("action", "updateOta")
                    executor.execute {
                        try {
                            val otaUrl = jsonObject.getString("otaUrl")
                            RemoteLogger.log(context, Const.LOG_INFO, "Starting OTA download: $otaUrl")
                            showToast(context, "Starting OTA download...")

                            // 1) BLOCKING download — returns only after the file is fully written
                            val localPath = downloadOtaFileWithOkHttp(context, otaUrl)

                            RemoteLogger.log(context, Const.LOG_INFO, "OTA download finished, path = $localPath")
                            Log.i("OTA", "Downloaded OTA to: $localPath")
                            showToast(context, "OTA Download completed")

                            // Optional safety check
                            val f = File(localPath)
                            if (!f.exists() || f.length() == 0L) {
                                RemoteLogger.log(
                                    context, Const.LOG_ERROR,
                                    "OTA file missing or empty after download: $localPath"
                                )
                                return@execute
                            }

                            // 2) Call Kozen OTA API — only after download completed
                            showToast(context, "Updating firmware...")
                            kCommand.execute(jsonObject)
                            val rm = TerminalManager.INSTANCE.resourceManager
                            val otaListener = object : OnUpdateOTAListener {
                                override fun onSuccess() {
                                    showToast(context, "OTA update successful")
                                    RemoteLogger.log(context, Const.LOG_INFO, "OTA updated successfully")
                                }

                                override fun onError(msg: String, code: Int) {
                                    showToast(context, "OTA failed: $msg (code $code)")
                                    RemoteLogger.log(
                                        context, Const.LOG_ERROR,
                                        "OTA error code=$code, detail=$msg"
                                    )
                                }
                            }

                            RemoteLogger.log(
                                context, Const.LOG_INFO,
                                "Calling updateOTAWithListener with path: $localPath"
                            )
                            val ret = rm.updateOTAWithListener(localPath, otaListener)
                            RemoteLogger.log(
                                context, Const.LOG_INFO,
                                "updateOTAWithListener returned: $ret"
                            )
                        } catch (e: Exception) {
                            showToast(context, "OTA process failed: ${e.message}")
                            Log.e("OTA", "Download or OTA update failed", e)
                            RemoteLogger.log(
                                context, Const.LOG_ERROR,
                                "Download or OTA update failed: ${e.message}"
                            )
                        }
                    }
                    return
                }
            }

            else -> {
                val textObj = message.payloadJSON.toString()
                RemoteLogger.log(context, Const.LOG_INFO, "Else flow result: $textObj")
                Toast.makeText(context, textObj, Toast.LENGTH_LONG).show()
            }
        }

        // Send broadcast to all plugins
        val intent = Intent(Const.INTENT_PUSH_NOTIFICATION_PREFIX + message.messageType)
        val jsonObject = message.payloadJSON
        if (jsonObject != null) {
            intent.putExtra(Const.INTENT_PUSH_NOTIFICATION_EXTRA, jsonObject.toString())
        }
        context.sendBroadcast(intent)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun downloadOtaFileWithOkHttp(context: Context, urlString: String): String {
        val fileName = urlString.substringAfterLast('/')
        if (!fileName.endsWith(".zip")) {
            throw IOException("OTA file must be .zip, got: $fileName")
        }

        val outFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        outFile.parentFile?.takeIf { !it.exists() }?.mkdirs()

        val request = Request.Builder()
            .url(urlString)
            .get()
            .build()

        OTA_HTTP_CLIENT.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP code ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength() // can be -1 if unknown
            RemoteLogger.log(
                context, Const.LOG_INFO,
                "Starting file download, size = $contentLength bytes"
            )

            BufferedInputStream(body.byteStream()).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read

                        // Log progress every ~50 MB
                        if (contentLength > 0 && totalRead % (50L * 1024 * 1024) < 8192) {
                            val progress = (100L * totalRead / contentLength).toInt()
                            showToast(context, "Downloading: $progress%")
                            Log.d(
                                "OTA",
                                "Download progress: $progress% (${totalRead / (1024 * 1024)} MB)"
                            )
                        }
                    }
                    output.flush()
                }
            }
        }

        return outFile.absolutePath
    }

    @JvmStatic
    fun sendBroadCast(context: Context, str: String, str2: String) {
        Log.d("BaseMDM", "handlePushMqttMsg:: title= $str content= $str2")
        val intent = Intent("com.xcheng.mdm.action.MQTT_PUSH_MSG").apply {
            putExtra("title", str)
            putExtra("content", str2)
            // Always explicitly set the receiver
            setClassName(
                "com.xcheng.mdm",                             // package
                "com.xcheng.mdm.component.MyMessageReceiver"  // full class path
            )
        }
        context.sendBroadcast(intent)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun runApplication(context: Context, payload: JSONObject?) {
        if (payload == null) return
        try {
            val pkg = payload.getString("pkg")
            val action = payload.optString("action", null)
            val extras = payload.optJSONObject("extra")
            val data = payload.optString("data", null)
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return

            if (action != null) launchIntent.setAction(action)
            if (data != null) {
                try { launchIntent.data = Uri.parse(data) } catch (e: Exception) {
                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to parse intent data URI: ${e.message}")
                }
            }
            extras?.let { applyExtrasToIntent(launchIntent, it) }

            // These flags preserve the app activity stack (standard launcher pattern)
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            RemoteLogger.log(context, Const.LOG_WARN, "Failed to run application: ${e.message}")
        }
    }

    @Suppress("unused") // kept for potential future use / symmetry with original
    private fun uninstallApplication(context: Context, payload: JSONObject?) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Uninstall request failed: no package specified")
            return
        }
        if (!Utils.isDeviceOwner(context)) {
            RemoteLogger.log(context, Const.LOG_WARN, "Uninstall request failed: no device owner")
            return
        }
        try {
            val pkg = payload.getString("pkg")
            InstallUtils.silentUninstallApplication(context, pkg)
            RemoteLogger.log(context, Const.LOG_INFO, "Uninstalled application: $pkg")
        } catch (e: Exception) {
            RemoteLogger.log(context, Const.LOG_WARN, "Uninstall request failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun deleteFile(context: Context, payload: JSONObject?) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "File delete failed: no path specified")
            return
        }
        try {
            val path = payload.getString("path")
            File(Environment.getExternalStorageDirectory(), path).delete()
            RemoteLogger.log(context, Const.LOG_INFO, "Deleted file: $path")
        } catch (e: Exception) {
            RemoteLogger.log(context, Const.LOG_WARN, "File delete failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { deleteRecursive(it) }
        }
        fileOrDirectory.delete()
    }

    private fun deleteDir(context: Context, payload: JSONObject?) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory delete failed: no path specified")
            return
        }
        try {
            val path = payload.getString("path")
            deleteRecursive(File(Environment.getExternalStorageDirectory(), path))
            RemoteLogger.log(context, Const.LOG_INFO, "Deleted directory: $path")
        } catch (e: Exception) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory delete failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun purgeDir(context: Context, payload: JSONObject?) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory purge failed: no path specified")
            return
        }
        try {
            val path = payload.getString("path")
            val dir = File(Environment.getExternalStorageDirectory(), path)
            if (!dir.isDirectory) {
                RemoteLogger.log(context, Const.LOG_WARN, "Directory purge failed: not a directory: $path")
                return
            }
            val recursive = payload.optString("recursive")
            dir.listFiles()?.forEach { child ->
                if (recursive != "1") {
                    if (!child.isDirectory) child.delete()
                } else {
                    deleteRecursive(child)
                }
            }
            RemoteLogger.log(context, Const.LOG_INFO, "Purged directory: $path")
        } catch (e: Exception) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory purge failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun runCommand(context: Context, payload: JSONObject?) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Command failed: no command specified")
            return
        }
        try {
            val command = payload.getString("command")
            Log.d(Const.LOG_TAG, "Executing a command: $command")
            var result = SystemUtils.executeShellCommand(command, true)
            var msg = "Executed a command: $command"
            if (result.isNotEmpty()) {
                if (result.length > 200) result = result.substring(0, 200) + "..."
                msg += " Result: $result"
            }
            RemoteLogger.log(context, Const.LOG_DEBUG, msg)
        } catch (e: Exception) {
            RemoteLogger.log(context, Const.LOG_WARN, "Command failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun reboot(context: Context) {
        RemoteLogger.log(context, Const.LOG_WARN, "Rebooting by a Push message")
        if (Utils.checkAdminMode(context)) {
            if (!Utils.reboot(context)) {
                RemoteLogger.log(
                    context, Const.LOG_WARN,
                    "Reboot by native failed, change to use EDC provider API"
                )
                val kCommand = KozenCommandHandler(context)
                val jsonObject = JSONObject().apply {
                    put("action", "deviceReboot")
                    put("payload", "{}")
                }
                kCommand.execute(jsonObject)
            }
        } else {
            RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed: no permissions")
        }
    }

    private fun resetFactory(context: Context) {
        RemoteLogger.log(context, Const.LOG_WARN, "Resetting Factory by a Push message")
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.wipeData(0)
    }

    private fun clearDownloads(context: Context) {
        RemoteLogger.log(context, Const.LOG_WARN, "Clear download history by a Push message")
        val db = DatabaseHelper.instance(context).writableDatabase
        DownloadTable.selectAll(db).forEach { d ->
            try { File(d.path).delete() } catch (e: Exception) { e.printStackTrace() }
        }
        DownloadTable.deleteAll(db)
    }

    private fun callIntent(context: Context, payload: JSONObject?) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Calling intent failed: no parameters specified")
            return
        }
        try {
            val action = payload.getString("action")
            Log.d(Const.LOG_TAG, "Calling intent: $action")
            val extras = payload.optJSONObject("extra")
            val data = payload.optString("data", null)
            val pkg = payload.optString("pkg", null)
            Log.d(Const.LOG_TAG, "Calling intent: $action $data $pkg $extras")

            val i = Intent()
            if (data != null) {
                try { i.data = Uri.parse(data) } catch (e: Exception) { e.printStackTrace() }
            }
            extras?.let { applyExtrasToIntent(i, it) }
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (pkg != null) {
                i.setClassName(pkg, action)
            } else {
                i.action = action
            }
            context.startActivity(i)
        } catch (e: Exception) {
            RemoteLogger.log(context, Const.LOG_WARN, "Calling intent failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun grantPermissions(context: Context, payload: JSONObject?) {
        if (!Utils.isDeviceOwner(context) && !BuildConfig.SYSTEM_PRIVILEGES) {
            RemoteLogger.log(context, Const.LOG_WARN, "Can't auto grant permissions: no device owner")
        }

        val config = SettingsHelper.getInstance(context).config
        val apps: MutableList<String>

        if (payload != null) {
            apps = mutableListOf()
            val pkgs = payload.optJSONArray("pkg")
            if (pkgs != null) {
                for (i in 0 until pkgs.length()) {
                    pkgs.optString(i)?.let { apps.add(it) }
                }
            } else {
                payload.optString("pkg")?.let { apps.add(it) }
            }
        } else {
            // By default, grant permissions to all packages that have a URL
            apps = config.applications
                .filter { Application.TYPE_APP == it.type && it.url != null && it.pkg != null }
                .map { it.pkg }
                .toMutableList()
        }

        for (app in apps) {
            Utils.autoGrantRequestedPermissions(context, app, config.appPermissions, false)
        }
    }

    /**
     * Shared helper that applies a [JSONObject] of extras onto an [Intent].
     * Handles String, Integer, Float, and Boolean value types.
     */
    private fun applyExtrasToIntent(intent: Intent, extras: JSONObject) {
        val keys = extras.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = extras.get(key)) {
                is String  -> intent.putExtra(key, value)
                is Int     -> intent.putExtra(key, value)
                is Float   -> intent.putExtra(key, value)
                is Boolean -> intent.putExtra(key, value)
            }
        }
    }
}
