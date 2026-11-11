# Breathe2Unlock — Finalized MVP Implementation Specification

## Product decisions

| Item | Decision |
|---|---|
| Platform | Android |
| Deployment | Personal phone |
| Language / UI | Kotlin + Jetpack Compose |
| Theme | Light |
| Breathing inputs | Front camera + microphone |
| Unlock rule | 10 candidate inhale/exhale cycles |
| Access duration | 5 minutes |
| Usage threshold | Configurable; default 120 minutes/day |
| Data processing | On-device |
| Backend | None in MVP |

## Personal-mode boundary

The personal-phone MVP can read approximate foreground usage after Usage Access is granted and can show a focus gate inside its own activity. It cannot securely lock all other apps or Android system surfaces. It does not use overlays, Accessibility Services, hidden background launches, or device-owner privileges to imitate whole-device enforcement.

A true device-wide kiosk restriction is deferred to a separate managed-device product variant using a properly provisioned DPC/device-owner deployment and Lock Task Mode. Emergency calling and recovery would need OEM/device-specific validation.

## Runtime flows

### Setup

1. User opens the app.
2. App explains the personal-mode restriction boundary.
3. User grants Usage Access in Android Settings.
4. User grants Camera and Microphone runtime permissions when starting an exercise.

### Usage threshold

1. App queries current-day UsageStats while visible / on resume.
2. App compares the total foreground-use duration against the configured threshold.
3. When threshold is reached, the app presents the focus gate on its next visible render.
4. Other Android apps remain accessible because this is personal mode.

### Breathing session

1. User enters the visible breathing screen.
2. CameraX streams front-camera frames to ML Kit Pose Detection in stream mode.
3. Shoulder and hip landmarks are used to estimate a torso-geometry signal.
4. AudioRecord captures short PCM windows while the session is visible.
5. Audio RMS is calculated in memory; raw samples are discarded.
6. A fusion heuristic seeks a torso expansion-to-contraction transition with recent audio energy variation.
7. At 10 candidate cycles, the app grants five minutes of in-app access.
8. On exit, camera analysis and microphone capture stop.

## Components

- `UsageStatsReader`: checks AppOps Usage Access and queries current-day foreground time.
- `CameraBreathPreview`: owns CameraX preview + ImageAnalysis and ML Kit Pose Detector lifecycle.
- `AudioBreathMonitor`: owns a short-buffer AudioRecord capture thread.
- `BreathCycleFusionEngine`: holds rolling signal windows, normalizes values, and counts candidate cycles.
- `Breathe2UnlockApp`: owns UI state, threshold preference, focus-gate selection, and five-minute unlock expiry.

## Safety and privacy constraints

- Do not block Android emergency calling, Settings, Home, Recents, or device authentication in personal mode.
- Never replace PIN/password/biometric authentication.
- Do not force breath holding, hyperventilation, or unusually deep breathing.
- Provide a visible exit from the breathing session.
- Never store raw microphone data or camera frames.
- Clearly label the breathing detector experimental and non-medical.
- Do not assert a cycle is verified to a high confidence until validation data supports that claim.

## Acceptance criteria for this MVP

- Android Studio can sync and build the app with the pinned toolchain.
- User can grant Usage Access and see a non-zero daily foreground time when usage exists.
- Daily threshold is adjustable and persists after process restart.
- User can launch a visible camera/microphone breathing session after runtime permissions.
- Camera and microphone stop when the session closes.
- The fusion engine can increment a candidate count under a controlled test signal.
- Ten candidate cycles trigger a five-minute in-app access timer.
- App remains usable if Usage Access or sensor permissions are denied/revoked.
- No raw video/audio is written to storage or sent over the network.
