package com.base.launcher.kozen;

import android.content.Context;
import android.util.Log;

import com.kozen.terminalmanager.aidl.location.entity.LocationClientOption;
import com.kozen.terminalmanager.aidl.network.entity.ApnConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Executes remote Kozen-related commands coming from the MDM server.
 *
 * Input: JSON command object with "type" and "payload".
 * Output: Kozen SDK calls + local result JSON for server acknowledgement.
 */
public class KozenCommandHandler {

    private static final String TAG = "KozenCommandHandler";

    private final Context context;
    private final KozenTerminalFacade terminal;
    private final KozenComponentFacade component;

    public KozenCommandHandler(Context context) {
        this.context = context.getApplicationContext();
        this.terminal = KozenTerminalFacade.get();
        this.component = KozenComponentFacade.get();
    }

    public JSONObject execute(JSONObject command) throws JSONException {
        String type = command.optString("type", "");
        JSONObject payload = command.optJSONObject("payload");
        if (payload == null) payload = new JSONObject();

        JSONObject result = new JSONObject();
        result.put("id", command.optLong("id", -1));
        result.put("type", type);

        try {
            switch (type) {
                // ===== Certification module =====
                case "kozen.cert.update":
                    result.put("status", handleCertUpdate(payload));
                    break;
                case "kozen.cert.delete":
                    result.put("status", handleCertDelete(payload));
                    break;
                case "kozen.cert.list":
                    result.put("data", handleCertList());
                    result.put("status", 0);
                    break;

                // ===== Device information module =====
                case "kozen.device_info.full":
                    result.put("data", handleDeviceInfoFull());
                    result.put("status", 0);
                    break;

                // ===== Device module =====
                case "kozen.device.set_time":
                    result.put("status", handleSetTime(payload));
                    break;
                case "kozen.device.set_timezone":
                    result.put("status", handleSetTimeZone(payload));
                    break;
                case "kozen.device.reboot":
                    handleReboot();
                    result.put("status", 0);
                    break;
                case "kozen.device.shutdown":
                    handleShutdown();
                    result.put("status", 0);
                    break;
                case "kozen.device.set_pci_reboot":
                    result.put("status", handleSetPCIReboot(payload));
                    break;
                case "kozen.device.cancel_pci_reboot":
                    result.put("status", handleCancelPCIReboot());
                    break;
                case "kozen.device.set_silent_install":
                    handleSetSilentInstall(payload);
                    result.put("status", 0);
                    break;
                case "kozen.device.force_permission":
                    handleForcePermission(payload);
                    result.put("status", 0);
                    break;

                // ===== Location module =====
                case "kozen.location.open":
                    result.put("status", handleLocationOpen(payload));
                    break;
                case "kozen.location.set_option":
                    result.put("status", handleLocationSetOption(payload));
                    break;
                case "kozen.location.start_once":
                    result.put("status", terminal.startOnceLocation());
                    break;
                case "kozen.location.set_geofence":
                    result.put("status", handleLocationSetGeofence(payload));
                    break;
                case "kozen.location.clear_geofence":
                    result.put("status", terminal.removeAllGeoFence());
                    break;
                case "kozen.location.block_app_add":
                    result.put("status", handleLocationBlockAppAdd(payload));
                    break;
                case "kozen.location.block_app_remove":
                    result.put("status", handleLocationBlockAppRemove(payload));
                    break;

                // ===== Network module =====
                case "kozen.network.add_apn":
                    result.put("status", handleNetworkAddApn(payload));
                    break;
                case "kozen.network.enable_apn":
                    result.put("status", handleNetworkEnableApn(payload));
                    break;

                // ===== Resource module =====
                case "kozen.resource.install_or_update":
                    result.put("status", handleInstallOrUpdate(payload));
                    break;
                case "kozen.resource.uninstall":
                    result.put("status", handleUninstall(payload));
                    break;
                case "kozen.resource.update_ota":
                    result.put("status", handleUpdateOta(payload));
                    break;
                case "kozen.resource.update_custom_res":
                    result.put("status", handleUpdateCustomRes(payload));
                    break;

                // ===== Keyboard module =====
                case "kozen.keyboard.start":
                    result.put("status", handleKeyboardStart());
                    break;
                case "kozen.keyboard.stop":
                    result.put("status", handleKeyboardStop());
                    break;
                case "kozen.keyboard.set_sound":
                    result.put("status", handleKeyboardSetSound(payload));
                    break;

                // ===== Secondary screen module =====
                case "kozen.secondary.show_pic":
                    result.put("status", handleSecondaryShowPic(payload));
                    break;
                case "kozen.secondary.show_video":
                    result.put("status", handleSecondaryShowVideo(payload));
                    break;
                case "kozen.secondary.power":
                    result.put("status", handleSecondaryPower(payload));
                    break;
                case "kozen.secondary.brightness":
                    result.put("status", handleSecondaryBrightness(payload));
                    break;
                case "kozen.secondary.set_boot_logo":
                    result.put("status", handleSecondarySetBootLogo(payload));
                    break;

                default:
                    result.put("status", -1);
                    result.put("error", "Unknown type: " + type);
                    break;
            }
        } catch (Throwable t) {
            Log.e(TAG, "execute error for type=" + type, t);
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

    private int handleInstallOrUpdate(JSONObject payload) throws JSONException {
        String path = payload.getString("path"); // e.g. /sdcard/mdm_downloads/app.apk
        return terminal.installOrUpdate(path);
    }

    private int handleUninstall(JSONObject payload) throws JSONException {
        String pkg = payload.getString("package");
        return terminal.unInstall(pkg);
    }

    private int handleUpdateOta(JSONObject payload) throws JSONException {
        String path = payload.getString("path"); // OTA file
        return terminal.updateOTA(path);
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
        String path = payload.getString("path");
        return component.setBootLogo(path);
    }
}
