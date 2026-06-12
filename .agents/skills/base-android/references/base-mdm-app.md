# base-mdm-app — Workspace Reference

> Loaded automatically by `base-android` SKILL.md when working in `c:\Git\thebase\base-mdm-app`.

---

## What this app is

**Base MDM** — an open-source Android MDM (Mobile Device Management) launcher. It enrolls Android devices with a management server, enforces device policies, silently installs/uninstalls apps, relays push commands via MQTT, and optionally locks the device into kiosk mode.

The app also integrates **Kozen terminal hardware** (EDC payment terminals) via two embedded AAR SDKs.

---

## Language & Architecture

- **Language**: Java (no Kotlin — no `.kt` files in `app/src/main/`)
- **Architecture**: Activities + Helpers + `AsyncTask` — **NOT** MVVM, **NOT** Hilt, **NOT** Room
- **UI**: XML layouts + `DataBinding` (`dataBinding { enabled = true }`) + ViewBinding utility
- **No Jetpack Compose**, no Fragments, no Navigation component, no ViewModel/StateFlow
- **DI**: manual — `SettingsHelper.getInstance(context)` singleton pattern throughout
- **Database**: custom `SQLiteOpenHelper` via `DatabaseHelper` — **NOT** Room

---

## Module layout

```
base-mdm-app/
  app/          — main launcher application (com.base.launcher)
  lib/          — Base MDM plugin SDK (com.base) — used by plugin apps, not the launcher itself
  settings.gradle
  build.gradle  — root buildscript (AGP 8.11.2)
```

---

## Build config

### app/build.gradle

| Field | Value |
|---|---|
| `applicationId` | `com.base.launcher` |
| `minSdk` | 24 (Android 7.0) |
| `targetSdk` / `compileSdk` | 34 |
| `versionCode` | 1001 |
| `versionName` | `1.0.0.1` |
| Flavor dimension | `all` |
| Product flavors | `base` (single flavor) |
| Java compatibility | `VERSION_11` |

### APK output name

`base_agent_{buildType}_v{versionName}_{ddMMyyyy}.apk`

### Signing

Both `release` and `debug` use the same keystore:
- Path: `C:\Git\android_keystores\base\base.jks`
- Alias: `base`
- Password: stored in `build.gradle` signingConfigs (never move to `key.properties` unless the team decides)

### Key BuildConfig fields

| Field | Default value | Purpose |
|---|---|---|
| `BASE_URL` | `https://mdm.thebeanfamily.org` | Primary MDM server URL |
| `SECONDARY_BASE_URL` | `https://mdm.thebeanfamily.org` | Fallback URL |
| `SERVER_PROJECT` | `""` | URL sub-path (empty = root) |
| `DEVICE_ID_CHOICE` | `"suggest"` | How device ID is set on first run (`user`/`suggest`/`imei`/`serial`/`mac`) |
| `ENABLE_PUSH` | `true` | Enables MQTT push notifications |
| `MQTT_DOMAIN` | `"mqtt.thebase.vn"` | MQTT broker host |
| `MQTT_PORT` | `18883` | MQTT broker port (TLS) |
| `MQTT_TLS` | `true` | Use SSL |
| `MQTT_QOS` | `1` | MQTT QoS level |
| `MQTT_USERNAME` | `"basemdm"` | MQTT credentials |
| `MQTT_PASSWORD` | `"theBase15112023@"` | MQTT credentials |
| `SYSTEM_PRIVILEGES` | `true` | Enables silent install without device-owner (requires system signing) |
| `REQUEST_SIGNATURE` | `"068bd957..."` | Shared secret for server request signing |
| `LIBRARY_API_KEY` | `"068bd957..."` | API key for privileged lib requests |

Build commands:
```
./gradlew assembleBaseDebug      # debug APK
./gradlew assembleBaseRelease    # release APK
```

---

## Key classes and their roles

### Application entry point

[app/src/main/java/com/base/launcher/App.java](app/src/main/java/com/base/launcher/App.java)
- Extends `Application`
- Initializes Picasso (OkHttp3 downloader)
- Initializes `TerminalManager` (Kozen terminal SDK)
- Initializes `ComponentEngine` (Kozen component SDK) — powers on secondary screen, shows `momo_logo.png`

### Constants

[app/src/main/java/com/base/launcher/Const.java](app/src/main/java/com/base/launcher/Const.java)
- All broadcast action strings, preference keys, timeout values, log levels, state strings.
- `LOG_TAG = "BaseMDM"` — used by `Log.d(Const.LOG_TAG, ...)` throughout.

### Settings / state management

[app/src/main/java/com/base/launcher/helper/SettingsHelper.java](app/src/main/java/com/base/launcher/helper/SettingsHelper.java)
- Singleton: `SettingsHelper.getInstance(context)`
- Wraps `SharedPreferences` for device ID, server URL, MQTT config, cached `ServerConfig` JSON, etc.
- Added by Jean (Apr 2025): external MQTT config getters (`getMqttDomain()`, `getMqttPort()`, `getMqttTls()`, `getMqttUsername()`, `getMqttPassword()`)

