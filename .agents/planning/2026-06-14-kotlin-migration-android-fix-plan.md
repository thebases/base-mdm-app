# Fix Plan — Kotlin Migration + Review Findings
**Date:** 2026-06-14  
**App:** base-mdm-app (`com.base.launcher`)  
**Goal:** Add Kotlin support and incrementally migrate the codebase following the recommended approach from the review report, while also resolving all blocking and important findings.

---

## Priority Order (most critical → least)

1. **[PHASE 0]** Fix blocking security issues (B1, B3) — no Kotlin required
2. **[PHASE 1]** Add Kotlin support to the project
3. **[PHASE 2]** Update dependencies (I1)
4. **[PHASE 3]** Migrate `json/` data models → Kotlin `data class` (Recommended step 2)
5. **[PHASE 4]** Migrate `util/` helpers → Kotlin (Recommended step 3)
6. **[PHASE 5]** Replace `AsyncTask` in `task/` with coroutines (Recommended step 4 / B4)
7. **[PHASE 6]** Fix architectural issues (B2, I2, I3, I4, I5)
8. **[PHASE 7]** MainActivity decomposition (prerequisite for Recommended step 5)

> **Rule:** All **new files** written from today must be `.kt`. Never create new `.java` files.

---

## PHASE 0 — Security Fixes (Do Before Anything Else)

### Fix B1 — Remove hardcoded credentials from build.gradle

**Files to change:** `app/build.gradle`  
**Effort:** Small

**Action:**

1. Create `local.properties` (already git-ignored by Android Studio default `.gitignore`). Add:
   ```properties
   mqtt.password=theBase15112023@
   mqtt.username=basemdm
   mqtt.domain=mqtt.thebase.vn
   request.signature=068bd957181f91076e962a61c8788487
   library.api.key=068bd957181f91076e962a61c8788487
   keystore.password=Base12345678@
   keystore.alias=base
   ```

2. In `app/build.gradle`, load `local.properties` at the top:
   ```groovy
   def localProps = new Properties()
   def localPropsFile = rootProject.file('local.properties')
   if (localPropsFile.exists()) { localPropsFile.withReader { localProps.load(it) } }
   ```

3. Replace hardcoded `buildConfigField` values:
   ```groovy
   buildConfigField("String", "MQTT_PASSWORD", "\"${localProps['mqtt.password']}\"")
   buildConfigField("String", "MQTT_USERNAME", "\"${localProps['mqtt.username']}\"")
   buildConfigField("String", "REQUEST_SIGNATURE", "\"${localProps['request.signature']}\"")
   buildConfigField("String", "LIBRARY_API_KEY", "\"${localProps['library.api.key']}\"")
   ```

4. Replace `signingConfigs` to use `local.properties`:
   ```groovy
   signingConfigs {
       config {
           storeFile file('C:\\Git\\android_keystores\\base\\base.jks')
           keyAlias = localProps['keystore.alias'] ?: 'base'
           keyPassword localProps['keystore.password']
           storePassword localProps['keystore.password']
       }
   }
   ```

5. Add `local.properties` to `.gitignore` if not already present.

6. **Rotate** MQTT password and request signature key on the server — the old values are in git history and must be considered compromised.

**Definition of done:** `git grep "theBase15112023"` returns no hits in tracked files.

---

### Fix B3 — Move DEVICE_ADMIN_DEBUG to debug buildType only

**File:** `app/build.gradle`  
**Effort:** Small

**Action:**

1. Remove from `defaultConfig`:
   ```groovy
   // DELETE THIS LINE:
   buildConfigField("Boolean", "DEVICE_ADMIN_DEBUG", "true")
   ```

2. Add to `buildTypes.debug`:
   ```groovy
   debug {
       buildConfigField("Boolean", "DEVICE_ADMIN_DEBUG", "true")
       // ...existing config
   }
   ```

3. Add to `buildTypes.release` (explicitly off):
   ```groovy
   release {
       buildConfigField("Boolean", "DEVICE_ADMIN_DEBUG", "false")
       // ...existing config
   }
   ```

**Definition of done:** Release APK has `BuildConfig.DEVICE_ADMIN_DEBUG == false`.

---

## PHASE 1 — Add Kotlin Support

**Files to change:** `build.gradle` (root), `app/build.gradle`  
**Effort:** Small  
**Dependency:** None — can be done standalone.

**Action:**

1. Root `build.gradle` — add Kotlin plugin to `buildscript.dependencies`:
   ```groovy
   classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21'
   ```

2. `app/build.gradle` — apply plugin at the top (after `com.android.application`):
   ```groovy
   apply plugin: 'kotlin-android'
   ```

