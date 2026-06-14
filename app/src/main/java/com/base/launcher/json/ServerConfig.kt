package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class ServerConfig {
    var newNumber: String? = null
    var backgroundColor: String? = null
    var textColor: String? = null
    var backgroundImageUrl: String? = null
    var password: String? = null
    var phone: String? = null
    var imei: String? = null
    var iconSize: Int? = null
    var title: String? = null
    var isDisplayStatus: Boolean = false
    var gps: Boolean? = null
    var bluetooth: Boolean? = null
    var wifi: Boolean? = null
    var mobileData: Boolean? = null
    var mainApp: String? = null
    var lockStatusBar: Boolean? = null
    var systemUpdateType: Int? = null
    var systemUpdateFrom: String? = null
    var systemUpdateTo: String? = null
    var appUpdateFrom: String? = null
    var appUpdateTo: String? = null
    var downloadUpdates: String? = null
    var factoryReset: Boolean? = null
    var reboot: Boolean? = null
    var lock: Boolean? = null
    var lockMessage: String? = null
    var passwordReset: String? = null
    var pushOptions: String? = null
    var keepaliveTime: Int? = null
    var requestUpdates: String? = null
    var disableLocation: Boolean? = null
    var appPermissions: String? = null
    var usbStorage: Boolean? = null
    var autoBrightness: Boolean? = null
    var brightness: Int? = null
    var manageTimeout: Boolean? = null
    var timeout: Int? = null
    var lockVolume: Boolean? = null
    var manageVolume: Boolean? = null
    var volume: Int? = null
    var passwordMode: String? = null
    var timeZone: String? = null
    var allowedClasses: String? = null
    var orientation: Int? = null
    var restrictions: String? = null
    var description: String? = null
    var custom1: String? = null
    var custom2: String? = null
    var custom3: String? = null
    var runDefaultLauncher: Boolean? = null
    var newServerUrl: String? = null
    var isLockSafeSettings: Boolean = false
    var isPermissive: Boolean = false
    var isDisableScreenshots: Boolean = false
    var isAutostartForeground: Boolean = false
    var isShowWifi: Boolean = false
    var appName: String? = null
    var vendor: String? = null
    var applications: MutableList<Application> = mutableListOf()
    var applicationSettings: MutableList<ApplicationSetting> = mutableListOf()
    var files: MutableList<RemoteFile> = mutableListOf()
    var actions: MutableList<Action> = mutableListOf()

    companion object {
        const val TITLE_NONE = "none"
        const val TITLE_DEVICE_ID = "deviceId"
        const val TITLE_DESCRIPTION = "description"
        const val TITLE_CUSTOM1 = "custom1"
        const val TITLE_CUSTOM2 = "custom2"
        const val TITLE_CUSTOM3 = "custom3"
        const val TITLE_IMEI = "imei"
        const val TITLE_SERIAL = "serialNumber"
        const val TITLE_EXTERNAL_IP = "externalIp"
        const val DEFAULT_ICON_SIZE = 100
        const val SYSTEM_UPDATE_DEFAULT = 0
        const val SYSTEM_UPDATE_INSTANT = 1
        const val SYSTEM_UPDATE_SCHEDULE = 2
        const val SYSTEM_UPDATE_MANUAL = 3
        const val PUSH_OPTIONS_MQTT_WORKER = "mqttWorker"
        const val PUSH_OPTIONS_MQTT_ALARM = "mqttAlarm"
        const val PUSH_OPTIONS_POLLING = "polling"
        const val APP_PERMISSIONS_ASK_LOCATION = "asklocation"
        const val APP_PERMISSIONS_DENY_LOCATION = "denylocation"
        const val APP_PERMISSIONS_ASK_ALL = "askall"
    }
}
