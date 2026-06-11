---
name: base-android
description: Android developer skill for this EDC payment app. Use when adding features, fixing bugs, creating new screens, DAOs, repositories, ViewModels, API endpoints, MQTT handlers, or DI modules. Covers Hilt, Room, Retrofit, MQTT (Eclipse Paho), and SQLite patterns for Android 7.x (minSdk 24).
---

# base-android — EDC App Developer Guide

> **MANDATORY — before every task:** Read `.agents/memory/context/context.md` for the current architecture, file locations, and conventions.
> **MANDATORY — after every task:** Update the most specific matching file under `.agents/memory/context/` in compact mode (one phrase per fact, no prose). Then append a one-line entry to `.agents/memory/log/changelog.md` in the format `YYYY-MM-DDTHH:MM:SS+TZ — <what changed and why>. Context updated: <file(s)>.` If the task adds no durable knowledge, record `Context review: no durable update required` in the task log instead.

---

## Knowledge Lookup Priority

When you need to look up a concept, API, pattern, or integration detail, use this order — stop at the first tier that answers the question:

1. **Local Obsidian vault** — `C:\Git\Vault\`
   - EDC/MQTT/payment specifics: `wiki\bcvn_edc\` (e.g. `edc-payment-flows.md`, `edc-mqtt-integration.md`)
   - Concepts and patterns: `wiki\concepts\`, `wiki\answers\`
   - Raw notes and research: `raw\`
   - Read with the `Read` tool using the absolute path above.

2. **Repo knowledge base** — `.agents/knowledge/`
   - Reference implementations: `.agents/knowledge/zengi_edc/` (older Compose-era source — use for patterns, not for copy-paste)
   - MDM/device management: `.agents/knowledge/mdm/`
   - Docs: `.agents/knowledge/SRS-device-edc-app.md`, `.agents/knowledge/payment-state-manager-payment-flow.md`
   - Use `Read` or `Grep` to search.

3. **Web search** — only when tiers 1 and 2 have no answer.
   - Use `WebSearch` for official library docs, API references, Android SDK specifics, or anything not covered locally.

---

This is a Kotlin Android payment app targeting **minSdk 24 (Android 7.0 Nougat)** for PAX EDC devices. The architecture is MVVM + Clean Architecture with layers: `data` → `domain` → `ui`. The UI layer uses **XML Views + ViewBinding + Fragment Navigation** (Jetpack Compose has been fully removed). Material 2 (`Theme.MaterialComponents.*`) is used because M3 dynamic-color requires Android 12+.

Package root: `com.base.payment`  
Entry point: [BaseEdcApp.kt](app/src/main/java/com/base/payment/BaseEdcApp.kt) (`@HiltAndroidApp`)  
Background service: [ZengiService.kt](app/src/main/java/com/base/payment/service/ZengiService.kt) (handles MQTT + payment broadcasts)

---

## Build & Flavors

Three product flavors in the `version` dimension:

| Flavor | Suffix | Use |
|---|---|---|
| `dev-universal` | `.dev` | Development, points at `baseUrlDev` |
| `uat-universal` | `.uat` | UAT, points at `baseUrlProd` |
| `prod-universal` | *(none)* | Production |

Keys come from `key.properties` (required, never commit). Release signing from `signing.properties` (optional; falls back to env vars).

Build commands:
```
./gradlew assembleDevUniversalDebug        # dev debug APK
./gradlew assembleDevUniversalRelease      # dev release APK (needs signing.properties)
./gradlew assembleProdUniversalRelease     # production release APK
```

APK output name format: `app-{flavor}-{buildType}-v{versionName}.{versionCode}.apk`

Schema files for Room migrations are exported to `app/schemas/`.

---

## Hilt — Dependency Injection

The app uses **Hilt 2.51.1** with **KSP** (not KAPT for most things — but `kotlin-kapt` is still declared for Hilt aggregation).

### Component scopes used

| Scope | Module | Lifetime |
|---|---|---|
| `SingletonComponent` | `DatabaseModule` | App lifetime |
| `ViewModelComponent` | `ApiModule`, `RepositoryModule`, `UseCaseModule` | ViewModel lifetime |

### How to add a new dependency

**Singleton (app-wide)** — add to [DatabaseModule.kt](app/src/main/java/com/base/payment/di/modules/DatabaseModule.kt):
```kotlin
@Provides
@Singleton
fun provideMyThing(@ApplicationContext context: Context): MyThing = MyThing(context)
```

**ViewModel-scoped API** — add to [ApiModule.kt](app/src/main/java/com/base/payment/di/modules/ApiModule.kt):
```kotlin
@Provides
fun provideMyApi(retrofit: Retrofit): MyApi = retrofit.create(MyApi::class.java)
```

**ViewModel-scoped repository** — add to [RepositoryModule.kt](app/src/main/java/com/base/payment/di/modules/RepositoryModule.kt):
```kotlin
@Provides
fun provideMyRepo(myApi: MyApi, sharedPreferences: SharedPreferences): MyRepository =
    MyRepositoryImpl(myApi, sharedPreferences)