3. `app/build.gradle` — add `kotlinOptions` inside `android {}`:
   ```groovy
   kotlinOptions {
       jvmTarget = '11'
   }
   ```

4. `app/build.gradle` — add stdlib and coroutines to `dependencies`:
   ```groovy
   implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.0.21'
   implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0'
   implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0'
   ```

5. Sync project. Verify existing Java files still compile — Kotlin plugin is additive, no Java files change.

6. Create a throwaway `app/src/main/java/com/base/launcher/Hello.kt` with one line `val test = "ok"`, build, then delete it. Confirms Kotlin compiles.

**Definition of done:** `./gradlew assembleBaseDebug` succeeds with Kotlin plugin active and no Java compilation errors.

---

## PHASE 2 — Update Dependencies (I1)

**File:** `app/build.gradle`  
**Effort:** Medium (test each update individually)

Update in this order (most CVE-critical first):

| Library | From | To | Action |
|---|---|---|---|
| `jackson-databind` | 2.9.4 | 2.18.3 | Change version. Also update `jackson-core` and `jackson-annotations` to match. |
| `jackson-core` | 2.9.4 | 2.18.3 | Same |
| `jackson-annotations` | 2.9.4 | 2.18.3 | Same |
| `retrofit2` | 2.3.0 | 2.11.0 | Change version. Check `converter-jackson` matches. |
| `converter-jackson` | 2.3.0 | 2.11.0 | Same |
| `appcompat` | 1.1.0 | 1.7.0 | Change version |
| `recyclerview` | 1.1.0 | 1.3.2 | Change version |
| `material` | 1.1.0 | 1.12.0 | Change version |
| `commons-io` | 2.0.1 | 2.17.0 | Change version |
| `picasso` | 2.5.2 | 2.8.0 | Change version; verify `picasso2-okhttp3-downloader` still compatible |
| `work-runtime` | 2.9.1 | 2.10.0 | Change version |

**After each group update:** Build and run on device. Verify server config sync and MQTT still works.

**Definition of done:** `./gradlew dependencyUpdates` (if plugin added) or manual check shows no dependency with known CVE. App boots, syncs config, and receives MQTT push successfully.

---

## PHASE 3 — Migrate `json/` Package to Kotlin `data class`

**Recommended approach step 2:** "Migrate the `json/` data model package first (pure POJOs → data classes, low risk)."

**Files (20 total):** All `.java` files in `app/src/main/java/com/base/launcher/json/`  
**Effort:** Medium  
**Dependency:** Phase 1 must be complete.

**Migration order (inner → outer, no interdependency first):**

| Order | File | Notes |
|---|---|---|
| 1 | `Action.java` | Simple POJO |
| 2 | `Download.java` | Simple POJO |
| 3 | `RemoteLogItem.java` | Simple POJO |
| 4 | `RemoteLogConfig.java` | Simple POJO |
| 5 | `RemoteLogConfigResponse.java` | Contains `RemoteLogConfig` |
| 6 | `ApplicationSetting.java` | Simple POJO |
| 7 | `DetailedInfo.java` | Simple POJO |
| 8 | `DetailedInfoConfig.java` | Simple POJO |
| 9 | `DetailedInfoConfigResponse.java` | Contains `DetailedInfoConfig` |
| 10 | `ServerResponse.java` | Simple POJO |
| 11 | `PushMessageJson.java` | Simple POJO |
| 12 | `PushMessage.java` | May reference `PushMessageJson` |
| 13 | `PushResponse.java` | Contains `PushMessage` list |
| 14 | `DeviceCreateOptions.java` | Simple POJO |
| 15 | `DeviceEnrollOptions.java` | May reference `DeviceCreateOptions` |
| 16 | `RemoteFile.java` | Simple POJO |
| 17 | `DeviceInfo.java` | Larger POJO |
| 18 | `ServerConfigResponse.java` | Wraps `ServerConfig` |
| 19 | `Application.java` | Complex POJO, many fields |
| 20 | `ServerConfig.java` | Largest — do last |

**Pattern for each file:**

```kotlin
// Before (Java):
public class Action {
    @JsonProperty("id")
    private String id;
    @JsonProperty("action")
    private String action;
    // getters/setters...
}

// After (Kotlin):
import com.fasterxml.jackson.annotation.JsonProperty

data class Action(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("action") val action: String? = null
)
```

**Rules:**
- All fields default to `null` — Jackson will handle absent JSON fields.
- Keep `@JsonProperty` annotations if field name differs from JSON key.
- Delete the `.java` file after the `.kt` file builds successfully.
- Do NOT use `@JvmField` unless you find a Java caller that breaks — check with `Grep` before adding.

