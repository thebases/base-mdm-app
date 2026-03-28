package com.base.launcher.kozen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.preference.PreferenceManager;

import com.base.launcher.Const;
import com.base.launcher.util.RemoteLogger;
import com.kozen.terminalmanager.aidl.location.entity.LocationClientOption;
import com.kozen.terminalmanager.aidl.network.entity.ApnConfiguration;
import com.kozen.terminalmanager.resource.OnAppUpdateListener;
import com.kozen.terminalmanager.resource.OnUpdateOTAListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Executes remote Kozen-related commands coming from the MDM server.
 * <p>
 * Input: JSON command object with "type" and "payload".
 * Output: Kozen SDK calls + local result JSON for server acknowledgement.
 */
public class KozenCommandHandler {

    private static final String TAG = "KozenCommandHandler";
    private static final long TOAST_INTERVAL_MS = 3000L; // 3 seconds.
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private final Context context;
    private final KozenTerminalFacade terminal;
    private final KozenComponentFacade component;

    public KozenCommandHandler(Context context) {
        this.context = context.getApplicationContext();
        this.terminal = KozenTerminalFacade.get();
        this.component = KozenComponentFacade.get();
    }

    private static Toast toast;

    public static void showToast(final Context context, final String msg) {
        if (toast != null) toast.cancel();
        new android.os.Handler(Looper.getMainLooper()).post(() -> {
                    toast = Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT);
                    toast.show();
                }
        );
    }

    public JSONObject execute(JSONObject command) throws JSONException {
        String action = command.optString("action", "");
        JSONObject payload = command.optJSONObject("data");
        RemoteLogger.log(context, Const.LOG_INFO, "action: " + action);

        if (payload == null) {
            payload = new JSONObject();
        } else {
            RemoteLogger.log(context, Const.LOG_INFO, "data: " + payload.toString());
        }

        JSONObject result = new JSONObject();
        result.put("id", command.optLong("id", -1));
        result.put("action", action);

        try {
            switch (action) {
                // ===== Certification module =====
                case "certUpdate":
                    result.put("status", handleCertUpdate(payload));
                    break;
                case "certDelete":
                    result.put("status", handleCertDelete(payload));
                    break;
                case "certList":
                    result.put("data", handleCertList());
                    result.put("status", 0);
                    break;

                // ===== Device information module =====
                case "deviceInfo":
                    result.put("data", handleDeviceInfoFull());
                    result.put("status", 0);
                    break;

                // ===== Device module =====
                case "deviceSetTime":
                    result.put("status", handleSetTime(payload));
                    break;
                case "deviceSetTimezone":
                    result.put("status", handleSetTimeZone(payload));
                    break;
                case "deviceReboot":
                    handleReboot();
                    result.put("status", 0);
                    break;
                case "deviceShutdown":
                    handleShutdown();
                    result.put("status", 0);
                    break;
                case "deviceSetPciReboot":
                    result.put("status", handleSetPCIReboot(payload));
                    break;
                case "deviceCancelPciReboot":
                    result.put("status", handleCancelPCIReboot());
                    break;
                case "deviceSetSilentInstall":
                    handleSetSilentInstall(payload);
                    result.put("status", 0);
                    break;
                case "deviceForcePermission":
                    handleForcePermission(payload);
                    result.put("status", 0);
                    break;

                // ===== Location module =====
                case "locationOpen":
                    result.put("status", handleLocationOpen(payload));
                    break;
                case "locationSetOption":
                    result.put("status", handleLocationSetOption(payload));
                    break;
                case "locationStartOnce":
                    result.put("status", terminal.startOnceLocation());
                    break;
                case "locationSetGeofence":
                    result.put("status", handleLocationSetGeofence(payload));
                    break;
                case "locationClearGeofence":
                    result.put("status", terminal.removeAllGeoFence());
                    break;
                case "locationBlockAppAdd":
                    result.put("status", handleLocationBlockAppAdd(payload));
                    break;
                case "locationBlockAppRemove":
                    result.put("status", handleLocationBlockAppRemove(payload));
                    break;

                // ===== Network module =====
                case "networkAddApn":
                    result.put("status", handleNetworkAddApn(payload));
                    break;
                case "networkEnableApn":
                    result.put("status", handleNetworkEnableApn(payload));
                    break;

                // ===== Resource module =====
                case "resourceInstallOrUpdate":
                    result.put("status", handleInstallOrUpdate(payload));
                    break;
                case "resourceUninstall":
                    result.put("status", handleUninstall(payload));
                    break;
                case "resourceUpdateOta":
                    result.put("status", handleUpdateOta(payload));
                    break;
                case "resourceUpdateCustomRes":
                    result.put("status", handleUpdateCustomRes(payload));
                    break;

                // ===== Keyboard module =====
                case "keyboardStart":
                    result.put("status", handleKeyboardStart());
                    break;
                case "keyboardStop":
                    result.put("status", handleKeyboardStop());
                    break;
                case "keyboardSetSound":
                    result.put("status", handleKeyboardSetSound(payload));
                    break;

                // ===== Secondary screen module =====
                case "secondaryShowPic":
                    result.put("status", handleSecondaryShowPic(payload));
                    break;
                case "secondaryShowVideo":
                    result.put("status", handleSecondaryShowVideo(payload));
                    break;
                case "secondaryPower":
                    result.put("status", handleSecondaryPower(payload));
                    break;
                case "secondaryBrightness":
                    result.put("status", handleSecondaryBrightness(payload));
                    break;
                case "secondarySetBootLogo":
                    result.put("status", handleSecondarySetBootLogo(payload));
                    break;

                default:
                    result.put("status", -1);
                    result.put("error", "Unknown type: " + action);
                    break;
            }
        } catch (Throwable t) {
            Log.e(TAG, "execute error for type=" + action, t);
            result.put("status", -1);
            result.put("error", t.getMessage());
        }

        return result;
    }

    // ----------------- Certification -----------------

    private int handleCertUpdate(JSONObject payload) throws JSONException {
        String certData = payload.getString("certData");
        return terminal.updateAppSignature(certData);
    }

    private int handleCertDelete(JSONObject payload) throws JSONException {
        String certData = payload.getString("certData");
        return terminal.deleteAppSignature(certData);
    }

    private JSONArray handleCertList() throws JSONException {
        JSONArray arr = new JSONArray();
        for (String line : terminal.getAppSignatureInfo()) {
            arr.put(line);
        }
        return arr;
    }

    // ----------------- Device info -----------------

    private JSONObject handleDeviceInfoFull() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("sdkVersion", terminal.getSdkServiceVersion());
        data.put("serialNo", terminal.getSerialNo());
        data.put("vendorName", terminal.getVendorName());
        data.put("deviceModel", terminal.getDeviceModel());
        data.put("osVersion", terminal.getOsVersion());
        data.put("kernelVersion", terminal.getKernelVersion());
        data.put("mcuVersion", terminal.getMcuVersion());
        data.put("hardwareVersion", terminal.getHardwareVersion());
        data.put("emvKernelVersion", terminal.getEmvKernelVersion());
        data.put("TUSN", terminal.getTUSN());
        data.put("CSN", terminal.getCSN());

        JSONArray imsiArr = new JSONArray();
        for (String imsi : terminal.getImsi()) imsiArr.put(imsi);
        data.put("imsi", imsiArr);

        JSONArray imeiArr = new JSONArray();
        for (String imei : terminal.getImei()) imeiArr.put(imei);
        data.put("imei", imeiArr);

        return data;
    }

    // ----------------- Device module -----------------

    private int handleSetTime(JSONObject payload) throws JSONException {
        long ts = payload.getLong("timestamp"); // millis
        return terminal.setSystemTime(ts);
    }

    private int handleSetTimeZone(JSONObject payload) throws JSONException {
        String tz = payload.getString("timezone"); // e.g. "Asia/Ho_Chi_Minh"
        return terminal.setTimeZone(tz);
    }

    private void handleReboot() {
        terminal.reboot();
    }

    private void handleShutdown() {
        terminal.shutdown();
    }

    private int handleSetPCIReboot(JSONObject payload) throws JSONException {
        long millis = payload.getLong("delayMillis");
        return terminal.setPCIReboot(millis);
    }

    private int handleCancelPCIReboot() {
        return terminal.cancelPCIReboot();
    }

    private void handleSetSilentInstall(JSONObject payload) throws JSONException {
        boolean open = payload.getBoolean("open");
        terminal.setSilentInstall(open);
    }

    private void handleForcePermission(JSONObject payload) throws JSONException {
        boolean open = payload.getBoolean("open");
        terminal.forcePermission(open);
    }

    // ----------------- Location module -----------------
    // For simplicity: open() with no params; you can extend to key/type if needed.

    private int handleLocationOpen(JSONObject payload) {
        return terminal.openLocation();
    }

    private int handleLocationSetOption(JSONObject payload) throws JSONException {
        // Build LocationClientOption from payload
        // Example: {"intervalMs":5000,"needAddress":true}
        LocationClientOption opt =
                new LocationClientOption();
//        if (payload.has("intervalMs")) {
//            opt.setScanSpan(payload.getInt("intervalMs"));
//        }
//        if (payload.has("needAddress")) {
//            opt.setIsNeedAddress(payload.getBoolean("needAddress"));
//        }
        // Add more options as needed
        return terminal.setLocationOption(opt);
    }

    private int handleLocationSetGeofence(JSONObject payload) throws JSONException {
        double lon = payload.getDouble("longitude");
        double lat = payload.getDouble("latitude");
        float radius = (float) payload.getDouble("radius");
        String customId = payload.getString("customId");
        return terminal.addGeoFence(lon, lat, radius, customId);
    }

    private int handleLocationBlockAppAdd(JSONObject payload) throws JSONException {
        String pkg = payload.getString("package");
        return terminal.addToBlockOpenAppList(pkg);
    }

    private int handleLocationBlockAppRemove(JSONObject payload) throws JSONException {
        String pkg = payload.getString("package");
        return terminal.removeFromBlockOpenAppList(pkg);
    }

    // ----------------- Network module -----------------

    private int handleNetworkAddApn(JSONObject payload) throws JSONException {
        ApnConfiguration cfg =
                new ApnConfiguration();
        cfg.setName(payload.getString("name"));
        cfg.setApn(payload.getString("apn"));
        if (payload.has("mcc")) cfg.setMcc(payload.getString("mcc"));
        if (payload.has("mnc")) cfg.setMnc(payload.getString("mnc"));
        if (payload.has("user")) cfg.setUser(payload.getString("user"));
        if (payload.has("password")) cfg.setPassword(payload.getString("password"));
        if (payload.has("proxy")) cfg.setProxy(payload.getString("proxy"));
        if (payload.has("port")) cfg.setPort(payload.getString("port"));
        // Set other APN fields as required.
        return terminal.addApn(cfg);
    }

    private int handleNetworkEnableApn(JSONObject payload) throws JSONException {
        String name = payload.getString("name");
        return terminal.enableApn(name);
    }

    // ----------------- Resource module -----------------

    //    private int handleInstallOrUpdate(JSONObject payload) throws JSONException {
