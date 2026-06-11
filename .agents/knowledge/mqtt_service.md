# MQTT Service — Technical Report

**Package:** `org.eclipse.paho.android.service`  
**Location:** `app/src/main/java/org/eclipse/paho/android/service/`  
**Total files:** 16  
**Origin:** Eclipse Paho MQTT Android Service 1.2.0, **customized by The Base team**  
**Date analysed:** 2026-06-11

---

## 1. Overview

This package is an embedded, source-level fork of the Eclipse Paho MQTT Android Service library. Rather than consuming the upstream library as a Gradle dependency, The Base team copied the source into the project and added proprietary modifications on top of the standard Paho code. This gives the team full control over the MQTT layer — most critically around keepalive scheduling, death detection, and build-config integration — without modifying upstream Paho client internals (`org.eclipse.paho.client.mqttv3`), which remain a compiled Gradle dependency.

The library's purpose is to run a persistent MQTT connection to the MDM broker (`mq.thebeanfamily.org:18883`, TLS) so that the MDM server can push policy commands to devices in real time.

---

## 2. Architecture: Three-Layer Design

```
[Application layer — com.base.launcher]
         │  binds / starts
         ▼
[MqttAndroidClient]  ← implements IMqttAsyncClient, extends BroadcastReceiver
         │  bound service connection
         ▼
[MqttService]        ← Android Service (START_STICKY)
         │  creates / manages
         ▼
[MqttConnection]     ← one per broker URI+clientId pair
         │  wraps
         ▼
[MqttAsyncClient]    ← upstream Paho client (compiled dep)
```

Each layer communicates through a specific mechanism:
- **MqttAndroidClient → MqttService**: `ServiceConnection` + direct method calls on `MqttService` (obtained via `MqttServiceBinder`)
- **MqttService → MqttAndroidClient**: `LocalBroadcastManager` intents (action = `MqttServiceConstants.CALLBACK_TO_ACTIVITY`)
- **MqttAsyncClient → MqttConnection**: callback interface `MqttCallbackExtended`

---

## 3. File-by-File Reference

### 3.1 `MqttService.java` — Android Service host (983 lines)

**Role:** The long-running Android `Service` that owns all active MQTT connections.

**Key facts:**
- Extends `Service`, declared with `START_STICKY` so Android restarts it if killed
- Declares `foregroundServiceType="specialUse|systemExempted"` for Android 14 foreground service rules
- Maintains `ConcurrentHashMap<String, MqttConnection> connections` keyed by `clientHandle` (`"clientId|serverURI"`)
- On `onStartCommand`: if `EXTRA_START_AT_BOOT` is in the intent, calls `PushNotificationMqttWrapper.getInstance().connect()` to restore the MDM connection after a reboot
- `BuildConfig.MQTT_SERVICE_FOREGROUND = true` enables foreground notification to keep process priority high
- Inner class `NetworkConnectionIntentReceiver` listens for `ConnectivityManager.CONNECTIVITY_ACTION`; on network restore calls `reconnect()` on each connection
- Implements `MqttTraceHandler` — routes debug/error/exception to Android `Log`
- `onBind()` returns `MqttServiceBinder` carrying a direct reference to `this`
- Calls `Utils.startStableForegroundService(this, NOTIFICATION_ID, notification)` for foreground start (method is from `ComponentLib` AAR)

---

### 3.2 `MqttAndroidClient.java` — Client API (1795 lines)

**Role:** The public-facing API class application code interacts with.

**Key facts:**
- Implements `IMqttAsyncClient` (full Paho client interface)
- Extends `BroadcastReceiver` — registered/unregistered during `connect()`/`disconnect()` to receive `CALLBACK_TO_ACTIVITY` intents
- Binds to `MqttService` via inner class `MyServiceConnection`
- Maintains `SparseArray<IMqttToken> tokenMap` keyed by message ID for async response correlation
- Enum `Ack { AUTO_ACK, MANUAL_ACK }` — when `MANUAL_ACK`, message is not ACKed until app calls `acknowledgeMessage()`
- `getSSLSocketFactory()` reads BKS keystore from assets to build custom `SSLSocketFactory` for TLS
- **The Base additions** — custom `Intent` extras: `EXTRA_START_AT_BOOT`, `EXTRA_DOMAIN`, `EXTRA_KEEPALIVE_TIME`, `EXTRA_PUSH_OPTIONS`, `EXTRA_DEVICE_ID`

