package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class DetailedInfoConfigResponse : ServerResponse() {
    var data: DetailedInfoConfig? = null
}
