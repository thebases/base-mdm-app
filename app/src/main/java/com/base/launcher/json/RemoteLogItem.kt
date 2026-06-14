package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class RemoteLogItem {
    @field:JsonIgnore
    var id: Long = 0

    var timestamp: Long = 0
    var logLevel: Int = 0
    var packageId: String? = null
    var message: String? = null
}
