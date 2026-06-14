package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RemoteLogConfig(
    var packageId: String? = null,
    var logLevel: Int = 0,
    var filter: String? = null
)
