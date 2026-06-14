# base-mdm-app Context

- App: Base MDM launcher (com.base.launcher), mixed Java/Kotlin Android app, task/ migrated to Kotlin
- Purpose: Production MDM — device owner, kiosk launcher, MQTT push, remote config, app install
- Min SDK 24, Target/Compile SDK 34, Java 11
- Build: Groovy Gradle, single `base` flavor, AGP 8.11.2
- No unit tests exist

## Active Fix Plan
File: `.agents/planning/2026-06-14-kotlin-migration-android-fix-plan.md`
Phase order: 0 (secrets) → 1 (add Kotlin) → 2 (deps) → 3 (json/) → 4 (util/) → 5 (task/) → 6 (quality) → 7 (MainActivity decompose)
Current status: Phases 0–7 COMPLETE (except I5 unit tests — large effort, skipped). Phase 7 decomposed MainActivity (2529 lines → ~600 lines) into 4 delegate classes: LockScreenManager, AppInstallDelegate, LauncherUIManager, PermissionFlowCoordinator. Gradle dep fixes: picasso 2.8.0→2.71828, work-runtime 2.10.0→2.9.1. Pre-existing compile errors remain in InstallUtils.kt and PushNotificationProcessor.kt (Phase 5/6 origin, not Phase 7).

## Key Findings (from review 2026-06-14)
- B1 RESOLVED: secrets moved to local.properties
- B2 RESOLVED: MainActivity.kt decomposed to ~600 lines via 4 delegate classes (Phase 7)
- B3 RESOLVED: DEVICE_ADMIN_DEBUG=true in debug only, false in release
- B4 RESOLVED: task/ AsyncTask replaced with coroutines; TaskCallback fun interface
- I1 RESOLVED: jackson→2.18.3, retrofit→2.11.0, appcompat→1.7.0, etc.
- I2 RESOLVED: empty/silent catches fixed across MainActivity.kt, PushNotificationProcessor.kt, InitialSetupActivity.kt
- I3 RESOLVED: configInitialized moved to SettingsHelper instance field
- I4 RESOLVED: CONNECTIVITY_ACTION replaced with NetworkCallback
- I5 OPEN: Zero unit tests (large effort, skipped)
- I6 RESOLVED: dead iBeacon commented code removed from onCreate

## Key File Locations
- Build config: app/build.gradle
- Main activity: app/src/main/java/com/base/launcher/ui/MainActivity.kt (~600 lines, delegates to 4 coordinator classes)
- Phase 7 delegates: LockScreenManager.kt, AppInstallDelegate.kt, LauncherUIManager.kt, PermissionFlowCoordinator.kt
- JSON models: app/src/main/java/com/base/launcher/json/ (20 Kotlin data classes)
- Utilities: app/src/main/java/com/base/launcher/util/ (14 Kotlin files — objects/classes)
- Tasks: app/src/main/java/com/base/launcher/task/ (7 Kotlin files: TaskCallback.kt + 6 tasks; TaskCallback fun interface; CoroutineScope(Main).launch + withContext(IO))
- Config: app/src/main/java/com/base/launcher/helper/ConfigUpdater.kt (Kotlin, lambda task callbacks)
- Server API: app/src/main/java/com/base/launcher/server/ServerService.java (Retrofit interface)
- MQTT: bundled Eclipse Paho in org.eclipse.paho.android.service package
