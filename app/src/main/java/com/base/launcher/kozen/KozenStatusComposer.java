package com.base.launcher.kozen;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class KozenStatusComposer {

    public static JSONObject buildDeviceStatus() throws JSONException {
        KozenTerminalFacade t = KozenTerminalFacade.get();
        JSONObject obj = new JSONObject();

        obj.put("sdkVersion", t.getSdkServiceVersion());
        obj.put("serialNo", t.getSerialNo());
        obj.put("vendorName", t.getVendorName());
        obj.put("deviceModel", t.getDeviceModel());
        obj.put("osVersion", t.getOsVersion());
        obj.put("kernelVersion", t.getKernelVersion());
        obj.put("mcuVersion", t.getMcuVersion());
        obj.put("hardwareVersion", t.getHardwareVersion());
        obj.put("emvKernelVersion", t.getEmvKernelVersion());
        obj.put("TUSN", t.getTUSN());
        obj.put("CSN", t.getCSN());

        JSONArray imsiArr = new JSONArray();
        for (String imsi : t.getImsi()) imsiArr.put(imsi);
        obj.put("imsi", imsiArr);

        JSONArray imeiArr = new JSONArray();
        for (String imei : t.getImei()) imeiArr.put(imei);
        obj.put("imei", imeiArr);

        return obj;
    }
}
