# Software Requirements Specification  
## Base MDM Agent — Android Application

**Version:** 1.0.0.8 (versionCode 1008)  
**Package:** `com.base.launcher`  
**Repository:** `base-mdm-app` (branch: `base_mdm_kotlin_lite`)  
**Vendor:** The Base (https://thebase.vn)  
**License:** Apache 2.0  
**Platform:** Android 7.0+ (minSdk 24) to Android 15 (targetSdk 35)  
**Date:** 2026-06-11

---

## 1. Introduction

### 1.1 Purpose

This SRS documents the complete requirements for the Base MDM Android Agent application. The application is an open-source Mobile Device Management (MDM) solution that functions simultaneously as an Android home-screen launcher and an MDM policy-enforcement agent.

### 1.2 Scope

The Base MDM Agent:
- Acts as the default home launcher on managed Android devices
- Enforces policies pushed by the MDM server
- Supports Android Device Owner (DO) mode for silent, privileged management
- Supports corporate kiosk (COSU — Corporate Owned Single Use) lockdown
- Communicates with the MDM backend via REST API and MQTT push
- Exposes a plugin IPC API for companion apps via AIDL

### 1.3 Definitions

| Term | Definition |
|---|---|
| MDM | Mobile Device Management |
| DO | Android Device Owner — highest privilege level, granted via QR provisioning or DPC |
| COSU | Corporate Owned Single Use — single-app kiosk lockdown via Android Lock Task |
| MQTT | Message Queuing Telemetry Transport — pub/sub protocol for push commands |
| Device ID | Unique identifier for the device within the MDM system (IMEI, serial, or MAC) |
| Config | JSON policy document fetched from MDM server |
| Push | Real-time command delivery via MQTT or HTTP long-polling |

---

## 2. Overall Description

### 2.1 System Architecture

```
[MDM Server — mdm.thebeanfamily.org]
        │ REST (HTTPS)         │ MQTT TLS
        │                      │
        ▼                      ▼
[ConfigUpdater]        [MqttService]
        │                      │
        ▼                      ▼
[PolicyEnforcer]    [PushNotificationMqttWrapper]
        │                      │
        └──────────────────────┘
                   │
           [Android Device]
```

The agent has two communication paths:
1. **REST (primary):** `ConfigUpdater` polls and syncs full device policy via HTTPS
2. **MQTT (push):** `MqttService` maintains a persistent TLS connection for real-time command delivery; falls back to HTTP long-polling via `PushLongPollingService`

### 2.2 Product Functions Summary

- Enroll devices and fetch policy from MDM server
- Apply and enforce policy (apps, files, certificates, restrictions, kiosk mode)
- Report device status, location, and logs to MDM server
- Execute remote commands (reboot, factory reset, password reset, lock)
- Expose plugin IPC API to companion Android apps

### 2.3 User Classes

| Class | Description |
|---|---|
| MDM Administrator | Configures policies on the MDM web panel |
| Device Owner | The Android `DevicePolicyManager` Device Owner role (the app itself) |
| Kiosk End User | End user operating the managed device in kiosk mode |
| Plugin Developer | Developer building companion apps using the AIDL plugin API |

### 2.4 Operating Environment

- Android 7.0 (API 24) through Android 15 (API 35)
- Device Owner mode (provisioned via QR code or `dpm set-device-owner` ADB command)
- Network: HTTPS to `mdm.thebeanfamily.org`, MQTT TLS to `mq.thebeanfamily.org:18883`
- Shared UID: `android:sharedUserId="com.base"` for permission sharing with companion libraries

---

## 3. Functional Requirements

### FR-01: Device Enrollment and Provisioning

**Description:** The app must support two provisioning paths:
1. **QR code provisioning** — scan a QR code containing `BASE_URL` and `deviceId`. Uses `InitialSetupActivity` (triggered by `android.app.action.ADMIN_POLICY_COMPLIANCE`)
2. **Manual provisioning** — user enters Device ID and server URL in `MdmChoiceSetupActivity`

**API:** `POST {project}/rest/public/sync/configuration/{number}` — enrolls device and receives initial config

**Prerequisites:**
- App must be set as Device Owner OR have Device Admin rights
- Device ID must be determined (IMEI / serial / MAC per `DEVICE_ID_CHOICE` build flag)

**Acceptance criteria:**
- `enrollDevice()` sends device info payload and receives `ServerConfig` JSON
- On success, config is persisted to `SettingsHelper`
- `InitialSetupActivity` is launched from Android provisioning flow (`ADMIN_POLICY_COMPLIANCE`)

---

### FR-02: Configuration Synchronization

**Description:** The app must periodically fetch the full device policy from the MDM server and apply all changes.

**API:** `GET {project}/rest/public/sync/configuration/{number}` — fetches current config

**Orchestrator:** `ConfigUpdater.updateConfig()` — the central config update lifecycle:

```
updateConfig()
  → GetServerConfigTask          // fetch JSON config
  → updateRemoteLogConfig()      // update log rules
  → checkServerMigration()       // handle server URL changes
  → setupPushService()           // start/stop MQTT or long-poll
  → checkFactoryReset()          // execute pending factory reset
  → checkRemoteReboot()          // execute pending reboot
  → checkPasswordReset()         // execute pending password reset
  → setDefaultLauncher()         // ensure app is default home
  → updatePolicies()             // apply all policy fields
  → checkAndUpdateFiles()        // sync remote file list
  → loadAndInstallFiles()        // download and install files
  → installCertificates()        // install CA/client certs
  → checkAndUpdateApplications() // sync app whitelist/blacklist
  → loadAndInstallApplications() // download and install APKs
  → lockRestrictions()           // apply DevicePolicyManager restrictions
  → notifyThreads()              // signal completion
  → setActions()                 // execute deferred actions
```

**Acceptance criteria:**
- Config sync runs on `StatusControlService` periodic check and on every push notification
- Request carries `X-Request-Signature` HMAC header and `X-CPU-Arch` header

---

### FR-03: Push Notification (MQTT)

**Description:** The app maintains a persistent MQTT connection to receive real-time policy update triggers from the MDM server.

**Broker:** `mq.thebeanfamily.org:18883` (TLS)  
**Topic pattern:** device-specific topic based on Device ID  
**QoS:** 1 (at-least-once delivery)

**Services:**
- `MqttService` — Android foreground service hosting the MQTT connection
- `MqttAndroidClient` — client API, receives messages via `LocalBroadcastManager`
- `PushNotificationMqttWrapper` — bridges MQTT messages to `ConfigUpdater`

**Connection resilience:**
- `START_STICKY` service restart on kill
- Auto-reconnect on network restoration via `NetworkConnectionIntentReceiver`
- Keepalive via `AlarmPingSender` (default) or `WorkerPingSender` (WorkManager)
- `PingDeathDetector` watchdog: triggers reconnect if no ping for 30 minutes
- Reconnect on boot via `BootReceiver` → `Initializer`

**Acceptance criteria:**
- MQTT message triggers `ConfigUpdater.updateConfig()` within 5 seconds of receipt
- Connection is restored automatically after network interruption
- Service survives process kill and device reboot

---

### FR-04: Push Notification Fallback (HTTP Long-Polling)

**Description:** When MQTT is unavailable, the app falls back to HTTP long-polling.

**API:** `GET {project}/rest/notification/polling/{number}`  
**Service:** `PushLongPollingService` — foreground service running a polling thread

**Acceptance criteria:**
- Long-polling is enabled when `ServerConfig.pushOptions` specifies it
- Poll request is held open server-side until a notification is available or timeout
- On notification receipt, triggers `ConfigUpdater.updateConfig()`

---

### FR-05: Application Management

**Description:** The app manages the lifecycle of installed applications on the device.

**Capabilities:**
- Whitelist/blacklist apps (hide icons, prevent launch)
- Silent APK installation (when Device Owner, uses `PackageInstaller` APIs)
- Silent APK uninstallation (Device Owner only)
- Force-update installed applications when version changes
- Lock task (kiosk) app list management

**Configuration fields (`ServerConfig`):** `applications[]` — list with `packageName`, `version`, `url`, `remove`, `install`

**Acceptance criteria:**
- Silent install completes without user confirmation when in DO mode
- Apps not in whitelist have their launchers hidden
- Update triggers when server version ≠ installed version

---

### FR-06: Kiosk Mode (COSU)

**Description:** Lock the device to a single application using Android Lock Task mode.

**Implementation:** `ProUtils.startCosuKioskMode()` — delegates to Android `DevicePolicyManager.setLockTaskPackages()` and `Activity.startLockTask()`

**Configuration fields:** `kioskMode`, `mainApp`, `kioskExit`, `kioskExitPassword`

**Features:**
- Lock to single app specified by `mainApp`
- Optional kiosk exit button (requires `SYSTEM_ALERT_WINDOW` overlay)
- COSU task features configurable via `setLockTaskFeatures()` (API 28+)
- Status bar expansion blocked via overlay view (Pro)

**Acceptance criteria:**
- Device cannot leave kiosk app without correct exit password
- Home button returns to kiosk app
- Recent apps button is disabled in kiosk mode

---

### FR-07: Remote Commands

**Description:** The MDM server can issue remote commands to the device.

| Command | `ServerConfig` field | Implementation | API requirement |
|---|---|---|---|
| Factory reset | `factoryReset` | `DevicePolicyManager.wipeData()` | Device Owner |
| Reboot | `reboot` | `DevicePolicyManager.reboot()` | API 24+, Device Owner |
| Lock screen | `lock` + `lockMessage` | `DevicePolicyManager.lockNow()` | Device Admin |
| Password reset | `passwordReset` | `DevicePolicyManager.resetPassword()` | API 23+, Device Owner |
| Brightness | `brightness` | `Settings.System.SCREEN_BRIGHTNESS` | `WRITE_SETTINGS` |
| Volume | `volume` | `AudioManager.setStreamVolume()` | — |
| Screen timeout | `timeout` | `Settings.System.SCREEN_OFF_TIMEOUT` | `WRITE_SETTINGS` |

**Post-command confirmation:**
- `POST {project}/rest/plugins/devicereset/public/{number}` — factory reset
- `POST {project}/rest/plugins/devicereset/public/reboot/{number}` — reboot
- `POST {project}/rest/plugins/devicereset/public/password/{number}` — password reset

---

### FR-08: Connectivity Control

**Description:** The MDM server can enforce connectivity state on the device.

| Policy field | Resource | Method |
|---|---|---|
| `wifi` | WiFi on/off | `WifiManager.setWifiEnabled()` |
| `bluetooth` | Bluetooth on/off | `BluetoothAdapter.enable()`/`disable()` |
| `mobileData` | Mobile data on/off | Reflection on `ConnectivityManager` |
| `gps` | GPS required/forbidden | Detects violation, shows dialog |

**Enforcement:** `StatusControlService` polls every 10 seconds; broadcasts `ACTION_POLICY_VIOLATION` if state doesn't match config.

---

### FR-09: Location Tracking

**Description:** Collect device GPS/network location and report to the MDM server.

**Service:** `LocationService` — foreground service using `LocationManager`

**API:** `PUT {project}/rest/plugins/devicelocations/public/update/{number}`

**Configuration:** `ServerConfig.gps` enables/disables GPS updates; `ServerConfig.locationInterval` (from detailed info config)

**Capabilities:**
- GPS and network provider fusion
- GNSS satellite count reporting (API 24+)
- Location uploaded via `SendDeviceInfoWorker` (WorkManager)

---

### FR-10: Device Info Reporting

**Description:** Collect and report comprehensive device hardware and software information.

**API:** 
- `POST {project}/rest/public/sync/info` — basic device info (sync)
- `PUT {project}/rest/plugins/deviceinfo/deviceinfo/public/{number}` — detailed info

**Reported fields** (`DeviceInfo`/`DetailedInfo`): IMEI, serial, MAC, Android version, build, CPU arch, screen resolution, battery, storage, RAM, installed apps list, network state, SIM info, satellite count.

**Scheduling:** `SendDeviceInfoWorker` — WorkManager periodic task

---

### FR-11: Remote Logging

**Description:** Capture application logs and upload them to the MDM server.

**API:**
- `GET {project}/rest/plugins/devicelog/log/rules/{number}` — fetch log config
- `POST {project}/rest/plugins/devicelog/log/list/{number}` — upload log batch

**Local storage:** SQLite `log` table (`LogTable`) and `log_config` table  
**Worker:** `RemoteLogWorker` — uploads batched log entries on WorkManager schedule

**Log levels:** VERBOSE, DEBUG, INFO, WARNING, ERROR (controlled by server-side `RemoteLogConfig`)

---

### FR-12: File Management (Remote Files)

**Description:** Download and deploy files to the device from URLs specified in the server config.

**Configuration:** `ServerConfig.files[]` — list with `url`, `path`, `checksum`

**Process:**
1. Compare server file list against `remote_file` table in SQLite
2. Download changed/new files to the specified path
3. Verify SHA checksum after download
4. Track download state in `download` SQLite table

**Certificate installation:** PEM/DER CA certificates and PKCS12 client certs installed via `DevicePolicyManager.installCaCert()` (Device Owner) or `KeyChain` API.

---

### FR-13: Policy Enforcement (Restrictions)

**Description:** Apply Android system restrictions to the device.

**Implementation:** `DevicePolicyManager.addUserRestriction()` / `removeUserRestriction()` (Device Owner)

**Common restrictions applied:**
- `DISALLOW_FACTORY_RESET` — prevent user factory reset
- `DISALLOW_SAFE_BOOT` — prevent safe boot mode
- `DISALLOW_DEBUGGING_FEATURES` — disable ADB/developer options
- `DISALLOW_INSTALL_UNKNOWN_SOURCES` — block sideloading
- `DISALLOW_USB_FILE_TRANSFER` — block USB mass storage

**Configuration fields:** `ServerConfig.restrictions`, `ServerConfig.permissive`, `ServerConfig.disableScreenshots`, `ServerConfig.passwordMode`

---

### FR-14: Plugin IPC API (AIDL)

**Description:** Expose MDM data and control to companion applications via Android AIDL.

**Interface:** `com.base.IMdmApi` (AIDL)  
**Service:** `PluginApiService` — exported service with intent filter `com.base.action.Connect`

**Methods:**

| Method | Description |
|---|---|
| `queryConfig()` | Returns JSON-serialized `ServerConfig` |
| `log(level, tag, message)` | Writes to remote log via `RemoteLogger` |
| `queryAppPreference(key)` | Read per-app preference from `ApplicationSetting` |
| `setAppPreference(key, value)` | Write per-app preference |
| `commitAppPreferences()` | Persist preference changes |
| `getVersion()` | Returns MDM agent `BuildConfig.VERSION_NAME` |
| `queryPrivilegedConfig(apiKey)` | Returns privileged config fields (requires valid `apiKey`) |
| `setCustom(index, value)` | Write `custom1/2/3` fields to config |
| `forceConfigUpdate()` | Trigger immediate config sync |

**Security:** `queryPrivilegedConfig()` validates `apiKey` against `BuildConfig.LIBRARY_API_KEY`

---

### FR-15: Boot Persistence

**Description:** The app must automatically restart all MDM services after device reboot or app update.

**Receiver:** `BootReceiver` — handles `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, `com.htc.intent.action.QUICKBOOT_POWERON`

**On boot:**
1. Start `MqttService` (or `PushLongPollingService`) via `startForegroundService()` (API 26+) or `startService()` (API 24–25)
2. Schedule `StatusControlService`
3. Schedule WorkManager tasks (log upload, device info, location)
4. Register `ShutdownReceiver` and `SimChangedReceiver`

**Acceptance criteria:**
- All services operational within 30 seconds of boot completion
- Device Owner status is preserved across reboots (Android platform guarantee)

---

### FR-16: Launcher UI

**Description:** The app functions as the Android home screen launcher.

**Main activity:** `MainActivity` — declared with `android.intent.category.HOME` and `android.intent.category.DEFAULT`

**UI features:**
- Grid of allowed application icons (filtered by `ServerConfig.applications`)
- Custom background image (loaded via Picasso with offline cache)
- Status bar with battery, network, time indicators
- Custom lock screen message (`ServerConfig.lockMessage`)
- Orientation lock / landscape control
- MIUI-specific permission handling

**Content rendering:**
- Background image from URL (Picasso + OkHttp cache)
- Fallback to locally cached image on network failure
- App icons grid rebuilt on every config update

---

## 4. Non-Functional Requirements

### NFR-01: Performance

- Config sync must complete within 30 seconds on a 3G connection
- MQTT connection must be restored within 60 seconds of network restoration
- Launcher home screen must render within 2 seconds of app start
- SQLite operations must not block the main thread

### NFR-02: Reliability

- MQTT service uses `START_STICKY` — OS must restart it after kill
- WorkManager tasks are persisted across reboots
- `PingDeathDetector` detects and recovers from silent MQTT failures within 30 minutes
- All HTTP requests use retry logic (via `ServerServiceKeeper`)

### NFR-03: Security

- All API requests carry `X-Request-Signature` HMAC header computed from `REQUEST_SIGNATURE` build constant
- MQTT connection uses TLS on port 18883
- `TRUST_ANY_CERTIFICATE = false` by default; self-signed certs require explicit opt-in
- Plugin API `queryPrivilegedConfig()` validates `LIBRARY_API_KEY`
- Sensitive config not logged in release builds

### NFR-04: Compatibility

- Minimum API: 24 (Android 7.0 Nougat)
- Target API: 35 (Android 15)
- All API-specific calls are guarded with `Build.VERSION.SDK_INT` checks
- Supports ARM (`armeabi-v7a`, `arm64-v8a`) architectures

### NFR-05: Privacy

- `ACCESS_BACKGROUND_LOCATION` declared for MDM location tracking (user consent via Device Owner provisioning)
- No SMS read permission (commented out in manifest)
- `QUERY_ALL_PACKAGES` declared for app management functionality

---

## 5. Data Model

### 5.1 SQLite Database (`mdm.db`, version 10)

| Table | Columns | Purpose |
|---|---|---|
| `log` | id, timestamp, level, tag, message | Remote log buffer |
| `log_config` | id, rules JSON | Server-pushed log level rules |
| `info_history` | id, timestamp, type, data | Device info upload history |
| `remote_file` | id, url, path, checksum, status | Tracked remote files |
| `location` | id, timestamp, lat, lon, accuracy, provider | Location upload buffer |
| `download` | id, url, status, path, progress | Active/completed downloads |

### 5.2 SharedPreferences (`SettingsHelper`)

Key data stored:
- `deviceId` — current Device ID
- `serverUrl` — active MDM server URL
- `config` — last received `ServerConfig` JSON
- `mqttConnected` — MQTT connection state
- `satelliteCount` — last GNSS satellite count
- Per-app `ApplicationSetting` preferences

### 5.3 MQTT Message Database (`mqttAndroidService.db`)

Single table `MqttArrivedMessageTable` for QoS 1/2 message persistence. See [mqtt_service.md](mqtt_service.md) §3.6 for full schema.

---

## 6. External API Contracts

### 6.1 REST Endpoints (`ServerService.java`)

Base URL: `BuildConfig.BASE_URL` (default: `https://mdm.thebeanfamily.org`)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `{project}/rest/public/sync/configuration/{number}` | Enroll device |
| `GET` | `{project}/rest/public/sync/configuration/{number}` | Fetch config |
| `POST` | `{project}/rest/public/sync/info` | Send basic device info |
| `GET` | `{project}/rest/notifications/device/{number}` | Check for notifications |
| `GET` | `{project}/rest/notification/polling/{number}` | Long-poll for push |
| `GET` | `{project}/rest/plugins/devicelog/log/rules/{number}` | Fetch log config |
| `POST` | `{project}/rest/plugins/devicelog/log/list/{number}` | Upload logs |
| `PUT` | `{project}/rest/plugins/deviceinfo/deviceinfo/public/{number}` | Upload detailed info |
| `PUT` | `{project}/rest/plugins/devicelocations/public/update/{number}` | Upload locations |
| `GET` | `{project}/rest/plugins/deviceinfo/deviceinfo-plugin-settings/device/{number}` | Fetch info config |
| `POST` | `{project}/rest/plugins/devicereset/public/{number}` | Confirm factory reset |
| `POST` | `{project}/rest/plugins/devicereset/public/reboot/{number}` | Confirm reboot |
| `POST` | `{project}/rest/plugins/devicereset/public/password/{number}` | Confirm password reset |

**Common headers:**
- `X-Request-Signature: {HMAC}`
- `X-CPU-Arch: {abi}`

### 6.2 `ServerConfig` — Central Policy Object

Key fields:

| Field | Type | Effect |
|---|---|---|
| `backgroundColor` | String | Launcher background color |
| `kioskMode` | Boolean | Enable/disable COSU kiosk |
| `mainApp` | String | Package name for kiosk app |
| `factoryReset` | Boolean | Pending factory reset command |
| `reboot` | Boolean | Pending reboot command |
| `lock` | Boolean | Screen lock command |
| `lockMessage` | String | Message shown on locked screen |
| `passwordReset` | String | New password for reset |
| `pushOptions` | Object | MQTT / long-poll configuration |
| `keepaliveTime` | Integer | MQTT keepalive seconds |
| `gps` | Boolean | GPS required/forbidden |
| `bluetooth` | Boolean | Bluetooth on/off |
| `wifi` | Boolean | WiFi on/off |
| `mobileData` | Boolean | Mobile data on/off |
| `brightness` | Integer | Screen brightness (0–255) |
| `volume` | Integer | System volume |
| `timeout` | Integer | Screen timeout (ms) |
| `passwordMode` | Integer | Screen lock mode |
| `restrictions` | Object | Android user restrictions map |
| `permissive` | Boolean | Disable app-blocking overlays |
| `kioskExit` | Boolean | Show kiosk exit button |
| `disableScreenshots` | Boolean | Block screenshot capability |
| `applications` | Array | App install/whitelist list |
| `files` | Array | Remote files to deploy |
| `actions` | Array | Deferred action commands |
| `applicationSettings` | Array | Per-app preference settings |
| `custom1/2/3` | String | Custom fields for plugins |

---

## 7. System Constraints

| Constraint | Description |
|---|---|
| Device Owner required | Silent app install, factory reset, lock task, restrictions require DO |
| `SYSTEM_ALERT_WINDOW` | Required for kiosk exit button and status bar blocking overlay |
| `sharedUserId="com.base"` | Must be signed with the same key as companion AAR libraries |
| Closed-source AARs | `ComponentLib_1.2.5_release.aar` and `TerminalManagerLib_1.1.0_release.aar` provide `Utils`, `SystemUtils`, and Kozen hardware integration |
| TLS pinning | `TRUST_ANY_CERTIFICATE = false` — server must present a valid CA-signed certificate |
| `usesCleartextTraffic="true"` | Declared but network security config may override per-domain |
| Multidex | `multiDexEnabled true` required (large method count from dependencies) |

---

## 8. Open Questions

1. **`Utils.startStableForegroundService()`** — implementation is inside `ComponentLib` AAR. Does it pass the correct foreground service type (`SPECIAL_USE | SYSTEM_EXEMPTED`) on API 34+? If not, `MqttService` and `PushLongPollingService` will crash on Android 14.

2. **`WorkerPingSender.doWork()` returns `null`** — WorkManager may silently fail or reschedule. Should return `Result.success()`.

3. **`DatabaseMessageStore.getArrivedRowCount()`** always returns 0 due to reading a TEXT column as int. The count is only used for trace logging, so functional impact is low but the value is always misleading.

4. **Background location** (`ACCESS_BACKGROUND_LOCATION`) is declared. On Android 11+ (API 30), this requires a separate user permission request. For Device Owner mode, this is automatically granted. Behaviour in non-DO mode?

5. **`ProUtils` stubs** — the open-source version has all Pro methods as stubs. Which features are only available in the Pro/commercial version?

---

## 9. Build Configuration Reference

Key `BuildConfig` fields in `app/build.gradle`:

| Field | Default | Description |
|---|---|---|
| `BASE_URL` | `https://mdm.thebeanfamily.org` | Primary MDM server URL |
| `SECONDARY_BASE_URL` | Same as BASE_URL | Fallback MDM server URL |
| `SERVER_PROJECT` | `""` | Relative path suffix for the MDM panel |
| `DEVICE_ID_CHOICE` | `"suggest"` | How Device ID is determined at first start |
| `ENABLE_PUSH` | `true` | Enable MQTT/long-poll push |
| `MQTT_DOMAIN` | `"mq.thebeanfamily.org"` | MQTT broker hostname |
| `MQTT_PORT` | `18883` | MQTT broker port |
| `MQTT_TLS` | `true` | Use TLS for MQTT |
| `MQTT_QOS` | `1` | MQTT quality of service level |
| `MQTT_USERNAME` | `"basemdm"` | MQTT broker credentials |
| `MQTT_PASSWORD` | `"theBase15112023@"` | MQTT broker credentials |
| `SYSTEM_PRIVILEGES` | `true` | Enable system-level privilege escalation |
| `TRUST_ANY_CERTIFICATE` | `false` | Accept self-signed TLS certs |
| `REQUEST_SIGNATURE` | `"068bd..."` | HMAC key for API request signing |
| `CHECK_SIGNATURE` | `false` | Verify server HMAC in push messages |
| `MQTT_SERVICE_FOREGROUND` | `true` | Run MqttService as foreground |
| `LIBRARY_API_KEY` | `"068bd..."` | API key for privileged plugin queries |
| `USE_ACCESSIBILITY` | `false` | Use accessibility service for app control |

---

## 10. Appendix

### A. Provisioning Command

To set the app as Device Owner via ADB:
```bash
adb shell dpm set-device-owner com.base.launcher/.AdminReceiver
```

### B. Key Source Files

| File | Role |
|---|---|
| `helper/ConfigUpdater.java` | Full config update lifecycle orchestrator |
| `helper/Initializer.java` | Boot/startup initialization |
| `json/ServerConfig.java` | Central policy data model |
| `server/ServerService.java` | Retrofit REST API interface |
| `service/StatusControlService.java` | Periodic status enforcement |
| `service/LocationService.java` | GPS/network location collection |
| `service/PushLongPollingService.java` | HTTP long-poll fallback |
| `service/PluginApiService.java` | AIDL plugin IPC service |
| `ui/MainActivity.java` | Home screen launcher (2881 lines) |
| `ui/InitialSetupActivity.java` | QR provisioning setup flow |
| `receiver/BootReceiver.java` | Service restart on boot |
| `pro/ProUtils.java` | Kiosk / Pro feature stubs |
| `org.eclipse.paho.android.service/` | Embedded MQTT library (16 files) |

### C. Related Knowledge Documents

- [mqtt_service.md](mqtt_service.md) — detailed MQTT layer analysis
- [android_7_15_compatibility.md](android_7_15_compatibility.md) — API 24–35 compatibility guide
