package com.base.launcher.json

import org.json.JSONObject

class PushMessageJson : PushMessage {
    private var payloadJSON: JSONObject? = null

    constructor() : super()

    constructor(messageType: String, payloadJSON: JSONObject) : super() {
        this.messageType = messageType
        this.payloadJSON = payloadJSON
    }

    override fun getPayloadJSON(): JSONObject? = payloadJSON

    fun setPayloadJSON(payloadJSON: JSONObject?) {
        this.payloadJSON = payloadJSON
    }
}
