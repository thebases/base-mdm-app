package com.base.launcher.json

import android.annotation.SuppressLint
import android.database.Cursor
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class Download() {
    @field:JsonIgnore
    var id: Long = 0

    var url: String? = null
    var path: String? = null
    var attempts: Long = 0
    var lastAttemptTime: Long = 0
    var isDownloaded: Boolean = false
    var isInstalled: Boolean = false

    constructor(download: Download) : this() {
        id = download.id
        url = download.url
        path = download.path
        attempts = download.attempts
        lastAttemptTime = download.lastAttemptTime
        isDownloaded = download.isDownloaded
        isInstalled = download.isInstalled
    }

    @SuppressLint("Range")
    constructor(cursor: Cursor) : this() {
        id = cursor.getLong(cursor.getColumnIndex("_id"))
        url = cursor.getString(cursor.getColumnIndex("url"))
        path = cursor.getString(cursor.getColumnIndex("path"))
        attempts = cursor.getLong(cursor.getColumnIndex("attempts"))
        lastAttemptTime = cursor.getLong(cursor.getColumnIndex("lastAttemptTime"))
        isDownloaded = cursor.getInt(cursor.getColumnIndex("downloaded")) != 0
        isInstalled = cursor.getInt(cursor.getColumnIndex("installed")) != 0
    }
}