### Config update flow

[app/src/main/java/com/base/launcher/helper/ConfigUpdater.java](app/src/main/java/com/base/launcher/helper/ConfigUpdater.java)
- Central orchestrator for the device lifecycle: enroll → fetch config → install certs → install/remove apps → set policies → connect MQTT push.
- `UINotifier` interface — implemented by `MainActivity` to show progress dialogs.
- `ConfigUpdater.notifyConfigUpdate(context)` — call from anywhere to trigger a refresh.
- `ConfigUpdater.forceConfigUpdate(context)` — bypasses "already running" guard.
- Retry: on network error, retries once after 15 s delay.

### Server API

[app/src/main/java/com/base/launcher/server/ServerService.java](app/src/main/java/com/base/launcher/server/ServerService.java) — Retrofit interface.
[app/src/main/java/com/base/launcher/server/ServerServiceKeeper.java](app/src/main/java/com/base/launcher/server/ServerServiceKeeper.java) — builds and caches the Retrofit instance; call `ServerServiceKeeper.resetServices()` after URL migration.

HTTP client: `UnsafeOkHttpClient` (for self-signed cert support when `TRUST_ANY_CERTIFICATE=true`).

JSON serialization: **Jackson 2.9.4** (`converter-jackson`) — **not Gson**.

Request headers:
- `X-Request-Signature` — HMAC signature of the request body
- `X-CPU-Arch` — device CPU architecture
- `X-Response-Signature` — returned by server for response verification
- `X-IP-Address` — device IP (filled by server)

### MQTT push wrapper

[app/src/main/java/com/base/launcher/util/PushNotificationMqttWrapper.java](app/src/main/java/com/base/launcher/util/PushNotificationMqttWrapper.java)
- Singleton: `PushNotificationMqttWrapper.getInstance()`
- Wraps `MqttAndroidClient` (Paho, inlined source at `org.eclipse.paho.android.service`)
- Called from `ConfigUpdater.setupPushService()` when `pushOptions` is `"mqtt_worker"` or `"mqtt_alarm"`.
- Connection-loop protection: stops reconnecting if >15 connections in 60 s (duplicate device ID guard).
- On message: dispatches to `PushNotificationProcessor` via `WorkManager`.

Paho Android Service is **inlined as Java source** in `app/src/main/java/org/eclipse/paho/android/service/` — do not import the external AAR for this.

### Database

[app/src/main/java/com/base/launcher/db/DatabaseHelper.java](app/src/main/java/com/base/launcher/db/DatabaseHelper.java) — `SQLiteOpenHelper` subclass.
- Singleton: `DatabaseHelper.instance(context)`
- Tables: `DownloadTable`, `RemoteFileTable`, `LogTable`, `LogConfigTable`, `InfoHistoryTable`, `LocationTable`

To add a new table:
1. Create `*Table.java` in `com.base.launcher.db` with static `create(db)`, `insert(db, ...)`, `select*(db, ...)` methods.
2. Add `CREATE TABLE` SQL in `DatabaseHelper.onCreate()` and `onUpgrade()` (never drop data).

### Kozen terminal SDK

Libraries in `app/libs/`:
- `TerminalManagerLib_1.1.0_release.aar` — device management (reboot, OTA, certs, APN, location, etc.)
- `ComponentLib_1.2.5_release.aar` — secondary screen / customer display control

Facades:
- [KozenTerminalFacade.java](app/src/main/java/com/base/launcher/kozen/KozenTerminalFacade.java) — thin wrapper around `TerminalManager.INSTANCE`
- [KozenComponentFacade.java](app/src/main/java/com/base/launcher/kozen/KozenComponentFacade.java) — thin wrapper around `ComponentEngine.INSTANCE`

Command dispatcher:
- [KozenCommandHandler.java](app/src/main/java/com/base/launcher/kozen/KozenCommandHandler.java) — receives `{"action": "...", "data": {...}}` JSON from MQTT and dispatches to `KozenTerminalFacade` / `KozenComponentFacade`.

To add a new Kozen command:
1. Add a `case "actionName":` in `KozenCommandHandler.execute()`.
2. Call the appropriate `terminal.*` or `component.*` method.
3. Put `result.put("status", returnCode)` in the case body.

### Secondary screen (customer display)

Controlled via `KozenComponentFacade`:
```java
KozenComponentFacade comp = KozenComponentFacade.get();
comp.power(true);
comp.setBrightness(80);       // 0–100
comp.showPic("/sdcard/Base/momo_logo.png");
comp.showVideo("/sdcard/video.mp4");
comp.setBootLogo("/sdcard/logo.png");
```
Assets are copied to `Environment.getExternalStorageDirectory() + "/Base/"` on `App.onCreate()`.

### iBeacon advertiser

[app/src/main/java/com/base/launcher/IBeaconAdvertiser.java](app/src/main/java/com/base/launcher/IBeaconAdvertiser.java)
- BLE LE advertiser broadcasting an iBeacon frame.
- `IBeaconAdvertiser.start(context)` / `IBeaconAdvertiser.stop(context)`
- UUID is random per install (line 24: `UUID.randomUUID()`).

