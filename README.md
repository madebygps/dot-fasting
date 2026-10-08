# Dot Fasting

A separate native fasting timer for Nothing Phone (3), Nothing OS 4.1 / Android 16.
Kotlin, Jetpack Compose, Room and DataStore. Local-only, offline, no account,
backend, analytics or health-data integration.

## What it does

- Manually start and end one fast.
- Choose 12h, 16h, 18h, 24h or a custom duration. No goal is selected on first use;
  subsequent starts remember the last selection.
- See elapsed time, remaining time and a circular progress display. At the goal,
  the ring is full and the status says **Goal met**. The timer keeps going until
  you end it.
- Choose **Count up** or **Count down** in Settings. The selection persists and
  applies to the app and widget. Countdown stays at zero after the goal; it
  never ends a fast automatically.
- Save history and correct start/end times. On an active timer, tap **Edit start
  time**, select the date and time, then Save.
- Delete a fast from its History detail view after confirmation. Deletion is
  permanent; deleting an active fast also clears its timer and background status.
- Optionally receive a goal-met notification.
- Add a display-only launcher widget. Tapping it opens the app.
- Optionally use a 25x25 Glyph Toy showing progress, elapsed time or remaining time.

There are no automatic starts/stops, eating-window schedules, streaks, weight-loss
recommendations or claims about physiological phases. Durations are choices,
not recommendations.

## Build

Use JDK 17 and an Android SDK with API 37 and compatible build tools. The app
requires and targets API 36 (Android 16). AGP 9 uses built-in Kotlin; do not add
the `kotlin-android` plugin.

