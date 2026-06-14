package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class RemoteLogConfigResponse : ServerResponse() {
    var data: List<RemoteLogConfig>? = null
}
