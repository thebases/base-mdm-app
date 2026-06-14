package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class DetailedInfoConfig(
    var sendData: Boolean? = null,
    var intervalMins: Int? = null
)
