package com.base.launcher.util

import android.content.Context
import android.util.Log
import com.base.launcher.Const
import com.base.launcher.db.DatabaseHelper
import com.base.launcher.db.LogConfigTable
import com.base.launcher.db.LogTable
import com.base.launcher.json.RemoteLogConfig
import com.base.launcher.json.RemoteLogItem
import com.base.launcher.worker.RemoteLogWorker

object RemoteLogger {
    @JvmField
    var lastLogRemoval: Long = 0

    @JvmStatic
    fun updateConfig(context: Context, rules: List<RemoteLogConfig>) {
        LogConfigTable.replaceAll(DatabaseHelper.instance(context).writableDatabase, rules)
    }

    @JvmStatic
    fun log(context: Context, level: Int, message: String) {
        when (level) {
            Const.LOG_VERBOSE -> Log.v(Const.LOG_TAG, message)
            Const.LOG_DEBUG   -> Log.d(Const.LOG_TAG, message)
            Const.LOG_INFO    -> Log.i(Const.LOG_TAG, message)
            Const.LOG_WARN    -> Log.w(Const.LOG_TAG, message)
            Const.LOG_ERROR   -> Log.e(Const.LOG_TAG, message)
        }
        val item = RemoteLogItem().apply {
            timestamp = System.currentTimeMillis()
            logLevel = level
            packageId = context.packageName
            this.message = message
        }
        postLog(context, item)
    }

    @JvmStatic
    fun postLog(context: Context, item: RemoteLogItem) {
        val dbHelper = DatabaseHelper.instance(context)
        var db = dbHelper.readableDatabase
        if (LogConfigTable.match(db, item)) {
            db = dbHelper.writableDatabase
            LogTable.insert(db, item)
            sendLogsToServer(context)
        }
        val now = System.currentTimeMillis()
        if (now > lastLogRemoval + 3600000L) {
            LogTable.deleteOldItems(dbHelper.writableDatabase)
            lastLogRemoval = now
        }
    }

    @JvmStatic
    fun resetState() {
        RemoteLogWorker.resetState()
    }

    @JvmStatic
    fun sendLogsToServer(context: Context) {
        RemoteLogWorker.scheduleUpload(context)
    }
}
