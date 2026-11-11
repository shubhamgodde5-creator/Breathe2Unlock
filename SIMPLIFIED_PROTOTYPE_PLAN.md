# Breathe2Unlock — Simplified Prototype Plan

**Version**: 1.0 (Prototype)  
**Date**: 2026-11-10  
**Scope**: Proximity + Microphone only, Box Breathing (4-4-4-4), Overlay-based

---

## Simplified Requirements

| Requirement | Detail |
|-------------|--------|
| **Sensors** | Proximity sensor (detect phone at chin) + Microphone (breathing audio) |
| **Trigger** | After screen unlock, if cumulative screen time ≥ threshold (e.g., 2 min) |
| **UI** | System overlay / full-screen activity showing breathing guide |
| **Pattern** | Box breathing only: 4s inhale → 4s hold → 4s exhale → 4s hold |
| **Guidance** | Vibration pattern synced to phases (inhale=long, hold=short, exhale=long, hold=short) |
| **Validation** | Microphone RMS confirms breathing during exhale/inhale phases |
| **Penalty** | Skip → next unlock requires 2 cycles; 3 skips → 5-min lockout |
| **No Camera** | Removed entirely |
| **No Device Admin** | Uses foreground service + overlay permission (personal mode) |

---

## Architecture (Simplified)

```
┌─────────────────────────────────────────────────────────────┐
│  Foreground Service (ScreenTimeMonitor)                     │
│  - Listens: SCREEN_ON, SCREEN_OFF, USER_PRESENT             │
│  - Tracks: cumulative screen-on time since last unlock      │
│  - When threshold met → launches BreathingOverlay           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  BreathingOverlay (SYSTEM_ALERT_WINDOW)                     │
│  - Full-screen semi-transparent overlay                     │
│  - Shows: Box breathing animation + phase text              │
│  - "Start" button → begins vibration + audio monitoring     │
│  - Proximity sensor: confirms phone at chin (value < 5cm)   │
│  - Microphone: validates breathing pattern compliance       │
│  - On complete → dismiss, reset screen-time counter         │
│  - On skip/cancel → PenaltyManager records, dismiss         │
└─────────────────────────────────────────────────────────────┘
```

---

## Components to Create/Modify

### New Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/breathe2unlock/monitor/ScreenTimeMonitor.kt` | Foreground service tracking screen on/off, cumulative time |
| `app/src/main/java/com/breathe2unlock/breathing/BoxBreathingPattern.kt` | Box breathing timing: 4-4-4-4 phases, vibration patterns |
| `app/src/main/java/com/breathe2unlock/breathing/ProximityBreathMonitor.kt` | Proximity sensor listener (detects phone at chin) |
| `app/src/main/java/com/breathe2unlock/breathing/AudioBreathValidator.kt` | Microphone RMS validation during breathing phases |
| `app/src/main/java/com/breathe2unlock/overlay/BreathingOverlayService.kt` | SYSTEM_ALERT_WINDOW overlay with Compose UI |
| `app/src/main/java/com/breathe2unlock/overlay/BreathingOverlayView.kt` | Compose UI: animation, phases, start button, progress |
| `app/src/main/java/com/breathe2unlock/penalty/PenaltyManager.kt` | Skip tracking, escalation (1→2 cycles, 3→lockout) |
| `app/src/main/java/com/breathe2unlock/receiver/BootReceiver.kt` | Restart services on boot |
| `app/src/main/res/xml/foreground_service_types.xml` | Foreground service type declarations |

### Modified Files

| File | Change |
|------|--------|
| `AndroidManifest.xml` | Add: FOREGROUND_SERVICE, SYSTEM_ALERT_WINDOW, RECEIVE_BOOT_COMPLETED, proximity sensor feature |
| `app/build.gradle.kts` | No new deps needed (uses existing CameraX/ML Kit deps can be removed later) |
| `MainActivity.kt` | Simplify: only "Activate Monitoring" button, requests overlay + microphone permissions |

---

## Box Breathing Pattern Spec

```
Phase 1: INHALE     — 4 seconds — Vibration: 1 long pulse (500ms)
Phase 2: HOLD       — 4 seconds — Vibration: 2 short pulses (100ms each, 200ms gap)
Phase 3: EXHALE     — 4 seconds — Vibration: 1 long pulse (500ms)
Phase 4: HOLD       — 4 seconds — Vibration: 2 short pulses (100ms each, 200ms gap)
────────────────────────────────────────────────────────────
Total: 16 seconds per cycle
Default requirement: 4 cycles = ~64 seconds
```

