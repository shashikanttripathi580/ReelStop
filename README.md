# ReelStop — Mindful Awareness for Instagram Reels

ReelStop is a production-quality Android application built with Jetpack Compose, Kotlin, Room Database, and Android AccessibilityService. It provides gentle, real-time awareness of Instagram Reels consumption by displaying a compact, non-interactive floating pill counter (`REELS: X`) at the top center of the screen without interfering with gestures or touch events.

---

## Key Features

1. **Top-Center Floating Counter**:
   - Displays `REELS: 1`, `REELS: 2`, `REELS: 3`, etc.
   - Non-interactive (`FLAG_NOT_TOUCHABLE`), allowing 100% of touches, swipes, double-taps, and drags to pass seamlessly to Instagram.
   - Respects display cutouts, camera punch-holes, and system safe areas.
   - Displays gentle pulse animation when the counter increments.
2. **Dedicated Detection Pipeline**:
   - `InstagramDetector`: Detects foreground transitions with grace periods for notification pulldowns and share sheets.
   - `ReelsDetector`: Identifies vertical clips view pagers, reels media containers, and characteristic text tags.
   - `ReelTransitionDetector`: Distinguishes actual Reel transitions from touch jitter and internal UI redraws.
   - `DebounceFilter`: Aggregates the 10–30 rapid accessibility events generated during a single swipe gesture into **exactly one** Reel increment.
3. **Local Room Persistence**:
   - `SessionEntity`: Records start time, end time, duration, and reel count for every Reels viewing session.
   - `DailyStatsEntity`: Automatically aggregates daily totals (`Today's Reels`, `Today's Sessions`, `Total Reel Time`, `Longest Session`).
   - `SettingsEntity`: Stores customizable awareness limits, milestone toggles, and counter preferences.
4. **Privacy by Design**:
   - **Zero Internet Permissions**: `android.permission.INTERNET` is **not declared** in `AndroidManifest.xml`. It is physically impossible for data to leave the device.
   - Local processing only: No screen recording, no screenshot capture, no reading of private messages, comments, or keystrokes.
5. **Non-Judgmental Product Philosophy**:
   - Tone is focused on mindful awareness rather than shame or guilt.
   - Milestone notifications at 25, 50, and 100 Reels encourage taking gentle pauses.

---

## Project Structure

```
ReelStop/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/reelstop/
│       │   │   ├── ReelStopApp.kt                 # Application class (@HiltAndroidApp)
│       │   │   ├── data/
│       │   │   │   ├── entity/                    # SessionEntity, DailyStatsEntity, SettingsEntity
│       │   │   │   ├── dao/                       # SessionDao, DailyStatsDao, SettingsDao
│       │   │   │   ├── ReelDatabase.kt            # Room Database
│       │   │   │   └── repository/ReelRepository.kt
│       │   │   ├── detection/
│       │   │   │   ├── InstagramDetector.kt       # Package foreground detection & grace period
│       │   │   │   ├── ReelsDetector.kt           # Bounded node hierarchy & layout detection
│       │   │   │   ├── ReelTransitionDetector.kt  # Scroll & content change inspection
│       │   │   │   └── DebounceFilter.kt          # Event aggregation & settling logic
│       │   │   ├── domain/
│       │   │   │   ├── CounterState.kt            # Live state model
│       │   │   │   ├── ReelCounterManager.kt      # Real-time counter logic & StateFlow
│       │   │   │   └── SessionManager.kt          # Session lifecycle, ticker, and DB sync
│       │   │   ├── service/
│       │   │   │   ├── ReelAccessibilityService.kt # Accessibility event listener
│       │   │   │   └── ReelOverlayService.kt      # WindowManager non-touchable overlay
│       │   │   ├── di/
│       │   │   │   └── DatabaseModule.kt          # Hilt dependency injection
│       │   │   └── ui/
│       │   │       ├── MainActivity.kt            # Edge-to-edge Compose entry point
│       │   │       ├── MainViewModel.kt           # Dashboard & permissions StateFlow
│       │   │       ├── SettingsViewModel.kt       # Settings preferences
│       │   │       ├── Screen.kt                  # Navigation routes
│       │   │       ├── theme/                     # Colors, Typography, Material 3 Theme
│       │   │       ├── dashboard/DashboardScreen.kt
│       │   │       ├── settings/SettingsScreen.kt
│       │   │       ├── onboarding/OnboardingScreen.kt
│       │   │       └── privacy/PrivacyScreen.kt
│       │   └── res/
│       │       ├── values/ (strings.xml, colors.xml, themes.xml)
│       │       └── xml/ (accessibility_service_config.xml, data_extraction_rules.xml, backup_rules.xml)
│       └── test/java/com/reelstop/
│           ├── DebounceFilterTest.kt              # Verifies burst-to-1 event aggregation
│           ├── ReelCounterManagerTest.kt          # Verifies 1-based start and increments
│           ├── SessionManagerTest.kt              # Verifies grace period & Room persistence
│           └── DailyStatsAggregationTest.kt       # Verifies stats math and longest session
├── gradle/
│   ├── libs.versions.toml                         # Dependency Version Catalog
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Build Instructions

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17 or higher
- Android SDK 35 (Android 15) with Build Tools 35.0.0
- Min SDK: Android 8.0 (API 26)

### Building via Terminal
```bash
# Clone or navigate to the project directory:
cd ReelStop

