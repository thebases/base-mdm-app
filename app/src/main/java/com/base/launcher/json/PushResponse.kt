package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PushResponse(
    var status: String? = null,
    var data: List<PushMessage>? = null
)
