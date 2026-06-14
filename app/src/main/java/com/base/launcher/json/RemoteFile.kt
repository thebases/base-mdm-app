package com.base.launcher.json

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class RemoteFile() {
    @field:JsonIgnore
    var id: Long = 0

    var url: String? = null
    var path: String? = null
    var lastUpdate: Long = 0
    var checksum: String? = null
    var isRemove: Boolean = false
    var description: String? = null
    var isVarContent: Boolean = false

    constructor(remoteFile: RemoteFile) : this() {
        id = remoteFile.id
        lastUpdate = remoteFile.lastUpdate
        url = remoteFile.url
        checksum = remoteFile.checksum
        isRemove = remoteFile.isRemove
        path = remoteFile.path
        description = remoteFile.description
        isVarContent = remoteFile.isVarContent
    }
}