```

**ViewModel injection** — use `@HiltViewModel` + `@Inject constructor(...)`:
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val myRepo: MyRepository
) : BaseViewModel()
```

**Fragment** — annotate with `@AndroidEntryPoint` and use `by viewModels()`:
```kotlin
@AndroidEntryPoint
class MyFragment : Fragment(R.layout.fragment_my) {
    private val viewModel: MyViewModel by viewModels()
}
```

> **Gotcha — minSdk 24:** `@ApplicationContext` works fine. Avoid any Hilt annotation that requires API 26+. All `@Provides` in `SingletonComponent` are safe.

---

## Room — Local Database

**Room 2.6.1** with KSP. Database name: `Zengi_DB`. Current version: **5**.  
Schema exported to `app/schemas/` — always export and commit schema files after migrations.

Database class: [AppDatabase.kt](app/src/main/java/com/base/payment/common/database/AppDatabase.kt)  
Entities: `TransactionModel`, `BatchModel`  
DAOs: [TransactionDao.kt](app/src/main/java/com/base/payment/common/database/dao/TransactionDao.kt), [BatchDao.kt](app/src/main/java/com/base/payment/common/database/dao/BatchDao.kt)

### How to add a new entity + DAO

1. Create the entity in `data/model/`:
```kotlin
@Entity(tableName = "MyTable")
data class MyModel(
    @PrimaryKey val id: String,
    val name: String
)
```

2. Create a DAO in `common/database/dao/`:
```kotlin
@Dao
interface MyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: MyModel)

    @Query("SELECT * FROM MyTable WHERE id = :id")
    fun getById(id: String): MyModel?
}
```

3. Register the entity and add the DAO accessor in `AppDatabase`:
```kotlin
@Database(
    entities = [TransactionModel::class, BatchModel::class, MyModel::class],
    version = 6,  // bump version
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun myDao(): MyDao
    // ...
}
```

4. Write a migration (never use `fallbackToDestructiveMigration` — EDC devices need data preserved):
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `MyTable` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}
```

5. Add migration to `AppDatabase.build()` and add `@Provides` in `DatabaseModule`.

> **Gotcha — minSdk 24:** Room works fully on API 24. Do not use `@AutoMigration` with `autoMigrateFrom` — manual migrations are required and already established.

---

## Retrofit — HTTP Networking

**Retrofit 2.9.0** + **OkHttp 4.12.0** + `converter-gson`.

Network module: created in `RepositoryModule` / `ApiModule` (Retrofit singleton provided via a network module — if you don't see it, add one to `DatabaseModule` or create `NetworkModule`).  
Interceptor: [AppInterceptor.kt](app/src/main/java/com/base/payment/common/network/AppInterceptor.kt) — adds `x-tid`/`x-mid` headers from `DeviceConfig` and handles billing request signing.

### How to add a new API endpoint

1. Create an interface in `data/api/`:
```kotlin
interface MyApi {
    @POST("v1/my-endpoint")
    fun doSomething(@Body request: MyRequest): Call<MyResponse>
}
```

2. Register in [ApiModule.kt](app/src/main/java/com/base/payment/di/modules/ApiModule.kt):
```kotlin
@Provides
fun provideMyApi(retrofit: Retrofit): MyApi = retrofit.create(MyApi::class.java)
```

3. Use `Call<T>.execute()` wrapped in `withContext(Dispatchers.IO)` inside the repository (see `PaymentRepositoryImpl` for the retry + `runCatching` pattern):
```kotlin
override suspend fun doSomething(request: MyRequest): MyResponse? {
    return withContext(Dispatchers.IO) {
        try {
            val response = myApi.doSomething(request).execute()
            if (response.isSuccessful) response.body() else null
        } catch (_: Exception) { null }
    }
}
```

> **Pattern:** Repos return `null` or a default object on failure — never rethrow to the ViewModel. Network calls always use `Dispatchers.IO`. Use `retryOnTimeout` pattern (see `PaymentRepositoryImpl`) for calls that may timeout on EDC hardware.

> **Gotcha — cleartext:** `android:usesCleartextTraffic="false"` in manifest. All API URLs must be HTTPS. Dev/UAT URLs from `key.properties`.

---

## MQTT — Eclipse Paho

**Library:** `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.1.0` + `org.eclipse.paho:org.eclipse.paho.android.service:1.1.1`  
**Wrapper class:** [MQTTUtil.kt](app/src/main/java/com/base/payment/utils/MQTTUtil.kt)  
**Service host:** [ZengiService.kt](app/src/main/java/com/base/payment/service/ZengiService.kt) — MQTT runs on a background `HandlerThread`, health-check pings every 30s.

### MQTTUtil API

```kotlin
mqttService.init(
    context = context,
    mqttConfig = MQTTConfig(
        domain = "mqtt.thebase.vn",
        username = "usolution",
        password = "theBase2026@",
        clientId = deviceId,
        port = "1883",       // or "18883" for TLS
        useSsl = false
    ),
    onInitMQTTComplete = { status: MQTTConnectStatus -> /* handle */ },
    onMessageReceived = { payload: String -> /* handle JSON */ }
)