//        String path = payload.getString("url"); // e.g. /sdcard/mdm_downloads/app.apk
//        RemoteLogger.log(context, Const.LOG_INFO, "Received Install/Update app message - url " + path);
//
//        if (path == null) {
//            return -1;
//        }
//
//        return terminal.installOrUpdate(path);
//    }
    private int handleInstallOrUpdate(JSONObject payload) throws JSONException {
        final String apkUrl = payload.getString("apkUrl");
//    final String pkg = payload.optString("packageName", null); // optional (helps logging/validation)

        RemoteLogger.log(context, Const.LOG_INFO,
                "Received TYPE_INSTALL_OR_UPDATE push message - apkurl=" + apkUrl);

        if (apkUrl == null || apkUrl.trim().isEmpty()) {
            RemoteLogger.log(context, Const.LOG_ERROR, "apkUrl is null/empty");
            return -1;
        }

        // Run everything (download + install) on a worker thread
        AsyncTask.execute(() -> {
            AtomicBoolean toastRunning = new AtomicBoolean(true);
            Handler mainHandler = new Handler(Looper.getMainLooper());

            Runnable stickyToastRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!toastRunning.get()) return;
                    showToast(context, "App update is running, please wait...");
                    mainHandler.postDelayed(this, TOAST_INTERVAL_MS);
                }
            };

            mainHandler.post(stickyToastRunnable);

            try {
                RemoteLogger.log(context, Const.LOG_INFO, "Starting APK download: " + apkUrl);
                showToast(context, "Starting app download...");

                // 1) BLOCKING download – returns only after file is fully written
                String localApkPath = downloadApkFileWithOkHttp(context, apkUrl);

                RemoteLogger.log(context, Const.LOG_INFO, "APK download finished, path=" + localApkPath);
                Log.i("APK", "Downloaded APK to: " + localApkPath);
//            showToast(context, "App download completed");

                File f = new File(localApkPath);
                if (!f.exists() || f.length() == 0) {
                    RemoteLogger.log(context, Const.LOG_ERROR,
                            "APK file missing or empty after download: " + localApkPath);
                    toastRunning.set(false);
                    return;
                }

                // 2) Install/Update
                showToast(context, "Installing app...");
                RemoteLogger.log(context, Const.LOG_INFO, "Installing APK: " + localApkPath);
                OnAppUpdateListener appInstallListener = new OnAppUpdateListener() {

                    @Override
                    public void onSuccess() {
                        toastRunning.set(false); // stop sticky toast
                        showToast(context, "Application install/update successful");
                        RemoteLogger.log(context, Const.LOG_INFO, "APK updated successfully");
                    }

                    @Override
                    public void onError(String msg, int code) {
                        toastRunning.set(false); // stop sticky toast
                        showToast(context, "Application install/update failed: " + msg + " (code " + code + ")");
                        RemoteLogger.log(context, Const.LOG_ERROR,
                                "OTA error code=" + code + ", detail=" + msg);
                    }
                };
                terminal.installOrUpdateWithListener(localApkPath, appInstallListener);

            } catch (Exception e) {
                toastRunning.set(false);
                showToast(context, "Install/Update process failed: " + e.getMessage());
                RemoteLogger.log(context, Const.LOG_ERROR, "Install process failed: " + e);
                Log.e("APK", "Download or install/update failed", e);
            }
        });

        // method returns immediately; download/install continues in background
        return 0;
    }

    private int handleUninstall(JSONObject payload) throws JSONException {
        RemoteLogger.log(context, Const.LOG_INFO, "payload: " + payload.toString());

        String pkg = payload.getString("pkg");
        return terminal.unInstall(pkg);
    }

    private int handleUpdateOta(JSONObject payload) throws JSONException {

        String path = payload.getString("otaUrl"); // OTA file
        RemoteLogger.log(context, Const.LOG_INFO, "Received TYPE_UPDATE_OTA push message - otaUrl " + path);

        if (path == null) {
            return -1;
        }
        // Run everything (download + OTA) on a worker thread
//        Executors.newSingleThreadExecutor().execute(() -> {
        AsyncTask.execute(() -> {
            // flag to control "never off" toast
            AtomicBoolean toastRunning = new AtomicBoolean(true);
            Handler mainHandler = new Handler(Looper.getMainLooper());

            // this runnable will keep re-showing the toast while toastRunning == true
            Runnable stickyToastRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!toastRunning.get()) {
                        return; // stop loop
                    }
                    showToast(context, "OTA is running, please wait...");
                    mainHandler.postDelayed(this, TOAST_INTERVAL_MS);
                }
            };

            // start the sticky toast loop
            mainHandler.post(stickyToastRunnable);
            try {

                RemoteLogger.log(context, Const.LOG_INFO, "Starting OTA download: " + path);
                showToast(context, "Starting OTA download...");

                // 1) BLOCKING download – this call RETURNS ONLY AFTER FILE IS FULLY WRITTEN
                String localPath = downloadOtaFileWithOkHttp(context, path);

                RemoteLogger.log(context, Const.LOG_INFO, "OTA download finished, path = " + localPath);
                Log.i("OTA", "Downloaded OTA to: " + localPath);
                showToast(context, "OTA Download completed");

                // Optional safety check
                File f = new File(localPath);
                if (!f.exists() || f.length() == 0) {
                    RemoteLogger.log(context, Const.LOG_ERROR,
                            "OTA file missing or empty after download: " + localPath);
                    toastRunning.set(false); // stop sticky toast
                    return;
                }
                // 2) Now call Kozen OTA API – this happens ONLY AFTER download completed
                showToast(context, "Updating firmware...");

                RemoteLogger.log(context, Const.LOG_INFO,
                        "Calling updateOTAWithListener with path: " + localPath);
                OnUpdateOTAListener otaListener = new OnUpdateOTAListener() {
                    @Override
                    public void onSuccess() {
                        toastRunning.set(false); // stop sticky toast
                        showToast(context, "OTA update successful");
                        RemoteLogger.log(context, Const.LOG_INFO, "OTA updated successfully");
                    }

                    @Override
                    public void onError(String msg, int code) {
                        toastRunning.set(false); // stop sticky toast
                        showToast(context, "OTA failed: " + msg + " (code " + code + ")");
                        RemoteLogger.log(context, Const.LOG_ERROR,
                                "OTA error code=" + code + ", detail=" + msg);
                    }
                };
                int ret = terminal.updateOTAWithListener(localPath, otaListener);
                RemoteLogger.log(context, Const.LOG_INFO,
                        "updateOTAWithListener returned: " + ret);

            } catch (Exception e) {
                toastRunning.set(false); // stop sticky toast
                showToast(context, "OTA process failed: " + e.getMessage());
                Log.e("OTA", "Download or OTA update failed", e);
            }
        });

        // method itself still returns immediately; OTA + toasts continue in background
        return 0;
    }

    // Single shared client for OTA
    private static final OkHttpClient OTA_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)   // large file -> long timeout
            .writeTimeout(10, TimeUnit.MINUTES)
            .build();

    public static String downloadApkFileWithOkHttp(Context context, String urlString) throws IOException {
        String fileName = urlString.substring(urlString.lastIndexOf('/') + 1);
        if (!fileName.endsWith(".apk")) {
            throw new IOException("APK file must be .apk, got: " + fileName);
        }

        File outFile = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
        );
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        Request request = new Request.Builder().url(urlString).get().build();

        try (Response response = OTA_HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected HTTP code " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty response body");

            long contentLength = body.contentLength();
            RemoteLogger.log(context, Const.LOG_INFO,
                    "Starting APK download, size=" + contentLength + " bytes");

            try (InputStream in = new BufferedInputStream(body.byteStream());
                 FileOutputStream out = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[8192];
                int read;
                long totalRead = 0L;

                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalRead += read;

                    if (contentLength > 0 && totalRead % (20L * 1024 * 1024) < 8192) { // every ~20MB
                        int progress = (int) (100L * totalRead / contentLength);
//                        showToast(context, "Downloading: " + progress + "%");
                        Log.d("APK", "Download progress: " + progress + "% (" +
                                (totalRead / (1024 * 1024)) + " MB)");
                    }
                }
                out.flush();
            }
            showToast(context, "Download completed");
            return outFile.getAbsolutePath();
        }
    }

    public static String downloadOtaFileWithOkHttp(Context context, String urlString) throws IOException {
        // Extract file name
        String fileName = urlString.substring(urlString.lastIndexOf('/') + 1);
        if (!fileName.endsWith(".zip")) {
            throw new IOException("OTA file must be .zip, got: " + fileName);
        }

        // Target: /sdcard/Download/<fileName>
        File outFile = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
        );
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        Request request = new Request.Builder()
                .url(urlString)
                .get()
                .build();

        try (Response response = OTA_HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected HTTP code " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }

            long contentLength = body.contentLength(); // can be -1 if unknown
            RemoteLogger.log(context, Const.LOG_INFO,
                    "Starting file download, size = " + contentLength + " bytes");

            try (InputStream in = new BufferedInputStream(body.byteStream());
                 FileOutputStream out = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[8192];
                int read;
                long totalRead = 0L;

                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalRead += read;

                    // Optional debug: log progress every ~50MB
                    if (contentLength > 0 && totalRead % (50L * 1024 * 1024) < 8192) {
                        int progress = (int) (100L * totalRead / contentLength);
                        // Toast on progress
                        showToast(context, "Downloading: " + progress + "%");
                        Log.d("OTA", "Download progress: " + progress + "% (" +
                                (totalRead / (1024 * 1024)) + " MB)");
                    }
                }
                out.flush();
            }

            return outFile.getAbsolutePath();
        }
    }

    public static String downloadBootLogoWithOkHttp(Context context, String urlString) throws IOException {
        // Extract file name from URL
        Future<String> future = EXECUTOR.submit(() -> {
            // ======= ORIGINAL BLOCKING BODY MOVES HERE =======
            String fileName = urlString.substring(urlString.lastIndexOf('/') + 1);
            if (fileName.isEmpty()) {
                throw new IOException("Invalid boot logo file name from URL: " + urlString);
            }

            // Optionally enforce image extensions
            if (!fileName.endsWith(".png") && !fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")) {
                throw new IOException("Boot logo must be an image (.png/.jpg), got: " + fileName);
            }

            // Target directory: /sdcard/Download/<fileName>
//            /storage/emulated/0/ViceScreen/sub_boot_logo
            File outFile = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
            );
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }

            Request request = new Request.Builder()
                    .url(urlString)
                    .get()
                    .build();

            try (Response response = OTA_HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected HTTP code " + response.code());
                }

                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty response body");
                }

                long contentLength = body.contentLength(); // may be -1
                RemoteLogger.log(context, Const.LOG_INFO,
                        "Starting boot logo download, size = " + contentLength + " bytes");

                try (InputStream in = new BufferedInputStream(body.byteStream());
                     FileOutputStream out = new FileOutputStream(outFile)) {

                    byte[] buffer = new byte[8192];
                    int read;
                    long totalRead = 0L;

                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        totalRead += read;
                    }
                    out.flush();
                }

                RemoteLogger.log(context, Const.LOG_INFO,
                        "Boot logo downloaded to: " + outFile.getAbsolutePath());
                return outFile.getAbsolutePath();
            }
        });
        try {
            // You can add a timeout if you want:
            // return future.get(2, TimeUnit.MINUTES);
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Boot logo download interrupted", e);
        } catch (ExecutionException e) {
            // unwrap original exception
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Boot logo download failed", cause);
        }
    }


    private int handleUpdateCustomRes(JSONObject payload) throws JSONException {
        String path = payload.getString("path");
        return terminal.updateCustomRes(path);
    }

    // ----------------- Keyboard module -----------------

    private int handleKeyboardStart() {
        // Simple start; if you want mapping to UI, implement callback wiring elsewhere
        return component.startPhysicalKeyboard(null);
    }

    private int handleKeyboardStop() {
        return component.stopPhysicalKeyboard();
    }

    private int handleKeyboardSetSound(JSONObject payload) throws JSONException {
        boolean enable = payload.getBoolean("enable");
        return component.switchKeyButtonVoiceEnable(enable);
    }

    // ----------------- Secondary screen module -----------------

    private int handleSecondaryShowPic(JSONObject payload) throws JSONException {
        if (payload.has("paths")) {
            JSONArray arr = payload.getJSONArray("paths");
            java.util.ArrayList<String> list = new java.util.ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
            int interval = payload.optInt("intervalSeconds", 5);
            return component.showPic(list, interval);
        } else {
            String path = payload.getString("path");
            return component.showPic(path);
        }
    }

    private int handleSecondaryShowVideo(JSONObject payload) throws JSONException {
        String path = payload.getString("path");
        return component.showVideo(path);
    }

    private int handleSecondaryPower(JSONObject payload) throws JSONException {
        boolean on = payload.getBoolean("on");
        return component.power(on);
    }

    private int handleSecondaryBrightness(JSONObject payload) throws JSONException {
        int value = payload.getInt("value"); // 0-255 or device-specific
        return component.setBrightness(value);
    }

    private int handleSecondarySetBootLogo(JSONObject payload) throws JSONException {
        String url = payload.getString("url"); // remote URL of logo image
        RemoteLogger.log(context, Const.LOG_INFO,
                "Received TYPE_SECONDARY_SET_BOOT_LOGO - url=" + url);

        if (url == null || url.isEmpty()) {
            RemoteLogger.log(context, Const.LOG_ERROR,
                    "Boot logo path is null or empty");
            return -1;
        }

        try {
            // 1) Download logo to local storage (blocking)
            showToast(context, "Downloading boot logo...");
            String localPath = downloadBootLogoWithOkHttp(context, url);

            // Optional: sanity check
            File f = new File(localPath);
            if (!f.exists() || f.length() == 0) {
                RemoteLogger.log(context, Const.LOG_ERROR,
                        "Boot logo file missing or empty: " + localPath);
                showToast(context, "Boot logo download failed");
                return -1;
            }

            // 2) Set boot logo from local file
            RemoteLogger.log(context, Const.LOG_INFO,
                    "Setting boot logo from: " + localPath);
            int ret = component.setBootLogo(localPath);

            RemoteLogger.log(context, Const.LOG_INFO,
                    "setBootLogo returned: " + ret);
            if (ret == 0) {
                showToast(context, "Boot logo updated");
            } else {
                showToast(context, "Set boot logo failed, code: " + ret);
            }

            return ret;

        } catch (IOException e) {
            RemoteLogger.log(context, Const.LOG_ERROR,
                    "Boot logo download failed: " + e.getMessage());
            showToast(context, "Boot logo download error: " + e.getMessage());
            return -1;
        }
    }

}