**Definition of done:** All 20 `.java` files in `json/` deleted; `./gradlew assembleBaseDebug` succeeds; no Java caller is broken (verified by successful build).

---

## PHASE 4 — Migrate `util/` Package to Kotlin

**Recommended approach step 3:** "Migrate `util/` helpers next (no Android lifecycle, easy to unit test)."

**Files (14 total):** All `.java` files in `app/src/main/java/com/base/launcher/util/`  
**Effort:** Medium-Large  
**Dependency:** Phase 1 complete. Phase 3 recommended first (util classes reference json models).

**Migration order (least-coupled first):**

| Order | File | Notes |
|---|---|---|
| 1 | `CrashLoopProtection.java` | Pure SharedPreferences logic, no complex deps |
| 2 | `PreferenceLogger.java` | Pure SharedPreferences logic |
| 3 | `LegacyUtils.java` | Static utility methods |
| 4 | `ConnectionWaiter.java` | Thread-based, convert to coroutine suspension |
| 5 | `CryptoUtils.java` | Pure crypto — no Android lifecycle |
| 6 | `AppInfo.java` | PackageManager queries |
| 7 | `FileUtils.java` | File I/O helpers |
| 8 | `XapkUtils.java` | XAPK extraction — file I/O |
| 9 | `RemoteLogger.java` | References json models; migrate after Phase 3 |
| 10 | `PushNotificationMqttWrapper.java` | MQTT wrapper |
| 11 | `DeviceInfoProvider.java` | Heavy — reads IMEI, serial, MAC |
| 12 | `SystemUtils.java` | Device owner/system-level APIs |
| 13 | `InstallUtils.java` | APK installation helpers |
| 14 | `Utils.java` | Largest utility class — do last |

**Pattern for static utility classes:**

```kotlin
// Java static class becomes Kotlin object (singleton)
object CrashLoopProtection {
    private const val PREF_FAULT_COUNT = "fault_count"

    fun isCrashLoopDetected(context: Context): Boolean {
        // ...
    }

    fun registerFault(context: Context) {
        // ...
    }
}
```

**Pattern for `ConnectionWaiter` (thread → coroutine):**

```kotlin
// Replace Thread.sleep loops with suspendable delays
suspend fun waitForConnection(context: Context, timeoutMs: Long): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (isNetworkAvailable(context)) return true
        delay(500)
    }
    return false
}
```

**Definition of done:** All 14 `.java` files in `util/` deleted; build succeeds; callers (MainActivity, services, tasks) still compile via Kotlin interop.

---

## PHASE 5 — Replace `AsyncTask` in `task/` with Coroutines (B4)

**Recommended approach step 4:** "Replace `AsyncTask` in `task/` with coroutines as you touch each file."

**Files (6 total):** All `.java` files in `app/src/main/java/com/base/launcher/task/`  
**Effort:** Medium  
**Dependency:** Phase 1 complete. Coroutines dependency added in Phase 1.

**Pattern for each task:**

```kotlin
// Before (AsyncTask):
class SendDeviceInfoTask(context: Context) : AsyncTask<DeviceInfo, Void, Void>() {
    override fun doInBackground(vararg params: DeviceInfo): Void? {
        // network call
        return null
    }
}
// Usage: SendDeviceInfoTask(context).execute(deviceInfo)

// After (Kotlin coroutine function):
object SendDeviceInfoTask {
    fun execute(context: Context, deviceInfo: DeviceInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // network call
            } catch (e: Exception) {
                RemoteLogger.log(context, Const.LOG_ERROR, "SendDeviceInfo failed: ${e.message}")
            }
        }
    }
}
// Usage: SendDeviceInfoTask.execute(context, deviceInfo)
```

**Migration order:**

| Order | File | Notes |
|---|---|---|
| 1 | `ConfirmDeviceResetTask.java` | Simplest confirm task |
| 2 | `ConfirmPasswordResetTask.java` | Same pattern |
| 3 | `ConfirmRebootTask.java` | Same pattern |
| 4 | `GetRemoteLogConfigTask.java` | GET request + callback |
| 5 | `SendDeviceInfoTask.java` | Called from MainActivity — update callers after |
| 6 | `GetServerConfigTask.java` | Most complex — update all callers after |