mqttService.sendMessage(topic = "$deviceId-ping", payload = jsonString) {
    // connection lost callback
}

mqttService.disconnect { success -> }
mqttService.clearCache()  // call in onDestroy
```

### Connection status enum

`MQTTConnectStatus`: `CONNECT_SUCCESS`, `CONNECT_FAILURE`, `CONNECTION_LOST`, `INITIALIZING`, `INTERNET_DISCONNECTED`, `RECONNECTING`, `SERVER_KICKED`

### Message types (`MQTTMsgType`)

`QR`, `SALE_PAYMENT`, `SETTLEMENT_PAYMENT`, `VOID_PAYMENT`, `PAYMENT_RESULT`, `LOG_PAYMENT`, `DEVICE_CONFIG_CHANGE`

### Adding a new MQTT message handler

Add a branch in `ZengiService.onMessageReceived()`:
```kotlin
MQTTMsgType.MY_NEW_TYPE.value -> {
    sendPaymentInfo(ACTION_MY_ACTION, MQTTMsgType.MY_NEW_TYPE.value, requestInfo)
}
```

Then handle the broadcast in `HomeViewModel` (or wherever you register `LocalBroadcastManager`).

> **Gotcha — minSdk 24 / Paho Android Service:** `MqttAndroidClient` from `paho.android.service` requires `org.eclipse.paho.android.service.MqttService` declared in `AndroidManifest.xml` — it is already there. Do not remove it. On Android 7, background service restrictions are less strict than 8+, which is why the design uses a plain `Service` (not `JobIntentService` or `WorkManager`).

> **Gotcha — TLS:** Use port `18883` for SSL, `1883` for TCP. The `useSsl` flag switches the protocol prefix between `ssl://` and `tcp://`. TLSv1.2 is forced explicitly to avoid handshake failures on older PAX firmware.

> **Gotcha — WakeLock on pre-O:** `ZengiService.sendPaymentInfo()` acquires `FULL_WAKE_LOCK` on `Build.VERSION.SDK_INT < Build.VERSION_CODES.O` (i.e., Android < 8, which includes our minSdk 24 target) to wake the screen when a payment arrives. This is intentional for EDC devices that may be in sleep mode.

---

## SQLite / SharedPreferences

**Room** handles all structured DB access (see above).

**SharedPreferences** is used for device config, tokens, and lightweight state:
- Class: [AppSharedPreferences.kt](app/src/main/java/com/base/payment/data/AppSharedPreferences.kt)
- Provided as `@Singleton` `SharedPreferences` by `DatabaseModule`
- Inject `SharedPreferences` directly into repos — do not create new instances

```kotlin
// Reading
val config = sharedPreferences.getString(AppSharedPreferences.KEY_DEVICE_CONFIG, "") ?: ""

// Writing (use ktx extension)
sharedPreferences.edit {
    putString(AppSharedPreferences.KEY_DEVICE_CONFIG, json)
}
```

For sensitive data at rest, the project includes `androidx.security:security-crypto:1.1.0-alpha06` — use `EncryptedSharedPreferences` for secrets like keys/tokens.

