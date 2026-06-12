# Base MDM App — API Reference (kiosk fields removed)

> Generated from fresh codebase scan reflecting current branch state (`base_mdm_lite`).  
> Compare with `api.md` to see what changed — kiosk fields have been removed throughout.

---

## Network Configuration

**Framework**: Retrofit 2 + Jackson JSON  
**Source**: `server/ServerServiceKeeper.java`, `server/ServerUrl.java`

| Setting | Value |
|---|---|
| Connection timeout | 10,000 ms (`Const.CONNECTION_TIMEOUT`) |
| Read timeout (default) | 10,000 ms |
| Read timeout (long polling) | 300,000 ms (`Const.LONG_POLLING_READ_TIMEOUT`) |
| Write timeout | 10,000 ms |
| SSL | Normal; unsafe mode when `BuildConfig.TRUST_ANY_CERTIFICATE = true` |

**Base URL construction** (`ServerUrl.java`):
```
{protocol}://{host}[:{port}]/{project}
```
- Primary base URL: `SettingsHelper.getBaseUrl()`
- Secondary base URL: `SettingsHelper.getSecondaryBaseUrl()`
- Fallback: `BuildConfig.BASE_URL`
- `project`: path component stored separately in device settings

**Common Request Headers**:

| Header | Direction | Description |
|---|---|---|
| `X-Request-Signature` | Request | SHA-1 of request body (`BuildConfig.REQUEST_SIGNATURE` prefix) |
| `X-CPU-Arch` | Request | Device CPU architecture string |
| `Content-Type: application/json` | Request | Explicit on POST/PUT endpoints |
| `X-IP-Address` | Response | Client's external IP returned by server |
| `X-Response-Signature` | Response | SHA-1 of response body for integrity check |

---

## Endpoints

All paths are relative to the base URL. `{project}` and `{number}` are Retrofit `@Path` parameters injected per-request.

---

### 1. Enroll Device

```
POST /{project}/rest/public/sync/configuration/{number}
```

**Source**: `ServerService.enrollAndGetServerConfig()`  
**Called from**: `GetServerConfigTask.enrollPlain()`, `GetServerConfigTask.enrollSecure()`  
**Purpose**: First-time device enrollment; returns server configuration.

**Path params**:

| Param | Type | Description |
|---|---|---|
| `project` | String | Server project identifier |
| `number` | String | Device ID |