**Vibration Patterns** (using `VibrationEffect`):
- Inhale: `createOneShot(500, DEFAULT_AMPLITUDE)`
- Hold: `createWaveform([0, 100, 200, 100], -1)` (two short buzzes)
- Exhale: `createOneShot(500, DEFAULT_AMPLITUDE)`
- Hold: same as phase 2

---

## Proximity Sensor Logic

```kotlin
// Typical proximity sensor: 0cm = near, 5cm = far (device dependent)
const val PROXIMITY_THRESHOLD_CM = 5.0f
const val PROXIMITY_DEBOUNCE_MS = 500

// When user presses "Start":
// 1. Register SensorEventListener for TYPE_PROXIMITY
// 2. Wait for proximity < threshold (phone at chin)
// 3. Once confirmed → start vibration + audio validation
// 4. If proximity lost during session → pause, show "Keep phone at chin"
```

---

## Audio Validation Logic (Simplified)

```kotlin
// Reuse AudioBreathMonitor pattern: 16kHz, 200ms buffers, RMS
// Validation per phase:
INHALE  → RMS should RISE above baseline (air intake sound)
HOLD    → RMS should DROP near baseline (quiet)
EXHALE  → RMS should RISE above baseline (air outflow sound)  
HOLD    → RMS should DROP near baseline

// Simple heuristic: 
// - Compute rolling baseline (median of last 20 RMS values)
// - Phase valid if RMS > baseline * 1.5 during inhale/exhale
// - Phase valid if RMS < baseline * 1.2 during holds
// - Require 3/4 phases valid per cycle
```

---

## Screen Time Tracking Logic

```kotlin
// ScreenTimeMonitor (Foreground Service)
var screenOnStartTime: Long = 0
var cumulativeScreenTimeMs: Long = 0
var lastUnlockTime: Long = 0
const val THRESHOLD_MS = 2 * 60 * 1000 // 2 minutes

// On SCREEN_ON: screenOnStartTime = now
// On SCREEN_OFF: cumulativeScreenTimeMs += now - screenOnStartTime
// On USER_PRESENT (unlock): 
//   if cumulativeScreenTimeMs >= THRESHOLD_MS:
//       launch BreathingOverlayService
//       cumulativeScreenTimeMs = 0 // reset after overlay completes
```

---

## Penalty System

| Skip Count | Consequence |
|------------|-------------|
| 0 (first)  | Next unlock: 1 cycle required (default) |
| 1          | Next unlock: 2 cycles required |
| 2          | Next unlock: 3 cycles required |
| 3+         | 5-minute lockout (overlay shows countdown, no skip) |

Persisted in `DataStore`:
- `skipCount: Int`
- `lockoutUntilMs: Long`
- `requiredCycles: Int` (computed from skipCount)

---

## Permissions Required

| Permission | Type | Reason |
|------------|------|--------|
| `FOREGROUND_SERVICE` | Normal | ScreenTimeMonitor service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Normal | Overlay service |
| `SYSTEM_ALERT_WINDOW` | Special | Breathing overlay on lock screen |
| `RECORD_AUDIO` | Runtime | Microphone validation |
| `RECEIVE_BOOT_COMPLETED` | Normal | Auto-start on reboot |
| `WAKE_LOCK` | Normal | Keep CPU alive during session |

---

## Implementation Phases (Prototype)

### Phase 0: Manifest & Permissions (1 hr)
- Update `AndroidManifest.xml` with new permissions, services, receiver
- Add `foreground_service_types.xml`
- Remove CameraX/ML Kit dependencies from `build.gradle.kts` (optional, for clean build)

### Phase 1: ScreenTimeMonitor Service (2 hrs)
- Foreground service with `SCREEN_ON`/`SCREEN_OFF`/`USER_PRESENT` receivers
- Track cumulative screen time, persist to DataStore
- Launch overlay when threshold met

### Phase 2: BoxBreathingPattern + Vibration (1.5 hrs)
- `BoxBreathingPattern` data class with phase timings
- `VibrationGuidance` helper: plays phase-synced vibration
- Unit test: pattern timing accuracy

### Phase 3: Proximity + Audio Sensors (2 hrs)
- `ProximityBreathMonitor`: SensorEventListener, chin detection
- `AudioBreathValidator`: extends `AudioBreathMonitor`, phase validation
- Unit tests with synthetic signals

### Phase 4: Breathing Overlay (2.5 hrs)
- `BreathingOverlayService`: SYSTEM_ALERT_WINDOW, full-screen Compose
- `BreathingOverlayView`: Animation (expanding/contracting circle), phase text, Start button
- Integrate: proximity check → vibration → audio validation → cycle counting

### Phase 5: Penalty Manager + Integration (1.5 hrs)
- `PenaltyManager`: skip tracking, escalation, lockout
- Wire: overlay completion → reset screen time, update penalties
- BootReceiver: restart ScreenTimeMonitor

