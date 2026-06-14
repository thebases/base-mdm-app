package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class DeviceCreateOptions {
    var customer: String? = null
    var configuration: String? = null
    var groups: List<String>? = null

    fun setGroups(groups: Array<String>?) {
        this.groups = groups?.toList()
    }

    fun setGroups(groups: Set<String>?) {
        this.groups = groups?.toList()
    }

    fun setGroups(groups: String?) {
        this.groups = groups?.split(",")
    }

    fun getGroupSet(): Set<String>? = this.groups?.toHashSet()
}