**`onReceive()` dispatch table:**

| Intent action | Handler |
|---|---|
| `CONNECT_ACTION` | `connectAction()` → `notifyComplete()` on connect token |
| `CONNECT_EXTENDED_ACTION` | `connectExtendedAction()` → fires `MqttCallbackExtended.connectComplete()` |
| `MESSAGE_ARRIVED_ACTION` | `messageArrivedAction()` → unmarshals `ParcelableMqttMessage`, stores if QoS > 0, fires callback |
| `SUBSCRIBE_ACTION` | subscribe token completion |
| `UNSUBSCRIBE_ACTION` | unsubscribe token completion |
| `SEND_ACTION` | publish token completion |
| `MESSAGE_DELIVERED_ACTION` | delivery token completion |
| `ON_CONNECTION_LOST_ACTION` | `connectionLostAction()` → fires `MqttCallback.connectionLost()` |
| `DISCONNECT_ACTION` | disconnect token completion |
| `TRACE_ACTION` | routes to `MqttTraceHandler` if set |

---

### 3.3 `MqttConnection.java` — Per-connection state machine (1179 lines)

**Role:** Wraps a single `MqttAsyncClient` and manages its full lifecycle.

**Key facts:**
- Implements `MqttCallbackExtended`
- `instantiatePingSender()` reads `MqttAndroidConnectOptions.getPingType()` → creates `WorkerPingSender` (`PING_WORKER`) or `AlarmPingSender` (default `PING_ALARM`)
- `isConnecting` — `volatile boolean` prevents concurrent reconnect attempts
- `acquireWakeLock()` / `releaseWakeLock()` — `PARTIAL_WAKE_LOCK` held around `connect()` and `messageArrived()`
- `deliverBacklog()` — after reconnect, iterates `DatabaseMessageStore.getAllArrivedMessages()` and re-delivers unacknowledged messages
- `offline()` — when network is lost, triggers `connectionLost()` if not a clean session
- `reconnect()` — synchronized; supports Paho automatic reconnect or manual reconnect
- `defaultMessageListener` — if set, messages bypass `DatabaseMessageStore` entirely (fast path)
- Broadcasts callbacks to `MqttAndroidClient` via `LocalBroadcastManager` with `CALLBACK_TO_ACTIVITY`

---

### 3.4 `AlarmPingSender.java` — Alarm-based keepalive (197 lines)

**Role:** Default keepalive ping implementation using `AlarmManager`.

**Key facts:**
- Implements `MqttPingSender`
- One `PendingIntent` per client ID (action = `PING_SENDER + clientId`)
- **Android API compatibility matrix:**
  - API < 19: `AlarmManager.set()` (inexact)
  - API 19–22: `AlarmManager.setExact()`
  - API 23+: `AlarmManager.setExactAndAllowWhileIdle()` (survives Doze)
  - API 31+: checks `canScheduleExactAlarms()` first; logs warning if missing
- On receive: acquires `PARTIAL_WAKE_LOCK`, calls `PingDeathDetector.registerPing()`, calls `comms.checkForActivity()`, releases wake lock in both callbacks
- Calls `RemoteLogger.log()` to push ping events to MDM remote log

---

### 3.5 `WorkerPingSender.java` — WorkManager-based keepalive (117 lines)

**Role:** The Base-authored alternative to `AlarmPingSender`. Uses WorkManager so no exact-alarm permission is needed.

**Key facts:**
- Implements `MqttPingSender`; singleton
- `schedule()` converts ms → seconds and enqueues `OneTimeWorkRequest` with `ExistingWorkPolicy.REPLACE`
- `InternalWorker.doWork()` calls `comms.checkForActivity(null)` and `PingDeathDetector.registerPing()`
- **Known bug:** `doWork()` returns `null` instead of `Result.success()` (line 113)
- Tagged with `Const.WORK_TAG_COMMON` for bulk cancellation

---

### 3.6 `DatabaseMessageStore.java` — SQLite message persistence (463 lines)

**Role:** Durable storage for QoS 1/2 messages while the app is not actively consuming them.