### Phase 6: MainActivity Simplification (0.5 hrs)
- Single "Activate Protection" button
- Request: Overlay permission, Microphone permission
- Start ScreenTimeMonitor service

### Phase 7: Testing & Polish (2 hrs)
- Manual test: lock/unlock → 2 min → overlay appears
- Proximity: phone at chin → vibration starts
- Audio: breathing → cycles count → completion
- Skip → penalty escalates
- Reboot → service restarts

---

## Validation Commands

```bash
# Build
cd /tmp/Breathe2Unlock && ./gradlew :app:assembleDebug

# Unit tests
./gradlew :app:testDebugUnitTest

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Grant overlay permission (manual first time)
adb shell cmd appops set com.breathe2unlock SYSTEM_ALERT_WINDOW allow

# Test: lock phone, wait 2+ min, unlock → overlay should appear
```

---

## Acceptance Criteria (Prototype)

- [ ] App builds without CameraX/ML Kit dependencies
- [ ] ScreenTimeMonitor tracks screen-on time accurately
- [ ] After 2 min cumulative screen time + unlock → overlay appears
- [ ] Overlay shows Box breathing animation (4-4-4-4)
- [ ] "Start" button → waits for proximity (phone at chin)
- [ ] Vibration guides each phase (distinct patterns)
- [ ] Microphone validates breathing (inhale/exhale RMS changes)
- [ ] 4 cycles complete → overlay dismisses, screen time resets
- [ ] Skip button → penalty increments, next unlock requires more cycles
- [ ] 3 skips → 5-min lockout overlay
- [ ] Reboot → monitoring resumes automatically
- [ ] All permissions requested/granted in MainActivity

---

## Future Enhancements (Post-Prototype)

1. **Add 9 more patterns** (4-7-8, physiological sigh, etc.)
2. **Adaptive threshold** based on usage patterns
3. **Camera re-introduction** for torso movement validation
4. **UsageStats integration** for cross-app screen time
5. **Settings screen**: threshold, cycles, vibration intensity, patterns
6. **Statistics dashboard**: sessions completed, skip rate, avg duration
7. **Accessibility**: TalkBack announcements for phases, haptic-only mode

---

## File Tree (Prototype)

```
Breathe2Unlock/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml              ← UPDATED
│   │   ├── res/xml/
│   │   │   ├── foreground_service_types.xml  ← NEW
│   │   │   └── device_admin_receiver.xml     ← REMOVE (not needed)
│   │   ├── java/com/breathe2unlock/
│   │   │   ├── MainActivity.kt               ← SIMPLIFIED
│   │   │   ├── monitor/
│   │   │   │   └── ScreenTimeMonitor.kt      ← NEW
│   │   │   ├── breathing/
│   │   │   │   ├── BoxBreathingPattern.kt    ← NEW
│   │   │   │   ├── ProximityBreathMonitor.kt ← NEW
│   │   │   │   ├── AudioBreathValidator.kt   ← NEW (extends AudioBreathMonitor)
│   │   │   │   └── VibrationGuidance.kt      ← NEW
│   │   │   ├── overlay/
│   │   │   │   ├── BreathingOverlayService.kt ← NEW
│   │   │   │   └── BreathingOverlayView.kt   ← NEW
│   │   │   ├── penalty/
│   │   │   │   └── PenaltyManager.kt         ← NEW
│   │   │   ├── receiver/
│   │   │   │   └── BootReceiver.kt           ← NEW
│   │   │   └── usage/UsageStatsReader.kt     ← KEEP (optional future)
│   │   └── res/values/strings.xml            ← ADD overlay strings
│   ├── build.gradle.kts                      ← REMOVE CameraX/ML Kit deps
│   └── ...
├── SIMPLIFIED_PROTOTYPE_PLAN.md              ← THIS FILE
└── ...
```

---

## Risk Mitigation (Prototype)

| Risk | Mitigation |
|------|------------|
| Overlay permission denied | Guide user to Settings → Apps → Special access → Display over other apps |
| Proximity sensor unreliable | Add fallback: "Tap when phone at chin" button, calibrate threshold per device |
| Audio false positives | Require proximity + audio both valid; baseline calibration at session start |
| Foreground service killed | `START_STICKY`, `FOREGROUND_SERVICE_SPECIAL_USE`, battery optimization whitelist |
| Overlay not on lock screen | `FLAG_SHOW_WHEN_LOCKED | FLAG_TURN_SCREEN_ON | FLAG_KEEP_SCREEN_ON` |

---

**Status**: Ready for Phase 0 implementation.  
**Next Step**: Run `/plan-canvas` to review visually, or confirm with `"proceed"` to start coding.