### Push message processing

[app/src/main/java/com/base/launcher/worker/PushNotificationProcessor.java](app/src/main/java/com/base/launcher/worker/PushNotificationProcessor.java) — `Worker` handling incoming MQTT messages.
[app/src/main/java/com/base/launcher/worker/PushNotificationWorker.java](app/src/main/java/com/base/launcher/worker/PushNotificationWorker.java) — `Worker` for WorkManager-based push.

Push message types are defined in [app/src/main/java/com/base/launcher/json/PushMessage.java](app/src/main/java/com/base/launcher/json/PushMessage.java).

### Plugin SDK (lib module)

[lib/src/main/java/com/base/BaseMDM.java](lib/src/main/java/com/base/BaseMDM.java) — entry point for third-party plugin apps to communicate with the MDM.
[lib/src/main/java/com/base/MDMService.java](lib/src/main/java/com/base/MDMService.java) — AIDL-based service interface.
[lib/src/main/java/com/base/MDMPushHandler.java](lib/src/main/java/com/base/MDMPushHandler.java) — base class for plugin push handlers.

The lib module produces `base_mdm_lib_release_v1000_{date}.aar`.

---

## Manifest highlights

- `sharedUserId="com.base"` — required for inter-app communication with system-signed companion apps.
- `android:usesCleartextTraffic="true"` — HTTP allowed (overridden by `network_security_config.xml` which pins ISRG Root X1).
- Main activity (`MainActivity`) declared as both `LAUNCHER` and `HOME` category — makes the app the Android launcher.
- `MqttService` foreground service type: `specialUse|systemExempted`.
- `AdminReceiver` with `BIND_DEVICE_ADMIN` permission.
- `BootReceiver` starts the app after reboot.
- Plugin API: `PluginApiService` — exported, action `com.base.action.Connect`.
- AIDL interface: `app/src/main/aidl/com/base/IMdmApi.aidl`.

---

## Patterns for common tasks

### Add a new REST endpoint

1. Add method to [ServerService.java](app/src/main/java/com/base/launcher/server/ServerService.java):
```java
@POST("{project}/rest/my/endpoint/{number}")
@Headers("Content-Type: application/json")
Call<ResponseBody> myCall(@Path("project") String project,
                          @Path("number") String number,
                          @Body MyRequest body);
```
2. Call it from a new `AsyncTask` subclass in `com.base.launcher.task` (follow `GetServerConfigTask` pattern).

### Add a new MQTT push command (Kozen)

Add a `case` in [KozenCommandHandler.java:81](app/src/main/java/com/base/launcher/kozen/KozenCommandHandler.java#L81):
```java
case "myNewAction":
    result.put("status", handleMyNewAction(payload));
    break;
```
Add a `private int handleMyNewAction(JSONObject payload)` method.

### Add a new database table

1. Create `app/src/main/java/com/base/launcher/db/MyTable.java`:
```java
public class MyTable {
    public static final String TABLE_NAME = "my_table";
    public static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS my_table (id TEXT PRIMARY KEY, value TEXT)");
    }
    public static void insert(SQLiteDatabase db, String id, String value) {
        ContentValues cv = new ContentValues();
        cv.put("id", id); cv.put("value", value);
        db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
```
2. Call `MyTable.create(db)` in `DatabaseHelper.onCreate()` and `onUpgrade()`.

### Add a new Activity

1. Create `app/src/main/java/com/base/launcher/ui/MyActivity.java` extending `BaseActivity`.
2. Create layout `app/src/main/res/layout/activity_my.xml`.
3. Register in `AndroidManifest.xml`.

---

## Common gotchas

| Symptom | Cause | Fix |
|---|---|---|
| `SettingsHelper.getInstance()` returns stale config | Cached instance not refreshed | Re-call `getInstance(context.getApplicationContext())` after config reload |
| MQTT connects but no commands received | Wrong topic or QoS | Topic must match `deviceId` pattern; use QoS 1; re-subscribe in `connectComplete` |
| OTA update hangs | Large file + short read timeout | `OTA_HTTP_CLIENT` uses 10-min read timeout; ensure server does too |
| Silent install fails silently | `SYSTEM_PRIVILEGES=false` and not device owner | Check `BuildConfig.SYSTEM_PRIVILEGES` or `Utils.isDeviceOwner()` first |
| Secondary screen shows nothing | `ComponentEngine` not initialized | `App.initComponentSdk()` must complete before calling `KozenComponentFacade.get()` |
| Jackson deserialization fails | Unknown JSON field | Add `@JsonIgnoreProperties(ignoreUnknown = true)` to the model class |
| Build fails — missing AAR | `libs/` not present after clean checkout | Ensure `ComponentLib_1.2.5_release.aar` and `TerminalManagerLib_1.1.0_release.aar` are committed to `app/libs/` |
| `Log.*` calls visible in release | No ProGuard rules | Add `-assumenosideeffects` for `android.util.Log` in `proguard-rules.pro` |
