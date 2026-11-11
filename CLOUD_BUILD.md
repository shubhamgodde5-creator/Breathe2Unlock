# Build an installable APK in GitHub Actions

This repository includes `.github/workflows/build-apk.yml` so the Android project can be built on a GitHub-hosted runner without installing Android Studio on your computer or phone.

## Workflow output

The workflow runs unit tests and `:app:assembleDebug`, then uploads `Breathe2Unlock-debug-apk` as a workflow artifact. The APK is a debug-signed sideload build; it is not a Google Play release and it has not been device-tested.

## Requirements

The project must be pushed to a GitHub repository with Actions enabled. The workflow installs JDK 17, Android SDK Platform 35, Build Tools 35.0.0, and Gradle 8.9 on the GitHub runner.

## Download

After a successful workflow run, open the run in the repository's Actions tab and download the `Breathe2Unlock-debug-apk` artifact. Extract it and install `app-debug.apk` on Android. Android may ask you to allow installation from the browser or file manager used to open it.

## Important product limitations

- Personal Mode only shows the usage threshold and breathing gate inside Breathe2Unlock. It does not hard-lock other apps or the Android system UI.
- Camera/microphone breath detection is experimental and must be validated on physical phones before being relied on.
- The debug APK is for personal testing. A production release requires release signing, privacy review, API-level updates, and broader device testing.
