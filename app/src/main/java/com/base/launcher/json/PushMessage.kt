package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.json.JSONObject

@JsonIgnoreProperties(ignoreUnknown = true)
open class PushMessage {
    var messageType: String? = null
    var payload: String? = null

    open fun getPayloadJSON(): JSONObject? {
        if (payload != null) {
            try {
                return JSONObject(payload!!)
            } catch (e: Exception) {
                // Bad payload
            }
        }
        return null
    }

    companion object {
        const val TYPE_CONFIG_UPDATING = "configUpdating"
        const val TYPE_CONFIG_UPDATED = "configUpdated"
        const val TYPE_RUN_APP = "runApp"
        const val TYPE_UNINSTALL_APP = "uninstallApp"
        const val TYPE_DELETE_FILE = "deleteFile"
        const val TYPE_PURGE_DIR = "purgeDir"
        const val TYPE_DELETE_DIR = "deleteDir"
        const val TYPE_PERMISSIVE_MODE = "permissiveMode"
        const val TYPE_RUN_COMMAND = "runCommand"
        const val TYPE_REBOOT = "reboot"
        const val TYPE_CLEAR_DOWNLOADS = "clearDownloadHistory"
        const val TYPE_INTENT = "intent"
        const val TYPE_GRANT_PERMISSIONS = "grantPermissions"
        const val TYPE_ADMIN_PANEL = "adminPanel"
        const val TYPE_UPDATEOTA = "updateOta"
        const val TYPE_DEVICE_ACTION = "deviceAction"
        const val TYPE_DEVICE_BROADCAST = "deviceBroadcast"
        const val TYPE_DEVICE_FACTORY_RESET = "deviceFactoryReset"
        const val ACTION_OTAUPDATE_PUSH = "com.xcheng.mdm.action.OTAUPDATE_PUSH"
        const val ACTION__APPSTORE_PUSH = "com.xcheng.mdm.action.APPSTORE_PUSH"
        const val EXTRA_PUSH_CONTENT = "content"
    }
}
