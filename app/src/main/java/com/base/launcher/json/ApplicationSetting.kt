package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApplicationSetting(
    var packageId: String? = null,
    var name: String? = null,
    var type: Int = 0,
    var value: String? = null,
    var isReadOnly: Boolean = false,
    var lastUpdate: Long = 0
)
