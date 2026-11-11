# Build notes

This project uses Android Gradle Plugin 8.7.3, which is paired with Gradle 8.9 and JDK 17.

The source bundle intentionally does not include the Gradle wrapper JAR because it could not be retrieved in this environment. To build locally:

1. Install JDK 17 and Android Studio.
2. Install Android SDK Platform 35 from SDK Manager.
3. Install Gradle 8.9 locally, or let your Android Studio setup generate a Gradle wrapper for version 8.9.
4. From this folder, run `gradle :app:assembleDebug` (or run the `app` configuration from Android Studio).

The Android Gradle plugin and app dependencies are pinned in the Gradle files. The first sync requires internet access to Google Maven and Maven Central.
