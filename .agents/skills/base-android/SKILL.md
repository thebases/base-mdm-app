---
name: base-android
description: Android developer skill for any Android app in this org — Java or Kotlin, MVVM or Activity-based, Hilt or manual DI, Room or SQLiteOpenHelper, Jetpack Compose or XML Views. Use when adding features, fixing bugs, creating screens, database tables, API endpoints, MQTT handlers, push/notification flows, device-management commands, or SDK integrations on Android 7.x+ (minSdk 24). Always load the matching workspace reference before starting a task.
---

# base-android — Android Developer Skill

> **MANDATORY — before every task:**
> 1. Load the workspace-specific reference: `.agents/skills/base-android/references/<workspace-name>.md`
> 2. Read `.agents/memory/context/context.md` (if present) for current architecture state and open tasks.
>
> **MANDATORY — after every task:**
> Update the most specific file under `.agents/memory/context/` (compact mode: one phrase per fact). Append a one-line entry to `.agents/memory/log/changelog.md`:
> `YYYY-MM-DDTHH:MM:SS+TZ — <what changed and why>. Context updated: <file(s)>.`
> If no durable knowledge was added, record: `Context review: no durable update required`.

---

## Available workspace references

| Workspace | Reference file |
|---|---|
| base-mdm-app | [references/base-mdm-app.md](references/base-mdm-app.md) |

---

## Knowledge Lookup Priority

Stop at the first tier that answers the question:

1. **Workspace reference** — `.agents/skills/base-android/references/<workspace>.md`
   Contains the actual architecture, class locations, patterns, and gotchas for this repo.

2. **Local Obsidian vault** — `C:\Git\Vault\`
   - EDC/MQTT/payment specifics: `wiki\bcvn_edc\`
   - Concepts and patterns: `wiki\concepts\`, `wiki\answers\`
   - Raw notes: `raw\`

3. **Repo knowledge base** — `.agents/knowledge/`
   - Reference implementations, SRS, flow diagrams.

4. **Web search** — only when tiers 1–3 have no answer.

---

## Universal Android Patterns

These apply regardless of which workspace you are in. The workspace reference overrides any pattern below where the specific project diverges.

### Build system

All repos use **Gradle** with `build.gradle` (Groovy DSL) or `build.gradle.kts` (Kotlin DSL).

- `compileSdk` / `minSdk` / `targetSdk` are in the module-level `build.gradle`.
- Product flavors are in a `productFlavors` block inside `android {}`.
- Signing configs reference keystore files; never commit keystore paths or passwords.
- APK output name is controlled by `android.applicationVariants.all` or `android.libraryVariants.configureEach`.

### Dependency injection

| Style | When used | How to add a dependency |
|---|---|---|
| **Hilt** (modern) | Annotated `@HiltViewModel`, `@AndroidEntryPoint` | Add `@Provides` to the matching `@Module`; inject via constructor or field |
| **Manual / singleton** (legacy) | `getInstance(context)` helpers | Add to the appropriate helper or pass via constructor |

### Local database

| Style | When used | How to add a table |
|---|---|---|
| **Room** | DAOs + `@Database` class | Create `@Entity`, `@Dao`, bump DB version, write `Migration` |
| **SQLiteOpenHelper** | `DatabaseHelper` subclass | Add table in `onCreate`/`onUpgrade`, add a `*Table` helper class |

Never use `fallbackToDestructiveMigration` on devices that hold business-critical data.

### Networking (Retrofit)

All repos use **Retrofit** with OkHttp. Jackson or Gson is used for JSON.
- Define service interface with `@GET`/`@POST`/`@PUT`.
- Execute calls in a background thread (IO dispatcher for coroutines, `AsyncTask`/`Executor` for Java legacy).
- Log/retry at the repository layer; never rethrow to UI.

### MQTT (Eclipse Paho)

Both library variants are used across repos:
- `org.eclipse.paho.client.mqttv3` — core MQTT library
- Paho Android Service — either imported as AAR or inlined as Java source (`org.eclipse.paho.android.service` package)

Connection params (broker, port, TLS, credentials) come from `BuildConfig` fields or `SettingsHelper`/`SharedPreferences`. Never hardcode them in the MQTT client call.

Subscribe to topics after connection is established (`onConnectComplete`). Use `connectWithResult` or `MqttCallbackExtended.connectComplete` to re-subscribe after reconnect.

### Push / background work

| Pattern | Use case |
|---|---|
| `WorkManager` (`Worker` / `CoroutineWorker`) | Deferrable, constraint-based background work |
| `AsyncTask` (Java legacy) | Short-lived background work in older code; avoid in new code |
| Foreground `Service` | Long-running work that must survive low-memory (MQTT, location) |

### UI

| Style | When used |
|---|---|
| **XML Views + ViewBinding + Fragments** | Modern XML path (no Compose) |
| **Jetpack Compose** | Only in repos where it is already established |
| **Activities + XML** | Legacy repos — extend `BaseActivity`, find views by ID or use ViewBinding |

### Logging

- **Timber** (modern repos): `Timber.d()`, `Timber.e()` — never use `Log.*`
- **`Log.*` / `RemoteLogger`** (legacy repos): `Log.d(TAG, msg)` + `RemoteLogger.log(context, level, msg)` for remote-sent logs

### Permissions

- Declare in `AndroidManifest.xml`; request dangerous permissions at runtime.
- `SYSTEM_PRIVILEGES` build flag enables silent installs/uninstalls without device-owner grant.
- `DevicePolicyManager` calls require device-owner or profile-owner role.

### Device admin / MDM

- Device owner is checked via `Utils.isDeviceOwner(context)`.
- Admin actions (wipe, lock, reset password) go through `DevicePolicyManager`.
- Declare `AdminReceiver` in manifest with `android.app.device_admin` resource.

---

## Common Gotchas (universal)

| Symptom | Likely cause | Fix |
|---|---|---|
| MQTT reconnects in a tight loop | Two devices registered with the same client ID | Check connection-loop protection counter; ensure device IDs are unique |
| Silent install silently fails | Missing device-owner or `SYSTEM_PRIVILEGES` | Verify `isDeviceOwner()` or `BuildConfig.SYSTEM_PRIVILEGES` before `silentInstallApplication()` |
| `AsyncTask` crash on orientation change | Activity reference captured in `AsyncTask` | Use `WeakReference<Activity>` or move work to ViewModel/Worker |
| Retrofit call returns null on older devices | Wrong thread (UI thread) | Wrap in `new AsyncTask` or move to background executor |
| Room schema mismatch | Entity changed without migration | Write `MIGRATION_N_(N+1)`, register in `addMigrations()` |
| Hilt missing binding | New class in wrong component | Check `@InstallIn` scope; repos/APIs → `ViewModelComponent`, DB/prefs → `SingletonComponent` |