**Database:** `mqttAndroidService.db` (separate from the main MDM `mdm.db`)  
**Table:** `MqttArrivedMessageTable`

| Column | Type | Notes |
|---|---|---|
| `messageId` | TEXT (PK) | UUID v4 |
| `clientHandle` | TEXT | client identifier |
| `destinationName` | TEXT | MQTT topic |
| `payload` | BLOB | raw bytes |
| `qos` | INTEGER | 0, 1, or 2 |
| `retained` | TEXT | "true"/"false" |
| `duplicate` | TEXT | "true"/"false" |
| `mtimestamp` | INTEGER | `System.currentTimeMillis()` |

**Methods:** `storeArrived()` (insert + return UUID), `discardArrived()` (delete by id+clientHandle), `getAllArrivedMessages()` (iterator ordered `mtimestamp ASC`), `clearArrivedMessages()` (clean session)

**Known bugs:**
- `getArrivedRowCount()` queries `MESSAGE_ID` column but reads `c.getInt(0)` — always returns 0 (line 228)
- Schema upgrade = DROP + recreate (undelivered messages lost)
- `MqttMessageHack` inner class exposes `protected setDuplicate()` for DB reconstruction

---

### 3.7 `MessageStore.java` — Persistence interface (103 lines)

Contract for the message store. Allows storage backend to be swapped. Methods: `storeArrived`, `discardArrived`, `getAllArrivedMessages`, `clearArrivedMessages`, `close`. Inner interface `StoredMessage`: `getMessageId`, `getClientHandle`, `getTopic`, `getMessage`.

---

### 3.8 `MqttServiceConstants.java` — String constants (98 lines)

Central namespace for all `LocalBroadcastManager` intent keys between `MqttService` and `MqttAndroidClient`.

| Key constant | Value pattern |
|---|---|
| `CALLBACK_TO_ACTIVITY` | `"MqttService.callbackToActivity.v0"` |
| `PING_SENDER` | `"MqttService.pingSender."` + clientId |
| `PING_WAKELOCK` | `"MqttService.client."` + clientId |
| `NON_MQTT_EXCEPTION` | `-1` |
| `VERSION` | `"v0"` |

Database column name aliases: `DUPLICATE`, `RETAINED`, `QOS`, `PAYLOAD`, `DESTINATION_NAME`, `CLIENT_HANDLE`, `MESSAGE_ID`.

---

### 3.9 `MqttAndroidConnectOptions.java` — Extended connect options (37 lines)

The Base's extension of `MqttConnectOptions`. Adds `pingType` field:

```java
public static final int PING_ALARM  = 0;  // use AlarmPingSender (default)
public static final int PING_WORKER = 1;  // use WorkerPingSender
```

`MqttConnection.instantiatePingSender()` reads this to select the implementation.

---

### 3.10 `PingDeathDetector.java` — Ping liveness watchdog (37 lines)

**The Base-authored singleton.** Detects that pings have silently stopped despite the service appearing alive.

- `registerPing()` — records `System.currentTimeMillis()` as `lastPingTimestamp`
- `detectPingDeath(Context)` — returns `true` if `lastPingTimestamp != 0` AND `lastPingTimestamp < now - 1,800,000ms` (30 minutes)
- Returns `false` on first start (no ping sent yet)
- Called by `StatusControlService` during periodic health checks; triggers forced reconnect if true

---

### 3.11 `MqttServiceBinder.java` — IPC binder (52 lines)

Extends `android.os.Binder`. Returned by `MqttService.onBind()`. Carries direct reference to `MqttService` and an `activityToken` string.

---

### 3.12 `MqttTokenAndroid.java` — Async operation token (253 lines)

Implements `IMqttToken`. Future-like object for MQTT operation completion.

- `isComplete` and `lastException` are `volatile`
- `waitForCompletion()` — `synchronized(waitObject)` + `Object.wait()`
- `waitForCompletion(long)` — throws `REASON_CODE_CLIENT_TIMEOUT` on expiry
- `notifyComplete()` / `notifyFailure()` — wake waiters, fire listener
- `delegate` — delegates `getMessageId()` to underlying wire-level token

---

### 3.13 `MqttDeliveryTokenAndroid.java` — Publish delivery token (54 lines)

