package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class ServerConfigResponse : ServerResponse() {
    var data: ServerConfig? = null
}