**Also fix AsyncTask uses inside MainActivity.java** (lines 534, 581, 711):
- Extract each `new AsyncTask<Void,Void,Void>() {...}.execute()` block into a named coroutine call using `lifecycleScope.launch(Dispatchers.IO) { ... }`.
- Note: `lifecycleScope` is only available in Kotlin. These three specific blocks in `MainActivity.java` must be migrated to Kotlin OR use `Executors.newSingleThreadExecutor()` as an interim fix if MainActivity is not yet converted.
- **Interim fix** (if MainActivity stays Java):
  ```java
  // Replace AsyncTask with:
  ExecutorService executor = Executors.newSingleThreadExecutor();
  Handler handler = new Handler(Looper.getMainLooper());
  executor.execute(() -> {
      // background work
      handler.post(() -> {
          // UI update
      });
  });
  ```

**Definition of done:** Zero `AsyncTask` references in the codebase (`Grep "AsyncTask"` returns no hits in `src/`).

---

## PHASE 6 — Fix Architectural and Code Quality Issues

### Fix I2 — Silent empty catch blocks

**Effort:** Small  
**File:** `MainActivity.java` + scan all files

**Action:**

1. Search for empty catches:
   ```
   Grep pattern: catch\s*\([^)]+\)\s*\{\s*\}
   ```
2. For each hit, add at minimum:
   ```java
   } catch (Exception e) {
       RemoteLogger.log(context, Const.LOG_WARN, "Non-fatal: " + e.getMessage());
   }
   ```
3. For the specific case at `MainActivity.java:291` (policy application), escalate to `LOG_ERROR`.

**Definition of done:** `Grep "catch.*\{\s*\}"` returns no hits in `src/main/`.

---

### Fix I3 — Remove `static boolean configInitialized`

**File:** `app/src/main/java/com/base/launcher/ui/MainActivity.java:174`  
**Effort:** Small-Medium

**Action:**

1. Move `configInitialized` to `SettingsHelper` as a non-static instance field:
   ```java
   // In SettingsHelper.java
   private boolean configInitialized = false;
   public boolean isConfigInitialized() { return configInitialized; }
   public void setConfigInitialized(boolean value) { configInitialized = value; }
   ```
2. Update all references in `MainActivity`:
   ```java
   // Before:
   if (!configInitialized) { ... }
   configInitialized = true;
   // After:
   if (!settingsHelper.isConfigInitialized()) { ... }
   settingsHelper.setConfigInitialized(true);
   ```
3. On `onDestroy`, optionally reset the flag so a process restart re-fetches config.

**Definition of done:** `Grep "static.*configInitialized"` returns no hits.

---

### Fix I4 — Replace deprecated CONNECTIVITY_ACTION broadcast

**File:** `app/src/main/java/com/base/launcher/ui/MainActivity.java:272`  
**Effort:** Medium

**Action:**

1. In `onCreate`, after existing receiver registration, add a `NetworkCallback`:
   ```java
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
       ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
       networkCallback = new ConnectivityManager.NetworkCallback() {
           @Override
           public void onAvailable(Network network) {
               runOnUiThread(() -> applyEarlyPolicies(settingsHelper.getConfig()));
           }
           @Override
           public void onLost(Network network) {
               RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Network connection lost");
           }
       };
       cm.registerDefaultNetworkCallback(networkCallback);
   }
   ```
2. Declare `private ConnectivityManager.NetworkCallback networkCallback;` as a field.
3. In `onDestroy`, unregister:
   ```java
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
       ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
       cm.unregisterNetworkCallback(networkCallback);
   }
   ```
4. Remove `ConnectivityManager.CONNECTIVITY_ACTION` from `stateChangeReceiver`'s `IntentFilter` and remove the handler branch for it.

**Definition of done:** `Grep "CONNECTIVITY_ACTION"` returns no hits in `src/main/`.

---

### Fix I5 — Add unit tests for core logic

**Effort:** Large  
**Dependency:** Phases 3 and 4 complete (Kotlin files are easier to test).

**Action:**

1. Add test dependencies in `app/build.gradle`:
   ```groovy
   testImplementation 'junit:junit:4.13.2'
   testImplementation 'org.mockito:mockito-core:5.11.0'
   testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0'
   ```

2. Write tests for (in priority order):
   - `CrashLoopProtection` — pure SharedPreferences logic, easily mocked
   - `CryptoUtils` — pure functions, deterministic input/output
   - `PreferenceLogger` — SharedPreferences logic
   - `DeviceInfoProvider` — mock PackageManager, test field extraction
   - `GetServerConfigTask` / `SendDeviceInfoTask` — after coroutine migration, test with mock Retrofit

3. Test file location: `app/src/test/java/com/base/launcher/`

**Definition of done:** `./gradlew test` passes with at least 20 unit tests covering the classes above.

---

### Fix I6 — Remove dead iBeacon commented code

