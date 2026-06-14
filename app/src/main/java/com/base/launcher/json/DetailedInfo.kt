package com.base.launcher.json

import android.annotation.SuppressLint
import android.database.Cursor
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class DetailedInfo() {
    @field:JsonIgnore
    var id: Long = 0

    var ts: Long = 0
    var device: Device? = null
    var wifi: Wifi? = null
    var gps: Gps? = null
    var mobile: Mobile? = null
    var mobile2: Mobile? = null

    @SuppressLint("Range")
    constructor(cursor: Cursor) : this() {
        id = cursor.getLong(cursor.getColumnIndex("_id"))
        ts = cursor.getLong(cursor.getColumnIndex("ts"))
        device = Device().apply {
            batteryLevel = cursor.getInt(cursor.getColumnIndex("deviceBatteryLevel"))
            batteryCharging = cursor.getString(cursor.getColumnIndex("deviceBatteryCharging"))
            wifi = cursor.getInt(cursor.getColumnIndex("deviceWifi")) != 0
            gps = cursor.getInt(cursor.getColumnIndex("deviceGps")) != 0
            ip = cursor.getString(cursor.getColumnIndex("deviceIp"))
            keyguard = cursor.getInt(cursor.getColumnIndex("deviceKeyguard")) != 0
            ringVolume = cursor.getInt(cursor.getColumnIndex("deviceRingVolume"))
            mobileData = cursor.getInt(cursor.getColumnIndex("deviceMobileData")) != 0
            bluetooth = cursor.getInt(cursor.getColumnIndex("deviceBluetooth")) != 0
            usbStorage = cursor.getInt(cursor.getColumnIndex("deviceUsbStorage")) != 0
            memoryTotal = cursor.getInt(cursor.getColumnIndex("deviceMemoryTotal"))
            memoryAvailable = cursor.getInt(cursor.getColumnIndex("deviceMemoryAvailable"))
        }
        wifi = Wifi().apply {
            rssi = cursor.getInt(cursor.getColumnIndex("wifiRssi"))
            ssid = cursor.getString(cursor.getColumnIndex("wifiSsid"))
            security = cursor.getString(cursor.getColumnIndex("wifiSecurity"))
            state = cursor.getString(cursor.getColumnIndex("wifiState"))
            ip = cursor.getString(cursor.getColumnIndex("wifiIp"))
            tx = cursor.getLong(cursor.getColumnIndex("wifiTx"))
            rx = cursor.getLong(cursor.getColumnIndex("wifiRx"))
        }
        gps = Gps().apply {
            state = cursor.getString(cursor.getColumnIndex("gpsState"))
            lat = cursor.getDouble(cursor.getColumnIndex("gpsLat"))
            lon = cursor.getDouble(cursor.getColumnIndex("gpsLon"))
            alt = cursor.getDouble(cursor.getColumnIndex("gpsAlt"))
            speed = cursor.getDouble(cursor.getColumnIndex("gpsSpeed"))
            course = cursor.getDouble(cursor.getColumnIndex("gpsCourse"))
        }
        mobile = Mobile().apply {
            rssi = cursor.getInt(cursor.getColumnIndex("mobileRssi"))
            carrier = cursor.getString(cursor.getColumnIndex("mobileCarrier"))
            number = cursor.getString(cursor.getColumnIndex("mobileNumber"))
            imsi = cursor.getString(cursor.getColumnIndex("mobileImsi"))
            data = cursor.getInt(cursor.getColumnIndex("mobileData")) != 0
            ip = cursor.getString(cursor.getColumnIndex("mobileIp"))
            state = cursor.getString(cursor.getColumnIndex("mobileState"))
            simState = cursor.getString(cursor.getColumnIndex("mobileSimState"))
            tx = cursor.getLong(cursor.getColumnIndex("mobileTx"))
            rx = cursor.getLong(cursor.getColumnIndex("mobileRx"))
        }
        mobile2 = Mobile().apply {
            rssi = cursor.getInt(cursor.getColumnIndex("mobile2Rssi"))
            carrier = cursor.getString(cursor.getColumnIndex("mobile2Carrier"))
            number = cursor.getString(cursor.getColumnIndex("mobile2Number"))
            imsi = cursor.getString(cursor.getColumnIndex("mobile2Imsi"))
            data = cursor.getInt(cursor.getColumnIndex("mobile2Data")) != 0
            ip = cursor.getString(cursor.getColumnIndex("mobile2Ip"))
            state = cursor.getString(cursor.getColumnIndex("mobile2State"))
            simState = cursor.getString(cursor.getColumnIndex("mobile2SimState"))
            tx = cursor.getLong(cursor.getColumnIndex("mobile2Tx"))
            rx = cursor.getLong(cursor.getColumnIndex("mobile2Rx"))
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Device {
        var batteryLevel: Int? = null
        var batteryCharging: String? = null
        var wifi: Boolean? = null
        var gps: Boolean? = null
        var ip: String? = null
        var keyguard: Boolean? = null
        var ringVolume: Int? = null
        var mobileData: Boolean? = null
        var bluetooth: Boolean? = null
        var usbStorage: Boolean? = null
        var memoryTotal: Int? = null
        var memoryAvailable: Int? = null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Wifi {
        var rssi: Int? = null
        var ssid: String? = null
        var security: String? = null
        var state: String? = null
        var ip: String? = null
        var tx: Long? = null
        var rx: Long? = null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Gps {
        var state: String? = null
        var provider: String? = null
        var lat: Double? = null
        var lon: Double? = null
        var alt: Double? = null
        var speed: Double? = null
        var course: Double? = null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Mobile {
        var rssi: Int? = null
        var carrier: String? = null
        var number: String? = null
        var imsi: String? = null
        var data: Boolean? = null
        var ip: String? = null
        var state: String? = null
        var simState: String? = null
        var tx: Long? = null
        var rx: Long? = null
    }
}
