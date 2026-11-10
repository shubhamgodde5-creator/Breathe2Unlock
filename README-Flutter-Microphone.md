# Breathe2Unlock — microphone + proximity edition

This version borrows the broad app architecture of the supplied Unrush package (Flutter screens, Android Accessibility Service, foreground monitoring notification, and a native task Activity) without copying its packaged code or assets.

## Requested breathing ladder

Protected-app foreground time is counted locally per day. The target is cumulative:

| Protected-app use | Total breaths required |
|---:|---:|
| Before 5 minutes | 0 |
| 5 minutes | 2 |
| 10 minutes | 5 |
| 15 minutes | 10 |
| 20 minutes | 15 |
| 25 minutes | 20 |
| 30 minutes | 25 |

Completed breaths carry forward. For example, after completing 2 breaths at 5 minutes, the 10-minute checkpoint asks for 3 additional breaths. Each completed checkpoint opens protected apps for 5 minutes.

## Sensors and privacy

- Microphone detects breath-like sound envelopes only while the task screen is visible.
- Proximity sensor is used as a near/far check while the breathing task is active.
- No camera is requested or used.
- Raw microphone samples are converted to short-lived RMS energy values in memory; they are not stored or uploaded.
- Soft haptic pulses provide a pacing cue.
- The sound-envelope detector is experimental and can mistake other sustained sounds for breathing. It is not a medical or respiratory measurement.

## Android enforcement boundary

This personal-device build gates selected apps after the Android system lock screen has already been unlocked. It cannot block the PIN, password, pattern or biometric lock screen, prevent access to emergency calling, or reliably block every system surface. Android does not let a normal personal app take ownership of the secure keyguard. Whole-device kiosk enforcement requires a fully managed / Device Owner setup and is not enabled here.

## Install and configure

1. Install the APK from the latest **Breathe2Unlock-Android-APK** workflow artifact.
2. Open the app and choose which apps to protect.
3. Enable **Breathe2Unlock app protection** in Android Accessibility settings.
4. Return to the app and enable protection.
5. Grant microphone access when the first breathing task appears.
6. Keep the top of the phone close enough to your face for the proximity sensor to report NEAR (on devices that have one).

The foreground notification indicates when monitoring is enabled. Some Android vendors restrict accessibility services or background launches; real-device testing and battery-settings review may be needed.

## Build

The GitHub Actions workflow restores the checked-in source bundle, generates the Flutter Android scaffold, runs Flutter analysis and unit tests, and creates an installable APK signed with the CI debug key. It is suitable for personal sideloading, not Play Store release.

