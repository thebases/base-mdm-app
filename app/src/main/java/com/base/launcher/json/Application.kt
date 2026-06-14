package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class Application {
    var type: String? = null
    var name: String? = null
    var pkg: String? = null
    var version: String? = null
    var code: Int? = null
    var url: String? = null
    var isShowIcon: Boolean = false
    var isRemove: Boolean = false
    var isRunAfterInstall: Boolean = false
    var isRunAtBoot: Boolean = false
    var isSkipVersion: Boolean = false
    var iconText: String? = null
    var icon: String? = null
    var screenOrder: Int? = null
    var keyCode: Int? = null
    var isBottom: Boolean = false
    var isLongTap: Boolean = false
    var intent: String? = null

    companion object {
        const val TYPE_APP = "app"
        const val TYPE_WEB = "web"
        const val TYPE_INTENT = "intent"
    }
}
