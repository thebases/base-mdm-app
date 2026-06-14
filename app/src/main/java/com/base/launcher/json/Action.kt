package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Action(
    var action: String? = null,
    var categories: String? = null,
    var packageId: String? = null,
    var activity: String? = null,
    var schemes: String? = null,
    var hosts: String? = null,
    var mimeTypes: String? = null
)
