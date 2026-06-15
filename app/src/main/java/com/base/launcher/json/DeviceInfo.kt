package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class DeviceInfo {
    var model: String? = null
    var permissions: MutableList<Int> = mutableListOf()
    var applications: MutableList<Application> = mutableListOf()
    var files: MutableList<RemoteFile> = mutableListOf()
    var deviceId: String? = null
    var phone: String? = null
    var imei: String? = null
    var isMdmMode: Boolean = false
    var batteryLevel: Int = 0
    @get:JvmName("isBatteryCharging")
    var batteryCharging: String? = null
    var androidVersion: String? = null
    var factoryReset: Boolean? = null
    var kioskMode: Boolean = false
    var location: Location? = null
    var launcherType: String? = null
    var launcherPackage: String? = null
    var isDefaultLauncher: Boolean = false
    var iccid: String? = null
    var imsi: String? = null
    var phone2: String? = null
    var imei2: String? = null
    var iccid2: String? = null
    var imsi2: String? = null
    var cpu: String? = null
    var serial: String? = null
    var custom1: String? = null
    var custom2: String? = null
    var custom3: String? = null

    class Location {
        var ts: Long = 0
        var lat: Double = 0.0
        var lon: Double = 0.0
    }
}