Extends `MqttTokenAndroid`, implements `IMqttDeliveryToken`. Adds `message` field. `notifyDelivery(MqttMessage)` updates the stored message then calls `super.notifyComplete()`.

---

### 3.14 `MqttTraceHandler.java` — Trace interface (53 lines)

```java
void traceDebug(String tag, String message);
void traceError(String tag, String message);
void traceException(String tag, String message, Exception e);
```

Implemented by `MqttService`, which routes to Android `Log`.

---

### 3.15 `ParcelableMqttMessage.java` — Parcelable message wrapper (119 lines)

Wraps `MqttMessage` as Android `Parcelable` for transport in `Intent` extras.

**Parcel wire format:** `byte[]` payload → `int` qos → `boolean[]` `{retained, duplicate}` → `String` messageId.  
Used via `CALLBACK_MESSAGE_PARCEL` extra key in `MESSAGE_ARRIVED_ACTION` intents.

---

### 3.16 `Status.java` — Result enum (33 lines)

```java
enum Status { OK, ERROR, NO_RESULT }
```

`NO_RESULT` = async operation; result delivered via callback.

---

## 4. The Base Customizations vs. Upstream Paho 1.2.0

| Addition | File(s) | Purpose |
|---|---|---|
| `WorkerPingSender` | `WorkerPingSender.java` | WorkManager-based keepalive (no exact-alarm permission needed) |
| `MqttAndroidConnectOptions` | `MqttAndroidConnectOptions.java` | Carries `pingType` through connect options API |
| `PingDeathDetector` | `PingDeathDetector.java` | 30-minute ping watchdog for `StatusControlService` |
| Boot reconnect | `MqttService.java` | `EXTRA_START_AT_BOOT` → `PushNotificationMqttWrapper.connect()` on service start |
| Foreground service gate | `MqttService.java` | `BuildConfig.MQTT_SERVICE_FOREGROUND` enables/disables foreground notification |
| `RemoteLogger` integration | `AlarmPingSender.java`, `WorkerPingSender.java` | Sends ping events to server-side MDM log |
| Extra Intent fields | `MqttAndroidClient.java` | `EXTRA_DOMAIN`, `EXTRA_KEEPALIVE_TIME`, `EXTRA_PUSH_OPTIONS`, `EXTRA_DEVICE_ID` |

---

## 5. Connection Lifecycle

```
MqttAndroidClient.connect(options)
  → starts MqttService (Intent + extras)
  → MyServiceConnection.onServiceConnected() → MqttServiceBinder
  → MqttService.connect() → new MqttConnection → ConcurrentHashMap.put()
    → MqttConnection.connect()
      → instantiatePingSender()      → AlarmPingSender or WorkerPingSender
      → acquireWakeLock()
      → MqttAsyncClient.connect()   → TLS handshake mq.thebeanfamily.org:18883
        onSuccess → broadcast CONNECT_EXTENDED_ACTION
                 → MqttCallbackExtended.connectComplete()
      → PingSender.start()          → schedule first keepalive alarm/worker
      → releaseWakeLock()
```

**Reconnection after network loss:**
1. `NetworkConnectionIntentReceiver` → `MqttService.reconnect()`
2. `MqttConnection.reconnect()` checks `isConnecting` flag
3. On success: `deliverBacklog()` re-delivers stored messages

**Keepalive cycle:**
```
PingSender fires (AlarmManager or Worker)
  → PingDeathDetector.registerPing()
  → comms.checkForActivity() → MQTT PINGREQ → PINGRESP → release wake lock
  → PingSender.schedule(keepaliveMs)  [next cycle]
```

---

## 6. Inbound Message Flow

```
MQTT broker publishes to device topic
  → MqttAsyncClient → MqttConnection.messageArrived()
    → acquireWakeLock()
    → if defaultMessageListener set: call directly (no DB)
    → else: DatabaseMessageStore.storeArrived() → UUID
    → broadcast MESSAGE_ARRIVED_ACTION via LocalBroadcastManager
    → releaseWakeLock()
  → MqttAndroidClient.onReceive(MESSAGE_ARRIVED_ACTION)
    → unmarshal ParcelableMqttMessage
    → if AUTO_ACK: acknowledgeMessageArrival() → discardArrived()
    → messageCallback.messageArrived() → PushNotificationMqttWrapper
```

