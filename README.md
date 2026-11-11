# Breathe2Unlock — Android MVP

Breathe2Unlock is an Android-first digital-wellbeing prototype. This source project implements a personal-phone MVP with a light UI, daily usage tracking, a camera + microphone breathing-session prototype, a 10-cycle threshold, and a five-minute in-app access window.

## What this MVP does

- Reads approximate daily foreground-use totals using Android `UsageStatsManager` after the user grants Usage Access.
- Lets the user configure a daily use threshold (default: 120 minutes; range: 30–360 minutes).
- Shows an in-app focus gate when the threshold is reached.
- Starts a visible breathing session using the front camera (ML Kit pose landmarks) and microphone (short in-memory RMS windows).
- Runs an experimental fusion heuristic that looks for torso-motion oscillation plus recent microphone energy variation.
- After 10 detected candidate cycles, grants five minutes of access inside Breathe2Unlock.
- Processes camera frames and microphone buffers locally. The app does not save or upload raw video or audio.

## Important personal-phone limitation

This is **not a system-wide phone lock**. On an ordinary personal Android installation, the app cannot reliably prevent the user from opening other apps, using Home/Recents, opening Settings, or making calls. This MVP gates only its own UI. Do not describe or market this version as locking the entire phone.

A later managed-device edition would require a Device Policy Controller provisioned as device owner and Android Lock Task Mode. That generally requires a dedicated/fully managed device and enrollment, which is intentionally not included in this personal-phone MVP.

## Breathing detector status

The current detector is a research prototype, not a medical device and not a validated respiratory measurement system. Camera pose landmarks estimate upper-body geometry; the microphone contributes short-window sound-energy variation. The heuristic can miss breaths, count unrelated movement/noise, or fail on certain phone placements, clothing, lighting, microphones, and camera hardware.

For best experimental results:

1. Place the phone steadily about 1–2 metres away, with the upper torso visible in the front camera.
2. Keep the microphone reasonably close to the breathing source and use a quiet room.
3. Breathe normally. Do not force deep breaths or hold your breath to increase the count.
4. Stop if you feel dizzy, short of breath, or uncomfortable.

The detector requires a camera expansion/contraction pattern and recent microphone energy variation. If it does not count, adjust the position and check permissions; do not breathe harder or faster.

## Requirements

- Android Studio with Android SDK Platform 35 installed
- JDK 17
- Android device or emulator running Android 8.0 (API 26) or newer
- Front camera and microphone for the breathing session

The project pins Android Gradle Plugin 8.7.3, Kotlin 2.0.21, CameraX 1.4.1, and ML Kit Pose Detection 18.0.0-beta5. ML Kit Pose Detection is a beta API; its upstream API may change.

## Open and run

1. Unzip the project.
2. Open the `Breathe2Unlock` folder in Android Studio.
3. Install Android SDK Platform 35 if Android Studio prompts for it.
4. Allow Gradle sync to resolve the pinned dependencies from Google Maven and Maven Central.
5. Ensure Gradle 8.9 is available (see [BUILD.md](BUILD.md)); this bundle does not include the wrapper JAR.
6. Connect an Android phone with USB debugging enabled, or start an emulator.
7. Run the `app` configuration.
8. On first use, select **Enable Usage Access** and enable Breathe2Unlock in Android Settings.
9. Start a breathing session and grant Camera and Microphone permissions when requested.

There is no APK in this source bundle. The project has not been compiled in this environment because an Android SDK/Gradle installation is not available here. Build it in Android Studio before installing it on your device.

## Project structure

```text
Breathe2Unlock/
├── app/
│   ├── src/main/AndroidManifest.xml
│   ├── src/main/java/com/breathe2unlock/
│   │   ├── MainActivity.kt
│   │   ├── breathing/
│   │   │   ├── AudioBreathMonitor.kt
│   │   │   ├── BreathCycleFusionEngine.kt
│   │   │   └── CameraBreathPreview.kt
│   │   └── usage/UsageStatsReader.kt
│   └── build.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

## MVP behaviour

- Default daily usage threshold: 120 minutes.
- User-adjustable threshold: 30 to 360 minutes.
- Breathing requirement: 10 candidate cycles.
- Unlock period: 5 minutes, inside the Breathe2Unlock app only.
- Usage totals refresh while the app is visible and when it returns to the foreground. This MVP does not run a persistent background usage-monitoring service.

## Known limitations / next engineering work

- Validate cycle detection against a labelled breathing dataset and real devices; replace the current z-score heuristic if false positives/negatives are too high.
- Add a sensor-quality calibration screen and a no-count diagnostic view.
- Evaluate a better thoracic-expansion signal (for example, optical-flow region tracking) instead of relying only on pose-landmark geometry.
- Test microphone placement, device noise suppression, and alternate audio sources.
- Add unit tests for signal processing and integration tests for permission revocation, lifecycle interruptions, and session recovery.
- Improve daily usage aggregation with event-based validation against OEM digital-wellbeing totals.
- Before Google Play submission, update compile/target SDK to the currently required API level and review all relevant store policies. Google Play's new-app requirement changed to Android 16 / API 36 on 31 August 2026.

## Privacy

- Camera and microphone are started only while the breathing screen is visible.
- Audio buffers are processed in memory and discarded; no audio file is created.
- Camera frames are processed in memory by ML Kit; no photo or video file is created.
- Usage totals and the configured threshold are read/stored locally.
- No network permission or backend is included in this MVP.


## Build an APK without Android Studio

A GitHub Actions workflow is included at `.github/workflows/build-apk.yml`. See [CLOUD_BUILD.md](CLOUD_BUILD.md) for the cloud build and artifact download steps. This requires the project to be in a GitHub repository with Actions enabled.