**Request headers**: `X-Request-Signature`, `X-CPU-Arch`  
**Request body**: [`DeviceEnrollOptions`](#deviceenrolloptions)  
**Response**: [`ServerConfigResponse`](#serverconfigresponse)

**Variant**: `enrollAndGetServerConfigRaw()` → `Call<ResponseBody>` (used for signature verification before deserialization)

---

### 2. Get Server Configuration

```
GET /{project}/rest/public/sync/configuration/{number}
```

**Source**: `ServerService.getServerConfig()`  
**Called from**: `GetServerConfigTask.getServerConfigPlain()`, `GetServerConfigTask.getServerConfigSecure()`  
**Purpose**: Periodic sync of device configuration.

**Path params**: same as Enroll Device  
**Request headers**: `X-Request-Signature`, `X-CPU-Arch`  
**Response**: [`ServerConfigResponse`](#serverconfigresponse)

**Variant**: `getServerConfigRaw()` → `Call<ResponseBody>`

---

### 3. Send Device Information

```
POST /{project}/rest/public/sync/info
```

**Source**: `ServerService.sendDevice()`  
**Called from**: `SendDeviceInfoTask.doInBackground()`, `SendDeviceInfoWorker.doWork()`  
**Purpose**: Periodic heartbeat — sends device state to server. Runs every 15 min via `SendDeviceInfoWorker`.

**Request headers**: `Content-Type: application/json`  
**Request body**: [`DeviceInfo`](#deviceinfo)  
**Response**: `ResponseBody` (success = HTTP 2xx)

---

### 4. Query Push Notifications (Polling)

```
GET /{project}/rest/notifications/device/{number}
```

**Source**: `ServerService.queryPushNotifications()`  
**Called from**: `PushNotificationWorker.doPollingWork()`  
**Purpose**: Short-poll for pending push messages.

**Request headers**: `X-Request-Signature`  
**Response**: [`PushResponse`](#pushresponse)

---

### 5. Long Polling for Notifications

```
GET /{project}/rest/notification/polling/{number}
```

**Source**: `ServerService.queryPushLongPolling()`  
**Called from**: `PushLongPollingService.pollingRunnable`  
**Purpose**: Blocking long-poll; server holds connection until a message arrives.  
**Special**: Read timeout overridden to 300,000 ms. Runs in `PushLongPollingService` foreground service.

**Request headers**: `X-Request-Signature`  
**Response**: [`PushResponse`](#pushresponse)

---

### 6. Get Remote Log Configuration

```
GET /{project}/rest/plugins/devicelog/log/rules/{number}
```

**Source**: `ServerService.getRemoteLogConfig()`  
**Called from**: `GetRemoteLogConfigTask.doInBackground()`  
**Purpose**: Retrieves which packages and log levels to capture for remote logging.

**Response**: [`RemoteLogConfigResponse`](#remotelogconfigresponse)

---

### 7. Send Logs to Server

```
POST /{project}/rest/plugins/devicelog/log/list/{number}
```

**Source**: `ServerService.sendLogs()`  
**Called from**: `RemoteLogWorker.upload()`  
**Purpose**: Batched log upload. Max 10 items per request. Scheduled at 1-min intervals; 15-min retry on failure.

**Request headers**: `Content-Type: application/json`  
**Request body**: `List<`[`RemoteLogItem`](#remotelogitem)`>` (max 10 per batch)  
**Response**: `ResponseBody`

---

### 8. Send Detailed Device Information

```
PUT /{project}/rest/plugins/deviceinfo/deviceinfo/public/{number}
```

**Source**: `ServerService.sendDetailedInfo()`  
**Purpose**: Telemetry — sends rich device status snapshot.  
**Note**: Not called in the free/lite build.

**Request headers**: `Content-Type: application/json`  
**Request body**: `List<`[`DetailedInfo`](#detailedinfo)`>`  
**Response**: `ResponseBody`

---

### 9. Send Location Data

```
PUT /{project}/rest/plugins/devicelocations/public/update/{number}
```

**Source**: `ServerService.sendLocations()`  
**Purpose**: Bulk location history upload.

**Request headers**: `Content-Type: application/json`  
**Request body**: `List<`[`LocationTable.Location`](#locationtablelocation)`>`  
**Response**: `ResponseBody`

---

### 10. Get Detailed Info Configuration

```
GET /{project}/rest/plugins/deviceinfo/deviceinfo-plugin-settings/device/{number}
```

**Source**: `ServerService.getDetailedInfoConfig()`  
**Purpose**: Retrieves server settings for how frequently detailed info should be sent.

**Response**: [`DetailedInfoConfigResponse`](#detailedinfoconfigresponse)

---

### 11. Confirm Device Reset

```
POST /{project}/rest/plugins/devicereset/public/{number}
```

**Source**: `ServerService.confirmDeviceReset()`  
**Called from**: `ConfirmDeviceResetTask.doInBackground()`  
**Purpose**: Acknowledges that the device has performed a factory reset.

**Request headers**: `Content-Type: application/json`  
**Request body**: [`DeviceInfo`](#deviceinfo)  
**Response**: `ResponseBody`

---

### 12. Confirm Device Reboot

```
POST /{project}/rest/plugins/devicereset/public/reboot/{number}
```

**Source**: `ServerService.confirmReboot()`  
**Called from**: `ConfirmRebootTask.doInBackground()`  
**Purpose**: Acknowledges that the device has rebooted.

**Request headers**: `Content-Type: application/json`  
**Request body**: [`DeviceInfo`](#deviceinfo)  
**Response**: `ResponseBody`

---

### 13. Confirm Password Reset

```
POST /{project}/rest/plugins/devicereset/public/password/{number}
```

**Source**: `ServerService.confirmPasswordReset()`  
**Called from**: `ConfirmPasswordResetTask.doInBackground()`  
**Purpose**: Acknowledges that the device password has been reset.

**Request headers**: `Content-Type: application/json`  
**Request body**: [`DeviceInfo`](#deviceinfo)  
**Response**: `ResponseBody`

---

## Data Models

### DeviceEnrollOptions

**Source**: `json/DeviceEnrollOptions.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`

| Field | Type | Description |
|---|---|---|
| `customer` | String | Customer identifier |
| `configuration` | String | Configuration name |
| `groups` | List\<String\> | Device group memberships |

---

### DeviceCreateOptions

**Source**: `json/DeviceCreateOptions.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`

| Field | Type | Description |
|---|---|---|
| `customer` | String | Customer identifier |
| `configuration` | String | Configuration name |
| `groups` | List\<String\> | Device group memberships |

---

### ServerResponse *(base class)*

**Source**: `json/ServerResponse.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`

| Field | Type | Description |
|---|---|---|
| `status` | String | `"OK"` on success; error key otherwise |
| `message` | String | Human-readable message or error detail |

---

### ServerConfigResponse

**Source**: `json/ServerConfigResponse.java`  
Extends `ServerResponse`.

| Field | Type | Description |
|---|---|---|
| `data` | [ServerConfig](#serverconfig) | Device configuration payload |

---

### ServerConfig

**Source**: `json/ServerConfig.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`

> **Removed from previous version**: all kiosk fields (`kioskMode`, `kioskHome`, `kioskRecents`, `kioskNotifications`, `kioskSystemInfo`, `kioskKeyguard`, `kioskLockButtons`, `kioskScreenOn`, `kioskExit`).

| Field | Type | Description |
|---|---|---|
| `newNumber` | String | Updated device ID (if changed) |
| `backgroundColor` | String | UI background colour |
| `textColor` | String | UI text colour |
| `backgroundImageUrl` | String | Background image URL |
| `password` | String | Device password |
| `phone` | String | Phone number |
| `imei` | String | IMEI |
| `iconSize` | Integer | App icon size (default 100) |
| `title` | String | Title display mode (`none` / `deviceId` / `description` / `custom1–3` / `imei` / `serialNumber` / `externalIp`) |
| `displayStatus` | boolean | Show status bar |
| `gps` | Boolean | Enable GPS |
| `bluetooth` | Boolean | Enable Bluetooth |
| `wifi` | Boolean | Enable WiFi |
| `mobileData` | Boolean | Enable mobile data |
| `mainApp` | String | Launcher/main app package |
| `lockStatusBar` | Boolean | Lock status bar |
| `systemUpdateType` | Integer | `0`=default `1`=instant `2`=scheduled `3`=manual |
| `systemUpdateFrom` | String | Update window start |
| `systemUpdateTo` | String | Update window end |
| `appUpdateFrom` | String | App update window start |
| `appUpdateTo` | String | App update window end |
| `downloadUpdates` | String | OTA download URL |
| `factoryReset` | Boolean | Trigger factory reset |
| `reboot` | Boolean | Trigger reboot |
| `lock` | Boolean | Lock device |
| `lockMessage` | String | Lock screen message |
| `passwordReset` | String | Password reset trigger value |
| `pushOptions` | String | Push method: `mqttWorker` / `mqttAlarm` / `polling` |
| `keepaliveTime` | Integer | MQTT keep-alive (ms) |
| `requestUpdates` | String | Update request settings |
| `disableLocation` | Boolean | Disable location tracking |
| `appPermissions` | String | Permission mode: `asklocation` / `denylocation` / `askall` |
| `usbStorage` | Boolean | Enable USB storage |
| `autoBrightness` | Boolean | Auto brightness |
| `brightness` | Integer | Brightness level |
| `manageTimeout` | Boolean | Manage screen timeout |
| `timeout` | Integer | Screen timeout (ms) |
| `lockVolume` | Boolean | Lock volume |
| `manageVolume` | Boolean | Manage volume |
| `volume` | Integer | Volume level |
| `passwordMode` | String | Password mode |
| `timeZone` | String | Device timezone |
| `allowedClasses` | String | Allowed app classes |
| `orientation` | Integer | Screen orientation |
| `restrictions` | String | Device restrictions blob |
| `description` | String | Device description |
| `custom1` | String | Custom field 1 |
| `custom2` | String | Custom field 2 |
| `custom3` | String | Custom field 3 |
| `runDefaultLauncher` | Boolean | Use default launcher |
| `newServerUrl` | String | Server migration URL |
| `lockSafeSettings` | boolean | Lock safe settings menu |
| `permissive` | boolean | Permissive mode |
| `disableScreenshots` | boolean | Disable screenshots |
| `autostartForeground` | boolean | Auto-start foreground service |
| `showWifi` | boolean | Show WiFi status indicator |
| `appName` | String | Override app display name |
| `vendor` | String | Vendor name |
| `applications` | List\<[Application](#application)\> | Managed applications |
| `applicationSettings` | List\<[ApplicationSetting](#applicationsetting)\> | Per-app settings |
| `files` | List\<[RemoteFile](#remotefile)\> | Managed files |
| `actions` | List\<[Action](#action)\> | Intent actions |

---

### DeviceInfo

**Source**: `json/DeviceInfo.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`, `@JsonInclude(JsonInclude.Include.NON_NULL)`  
Used as request body for: Send Device Info, Confirm Reset/Reboot/Password.

> **Removed from previous version**: `kioskMode` field.

| Field | Type | Description |
|---|---|---|
| `model` | String | Device model |
| `permissions` | List\<Integer\> | Installed permission codes |
| `applications` | List\<[Application](#application)\> | Installed apps |
| `files` | List\<[RemoteFile](#remotefile)\> | Remote file states |
| `deviceId` | String | Device identifier |
| `phone` | String | SIM 1 phone number |
| `imei` | String | SIM 1 IMEI |
| `mdmMode` | boolean | MDM admin mode active |
| `batteryLevel` | int | Battery % (0–100) |
| `batteryCharging` | String | `"usb"` or `"ac"` |
| `androidVersion` | String | Android OS version |
| `factoryReset` | Boolean | Factory reset performed |
| `location` | [DeviceInfo.Location](#deviceinfolocation) | Current location |
| `launcherType` | String | Launcher type name |
| `launcherPackage` | String | Launcher package ID |
| `defaultLauncher` | boolean | Is set as default launcher |
| `iccid` | String | SIM 1 ICCID |
| `imsi` | String | SIM 1 IMSI |
| `phone2` | String | SIM 2 phone number |
| `imei2` | String | SIM 2 IMEI |
| `iccid2` | String | SIM 2 ICCID |
| `imsi2` | String | SIM 2 IMSI |
| `cpu` | String | CPU architecture |
| `serial` | String | Device serial number |
| `custom1` | String | Custom field 1 |
| `custom2` | String | Custom field 2 |
| `custom3` | String | Custom field 3 |

#### DeviceInfo.Location

| Field | Type | Description |
|---|---|---|
| `ts` | long | Timestamp (ms) |
| `lat` | double | Latitude |
| `lon` | double | Longitude |

---

### PushResponse

**Source**: `json/PushResponse.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`

| Field | Type | Description |
|---|---|---|
| `status` | String | `"OK"` or error key |
| `data` | List\<[PushMessage](#pushmessage)\> | Pending push messages |

---

### PushMessage

**Source**: `json/PushMessage.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`

| Field | Type | Description |
|---|---|---|
| `messageType` | String | Message type constant (see below) |
| `payload` | String | JSON payload string (parsed per type) |

**`messageType` constants**:

> **Removed from previous version**: `exitKiosk` type.

| Constant | Wire value | Action |
|---|---|---|
| `TYPE_CONFIG_UPDATING` | `configUpdating` | Configuration update in progress |
| `TYPE_CONFIG_UPDATED` | `configUpdated` | Configuration update complete |
| `TYPE_RUN_APP` | `runApp` | Launch an application |
| `TYPE_UNINSTALL_APP` | `uninstallApp` | Uninstall an application |
| `TYPE_DELETE_FILE` | `deleteFile` | Delete a specific file |
| `TYPE_PURGE_DIR` | `purgeDir` | Purge a directory |
| `TYPE_DELETE_DIR` | `deleteDir` | Delete a directory |
| `TYPE_PERMISSIVE_MODE` | `permissiveMode` | Enter permissive mode |
| `TYPE_RUN_COMMAND` | `runCommand` | Execute a shell command |
| `TYPE_REBOOT` | `reboot` | Reboot device |
| `TYPE_CLEAR_DOWNLOADS` | `clearDownloadHistory` | Clear download history |
| `TYPE_INTENT` | `intent` | Send an Android intent |
| `TYPE_GRANT_PERMISSIONS` | `grantPermissions` | Grant runtime permissions |
| `TYPE_ADMIN_PANEL` | `adminPanel` | Show admin panel |
| `TYPE_UPDATEOTA` | `updateOta` | Trigger OTA firmware update |
| `TYPE_DEVICE_ACTION` | `deviceAction` | Generic device action |
| `TYPE_DEVICE_BROADCAST` | `deviceBroadcast` | Send a broadcast |
| `TYPE_DEVICE_FACTORY_RESET` | `deviceFactoryReset` | Perform factory reset |

---

### RemoteLogConfigResponse

**Source**: `json/RemoteLogConfigResponse.java`  
Extends `ServerResponse`.

| Field | Type | Description |
|---|---|---|
| `data` | List\<[RemoteLogConfig](#remotelogconfig)\> | Logging rules |

---

### RemoteLogConfig

**Source**: `json/RemoteLogConfig.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`

| Field | Type | Description |
|---|---|---|
| `packageId` | String | Package to capture logs from |
| `logLevel` | int | Minimum log level to capture |
| `filter` | String | Additional log filter string |

---

### RemoteLogItem

**Source**: `json/RemoteLogItem.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`  
Sent as list body to [Send Logs](#7-send-logs-to-server).

| Field | Type | JSON | Description |
|---|---|---|---|
| `_id` | long | `@JsonIgnore` | Local DB primary key (not serialized) |
| `timestamp` | long | serialized | Log entry timestamp (ms) |
| `logLevel` | int | serialized | Log level (`0`=VERBOSE … `6`=ASSERT) |
| `packageId` | String | serialized | Source package |
| `message` | String | serialized | Log message |

---

### DetailedInfoConfigResponse

**Source**: `json/DetailedInfoConfigResponse.java`  
Extends `ServerResponse`.

| Field | Type | Description |
|---|---|---|
| `data` | [DetailedInfoConfig](#detailedinfoconfig) | Configuration object |

---

### DetailedInfoConfig

**Source**: `json/DetailedInfoConfig.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`

| Field | Type | Description |
|---|---|---|
| `sendData` | Boolean | Enable detailed data sending |
| `intervalMins` | Integer | Send interval in minutes |

---

### DetailedInfo

**Source**: `json/DetailedInfo.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`  
Sent as list body to [Send Detailed Device Information](#8-send-detailed-device-information).

| Field | Type | JSON | Description |
|---|---|---|---|
| `_id` | long | `@JsonIgnore` | Local DB primary key (not serialized) |
| `ts` | long | serialized | Snapshot timestamp (ms) |
| `device` | [Device](#detailedinfo--device) | serialized | Device state |
| `wifi` | [Wifi](#detailedinfo--wifi) | serialized | WiFi state |
| `gps` | [Gps](#detailedinfo--gps) | serialized | GPS state |
| `mobile` | [Mobile](#detailedinfo--mobile) | serialized | SIM 1 mobile state |
| `mobile2` | [Mobile](#detailedinfo--mobile) | serialized | SIM 2 mobile state |

#### DetailedInfo — Device

| Field | Type | Description |
|---|---|---|
| `batteryLevel` | Integer | Battery % |
| `batteryCharging` | String | Charging state |
| `wifi` | Boolean | WiFi enabled |
| `gps` | Boolean | GPS enabled |
| `ip` | String | Device IP |
| `keyguard` | Boolean | Keyguard active |
| `ringVolume` | Integer | Ring volume |
| `mobileData` | Boolean | Mobile data enabled |
| `bluetooth` | Boolean | Bluetooth enabled |
| `usbStorage` | Boolean | USB storage enabled |
| `memoryTotal` | Integer | Total RAM (bytes) |
| `memoryAvailable` | Integer | Available RAM (bytes) |

#### DetailedInfo — Wifi

| Field | Type | Description |
|---|---|---|
| `rssi` | Integer | Signal strength (dBm) |
| `ssid` | String | Network name |
| `security` | String | Security type |
| `state` | String | Connection state |
| `ip` | String | IP address |
| `tx` | Long | Transmitted bytes |
| `rx` | Long | Received bytes |

#### DetailedInfo — Gps

| Field | Type | Description |
|---|---|---|
| `state` | String | GPS state |
| `provider` | String | Location provider |
| `lat` | Double | Latitude |
| `lon` | Double | Longitude |
| `alt` | Double | Altitude (m) |
| `speed` | Double | Speed (m/s) |
| `course` | Double | Course heading (degrees) |

#### DetailedInfo — Mobile

| Field | Type | Description |
|---|---|---|
| `rssi` | Integer | Signal strength |
| `carrier` | String | Carrier name |
| `number` | String | Phone number |
| `imsi` | String | IMSI |
| `data` | Boolean | Data enabled |
| `ip` | String | IP address |
| `state` | String | Connection state |
| `simState` | String | SIM card state |
| `tx` | Long | Transmitted bytes |
| `rx` | Long | Received bytes |

---

### Application

**Source**: `json/Application.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`  
Used in `ServerConfig.applications` and `DeviceInfo.applications`.

> **Removed from previous version**: `useKiosk` field.

| Field | Type | Description |
|---|---|---|
| `type` | String | `"app"` / `"web"` / `"intent"` |
| `name` | String | Display name |
| `pkg` | String | Package ID |
| `version` | String | Version string |
| `code` | Integer | Version code |
| `url` | String | URL (web type) |
| `showIcon` | boolean | Show icon on launcher |
| `remove` | boolean | Uninstall flag |
| `runAfterInstall` | boolean | Auto-run after install |
| `runAtBoot` | boolean | Auto-run at boot |
| `skipVersion` | boolean | Skip version check |
| `iconText` | String | Icon label override |
| `icon` | String | Icon URL or resource |
| `screenOrder` | Integer | Display order |
| `keyCode` | Integer | Hardware key binding |
| `bottom` | boolean | Place in bottom area |
| `longTap` | boolean | Long-tap action enabled |
| `intent` | String | Intent string (intent type) |

---

### ApplicationSetting

**Source**: `json/ApplicationSetting.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`  
Used in `ServerConfig.applicationSettings`.

| Field | Type | Description |
|---|---|---|
| `packageId` | String | Target app package |
| `name` | String | Setting key |
| `type` | int | Setting type code |
| `value` | String | Setting value |
| `readOnly` | boolean | Read-only flag |
| `lastUpdate` | long | Last update timestamp (ms) |

---

### RemoteFile

**Source**: `json/RemoteFile.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`  
Used in `ServerConfig.files` and `DeviceInfo.files`.

| Field | Type | JSON | Description |
|---|---|---|---|
| `_id` | long | `@JsonIgnore` | Local DB primary key (not serialized) |
| `url` | String | serialized | Download URL |
| `path` | String | serialized | Local destination path |
| `lastUpdate` | long | serialized | Last update timestamp (ms) |
| `checksum` | String | serialized | Integrity checksum |
| `remove` | boolean | serialized | Delete file flag |
| `description` | String | serialized | File description |
| `varContent` | boolean | serialized | Content varies per device |

---

### Action

**Source**: `json/Action.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`  
Used in `ServerConfig.actions`.

| Field | Type | Description |
|---|---|---|
| `action` | String | Intent action string |
| `categories` | String | Comma-separated categories |
| `packageId` | String | Target package |
| `activity` | String | Target activity class |
| `schemes` | String | URI schemes |
| `hosts` | String | URI hosts |
| `mimeTypes` | String | MIME types |

---

### Download

**Source**: `json/Download.java`  
**Annotations**: `@JsonIgnoreProperties(ignoreUnknown = true)`  
Internal model — tracks download queue state; not directly sent/received as an API payload.

| Field | Type | JSON | Description |
|---|---|---|---|
| `_id` | long | `@JsonIgnore` | Local DB primary key |
| `url` | String | serialized | Download URL |
| `path` | String | serialized | Destination path |
| `attempts` | long | serialized | Number of download attempts |
| `lastAttemptTime` | long | serialized | Timestamp of last attempt (ms) |
| `downloaded` | boolean | serialized | Download completed |
| `installed` | boolean | serialized | Installation completed |

---

### LocationTable.Location

**Source**: `db/LocationTable.java`  
Sent as list body to [Send Location Data](#9-send-location-data).

| Field | Type | Description |
|---|---|---|
| `_id` | long | Local DB primary key |
| `ts` | long | Timestamp (ms) |
| `lat` | double | Latitude |
| `lon` | double | Longitude |

---

## Response Handling

**Source**: `task/GetServerConfigTask.java`, `task/SendDeviceInfoTask.java`, etc.

### Success path

```
response.isSuccessful()                         // HTTP 2xx
  → response.body().getStatus().equals("OK")    // Application-level OK
  → response.body().getData() != null            // Payload present
  → read response.headers().get("X-IP-Address") // Capture external IP (optional)
```

### Error path

| Scenario | Handling |
|---|---|
| Network / timeout | Exception caught; retry on secondary URL if configured |
| HTTP 4xx / 5xx | `!response.isSuccessful()` branch; HTTP 5xx treated as timeout; logged and retried |
| `status != "OK"` | Application error; `"error.notfound.device"` sets `isDeviceNotFound` flag in `GetServerConfigTask` → triggers re-enrollment |
| Invalid response signature | Signature mismatch → drop response, log security warning |
| Null response body | Treated as failure; no processing |

### Signature verification (when `CHECK_SIGNATURE` enabled)

```
sha1 = CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + responseBodyJson)
assert sha1 == response.headers().get("X-Response-Signature")
```

### Retry strategy

1. Attempt primary server service
2. On failure, attempt secondary server service (if configured)
3. Background execution via WorkManager / foreground Service with proper error propagation

---

## Scheduling Summary

| Worker / Service | Endpoint | Interval |
|---|---|---|
| `SendDeviceInfoWorker` | `POST /sync/info` | Every 15 min |
| `RemoteLogWorker` | `POST /devicelog/log/list/{n}` | Every 1 min; 15-min retry on failure; max 10 items/batch |
| `PushLongPollingService` | `GET /notification/polling/{n}` | Continuous foreground service (5-min read timeout, reconnects immediately) |
| `PushNotificationWorker` | `GET /notifications/device/{n}` | Every 15 min (periodic fallback) |
| Startup / config change | `GET /sync/configuration/{n}` | On startup and on `configUpdated` push message |

---

## Constants Reference

**Source**: `Const.java`

| Constant | Value | Purpose |
|---|---|---|
| `CONNECTION_TIMEOUT` | 10,000 ms | Default connect/read/write timeout |
| `LONG_POLLING_READ_TIMEOUT` | 300,000 ms | Long-poll read timeout |
| `STATUS_OK` | `"OK"` | Success status string |
| `HEADER_IP_ADDRESS` | `"X-IP-Address"` | External IP response header |
| `HEADER_RESPONSE_SIGNATURE` | `"X-Response-Signature"` | Signature verification header |

---

## Diff vs `api.md` (what changed)

| Location | Change |
|---|---|
| `ServerConfig` | Removed: `kioskMode`, `kioskHome`, `kioskRecents`, `kioskNotifications`, `kioskSystemInfo`, `kioskKeyguard`, `kioskLockButtons`, `kioskScreenOn`, `kioskExit` |
| `DeviceInfo` | Removed: `kioskMode` |
| `Application` | Removed: `useKiosk` |
| `PushMessage` types | Removed: `exitKiosk` (`TYPE_EXIT_KIOSK`) |
| New model | Added: `DeviceCreateOptions` (same shape as `DeviceEnrollOptions`) |
| `Download` model | Now explicitly documented (was implicit) |
| `RemoteFile` / `RemoteLogItem` / `DetailedInfo` | `@JsonIgnore` on `_id` fields now noted explicitly |
