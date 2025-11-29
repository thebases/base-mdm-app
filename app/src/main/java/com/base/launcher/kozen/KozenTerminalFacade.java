package com.base.launcher.kozen;

import android.content.Context;

import com.kozen.terminalmanager.TerminalManager;
import com.kozen.terminalmanager.aidl.location.entity.LocationClientOption;
import com.kozen.terminalmanager.aidl.network.entity.ApnConfiguration;
import com.kozen.terminalmanager.certification.ICertificationManager;
import com.kozen.terminalmanager.device.IDeviceManager;
import com.kozen.terminalmanager.deviceinfo.IDeviceInfoManager;
import com.kozen.terminalmanager.location.IGeoFenceCreateListener;
import com.kozen.terminalmanager.location.ILocationChangedListener;
import com.kozen.terminalmanager.location.ILocationManager;

import com.kozen.terminalmanager.location.constant.LocationConstant;

import com.kozen.terminalmanager.network.INetworkManager;
import com.kozen.terminalmanager.resource.IResourceManager;
import com.kozen.terminalmanager.resource.OnAppUpdateListener;
import com.kozen.terminalmanager.resource.OnUpdateCustomResListener;
import com.kozen.terminalmanager.resource.OnUpdateOTAListener;


import java.util.List;

/**
 * Thin wrapper around KOZEN TerminalManagerService exposing all APIs.
 *
 * This keeps all Kozen-specific code in a single place, so the rest of your
 * MDM launcher only depends on this facade.
 */
public class KozenTerminalFacade {

    private static KozenTerminalFacade instance;

    public static KozenTerminalFacade get() {
        if (instance == null) {
            instance = new KozenTerminalFacade();
        }
        return instance;
    }

    private final ICertificationManager certMgr;
    private final IDeviceInfoManager infoMgr;
    private final IDeviceManager devMgr;
    private final ILocationManager locMgr;
    private final INetworkManager netMgr;
    private final IResourceManager resMgr;

    private KozenTerminalFacade() {
        certMgr = TerminalManager.INSTANCE.getCertificationManager();
        infoMgr = TerminalManager.INSTANCE.getDeviceInfoManager();
        devMgr  = TerminalManager.INSTANCE.getDeviceManager();
        locMgr  = TerminalManager.INSTANCE.getLocationManager();
        netMgr  = TerminalManager.INSTANCE.getNetworkManager();
        resMgr  = TerminalManager.INSTANCE.getResourceManager();
    }

    // ---------------------------
    // 3.2 Certification module
    // ---------------------------
    public int updateAppSignature(String certData) {
        return certMgr.updateAppSignature(certData);
    }

    public int deleteAppSignature(String certData) {
        return certMgr.deleteAppSignature(certData);
    }

    public List<String> getAppSignatureInfo() {
        return certMgr.getAppSignatureInfo();
    }

    // ---------------------------
    // 3.3 Device information module
    // ---------------------------
    public String getSdkServiceVersion() {
        return infoMgr.getSdkServiceVersion();
    }

    public String getSerialNo() {
        return infoMgr.getSerialNo();
    }

    public String[] getImsi() {
        return infoMgr.getImsi();
    }

    public String[] getImei() {
        return infoMgr.getImei();
    }

    public String getVendorName() {
        return infoMgr.getVendorName();
    }

    public String getDeviceModel() {
        return infoMgr.getDeviceModel();
    }

    public String getOsVersion() {
        return infoMgr.getOsVersion();
    }

    public String getKernelVersion() {
        return infoMgr.getKernelVersion();
    }

    public String getMcuVersion() {
        return infoMgr.getMcuVersion();
    }

    public String getHardwareVersion() {
        return infoMgr.getHardwareVersion();
    }

    public String getEmvKernelVersion() {
        return infoMgr.getEmvKernelVersion();
    }

    public String getTUSN() {
        return infoMgr.getTUSN();
    }

    public String getCSN() {
        return infoMgr.getCSN();
    }

