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

package com.base.launcher.worker;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.base.launcher.BuildConfig;
import com.base.launcher.Const;
import com.base.launcher.db.DatabaseHelper;
import com.base.launcher.db.DownloadTable;
import com.base.launcher.helper.ConfigUpdater;
import com.base.launcher.helper.SettingsHelper;
import com.base.launcher.json.Application;
import com.base.launcher.json.Download;
import com.base.launcher.json.PushMessage;
import com.base.launcher.json.ServerConfig;
import com.base.launcher.util.InstallUtils;
import com.base.launcher.util.RemoteLogger;
import com.base.launcher.util.SystemUtils;
import com.base.launcher.util.Utils;
import com.kozen.terminalmanager.TerminalManager;
import com.kozen.terminalmanager.resource.IResourceManager;
import com.kozen.terminalmanager.resource.OnUpdateOTAListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class PushNotificationProcessor {
    private static void showToast(final Context context, final String msg) {
        new android.os.Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show()
        );
    }

    public static void process(PushMessage message, Context context) throws JSONException {
        RemoteLogger.log(context, Const.LOG_INFO, "Got Push Message, type " + message.getMessageType());
        if (message.getMessageType().equals(PushMessage.TYPE_CONFIG_UPDATED)) {
            // Update local configuration
            ConfigUpdater.notifyConfigUpdate(context);
            // The configUpdated should be broadcasted after the configuration update is completed
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_RUN_APP)) {
            // Run application
            runApplication(context, message.getPayloadJSON());
            // Do not broadcast this message to other apps
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_UNINSTALL_APP)) {
            // Uninstall application
            AsyncTask.execute(() -> uninstallApplication(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_DELETE_FILE)) {
            // Delete file
            AsyncTask.execute(() -> deleteFile(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_DELETE_DIR)) {
            // Delete directory recursively
            AsyncTask.execute(() -> deleteDir(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_PURGE_DIR)) {
            // Purge directory (delete all files recursively)
            AsyncTask.execute(() -> purgeDir(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_PERMISSIVE_MODE)) {
            // Turn on permissive mode
            LocalBroadcastManager.getInstance(context).
                    sendBroadcast(new Intent(Const.ACTION_PERMISSIVE_MODE));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_RUN_COMMAND)) {
            // Run a command-line script
            AsyncTask.execute(() -> runCommand(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_REBOOT)) {
            // Reboot a device
            AsyncTask.execute(() -> reboot(context));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_EXIT_KIOSK)) {
            // Temporarily exit kiosk mode
            LocalBroadcastManager.getInstance(context).
                    sendBroadcast(new Intent(Const.ACTION_EXIT_KIOSK));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_ADMIN_PANEL)) {
            LocalBroadcastManager.getInstance(context).
                    sendBroadcast(new Intent(Const.ACTION_ADMIN_PANEL));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_CLEAR_DOWNLOADS)) {
            // Clear download history
            AsyncTask.execute(() -> clearDownloads(context));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_INTENT)) {
            // Run a system intent (like settings or ACTION_VIEW)
            AsyncTask.execute(() -> callIntent(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_GRANT_PERMISSIONS)) {
            // Grant permissions to apps
            AsyncTask.execute(() -> grantPermissions(context, message.getPayloadJSON()));
            return;
        } else if (message.getMessageType().equals(PushMessage.TYPE_DEVICE_ACTION)) {


        } else if (message.getMessageType().equals(PushMessage.TYPE_UPDATEOTA)) {
            RemoteLogger.log(context, Const.LOG_INFO, "Received TYPE_UPDATEOTA push message");
            JSONObject jsonObject = message.getPayloadJSON();

            if (jsonObject !=null) {
                // Run everything (download + OTA) on a worker thread
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        // TODO: ideally take this from message.getPayloadJSON()
//                        String otaUrl =
//                                "https://tmseu1s3.eu.aw-iot.com/1679795875709317122/ota/1762313470082/20251105/38c2d5e46ddc631151aa76372fef4d63.zip";
                        String otaUrl = jsonObject.getString("otaUrl");

                        RemoteLogger.log(context, Const.LOG_INFO, "Starting OTA download: " + otaUrl);
                        showToast(context, "Starting OTA download...");

                        // 1) BLOCKING download – this call RETURNS ONLY AFTER FILE IS FULLY WRITTEN
                        String localPath = downloadOtaFileWithOkHttp(context, otaUrl);

                        RemoteLogger.log(context, Const.LOG_INFO, "OTA download finished, path = " + localPath);
                        Log.i("OTA", "Downloaded OTA to: " + localPath);
                        showToast(context, "OTA Download completed");

                        // Optional safety check
                        File f = new File(localPath);
                        if (!f.exists() || f.length() == 0) {
                            RemoteLogger.log(context, Const.LOG_ERROR,
                                    "OTA file missing or empty after download: " + localPath);
                            return;
                        }
                        // 2) Now call Kozen OTA API – this happens ONLY AFTER download completed
                        showToast(context, "Updating firmware...");
                        IResourceManager rm = TerminalManager.INSTANCE.getResourceManager();
                        OnUpdateOTAListener otaListener = new OnUpdateOTAListener() {
                            @Override
                            public void onSuccess() {
                                showToast(context, "OTA update successful");
                                RemoteLogger.log(context, Const.LOG_INFO, "OTA updated successfully");
                            }

                            @Override
                            public void onError(String msg, int code) {
                                showToast(context, "OTA failed: " + msg + " (code " + code + ")");
                                RemoteLogger.log(context, Const.LOG_ERROR,
                                        "OTA error code=" + code + ", detail=" + msg);
                            }
                        };

                        RemoteLogger.log(context, Const.LOG_INFO,
                                "Calling updateOTAWithListener with path: " + localPath);

                        int ret = rm.updateOTAWithListener(localPath, otaListener);

                        RemoteLogger.log(context, Const.LOG_INFO,
                                "updateOTAWithListener returned: " + ret);

                    } catch (Exception e) {
                        showToast(context, "OTA process failed: " + e.getMessage());
                        Log.e("OTA", "Download or OTA update failed", e);
                        RemoteLogger.log(context, Const.LOG_ERROR,
                                "Download or OTA update failed: " + e.getMessage());
                    }
                });

                return;
            }
        } else {

            String textObj = message.getPayloadJSON().toString();
            RemoteLogger.log(context, Const.LOG_INFO, "ELse flow result:" + textObj);
            Toast.makeText(context, textObj, Toast.LENGTH_LONG).show();
        }

        // Send broadcast to all plugins
        Intent intent = new Intent(Const.INTENT_PUSH_NOTIFICATION_PREFIX + message.getMessageType());
        JSONObject jsonObject = message.getPayloadJSON();
        if (jsonObject != null) {
            intent.putExtra(Const.INTENT_PUSH_NOTIFICATION_EXTRA, jsonObject.toString());
        }
        context.sendBroadcast(intent);
    }

    // Single shared client for OTA
    private static final OkHttpClient OTA_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)   // large file -> long timeout
            .writeTimeout(10, TimeUnit.MINUTES)
            .build();
    private static File getLocalOtaFile(String urlString) {
        String fileName = urlString.substring(urlString.lastIndexOf('/') + 1);
        return new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
        );
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



    private static void runApplication(Context context, JSONObject payload) {
        if (payload == null) {
            return;
        }
        try {
            String pkg = payload.getString("pkg");
            String action = payload.optString("action", null);
            JSONObject extras = payload.optJSONObject("extra");
            String data = payload.optString("data", null);
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launchIntent != null) {
                if (action != null) {
                    launchIntent.setAction(action);
                }
                if (data != null) {
                    try {
                        launchIntent.setData(Uri.parse(data));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (extras != null) {
                    Iterator<String> keys = extras.keys();
                    String key;
                    while (keys.hasNext()) {
                        key = keys.next();
                        Object value = extras.get(key);
                        if (value instanceof String) {
                            launchIntent.putExtra(key, (String) value);
                        } else if (value instanceof Integer) {
                            launchIntent.putExtra(key, ((Integer) value).intValue());
                        } else if (value instanceof Float) {
                            launchIntent.putExtra(key, ((Float) value).floatValue());
                        } else if (value instanceof Boolean) {
                            launchIntent.putExtra(key, ((Boolean) value).booleanValue());
                        }
                    }
                }

                // These magic flags are found in the source code of the default Android launcher
                // These flags preserve the app activity stack (otherwise a launch activity appears at the top which is not correct)
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(launchIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void uninstallApplication(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Uninstall request failed: no package specified");
            return;
        }
        if (!Utils.isDeviceOwner(context)) {
            // Require device owner for non-interactive uninstallation
            RemoteLogger.log(context, Const.LOG_WARN, "Uninstall request failed: no device owner");
            return;
        }

        try {
            String pkg = payload.getString("pkg");
            InstallUtils.silentUninstallApplication(context, pkg);
            RemoteLogger.log(context, Const.LOG_INFO, "Uninstalled application: " + pkg);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "Uninstall request failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void deleteFile(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "File delete failed: no path specified");
            return;
        }

        try {
            String path = payload.getString("path");
            File file = new File(Environment.getExternalStorageDirectory(), path);
            file.delete();
            RemoteLogger.log(context, Const.LOG_INFO, "Deleted file: " + path);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "File delete failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] childFiles = fileOrDirectory.listFiles();
            for (File child : childFiles) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    private static void deleteDir(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory delete failed: no path specified");
            return;
        }

        try {
            String path = payload.getString("path");
            File file = new File(Environment.getExternalStorageDirectory(), path);
            deleteRecursive(file);
            RemoteLogger.log(context, Const.LOG_INFO, "Deleted directory: " + path);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory delete failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void purgeDir(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory purge failed: no path specified");
            return;
        }

        try {
            String path = payload.getString("path");
            File file = new File(Environment.getExternalStorageDirectory(), path);
            if (!file.isDirectory()) {
                RemoteLogger.log(context, Const.LOG_WARN, "Directory purge failed: not a directory: " + path);
                return;
            }
            String recursive = payload.optString("recursive");
            File[] childFiles = file.listFiles();
            for (File child : childFiles) {
                if (recursive == null || !recursive.equals("1")) {
                    if (!child.isDirectory()) {
                        child.delete();
                    }
                } else {
                    deleteRecursive(child);
                }
            }
            RemoteLogger.log(context, Const.LOG_INFO, "Purged directory: " + path);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "Directory purge failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runCommand(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Command failed: no command specified");
            return;
        }

        try {
            String command = payload.getString("command");
            Log.d(Const.LOG_TAG, "Executing a command: " + command);
            String result = SystemUtils.executeShellCommand(command, true);
            String msg = "Executed a command: " + command;
            if (!result.equals("")) {
                if (result.length() > 200) {
                    result = result.substring(0, 200) + "...";
                }
                msg += " Result: " + result;
            }
            RemoteLogger.log(context, Const.LOG_DEBUG, msg);

        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "Command failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void reboot(Context context) {
        RemoteLogger.log(context, Const.LOG_WARN, "Rebooting by a Push message");
        if (Utils.checkAdminMode(context)) {
            if (!Utils.reboot(context)) {
                RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed");
            }
        } else {
            RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed: no permissions");
        }
    }

    private static void clearDownloads(Context context) {
        RemoteLogger.log(context, Const.LOG_WARN, "Clear download history by a Push message");
        DatabaseHelper dbHelper = DatabaseHelper.instance(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        List<Download> downloads = DownloadTable.selectAll(db);
        for (Download d : downloads) {
            File file = new File(d.getPath());
            try {
                file.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        DownloadTable.deleteAll(db);
    }

    private static void callIntent(Context context, JSONObject payload) {
        if (payload == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Calling intent failed: no parameters specified");
            return;
        }

        try {
            String action = payload.getString("action");
            Log.d(Const.LOG_TAG, "Calling intent: " + action);
            JSONObject extras = payload.optJSONObject("extra");
            String data = payload.optString("data", null);
            String pkg = payload.optString("pkg", null);
            Log.d(Const.LOG_TAG, "Calling intent: " + action + " " + data + " " + pkg + " " + extras);

            Intent i = new Intent();
            if (data != null) {
                try {
                    i.setData(Uri.parse(data));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (extras != null) {
                Iterator<String> keys = extras.keys();
                String key;
                while (keys.hasNext()) {
                    key = keys.next();
                    Object value = extras.get(key);
                    if (value instanceof String) {
                        i.putExtra(key, (String) value);
                    } else if (value instanceof Integer) {
                        i.putExtra(key, ((Integer) value).intValue());
                    } else if (value instanceof Float) {
                        i.putExtra(key, ((Float) value).floatValue());
                    } else if (value instanceof Boolean) {
                        i.putExtra(key, ((Boolean) value).booleanValue());
                    }
                }
            }
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (pkg != null) {
                i.setClassName(pkg, action);
            } else {
                i.setAction(action);
                ;
            }
            ;

            context.startActivity(i);
        } catch (Exception e) {
            RemoteLogger.log(context, Const.LOG_WARN, "Calling intent failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void grantPermissions(Context context, JSONObject payload) {
        if (!Utils.isDeviceOwner(context) && !BuildConfig.SYSTEM_PRIVILEGES) {
            RemoteLogger.log(context, Const.LOG_WARN, "Can't auto grant permissions: no device owner");
        }

        ServerConfig config = SettingsHelper.getInstance(context).getConfig();
        List<String> apps = null;

        if (payload != null) {
            apps = new LinkedList<>();
            String pkg;
            JSONArray pkgs = payload.optJSONArray("pkg");
            if (pkgs != null) {
                for (int i = 0; i < pkgs.length(); i++) {
                    pkg = pkgs.optString(i);
                    if (pkg != null) {
                        apps.add(pkg);
                    }
                }
            } else {
                pkg = payload.optString("pkg");
                if (pkg != null) {
                    apps.add(pkg);
                }
            }
        } else {
            // By default, grant permissions to all packagee having an URL
            apps = new LinkedList<>();
            List<Application> configApps = config.getApplications();
            for (Application app : configApps) {
                if (Application.TYPE_APP.equals(app.getType()) &&
                        app.getUrl() != null && app.getPkg() != null) {
                    apps.add(app.getPkg());
                }
            }
        }

        for (String app : apps) {
            Utils.autoGrantRequestedPermissions(context, app,
                    config.getAppPermissions(), false);
        }
    }
}