---

## Architecture — MVVM + Clean

```
ui/presentation/<feature>/
    <Feature>Fragment.kt     — @AndroidEntryPoint Fragment, ViewBinding, collects StateFlow
    <Feature>ViewModel.kt    — @HiltViewModel, extends BaseViewModel

res/layout/
    fragment_<feature>.xml   — XML layout, bound via FragmentFeatureBinding

res/navigation/
    nav_graph.xml            — root nav graph (splash → home)
    nav_home_graph.xml       — bottom-tab sub-graph (payment, transaction list, account)

domain/
    repo/<Foo>Repository.kt  — interface
    usecase/<Foo>UseCase.kt  — interface (optional thin layer)

data/
    api/<domain>/<Foo>Api.kt         — Retrofit interface
    repo/<Foo>RepositoryImpl.kt      — implements domain interface
    model/<domain>/<Foo>Model.kt     — data classes
```

### BaseViewModel

[BaseViewModel.kt](app/src/main/java/com/base/payment/common/base/BaseViewModel.kt) — all feature ViewModels extend it.

### ViewModel state pattern

Use `MutableStateFlow` / `StateFlow` — **not** `mutableStateOf` (Compose runtime removed):
```kotlin
private val _myState = MutableStateFlow<MyState>(MyState.Initial)
val myState: StateFlow<MyState> = _myState.asStateFlow()
```

Collect in Fragment with `repeatOnLifecycle(STARTED)`:
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.myState.collect { state -> /* update UI */ }
    }
}
```

### Navigation

Root nav graph: `res/navigation/nav_graph.xml`  
Home sub-graph: `res/navigation/nav_home_graph.xml`  
Fragment retrieves NavController via `findNavController()`.  
ViewModels emit navigation events via `StateFlow<Pair<String,String>?>` — never hold a `NavController` reference.  
Safe Args used for Fragment argument passing (check `nav_graph.xml` for `<argument>` declarations).

### AppBank IPC

Use `ActivityResultLauncher<Intent>` (from `androidx.activity.result`) — **not** the removed Compose-era `ManagedActivityResultLauncher`.

---

## Logging

**Timber** (`5.0.1`). Never use `Log.*` directly.

```kotlin
Timber.d("debug message")
Timber.w("warning")
Timber.e(exception, "error message")
```

- `dev-*` debug builds: full `DebugTree` with file:line tags
- `dev-*` release builds: INFO and above only
- `prod-*` / `uat-*`: no logging (empty tree planted)

---

## PAX / EDC Device Specifics

- Target hardware: PAX devices (manufacturer `PAX` detected at runtime)
- Neptune Lite API loaded from `app/libs/*.jar` via `fileTree`
- ABI filters: `armeabi-v7a`, `arm64-v8a` only (no x86)
- `jniLibs.useLegacyPackaging = true` required for the native `.so` files
- PAX permissions declared in manifest: `com.pax.permission.ICC/PICC/MAGCARD/PRINTER/PED/USB_SECURITY/LCD`
- `BootCompletedReceiver` auto-starts the service after device reboot — registered for `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, and `MY_PACKAGE_REPLACED`

---

## Common Gotchas

| Symptom | Cause | Fix |
|---|---|---|
| Hilt `@Provides` missing component error | New API in wrong module scope | Check `@InstallIn` — most repos/APIs go in `ViewModelComponent`, DB/SharedPrefs in `SingletonComponent` |
| Room crash on schema version mismatch | Bumped entity without migration | Write `MIGRATION_N_(N+1)` and register it in `AppDatabase.build()` |
| MQTT connects but no messages | Topic subscription race | Topic is subscribed in `setupConnectionAndSubscribe()` using `mqttConfig.clientId` — ensure `clientId` is set before `init()` |
| Retrofit response always null | Calling suspend fun without `withContext(IO)` | All `.execute()` calls must run on IO dispatcher |
| Build fails — `key.properties not found` | Missing local config file | Create `key.properties` at repo root with `baseUrlDev`, `baseUrlProd`, `clientIdDev`, `clientIdProd`, `thirdPartyApiKey`, `thirdPartyId`, `bidvPackageName` |
| APK not installable on PAX device | Wrong signing config | Use `zengi_edc_keystore.jks` via `signing.properties`; v1 signing must be enabled, v2 disabled (PAX firmware requirement) |