    // ---------------------------
    // 3.4 Device module
    // ---------------------------
    public int setSystemTime(long timestamp) {
        return devMgr.setSystemTime(timestamp);
    }

    public long getSystemTime() {
        return devMgr.getSystemTime();
    }

    public int setTimeZone(String tz) {
        return devMgr.setTimeZone(tz);
    }

    public String getTimeZone() {
        return devMgr.getTimeZone();
    }

    public void reboot() {
        devMgr.reboot();
    }

    public void shutdown() {
        devMgr.shutdown();
    }

    public int setPCIReboot(long millis) {
        return devMgr.setPCIReboot(millis);
    }

    public int cancelPCIReboot() {
        return devMgr.cancelPCIReboot();
    }

    public void setSilentInstall(boolean open) {
        devMgr.setSilentInstall(open);
    }

    public void forcePermission(boolean open) {
        devMgr.forcePermission(open);
    }

    // ---------------------------
    // 3.5 Location module
    // ---------------------------
    public int openLocation() {
        return locMgr.open();
    }

    public int openLocation(String key) {
        return locMgr.open(key);
    }

    public int openLocation(LocationConstant.LocationType type) {
        return locMgr.open(type);
    }

    public int openLocation(String key, LocationConstant.LocationType type) {
        return locMgr.open(key, type);
    }

    public int setLocationOption(LocationClientOption opt) {
        return locMgr.setLocationOption(opt);
    }

    public int startOnceLocation() {
        return locMgr.startOnceLocation();
    }

    public int registerLocationListener(ILocationChangedListener l) {
        return locMgr.registerLocationListener(l);
    }

    public int unRegisterLocationListener() {
        return locMgr.unRegisterLocationListener();
    }

    public int stopLocation() {
        return locMgr.stopLocation();
    }

    public int addGeoFence(double lon, double lat, float radius, String customId) {
        return locMgr.addGeoFence(lon, lat, radius, customId);
    }

    public int registerGeoFenceCreateListener(IGeoFenceCreateListener l) {
        return locMgr.registerGeoFenceCreateListener(l);
    }

    public int setGeoFenceResultAction(String action, String pkgName) {
        return locMgr.setGeoFenceResultAction(action, pkgName);
    }

    public int removeAllGeoFence() {
        return locMgr.removeAllGeoFence();
    }

    public int addToBlockOpenAppList(String pkgName) {
        return locMgr.addToBlockOpenAppList(pkgName);
    }

    public int removeFromBlockOpenAppList(String pkgName) {
        return locMgr.removeFromBlockOpenAppList(pkgName);
    }

    public boolean isInBlockOpenAppList(String pkgName) {
        return locMgr.isInBlockOpenAppList(pkgName);
    }

    public List<String> getBlockOpenAppList() {
        return locMgr.getBlockOpenAppList();
    }

    public void destroyLocation() {
        locMgr.onDestroy();
    }

    // ---------------------------
    // 3.6 Network module
    // ---------------------------
    public int addApn(ApnConfiguration config) {
        return netMgr.addApn(config);
    }

    public int enableApn(String name) {
        return netMgr.enableApn(name);
    }

    // ---------------------------
    // 3.7 Resource module
    // ---------------------------
    public int installOrUpdate(String path) {
        return resMgr.installOrUpdate(path);
    }

    public int unInstall(String pkgName) {
        return resMgr.unInstall(pkgName);
    }

    public int updateOTA(String path) {
        return resMgr.updateOTA(path);
    }

    public int updateCustomRes(String path) {
        return resMgr.updateCustomRes(path);
    }

    public int installOrUpdateWithListener(String path, OnAppUpdateListener listener) {
        return resMgr.installOrUpdateWithListener(path, listener);
    }

    public int updateCustomResWithListener(String path, OnUpdateCustomResListener listener) {
        return resMgr.updateCustomResWithListener(path, listener);
    }

    public int updateOTAWithListener(String path, OnUpdateOTAListener listener) {
        return resMgr.updateOTAWithListener(path, listener);
    }
}
