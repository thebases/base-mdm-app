package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
open class ServerResponse {
    var status: String? = null
    var message: String? = null
}