---

## 7. Known Issues and Risks

| Issue | Severity | Location |
|---|---|---|
| `WorkerPingSender.doWork()` returns `null` instead of `Result.success()` | Medium | `WorkerPingSender.java:113` |
| Singleton `WorkerPingSender.instance` is static — stale reference if service is recreated | Low | `WorkerPingSender.java:49` |
| `getArrivedRowCount()` always returns 0 (queries TEXT column as int) | Low | `DatabaseMessageStore.java:228` |
| No DB migration — upgrade = DROP all undelivered messages | Low | `DatabaseMessageStore.java:123` |
| `AlarmPingSender` on Android 12+ silently skips if `SCHEDULE_EXACT_ALARM` not granted | High | `AlarmPingSender.java:122` |
| `WorkerPingSender` effective delay may be capped to ~10–15 min by battery optimization | Medium | `WorkerPingSender.java:89` |

---

## 8. Configuration Surface

All MQTT connection parameters come from `BuildConfig` in `app/build.gradle`:

| BuildConfig field | Default value | Effect |
|---|---|---|
| `MQTT_DOMAIN` | `"mq.thebeanfamily.org"` | Broker hostname |
| `MQTT_PORT` | `18883` | Broker port |
| `MQTT_TLS` | `true` | TLS enabled |
| `MQTT_USERNAME` | `"basemdm"` | Authentication |
| `MQTT_PASSWORD` | `"theBase15112023@"` | Authentication |
| `MQTT_SERVICE_FOREGROUND` | `true` | Foreground notification |
| `ENABLE_PUSH` | `true` | Whether MQTT service starts at all |

At runtime, `keepaliveTime` is overridden by `ServerConfig.keepaliveTime` from the MDM server.

---

## 9. Inter-Package Integration Points

| Caller | Method/Class | Purpose |
|---|---|---|
| `com.base.launcher.util.PushNotificationMqttWrapper` | `MqttAndroidClient.connect()` | Establishes MDM MQTT connection |
| `com.base.launcher.util.PushNotificationMqttWrapper` | `MqttAndroidClient.subscribe()` | Subscribes to device-specific topic |
| `com.base.launcher.service.StatusControlService` | `PingDeathDetector.detectPingDeath()` | Health check watchdog |
| `com.base.launcher.util.PushNotificationMqttWrapper` | `MqttCallbackExtended.messageArrived()` | Receives inbound MDM commands |
| `com.base.launcher.util.RemoteLogger` | Called from `AlarmPingSender`, `WorkerPingSender` | Remote log of ping events |

---

## 10. File Summary Table

| File | Lines | Origin | Role |
|---|---|---|---|
| `MqttService.java` | 983 | Paho + The Base edits | Android Service host, connection map, network monitor |
| `MqttAndroidClient.java` | 1795 | Paho + The Base edits | Public API, service binding, broadcast dispatch |
| `MqttConnection.java` | 1179 | Paho + The Base edits | Per-connection state machine, wake lock, backlog |
| `AlarmPingSender.java` | 197 | Paho + The Base edits | AlarmManager-based PINGREQ sender |
| `WorkerPingSender.java` | 117 | **The Base only** | WorkManager-based PINGREQ sender |
| `DatabaseMessageStore.java` | 463 | Paho | SQLite message persistence |
| `MessageStore.java` | 103 | Paho | Message store interface |
| `MqttServiceConstants.java` | 98 | Paho | Intent/DB string constants |
| `MqttAndroidConnectOptions.java` | 37 | **The Base only** | Extended connect options with `pingType` |
| `PingDeathDetector.java` | 37 | **The Base only** | 30-minute ping watchdog |
| `MqttServiceBinder.java` | 52 | Paho | Service binder for IPC |
| `MqttTokenAndroid.java` | 253 | Paho | Async operation token / Future |
| `MqttDeliveryTokenAndroid.java` | 54 | Paho | Publish delivery token |
| `MqttTraceHandler.java` | 53 | Paho | Trace/log interface |
| `ParcelableMqttMessage.java` | 119 | Paho | Parcelable MQTT message for Intents |
| `Status.java` | 33 | Paho | OK/ERROR/NO_RESULT enum |
