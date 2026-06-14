package com.base.launcher.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.json.PushMessageJson
import com.base.launcher.json.ServerConfig
import com.base.launcher.worker.PushNotificationProcessor
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.android.service.MqttAndroidConnectOptions
import org.eclipse.paho.android.service.PingDeathDetector
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject
import java.util.LinkedList
import java.util.concurrent.TimeUnit

class PushNotificationMqttWrapper private constructor() {
    companion object {
        private var instance: PushNotificationMqttWrapper? = null

        @JvmStatic
        fun getInstance(): PushNotificationMqttWrapper {
            if (instance == null) instance = PushNotificationMqttWrapper()
            return instance!!
        }

        private const val WORKER_TAG_MQTT_RECONNECT = "com.base.launcher.WORK_TAG_MQTT_RECONNECT"
        private const val MQTT_RECONNECT_INTERVAL_SEC = 900
        private const val CONNECTION_LOOP_PROTECTION_TIME_MS = 60000
        private const val CONNECTION_LOOP_CRITICAL_COUNT = 15
    }

    private var client: MqttAndroidClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private val connectHangupMonitorHandler = Handler(Looper.getMainLooper())
    private var debugReceiver: BroadcastReceiver? = null
    private var context: Context? = null
    private var needProcessConnectExtended = false
    private val connectionLoopProtectionArray = LinkedList<Long>()

    fun connect(
        context: Context, host: String, port: Int, useSsl: Boolean, username: String,
        password: String, pushType: String, keepaliveTime: Int,
        deviceId: String, onSuccess: Runnable?, onFailure: Runnable?
    ) {
        Log.d(Const.LOG_TAG, "connect() called:$host - port:$port - useTls:$useSsl - username:$username - password:$password")
        this.context = context
        cancelReconnectionAfterFailure(context)

        if (client?.isConnected == true) {
            Log.d(Const.LOG_TAG, "MQTT client is already connected")
            if (onSuccess != null) handler.post(onSuccess)
            return
        }

        val connectOptions = MqttAndroidConnectOptions().apply {
            setAutomaticReconnect(true)
            setCleanSession(false)
            if (pushType == ServerConfig.PUSH_OPTIONS_MQTT_WORKER) {
                setPingType(MqttAndroidConnectOptions.PING_WORKER)
                setKeepAliveInterval(Const.DEFAULT_PUSH_WORKER_KEEPALIVE_TIME_SEC)
            } else {
                setPingType(MqttAndroidConnectOptions.PING_ALARM)
                setKeepAliveInterval(keepaliveTime)
            }
            setUserName(username)
            setPassword(password.toCharArray())
        }

        val serverUri = if (useSsl) "ssl://$host:$port" else "tcp://$host:$port"

        client?.unregisterResources()
        client = MqttAndroidClient(context, serverUri, deviceId).apply {
            setTraceEnabled(true)
            setDefaultMessageListener(mqttMessageListener)
        }
        setupDebugging(context)

        client!!.setCallback(object : MqttCallbackExtended {
            override fun connectionLost(cause: Throwable?) {}
            override fun messageArrived(topic: String, message: MqttMessage) {}
            override fun deliveryComplete(token: IMqttDeliveryToken) {}
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                if (reconnect || needProcessConnectExtended) {
                    RemoteLogger.log(context, Const.LOG_VERBOSE, "Reconnect complete")
                    if (checkConnectionLoop()) {
                        subscribe(context, deviceId, null, null)
                    } else {
                        RemoteLogger.log(context, Const.LOG_ERROR, "Reconnection loop detected! You have multiple devices with ID=$deviceId! MQTT service stopped.")
                        disconnect(context)
                    }
                }
            }
        })

        try {
            needProcessConnectExtended = false
            connectHangupMonitorHandler.postDelayed({
                RemoteLogger.log(context, Const.LOG_WARN, "MQTT connection timeout, disconnecting")
                try { client?.disconnect() } catch (e: Exception) { e.printStackTrace() }
                scheduleReconnectionAfterFailure(context, host, port, useSsl, username, password, pushType, keepaliveTime, deviceId)
                if (onFailure != null) handler.post(onFailure)
            }, 30000)

            client!!.connect(connectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    connectHangupMonitorHandler.removeCallbacksAndMessages(null)
                    subscribe(context, deviceId, onSuccess, onFailure)
                }
                override fun onFailure(asyncActionToken: IMqttToken, e: Throwable) {
                    e.printStackTrace()
                    connectHangupMonitorHandler.removeCallbacksAndMessages(null)
                    RemoteLogger.log(context, Const.LOG_WARN, "MQTT connection failure")
                    scheduleReconnectionAfterFailure(context, host, port, useSsl, username, password, pushType, keepaliveTime, deviceId)
                    needProcessConnectExtended = true
                    if (onFailure != null) handler.post(onFailure)
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
            if (onFailure != null) handler.post(onFailure)
        }
    }

