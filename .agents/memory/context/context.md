# base-mdm-app Context

- App: Base MDM launcher (com.base.launcher), pure Java Android app, 117 files, 0 Kotlin
- Purpose: Production MDM — device owner, kiosk launcher, MQTT push, remote config, app install
- Min SDK 24, Target/Compile SDK 34, Java 11
- Build: Groovy Gradle, single `base` flavor, AGP 8.11.2
- No unit tests exist

## Active Fix Plan
File: `.agents/planning/2026-06-14-kotlin-migration-android-fix-plan.md`
Phase order: 0 (secrets) → 1 (add Kotlin) → 2 (deps) → 3 (json/) → 4 (util/) → 5 (task/) → 6 (quality) → 7 (MainActivity decompose)
Current status: Phases 0, 1, 2, 3 COMPLETE. Next: Phase 4 (migrate util/ to Kotlin)

## Key Findings (from review 2026-06-14)
- B1 OPEN: MQTT password, keystore password, request signature hardcoded in build.gradle
- B2 OPEN: MainActivity.java is 2676 lines (God Class)
- B3 OPEN: DEVICE_ADMIN_DEBUG=true in defaultConfig (applies to release)
- B4 OPEN: AsyncTask used in task/ and MainActivity (deprecated API 30)
- I1 OPEN: jackson-databind 2.9.4 (CVE), retrofit 2.3.0, appcompat 1.1.0 all severely outdated
- I2 OPEN: ~8 empty catch blocks swallowing exceptions silently
- I3 OPEN: static boolean configInitialized on Activity class
- I4 OPEN: CONNECTIVITY_ACTION deprecated since API 28
- I5 OPEN: Zero unit tests
- I6 OPEN: iBeacon dead commented code in onCreate

## Key File Locations
- Build config: app/build.gradle (secrets here — fix in Phase 0)
- Main activity: app/src/main/java/com/base/launcher/ui/MainActivity.java
- JSON models: app/src/main/java/com/base/launcher/json/ (20 files — migrate Phase 3)
- Utilities: app/src/main/java/com/base/launcher/util/ (14 files — migrate Phase 4)
- Tasks: app/src/main/java/com/base/launcher/task/ (6 AsyncTask files — migrate Phase 5)
- Server API: app/src/main/java/com/base/launcher/server/ServerService.java (Retrofit interface)
- MQTT: bundled Eclipse Paho in org.eclipse.paho.android.service package