**File:** `app/src/main/java/com/base/launcher/ui/MainActivity.java:402–409`  
**Effort:** Small

**Action:**

1. Decide: is `IBeaconAdvertiser` actively used via `startBeaconWithPermissionCheck()`?
   - If **yes**: remove only the three commented-out lines (lines 403–408).
   - If **no**: remove the commented lines AND the `startBeaconWithPermissionCheck()` call AND the `IBeaconAdvertiser` class AND the `REQ_BT_PERMS` permission request handling.
2. Run the app and confirm boot sequence completes normally.

**Definition of done:** No `//` commented-out `IBeaconAdvertiser` calls in `onCreate`.

---

## PHASE 7 — MainActivity Decomposition (B2)

**Recommended approach step 5:** "Only migrate `MainActivity` after it has been decomposed (B2 fix) and has test coverage."

**File:** `app/src/main/java/com/base/launcher/ui/MainActivity.java` (2,676 lines)  
**Effort:** Large  
**Dependency:** Phases 5 and 6 complete. Phase 6 unit tests written (I5).

**Extract in this order:**

| New class | Responsibility | What to move from MainActivity |
|---|---|---|
| `PermissionFlowCoordinator.kt` | Orchestrates the sequential permission/setup checks | `checkAndStartLauncher()`, `checkAdminMode()`, `checkAlarmWindow()`, `checkUsageStatistics()`, `checkManageStorage()`, `checkAccessibilityService()`, `checkMiuiPermissions()`, `checkUnknownSources()` |
| `MainViewModel.kt` | Server config state, config update triggers | `configUpdater`, `configInitialized`, `updateConfig()` callbacks, `needSendDeviceInfoAfterReconfigure` flag |
| `AppInstallDelegate.kt` | App download/install lifecycle callbacks | All `onAppDownloading`, `onAppInstalling`, `onAppInstallError`, `onAllAppInstallComplete` implementations |
| `LockScreenManager.kt` | Overlay lock screen and "app not allowed" screen | `createApplicationNotAllowedScreen()`, `createLockScreen()`, `showLockScreen()`, `hideLockScreen()` |
| `LauncherUIManager.kt` | Content display, title, background, adapter setup | `showContent()`, `updateTitle()`, `createButtons()`, `createManageButton()` |

**Migration pattern:**

```kotlin
// PermissionFlowCoordinator.kt
class PermissionFlowCoordinator(
    private val activity: AppCompatActivity,
    private val settingsHelper: SettingsHelper,
    private val onAllPermissionsGranted: () -> Unit
) {
    fun start() {
        checkMiuiPermissions()
    }
    private fun checkMiuiPermissions() { /* ... */ }
    private fun checkUnknownSources() { /* ... */ }
    // etc.
}
```

**Definition of done:** `MainActivity.java` (or `.kt` after conversion) is under 300 lines. All extracted classes have at least one unit test.

---

## Summary — Execution Order

| Phase | What | Effort | Depends on |
|---|---|---|---|
| 0 | Security: secrets + debug flag | Small | Nothing |
| 1 | Add Kotlin + coroutines to build | Small | Nothing |
| 2 | Update CVE-affected dependencies | Medium | Nothing |
| 3 | Migrate `json/` → Kotlin data classes | Medium | Phase 1 |
| 4 | Migrate `util/` → Kotlin objects | Medium-Large | Phase 1, 3 |
| 5 | Replace `AsyncTask` in `task/` with coroutines | Medium | Phase 1 |
| 6 | Code quality fixes (I2, I3, I4, I5, I6) | Medium-Large | Phase 3, 4 |
| 7 | MainActivity decomposition | Large | Phase 5, 6 |

> **Note:** Phases 0, 1, and 2 are independent and can be done in parallel by different developers. Phases 3–7 must be done in order.

---

## Definition of Done — Overall

- [ ] `git grep "theBase15112023"` returns no hits in tracked files
- [ ] `BuildConfig.DEVICE_ADMIN_DEBUG` is `false` in release builds
- [ ] `./gradlew assembleBaseDebug` succeeds with Kotlin plugin active
- [ ] All `jackson-*` dependencies ≥ 2.18.x
- [ ] Zero `.java` files in `json/` package
- [ ] Zero `.java` files in `util/` package
- [ ] Zero `.java` files in `task/` package
- [ ] `Grep "AsyncTask"` returns no hits in `src/main/`
- [ ] `Grep "CONNECTIVITY_ACTION"` returns no hits in `src/main/`
- [ ] `./gradlew test` passes with ≥ 20 unit tests
- [ ] `MainActivity` is under 300 lines
