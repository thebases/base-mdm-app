package com.base.launcher.kozen

import android.content.Context
import android.os.Environment
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.base.launcher.Const
import com.base.launcher.util.RemoteLogger
import com.kozen.terminalmanager.aidl.location.entity.LocationClientOption
import com.kozen.terminalmanager.aidl.network.entity.ApnConfiguration
import com.kozen.terminalmanager.resource.OnUpdateOTAListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes remote Kozen-related commands coming from the MDM server.
 *
 * Input: JSON command object with "type" and "payload".
 * Output: Kozen SDK calls + local result JSON for server acknowledgement.
 */
class KozenCommandHandler(context: Context) {

    private val context: Context = context.applicationContext
    private val terminal: KozenTerminalFacade = KozenTerminalFacade.get()
    private val component: KozenComponentFacade = KozenComponentFacade.get()

    companion object {
        private const val TAG = "KozenCommandHandler"
        private const val OTA_TOAST_INTERVAL_MS = 3000L // 3 seconds.

        private val OTA_HTTP_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)   // large file -> long timeout
            .writeTimeout(10, TimeUnit.MINUTES)
            .build()

        @JvmStatic
        fun showToast(context: Context, msg: String) {
            android.os.Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun downloadOtaFileWithOkHttp(context: Context, urlString: String): String {
            // Extract file name
            val fileName = urlString.substring(urlString.lastIndexOf('/') + 1)
            if (!fileName.endsWith(".zip")) {
                throw IOException("OTA file must be .zip, got: $fileName")
            }

            // Target: /sdcard/Download/<fileName>
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
                    throw IOException("Unexpected HTTP code ${response.code()}")
                }

                val body = response.body() ?: throw IOException("Empty response body")
                val contentLength = body.contentLength() // can be -1 if unknown
                RemoteLogger.log(context, Const.LOG_INFO,
                    "Starting file download, size = $contentLength bytes")

                BufferedInputStream(body.byteStream()).use { input ->
                    FileOutputStream(outFile).use { out ->
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            totalRead += read

                            // Optional debug: log progress every ~50MB
                            if (contentLength > 0 && totalRead % (50L * 1024 * 1024) < 8192) {
                                val progress = (100L * totalRead / contentLength).toInt()
                                showToast(context, "Downloading: $progress%")
                                Log.d("OTA", "Download progress: $progress% (${totalRead / (1024 * 1024)} MB)")
                            }
                        }
                        out.flush()
                    }
                }
            }

            return outFile.absolutePath
        }

        @JvmStatic
        @Throws(IOException::class)
        fun downloadBootLogoWithOkHttp(context: Context, urlString: String): String {
            val fileName = urlString.substring(urlString.lastIndexOf('/') + 1)
            if (fileName.isEmpty()) {
                throw IOException("Invalid boot logo file name from URL: $urlString")
            }

            // Optionally enforce image extensions
            if (!fileName.endsWith(".png") && !fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")) {
                throw IOException("Boot logo must be an image (.png/.jpg), got: $fileName")
            }

            // Target directory: /sdcard/Download/<fileName>
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
                    throw IOException("Unexpected HTTP code ${response.code()}")
                }

                val body = response.body() ?: throw IOException("Empty response body")
                val contentLength = body.contentLength() // may be -1
                RemoteLogger.log(context, Const.LOG_INFO,
                    "Starting boot logo download, size = $contentLength bytes")

                BufferedInputStream(body.byteStream()).use { input ->
                    FileOutputStream(outFile).use { out ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                        out.flush()
                    }
                }

                RemoteLogger.log(context, Const.LOG_INFO,
                    "Boot logo downloaded to: ${outFile.absolutePath}")
            }

            return outFile.absolutePath
        }
    }

    @Throws(JSONException::class)
    fun execute(command: JSONObject): JSONObject {
        val action = command.optString("action", "")
        var payload = command.optJSONObject("data")
        RemoteLogger.log(context, Const.LOG_INFO, "action: $action")

        if (payload == null) {
            payload = JSONObject()
        } else {
            RemoteLogger.log(context, Const.LOG_INFO, "data: ${payload}")
        }

        val result = JSONObject()
        result.put("id", command.optLong("id", -1))
        result.put("action", action)

        try {
            when (action) {
                // ===== Certification module =====
                "certUpdate"   -> result.put("status", handleCertUpdate(payload))
                "certDelete"   -> result.put("status", handleCertDelete(payload))
                "certList"     -> {
                    result.put("data", handleCertList())
                    result.put("status", 0)
                }

                // ===== Device information module =====
                "deviceInfo"   -> {
                    result.put("data", handleDeviceInfoFull())
                    result.put("status", 0)
                }

                // ===== Device module =====
                "deviceSetTime"          -> result.put("status", handleSetTime(payload))
                "deviceSetTimezone"      -> result.put("status", handleSetTimeZone(payload))
                "deviceReboot"           -> {
                    handleReboot()
                    result.put("status", 0)
                }
                "deviceShutdown"         -> {
                    handleShutdown()
                    result.put("status", 0)
                }
                "deviceSetPciReboot"     -> result.put("status", handleSetPCIReboot(payload))
                "deviceCancelPciReboot"  -> result.put("status", handleCancelPCIReboot())
                "deviceSetSilentInstall" -> {
                    handleSetSilentInstall(payload)
                    result.put("status", 0)
                }
                "deviceForcePermission"  -> {
                    handleForcePermission(payload)
                    result.put("status", 0)
                }

                // ===== Location module =====
                "locationOpen"           -> result.put("status", handleLocationOpen(payload))
                "locationSetOption"      -> result.put("status", handleLocationSetOption(payload))
                "locationStartOnce"      -> result.put("status", terminal.startOnceLocation())
                "locationSetGeofence"    -> result.put("status", handleLocationSetGeofence(payload))
                "locationClearGeofence"  -> result.put("status", terminal.removeAllGeoFence())
                "locationBlockAppAdd"    -> result.put("status", handleLocationBlockAppAdd(payload))
                "locationBlockAppRemove" -> result.put("status", handleLocationBlockAppRemove(payload))

                // ===== Network module =====
                "networkAddApn"    -> result.put("status", handleNetworkAddApn(payload))
                "networkEnableApn" -> result.put("status", handleNetworkEnableApn(payload))

                // ===== Resource module =====
                "resourceInstallOrUpdate" -> result.put("status", handleInstallOrUpdate(payload))
                "resourceUninstall"       -> result.put("status", handleUninstall(payload))
                "resourceUpdateOta"       -> result.put("status", handleUpdateOta(payload))
                "resourceUpdateCustomRes" -> result.put("status", handleUpdateCustomRes(payload))

                // ===== Keyboard module =====
                "keyboardStart"    -> result.put("status", handleKeyboardStart())
                "keyboardStop"     -> result.put("status", handleKeyboardStop())
                "keyboardSetSound" -> result.put("status", handleKeyboardSetSound(payload))

                // ===== Secondary screen module =====
                "secondaryShowPic"     -> result.put("status", handleSecondaryShowPic(payload))
                "secondaryShowVideo"   -> result.put("status", handleSecondaryShowVideo(payload))
                "secondaryPower"       -> result.put("status", handleSecondaryPower(payload))
                "secondaryBrightness"  -> result.put("status", handleSecondaryBrightness(payload))
                "secondarySetBootLogo" -> result.put("status", handleSecondarySetBootLogo(payload))

                else -> {
                    result.put("status", -1)
                    result.put("error", "Unknown type: $action")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "execute error for type=$action", t)
            result.put("status", -1)
            result.put("error", t.message)
        }

        return result
    }

    // ----------------- Certification -----------------

    @Throws(JSONException::class)
    private fun handleCertUpdate(payload: JSONObject): Int {
        val certData = payload.getString("certData")
        return terminal.updateAppSignature(certData)
    }

    @Throws(JSONException::class)
    private fun handleCertDelete(payload: JSONObject): Int {
        val certData = payload.getString("certData")
        return terminal.deleteAppSignature(certData)
    }

    @Throws(JSONException::class)
    private fun handleCertList(): JSONArray {
        val arr = JSONArray()
        for (line in terminal.getAppSignatureInfo()) {
            arr.put(line)
        }
        return arr
    }

    // ----------------- Device info -----------------

    @Throws(JSONException::class)
    private fun handleDeviceInfoFull(): JSONObject {
        val data = JSONObject()
        data.put("sdkVersion", terminal.getSdkServiceVersion())
        data.put("serialNo", terminal.getSerialNo())
        data.put("vendorName", terminal.getVendorName())
        data.put("deviceModel", terminal.getDeviceModel())
        data.put("osVersion", terminal.getOsVersion())
        data.put("kernelVersion", terminal.getKernelVersion())
        data.put("mcuVersion", terminal.getMcuVersion())
        data.put("hardwareVersion", terminal.getHardwareVersion())
        data.put("emvKernelVersion", terminal.getEmvKernelVersion())
        data.put("TUSN", terminal.getTUSN())
        data.put("CSN", terminal.getCSN())

        val imsiArr = JSONArray()
        for (imsi in terminal.getImsi()) imsiArr.put(imsi)
        data.put("imsi", imsiArr)

        val imeiArr = JSONArray()
        for (imei in terminal.getImei()) imeiArr.put(imei)
        data.put("imei", imeiArr)

        return data
    }

    // ----------------- Device module -----------------

    @Throws(JSONException::class)
    private fun handleSetTime(payload: JSONObject): Int {
        val ts = payload.getLong("timestamp") // millis
        return terminal.setSystemTime(ts)
    }

    @Throws(JSONException::class)
    private fun handleSetTimeZone(payload: JSONObject): Int {
        val tz = payload.getString("timezone") // e.g. "Asia/Ho_Chi_Minh"
        return terminal.setTimeZone(tz)
    }

    private fun handleReboot() {
        terminal.reboot()
    }

    private fun handleShutdown() {
        terminal.shutdown()
    }

    @Throws(JSONException::class)
    private fun handleSetPCIReboot(payload: JSONObject): Int {
        val millis = payload.getLong("delayMillis")
        return terminal.setPCIReboot(millis)
    }

    private fun handleCancelPCIReboot(): Int {
        return terminal.cancelPCIReboot()
    }

    @Throws(JSONException::class)
    private fun handleSetSilentInstall(payload: JSONObject) {
        val open = payload.getBoolean("open")
        terminal.setSilentInstall(open)
    }

    @Throws(JSONException::class)
    private fun handleForcePermission(payload: JSONObject) {
        val open = payload.getBoolean("open")
        terminal.forcePermission(open)
    }

    // ----------------- Location module -----------------
    // For simplicity: open() with no params; you can extend to key/type if needed.

    private fun handleLocationOpen(payload: JSONObject): Int {
        return terminal.openLocation()
    }

    @Throws(JSONException::class)
    private fun handleLocationSetOption(payload: JSONObject): Int {
        // Build LocationClientOption from payload
        // Example: {"intervalMs":5000,"needAddress":true}
        val opt = LocationClientOption()
//        if (payload.has("intervalMs")) {
//            opt.setScanSpan(payload.getInt("intervalMs"))
//        }
//        if (payload.has("needAddress")) {
//            opt.setIsNeedAddress(payload.getBoolean("needAddress"))
//        }
        // Add more options as needed
        return terminal.setLocationOption(opt)
    }

    @Throws(JSONException::class)
    private fun handleLocationSetGeofence(payload: JSONObject): Int {
        val lon = payload.getDouble("longitude")
        val lat = payload.getDouble("latitude")
        val radius = payload.getDouble("radius").toFloat()
        val customId = payload.getString("customId")
        return terminal.addGeoFence(lon, lat, radius, customId)
    }

    @Throws(JSONException::class)
    private fun handleLocationBlockAppAdd(payload: JSONObject): Int {
        val pkg = payload.getString("package")
        return terminal.addToBlockOpenAppList(pkg)
    }

    @Throws(JSONException::class)
    private fun handleLocationBlockAppRemove(payload: JSONObject): Int {
        val pkg = payload.getString("package")
        return terminal.removeFromBlockOpenAppList(pkg)
    }

    // ----------------- Network module -----------------

    @Throws(JSONException::class)
    private fun handleNetworkAddApn(payload: JSONObject): Int {
        val cfg = ApnConfiguration()
        cfg.setName(payload.getString("name"))
        cfg.setApn(payload.getString("apn"))
        if (payload.has("mcc")) cfg.setMcc(payload.getString("mcc"))
        if (payload.has("mnc")) cfg.setMnc(payload.getString("mnc"))
        if (payload.has("user")) cfg.setUser(payload.getString("user"))
        if (payload.has("password")) cfg.setPassword(payload.getString("password"))
        if (payload.has("proxy")) cfg.setProxy(payload.getString("proxy"))
        if (payload.has("port")) cfg.setPort(payload.getString("port"))
        // Set other APN fields as required.
        return terminal.addApn(cfg)
    }

    @Throws(JSONException::class)
    private fun handleNetworkEnableApn(payload: JSONObject): Int {
        val name = payload.getString("name")
        return terminal.enableApn(name)
    }

    // ----------------- Resource module -----------------

    @Throws(JSONException::class)
    private fun handleInstallOrUpdate(payload: JSONObject): Int {
        val path = payload.getString("path") // e.g. /sdcard/mdm_downloads/app.apk
        return terminal.installOrUpdate(path)
    }

    @Throws(JSONException::class)
    private fun handleUninstall(payload: JSONObject): Int {
        RemoteLogger.log(context, Const.LOG_INFO, "payload: $payload")
        val pkg = payload.getString("pkg")
        return terminal.unInstall(pkg)
    }

    @Throws(JSONException::class)
    private fun handleUpdateOta(payload: JSONObject): Int {
        val path = payload.getString("otaUrl") // OTA file
        RemoteLogger.log(context, Const.LOG_INFO,
            "Received TYPE_UPDATE_OTA push message - otaUrl $path")

        // Run everything (download + OTA) on a worker thread; method returns immediately
        CoroutineScope(Dispatchers.Main).launch {
            // flag to control "never off" toast
            val toastRunning = AtomicBoolean(true)

            // sticky toast coroutine: keeps re-showing while toastRunning == true
            val toastJob = launch {
                while (toastRunning.get()) {
                    showToast(context, "OTA is running, please wait...")
                    delay(OTA_TOAST_INTERVAL_MS)
                }
            }

            try {
                withContext(Dispatchers.IO) {
                    RemoteLogger.log(context, Const.LOG_INFO, "Starting OTA download: $path")
                    showToast(context, "Starting OTA download...")

                    // 1) BLOCKING download – returns only after file is fully written
                    val localPath = downloadOtaFileWithOkHttp(context, path)

                    RemoteLogger.log(context, Const.LOG_INFO,
                        "OTA download finished, path = $localPath")
                    Log.i("OTA", "Downloaded OTA to: $localPath")
                    showToast(context, "OTA Download completed")

                    // Optional safety check
                    val f = File(localPath)
                    if (!f.exists() || f.length() == 0L) {
                        RemoteLogger.log(context, Const.LOG_ERROR,
                            "OTA file missing or empty after download: $localPath")
                        toastRunning.set(false) // stop sticky toast
                        return@withContext
                    }

                    // 2) Now call Kozen OTA API – only after download completed
                    showToast(context, "Updating firmware...")
                    RemoteLogger.log(context, Const.LOG_INFO,
                        "Calling updateOTAWithListener with path: $localPath")

                    val otaListener = object : OnUpdateOTAListener {
                        override fun onSuccess() {
                            toastRunning.set(false) // stop sticky toast
                            showToast(context, "OTA update successful")
                            RemoteLogger.log(context, Const.LOG_INFO, "OTA updated successfully")
                        }

                        override fun onError(msg: String, code: Int) {
                            toastRunning.set(false) // stop sticky toast
                            showToast(context, "OTA failed: $msg (code $code)")
                            RemoteLogger.log(context, Const.LOG_ERROR,
                                "OTA error code=$code, detail=$msg")
                        }
                    }

                    val ret = terminal.updateOTAWithListener(localPath, otaListener)
                    RemoteLogger.log(context, Const.LOG_INFO,
                        "updateOTAWithListener returned: $ret")
                }
            } catch (e: Exception) {
                toastRunning.set(false) // stop sticky toast
                toastJob.cancel()
                showToast(context, "OTA process failed: ${e.message}")
                Log.e("OTA", "Download or OTA update failed", e)
            }
        }

        // method itself returns immediately; OTA + toasts continue in background
        return 0
    }

    @Throws(JSONException::class)
    private fun handleUpdateCustomRes(payload: JSONObject): Int {
        val path = payload.getString("path")
        return terminal.updateCustomRes(path)
    }

    // ----------------- Keyboard module -----------------

    private fun handleKeyboardStart(): Int {
        // Simple start; if you want mapping to UI, implement callback wiring elsewhere
        return component.startPhysicalKeyboard(null)
    }

    private fun handleKeyboardStop(): Int {
        return component.stopPhysicalKeyboard()
    }

    @Throws(JSONException::class)
    private fun handleKeyboardSetSound(payload: JSONObject): Int {
        val enable = payload.getBoolean("enable")
        return component.switchKeyButtonVoiceEnable(enable)
    }

    // ----------------- Secondary screen module -----------------

    @Throws(JSONException::class)
    private fun handleSecondaryShowPic(payload: JSONObject): Int {
        return if (payload.has("paths")) {
            val arr = payload.getJSONArray("paths")
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            val interval = payload.optInt("intervalSeconds", 5)
            component.showPic(list, interval)
        } else {
            val path = payload.getString("path")
            component.showPic(path)
        }
    }

    @Throws(JSONException::class)
    private fun handleSecondaryShowVideo(payload: JSONObject): Int {
        val path = payload.getString("path")
        return component.showVideo(path)
    }

    @Throws(JSONException::class)
    private fun handleSecondaryPower(payload: JSONObject): Int {
        val on = payload.getBoolean("on")
        return component.power(on)
    }

    @Throws(JSONException::class)
    private fun handleSecondaryBrightness(payload: JSONObject): Int {
        val value = payload.getInt("value") // 0-255 or device-specific
        return component.setBrightness(value)
    }

    @Throws(JSONException::class)
    private fun handleSecondarySetBootLogo(payload: JSONObject): Int {
        val url = payload.getString("url") // remote URL of logo image
        RemoteLogger.log(context, Const.LOG_INFO,
            "Received TYPE_SECONDARY_SET_BOOT_LOGO - url=$url")

        if (url.isEmpty()) {
            RemoteLogger.log(context, Const.LOG_ERROR, "Boot logo path is null or empty")
            return -1
        }

        return try {
            // 1) Download logo to local storage (blocking)
            showToast(context, "Downloading boot logo...")
            val localPath = downloadBootLogoWithOkHttp(context, url)

            // Optional: sanity check
            val f = File(localPath)
            if (!f.exists() || f.length() == 0L) {
                RemoteLogger.log(context, Const.LOG_ERROR,
                    "Boot logo file missing or empty: $localPath")
                showToast(context, "Boot logo download failed")
                return -1
            }

            // 2) Set boot logo from local file
            RemoteLogger.log(context, Const.LOG_INFO, "Setting boot logo from: $localPath")
            val ret = component.setBootLogo(localPath)

            RemoteLogger.log(context, Const.LOG_INFO, "setBootLogo returned: $ret")
            if (ret == 0) {
                showToast(context, "Boot logo updated")
            } else {
                showToast(context, "Set boot logo failed, code: $ret")
            }

            ret
        } catch (e: IOException) {
            RemoteLogger.log(context, Const.LOG_ERROR,
                "Boot logo download failed: ${e.message}")
            showToast(context, "Boot logo download error: ${e.message}")
            -1
        }
    }
}