    private fun checkConnectionLoop(): Boolean {
        val now = System.currentTimeMillis()
        val iter = connectionLoopProtectionArray.iterator()
        while (iter.hasNext()) {
            if (iter.next() < now - CONNECTION_LOOP_PROTECTION_TIME_MS) iter.remove()
        }
        connectionLoopProtectionArray.add(now)
        return connectionLoopProtectionArray.size <= CONNECTION_LOOP_CRITICAL_COUNT
    }

    private val mqttMessageListener = object : IMqttMessageListener {
        @Throws(Exception::class)
        override fun messageArrived(topic: String, message: MqttMessage) {
            handler.post {
                try {
                    val obj = JSONObject(String(message.payload))
                    val messageType = obj.getString("messageType")
                    val msg = PushMessageJson(messageType, obj.optJSONObject("payload"))
                    PushNotificationProcessor.process(msg, context!!)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun subscribe(context: Context, deviceId: String, onSuccess: Runnable?, onFailure: Runnable?) {
        try {
            client!!.subscribe(deviceId, BuildConfig.MQTT_QOS, mqttMessageListener)
            if (onSuccess != null) {
                RemoteLogger.log(context, Const.LOG_DEBUG, "MQTT connection established")
                handler.post(onSuccess)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            RemoteLogger.log(context, Const.LOG_DEBUG, "Exception while subscribing: ${e.message}")
            if (onFailure != null) handler.post(onFailure)
        }
    }

    private fun setupDebugging(context: Context) {
        if (debugReceiver == null) {
            debugReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val errorMessage = intent.getStringExtra("MqttService.errorMessage")
                    if (errorMessage != null) {
                        Log.d(Const.LOG_TAG, "${intent.getStringExtra("MqttService.traceTag")} $errorMessage")
                    }
                }
            }
            LocalBroadcastManager.getInstance(context)
                .registerReceiver(debugReceiver!!, IntentFilter("MqttService.callbackToActivity.v0"))
        }
    }

    fun disconnect(context: Context) {
        try {
            cancelReconnectionAfterFailure(context)
            RemoteLogger.log(context, Const.LOG_DEBUG, "MQTT client disconnected by user request")
            client?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        client = null
        debugReceiver?.let {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
        }
        debugReceiver = null
    }

    private fun cancelReconnectionAfterFailure(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORKER_TAG_MQTT_RECONNECT)
    }

    private fun scheduleReconnectionAfterFailure(
        context: Context, host: String, port: Int, useSsl: Boolean,
        username: String, password: String, pushType: String, keepaliveTime: Int, deviceId: String
    ) {
        RemoteLogger.log(context, Const.LOG_INFO, "Scheduling MQTT reconnection in $MQTT_RECONNECT_INTERVAL_SEC sec")
        val data = Data.Builder()
            .putString("host", host)
            .putInt("port", port)
            .putBoolean("tls", useSsl)
            .putString("username", username)
            .putString("password", password)
            .putString("pushType", pushType)
            .putInt("keepalive", keepaliveTime)
            .putString("deviceId", deviceId)
            .build()
        val queryRequest = OneTimeWorkRequest.Builder(ReconnectAfterFailureWorker::class.java)
            .addTag(Const.WORK_TAG_COMMON)
            .setInitialDelay(MQTT_RECONNECT_INTERVAL_SEC.toLong(), TimeUnit.SECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORKER_TAG_MQTT_RECONNECT, ExistingWorkPolicy.REPLACE, queryRequest)
    }

    fun checkPingDeath(context: Context): Boolean {
        return client != null && client!!.isConnected && PingDeathDetector.getInstance().detectPingDeath(context)
    }

    class ReconnectAfterFailureWorker(
        @NonNull private val context: Context,
        @NonNull params: WorkerParameters
    ) : Worker(context, params) {
        @NonNull
        override fun doWork(): Result {
            val data = inputData
            getInstance().connect(
                context,
                data.getString("host") ?: "",
                data.getInt("port", 1883),
                data.getBoolean("tls", false),
                data.getString("username") ?: "",
                data.getString("password") ?: "",
                data.getString("pushType") ?: "",
                data.getInt("keepalive", Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC),
                data.getString("deviceId") ?: "",
                null, null
            )
            return Result.success()
        }
    }
}
