package com.base.launcher.util

import android.os.Parcel
import android.os.Parcelable

class AppInfo() : Parcelable {
    companion object {
        const val TYPE_APP = 0
        const val TYPE_WEB = 1
        const val TYPE_INTENT = 2

        @JvmField
        val CREATOR = object : Parcelable.Creator<AppInfo> {
            override fun createFromParcel(parcel: Parcel) = AppInfo(parcel)
            override fun newArray(size: Int) = arrayOfNulls<AppInfo>(size)
        }
    }

    var type: Int = 0
    var keyCode: Int? = null
    var name: CharSequence? = null
    var packageName: String? = null
    var url: String? = null
    var iconUrl: String? = null
    var screenOrder: Int? = null
    var useKiosk: Int = 0
    var longTap: Int = 0
    var intent: String? = null

    constructor(parcel: Parcel) : this() {
        type = parcel.readInt()
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        keyCode = parcel.readSerializable() as? Int
        name = parcel.readString()
        packageName = parcel.readString()
        url = parcel.readString()
        iconUrl = parcel.readString()
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        screenOrder = parcel.readSerializable() as? Int
        useKiosk = parcel.readInt()
        longTap = parcel.readInt()
        intent = parcel.readString()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(type)
        @Suppress("DEPRECATION")
        dest.writeSerializable(keyCode)
        dest.writeString(name?.toString())
        dest.writeString(packageName)
        dest.writeString(url)
        dest.writeString(iconUrl)
        @Suppress("DEPRECATION")
        dest.writeSerializable(screenOrder)
        dest.writeInt(useKiosk)
        dest.writeInt(longTap)
        dest.writeString(intent)
    }

    override fun describeContents() = 0
}