# Build debug APK:
./gradlew assembleDebug

# Run unit tests:
./gradlew test

# Install onto a connected device or emulator:
./gradlew installDebug
```

---

## Required Permissions & Setup

1. **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`)**:
   - Required to observe window changes, the presence of the Reels player, and vertical scrolling gestures.
   - Enable in: `Settings -> Accessibility -> Downloaded Apps -> ReelStop -> Enable`.
2. **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)**:
   - Required to draw the floating `REELS: X` pill above Instagram.
   - Enable in: `Settings -> Apps -> Special App Access -> Display over other apps -> ReelStop -> Allow`.
3. **Notifications (`POST_NOTIFICATIONS`)**:
   - Required on Android 13+ to display the background foreground service status and gentle milestone nudges.

---

## Testing Instructions

### 1. In-App Overlay Test
1. Open ReelStop.
2. Complete the initial permission setup if prompted.
3. On the Dashboard, tap **"Preview Counter Overlay"**.
4. A floating `REELS: 7` badge will appear at the top-center of the screen for 4.5 seconds and pulse gently.
5. Tap anywhere on your screen while the overlay is visible: notice that all touches pass straight through to whatever is beneath it (`FLAG_NOT_TOUCHABLE`).

### 2. Instagram Reels Live Testing
1. Launch Instagram.
2. Tap into the **Reels** tab or open a Reel.
3. Observe:
   - The top-center pill badge appears showing `REELS: 1`.
   - As you swipe up to the next Reel, observe that the counter increments to `REELS: 2`, `REELS: 3`, etc.
   - Rapid scrolling does not cause multi-increments due to the `DebounceFilter`.
   - Gestures (double-tap to like, swipe left to view profile, swipe up/down, tap comment button) are completely unaffected.
4. Exit Instagram or navigate to your Home screen:
   - Within 2 seconds, the overlay gracefully disappears.
5. Re-open ReelStop:
   - Notice today's stats (`Today's Reels`, `Today's Sessions`, `Total Reel Time`, `Longest Session`) are updated immediately.
   - The completed session appears in the "Recent Sessions" list.

---

## Known Android & Instagram Limitations

1. **Private API Inaccessibility**:
   - Instagram does not offer a public API or broadcast for Reel changes. Detection relies on public Android Accessibility APIs (`AccessibilityEvent` and `AccessibilityNodeInfo`).
2. **Instagram View Obfuscation / Code Minification**:
   - Internal view IDs (such as `clips_viewer_view_pager`) may shift across major Instagram app updates.
   - **Mitigation in ReelStop**: ReelStop employs a hybrid fallback strategy. If view IDs are obfuscated, ReelStop falls back to scroll trajectory settling (`SCROLL_SETTLE_WINDOW_MS`) and content description heuristics ("Audio", "Remix", "Reels").
3. **Aggressive OEM Background Killing**:
   - Certain Android device manufacturers (e.g. Xiaomi MIUI/HyperOS, Huawei EMUI, Samsung OneUI) may aggressively kill background AccessibilityServices or Foreground Services.
   - **Mitigation**: Users on these devices should disable "Battery Optimization" for ReelStop and lock ReelStop in the Recent Apps tray.
4. **Stories vs Reels Differentiation**:
   - Instagram Stories use horizontal progress segments and tap gestures, whereas Reels use vertical ViewPagers and vertical swipe gestures. The `DebounceFilter` filters by vertical scroll events to prevent Stories from polluting Reel counts.

---

## Future Improvements

1. **On-Device Machine Learning Gesture Classifier**:
   - Implement an on-device TensorFlow Lite model or sensor fusion classifier to detect short-form video consumption gestures with even lower latency.
2. **Weekly & Monthly Trend Visualizations**:
   - Add Compose Canvas charts (bar graphs and heatmaps) showing weekly scrolling trends.
3. **App Timer / Mindful Break Prompt**:
   - An optional gentle overlay that asks "Take a 5-minute breather?" when a configurable session limit (e.g. 30 minutes) is reached.
4. **Cross-Platform Support**:
   - Extend the detection engine architecture to YouTube Shorts and TikTok using the same decoupled `DebounceFilter` and `ReelCounterManager`.
