# Android 7–15 Compatibility Guide

**App:** Base MDM Agent (`com.base.launcher`)  
**Change:** `minSdk 29 → 24` (Android 10 → Android 7.0), `targetSdk/compileSdk 34 → 35`  
**Date analysed:** 2026-06-11

---

## 1. SDK Version Summary

| Field | Before | After |
|---|---|---|
| `compileSdk` | 34 | 35 |
| `minSdk` | 29 (Android 10) | **24 (Android 7.0)** |
| `targetSdk` | 34 | 35 |

---

## 2. `app/build.gradle` — Changes Required

```groovy
android {
    compileSdk 35          // was 34
    defaultConfig {
        minSdk 24          // was 29 — supports Android 7.0+ (Nougat)
        targetSdk 35       // was 34
    }
}
```

### Dependency updates (for Android 7–15 stability)

| Library | Before | After | Reason |
|---|---|---|---|
| `appcompat` | `1.1.0` | `1.7.0` | Android 15 window-inset support, predictive back |
| `recyclerview` | `1.1.0` | `1.3.2` | Stability fixes, API 35 compat |
| `material` | `1.1.0` | `1.12.0` | Colour/theme fixes for API 24–35 range |

Other dependencies (`work-runtime:2.9.1`, `retrofit:2.3.0`, etc.) are already compatible with `minSdk 14+`.

---

## 3. Code Audit: What Was Already Compatible

All Android-version-specific calls were already properly guarded. No runtime crashes on API 24–28 devices from existing source code.

| API | Minimum API | Guard in code |
|---|---|---|
| `NotificationChannel` | 26 (O) | `>= Build.VERSION_CODES.O` |
| `startForegroundService()` | 26 (O) | `>= Build.VERSION_CODES.O` |
| `GnssStatus.Callback` | 24 (N) | `>= Build.VERSION_CODES.N` |
| `LocationManager.registerGnssStatusCallback()` | 24 (N) | `>= Build.VERSION_CODES.N` |
| `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION` | 29 (Q) | `>= Build.VERSION_CODES.Q` |
| `AlarmManager.setExactAndAllowWhileIdle()` | 23 (M) | `>= 23` (inline check) |
| `AlarmManager.canScheduleExactAlarms()` | 31 (S) | `>= Build.VERSION_CODES.S` |
| `AlarmManager.setExact()` | 19 | `>= 19` (inline check) |
| `Context.RECEIVER_EXPORTED` | 33 (T) | Constant value only; method called inside `>= O` block, safe |
| `Environment.isExternalStorageManager()` | 30 (R) | `@RequiresApi(R)`, call site uses `>= R` |
| `PendingIntent.FLAG_IMMUTABLE` | 23 (M) | Inline constant, minSdk 24 ≥ 23 ✅ |
| `ACCESS_BACKGROUND_LOCATION` permission | 29 (Q) | `>= Q` at call site |
| `NotificationCompat.Builder(ctx, channelId)` | Always | `>= O` branch uses channelId; `< O` uses deprecated no-channel ctor |

---

## 4. Code Change Required: `StatusControlService.java`

`StatusControlService` declares `android:foregroundServiceType="dataSync"` in the manifest but was calling the 2-argument `startForeground(id, notification)`. On Android 14 (API 34) with `targetSdk ≥ 34`, this throws `MissingForegroundServiceTypeException`.

**Fix applied:**

```java
// Added import:
import android.content.pm.ServiceInfo;

// In onCreate():
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(NOTI_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
} else {
    startForeground(NOTI_ID, buildNotification());
}
```

`ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` was added in API 29. The 3-argument `startForeground()` was added in API 29. The guard `>= Q` (API 29) is correct.

---

## 5. Foreground Service Type Reference

Foreground service types used in this app and their API requirements:

| Manifest type | `ServiceInfo` constant | Added in API | Service |
|---|---|---|---|
| `location` | `FOREGROUND_SERVICE_TYPE_LOCATION` | 29 | `LocationService` |
| `dataSync` | `FOREGROUND_SERVICE_TYPE_DATA_SYNC` | 29 | `StatusControlService` |
| `specialUse` | `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` | 34 | `MqttService`, `PushLongPollingService` |
| `systemExempted` | `FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` | 34 | `MqttService`, `PushLongPollingService` |

**On API 24–28:** `android:foregroundServiceType` in the manifest is **silently ignored** by the system. No crash. All services call 2-arg `startForeground()` on this range.

**On API 29–33:** `foregroundServiceType` is respected. The 3-arg `startForeground()` must be used for services with a declared type. `specialUse` and `systemExempted` don't exist yet — the framework accepts `0` (no type).

**On API 34+:** All foreground services with a declared type MUST pass the type to `startForeground()`. Unknown type values are rejected.

---

## 6. `MqttService` and `PushLongPollingService` — Known Limitation

Both services call `Utils.startStableForegroundService(this, id, notification)` from the **closed-source** `ComponentLib_1.2.5_release.aar`. This method's implementation cannot be inspected.

**Risk:** If the AAR was compiled against API ≤ 33, it may call the 2-arg `startForeground()` and omit `FOREGROUND_SERVICE_TYPE_SPECIAL_USE | FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` on API 34+ devices.

**Symptom if broken:** `android.app.MissingForegroundServiceTypeException` on Android 14+ for the MQTT or long-polling service.

**Fix if needed:** Replace the AAR call with direct guarded code:
```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
    startForeground(id, notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE |
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(id, notification, 0); // no specific type
} else {
    startForeground(id, notification);
}
```

---

## 7. AndroidManifest.xml — No Changes Needed

All permission declarations in the manifest are forward/backward-compatible:
- Unknown permissions are **silently ignored** on Android versions where they don't exist
- `maxSdkVersion` attributes (e.g., `BLUETOOTH` limited to API 30) are already set
- `android:foregroundServiceType` values are ignored on API 24–28
- `<property>` elements (added in API 33) are ignored on API 24–32
- `android:requestLegacyExternalStorage="true"` is ignored on API 30+ but useful on API 29

---

## 8. Android 15 (API 35) / `targetSdk 35` Implications

| Change | Impact on this app |
|---|---|
| **Edge-to-edge enforced** | Minimal — the app is the home screen launcher, typically fullscreen; kiosk mode hides system bars |
| **Predictive back** | No impact — the launcher doesn't use back navigation in the usual sense |
| **`NetworkInfo` deprecated** | `NetworkInfo` used in `MqttService` is deprecated (since API 29) but **not removed** in API 35. No action needed. |
| **`READ_PHONE_STATE` changes** | Already declared with appropriate permissions; Device Owner grants this automatically |
| **OpenJDK 17 API restrictions** | `JavaVersion.VERSION_11` compile target is unaffected |

---

## 9. API 24–28 Behaviour Notes (New Target Range)

| Area | Behaviour on API 24–28 |
|---|---|
| Background services | No background restrictions (restrictions added in API 26 for Oreo). `startService()` works from background |
| Foreground service required | Only enforced from API 26+. The `>= O` guards in `Initializer.java` handle this |
| Location permissions | `ACCESS_BACKGROUND_LOCATION` (API 29) does not exist — only `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` are requested at runtime |
| APK install | `REQUEST_INSTALL_PACKAGES` permission (API 26) declared; on API 24–25 unknown sources can be enabled globally |
| Bluetooth | `BLUETOOTH` + `BLUETOOTH_ADMIN` (without `maxSdkVersion`) apply on API 24–30; `BLUETOOTH_CONNECT` (API 31) ignored below 31 |
| Exact alarms | No restrictions on API 24–30; `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM` are future-proofing declarations |

---

## 10. Build Commands After Changes

```bash
./gradlew assembleBaseDebug        # debug APK (all variants)
./gradlew assembleBaseRelease      # release APK
```

After changing `build.gradle`, always run **File → Sync Project with Gradle Files** in Android Studio before building.
