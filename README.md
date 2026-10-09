# Dot Fasting

A fasting tracker for **Nothing Phone (3)** (Nothing OS 4.1 / Android 16), matching [Dot Habits](https://github.com/madebygps/dot-habits).
Black UI, dot-matrix icons, one highlight colour. Works fully offline, with no account or backend.

## Features

- **Timer:** a large progress ring with count-up or count-down display. Start and end fasts manually.
- **Goals:** choose 12h, 16h, 18h, 24h or a custom duration. Reaching the goal never ends a fast.
- **Progress:** an offline dashboard shows current and best streaks, completed fasts, total and average fasting time, goal completion rate and the last seven days of activity.
- **History:** a Monday-first calendar marks days with completed fasts. Tap a session to edit its start and end dates and times independently, or delete it.
- **Notifications:** optional goal alerts.
- **Widgets** (display only): compact 1x1, progress-ring 2x2 and detailed wide 4x2 options. Each shows dotted elapsed hours and minutes; progress and details refresh with the timer. Tap to open the app. Widgets always count up, independently of the app's count-down setting.
- **Glyph Toy:** progress, elapsed and remaining views. Long press switches views; short press cycles system Toys.

## Build

Needs JDK 17 and the Android SDK with platform 37. Requires Android 16.
Set `ANDROID_HOME`, or put `sdk.dir` in an untracked `local.properties`.

```sh
./gradlew :app:testWithoutGlyphDebugUnitTest :app:installWithoutGlyphDebug
```

Run instrumented tests only on an emulator or dedicated test device. The project
keeps APKs installed after device tests to avoid Gradle's default uninstall/data
deletion, but tests can still change app state.

For Glyph support, obtain Nothing's official SDK and place `glyph-matrix-sdk-2.0.aar` in `app/libs/`.
See [Glyph setup and licensing](app/Glyph-Setup.md).

```sh
./gradlew :app:testWithGlyphDebugUnitTest :app:installWithGlyphDebug
```

Then enable Glyph in the app and select Dot Fasting in **Settings > Glyph Interface > Glyph Toys**.

## Good to know

- **Navigation:** use Android's back gesture or system back button. Tap Started on Home to edit the active start date or time.
- **Timers:** persisted timestamps keep the timer independent of the app process. Clock conflicts require review; timezone changes affect display only.
- **Background updates:** while a fast and widget are active, a non-wakeup Android alarm requests an update near each elapsed-minute boundary. Android battery restrictions can delay a displayed minute, progress update or alert. WorkManager provides periodic recovery. Force-stopping the app prevents background refreshes until it is reopened. Reboots and clock changes restore scheduling.
- **Privacy:** records stay on this device. No cloud backup or export; uninstalling clears the data.
- **Glyph Matrix SDK:** proprietary and not included in this repo. Commercial use requires Nothing's written permission.

## Verified on device

- Timer controls, timestamp editing, history navigation and deletion
- Widget rendering and tap-to-open
- Dotted minute-only widgets and background alarm refreshes

## Not yet verified on device

- Physical Glyph display, button behaviour and AOD
- Battery use over a full day
- Reboot, clock changes and alerts under Doze

## Licensing

Adapted Dot Habits components are MIT-licensed; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
No open-source license for Dot Fasting's own source is granted yet. Nothing's SDK has its own EULA.
