package com.base.launcher.json

import org.json.JSONObject

class PushMessageJson : PushMessage() {
    private var payloadJSON: JSONObject? = null

    constructor()

    constructor(messageType: String, payloadJSON: JSONObject) {
        this.messageType = messageType
        this.payloadJSON = payloadJSON
    }

    override fun getPayloadJSON(): JSONObject? = payloadJSON

    fun setPayloadJSON(payloadJSON: JSONObject?) {
        this.payloadJSON = payloadJSON
    }
}