Set `JAVA_HOME` and `ANDROID_HOME` for your installation, or set `sdk.dir` in an
untracked `local.properties`. On a Homebrew installation, for example:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :app:assembleWithoutGlyphDebug :app:testWithoutGlyphDebugUnitTest :app:lintWithoutGlyphDebug
```

The normal `withoutGlyph` build does not need the Nothing SDK. All timer, history,
notification and widget features work; Settings explicitly reports that the
Glyph SDK is not included.

APK: `app/build/outputs/apk/withoutGlyph/debug/app-withoutGlyph-debug.apk`.
To install on a connected, authorized device:

```sh
./gradlew :app:installWithoutGlyphDebug
```

Room schemas are versioned under `app/schemas/`. For instrumented tests, use an
API 36+ emulator or an explicitly authorized device:

```sh
./gradlew :app:connectedWithoutGlyphDebugAndroidTest
```

## Optional Glyph build

Read the official [Glyph Matrix SDK EULA](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/999b1143a6bff0dd79db11d21b92c15c56a346b1/LICENSE.md)
before acquiring or using the SDK. Obtain it yourself from the
[official kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
and place `glyph-matrix-sdk-2.0.aar` in `app/libs/`.

```sh
./gradlew :app:assembleWithGlyphDebug :app:testWithGlyphDebugUnitTest :app:lintWithGlyphDebug
```

No SDK binary or private key is included in this repository. The SDK-enabled
variant deliberately fails with setup instructions if the AAR is absent.
Nothing's EULA restricts redistribution and requires prior written permission
for commercial use; contact `GDKsupport@nothing.tech`. Do not interpret public
sample projects as permission to redistribute the SDK. Do not publish an
SDK-enabled APK without reviewing the applicable distribution permissions.

Enable Glyph in Dot Fasting Settings, then add Dot Fasting to the system's
Manage Glyph Toys queue. If the system shortcut is unavailable, navigate there
manually in Nothing Settings.

- Short press: Nothing OS cycles system Toys.
- Long press: Dot Fasting switches progress / elapsed / remaining views.
- No Glyph interaction starts, pauses or ends a fast.
- Glyph is monochrome and uses system brightness, not the app highlight color.
- System selection, service availability, firmware and AOD settings govern
  visibility. This is not a promise of uninterrupted always-on display.

## Timing, privacy and background limits

Session state is persisted, not reconstructed from a running countdown process.
Same-boot elapsed time uses monotonic timing anchors; reboot recovery uses the
stored UTC start time. Detected clock conflicts require timestamp correction.
Timezone/DST changes affect display, not interval duration. Arbitrary wall-clock
changes while powered off cannot always be detected.

There is no perpetual background ticker or foreground service. Goal alerts use
an inexact alarm and may be delayed by Android's battery restrictions. Enabling
them requires notification permission; denied permission or disabled channels
can prevent delivery. Ending or correcting a fast cancels/reconciles its alert.
Reopening after a force-stop restores projections; Android does not deliver
background events to force-stopped apps until they are reopened.

The widget shows a dot-matrix hours/minutes counter inside a progress circle,
in the selected direction. No icon, captions or visible timestamp. It is a snapshot, not
a second-by-second timer: it refreshes on state/preference changes and
periodically while a fast is active (15-minute work interval; Android can defer
refreshes). Snapshot freshness and direction are in the accessibility
description. Tap anywhere to open the app. No lock-screen support is promised.

Data lives in this app's separate local database/preferences. Android backup is
disabled. Uninstalling clears the app's data; no cloud copy or export is provided.

## Design and licensing

The palette, typography and compact UI conventions match the owner's Dot Habits
design: black surfaces, system body font, monospaced labels, original dot-matrix
accents, clockwise rings and a configurable highlight (Signal red by default).
The timer is centered in the available screen space, using a ring up to 300dp
with dot-matrix time inside it and a circular start/stop control. Home omits
the date and redundant status header; help lives in Settings.
Home has no app-title or timer-stage text. Calendar and gear icons open History
and Settings; Android's system back gesture/button returns through session
detail, History/Settings, and Home.
Settings has one Help entry at the bottom for timer, notification, widget and
Glyph guidance. Android/Toys setup actions appear only when relevant.
History opens a Monday-first month calendar. Any ended fast fills its local
end date in the highlight color, regardless of its goal. Active fasts have an
outlined start date. Tap a day to see its sessions, then open a session to edit
timestamps. Multiple fasts share a day marker; month arrows browse older records.
The selected date appears once above compact time-range/duration rows; goal
details stay inside the session. Cross-midnight rows include the start date.
Session detail centers the dotted duration above compact Started/Ended/Goal
rows. EDIT in its header opens timestamp correction; active sessions show live
elapsed time rather than an empty duration.
Dates and times use the device's local zone without UTC-offset
suffixes; DST-aware correction still preserves the original offset where possible.
The passive widget shares the progress circle and selected hours/minutes counter.
Nothing's SDK supplies hardware integration, not UI components.

This implementation and branding are separate from Dot Habits. Its exact
settings icon geometry, numeric font and typography are reused under MIT,
with an original calendar companion in the same proportions. Fasting-specific
controls keep manual-end confirmation (the stop symbol is not a pause action).
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
No open-source license for Dot Fasting's own source is granted yet.

## Verification boundaries

Workstation build/tests/lint do not verify Nothing firmware behavior. Hardware
acceptance on Phone (3) / Nothing OS 4.1 must cover:

- Start/end, goal overflow, timestamp correction, process death and reboot.
- Manual clock/timezone/DST changes and notification permission revocation.
- Goal alerts under screen lock, Doze, battery saver and force-stop/relaunch.
- Launcher widget sizing, refresh freshness, tap-to-open and theme alignment.
- Toy registration/round preview, short/long press, view cycling, AOD events,
  service cleanup, system brightness and battery impact.

No Essential Space API integration or Essential Key remapping is provided.

The `withoutGlyph` debug APK, unit suite and lint have been verified on
the workstation. Timer UI interaction tests have also run on the connected
Phone (3); this does not establish the background or Glyph acceptance items above.
The `withGlyph` variant has also been built, unit-tested and linted using a
locally downloaded official SDK. Its binary and accompanying local EULA are
ignored by Git. Glyph display, system Toy selection and AOD behavior still
require hardware acceptance; SDK inclusion alone does not verify them.
