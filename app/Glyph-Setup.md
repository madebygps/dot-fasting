# Optional Glyph Toy setup

The Glyph Toy is opt-in and requires a manually acquired Nothing Glyph Matrix SDK AAR. The standard `withoutGlyph` variant has no SDK dependency. For the SDK-enabled `withGlyph` variant, place the official, locally obtained AAR at `app/libs/glyph-matrix-sdk-2.0.aar`; do not commit or redistribute it. No SDK binary is included or downloaded by this project.

Obtain the SDK and review its current terms directly from the [official Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit). The EULA is pinned for review at [revision 999b1143a6bff0dd79db11d21b92c15c56a346b1](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/999b1143a6bff0dd79db11d21b92c15c56a346b1/LICENSE.md). It prohibits redistribution, and commercial use requires prior written permission. Acquiring the AAR and accepting its license are the user's responsibility; contact GDKsupport@nothing.tech for licensing questions.

On a supported Phone (3), enable Glyph Toy in Dot Fasting, then use `GlyphIntegration.openSetup(context)` to open the system Glyph Toys manager. If it is unavailable, open **Settings > Glyph Interface > Glyph Toys**, add **Dot Fasting**, and select it in the toy carousel. A short Glyph Button press remains system-owned carousel navigation. A long press changes only the displayed view.

The toy displays an original monochrome progress ring, elapsed time, and remaining time/goal-met marker. It does not start, stop, or otherwise mutate a fast. The service updates at minute boundaries while selected and responds to AOD events without a perpetual ticker or wake lock. OS toy selection, brightness, and service availability remain system controlled.

Only Phone (3), SDK target `Glyph.DEVICE_23112` with a 25x25 matrix, is supported. Hardware detection conservatively checks Nothing model `A024` and the SDK-reported matrix length; unknown model strings are unsupported, not presumed compatible. Emulator or build validation does not prove real device behavior; toy discovery, hardware button events, AOD lifecycle, brightness, screen lock, reboot, Doze, and battery behavior require verification on a Phone (3). Glyph Toy availability and behavior can vary by Nothing OS version.

Time views show hours and minutes below 100 hours. Longer durations show exact total minutes under `MIN`, split across two rows if necessary; hours never wrap around. `CHK` means the shared projection requires timestamp review, and questionable elapsed time is not advanced.

## App wiring contract

The app and service use the same stored settings and fasting repository through `GlyphIntegration.context(context)`. This adapter maps active sessions using the shared `project(session, clock.snapshot())` projection and timestamps elapsed samples with elapsed realtime:

```kotlin
val glyphContext = GlyphIntegration.context(context)
val enabled: Flow<Boolean> = glyphContext.enabled
val state: Flow<GlyphFastState> = glyphContext.fastState
```

Pass the current `glyphEnabled` value to `GlyphIntegration.capability(context, enabled)` when displaying capability status. `SETUP_REQUIRED` means supported and opted-in but service connection is unverified; `READY` is reported only after a real SDK connection callback. `SERVICE_UNAVAILABLE` means binding or data reading failed; it does not imply a rendered frame. `GlyphIntegration.openSetup(context)` either opens the resolved system manager or returns `MANUAL_STEPS_REQUIRED` for the app to show manual directions. The SDK-enabled flavor also needs the local AAR Gradle dependency and the manifest merger to include `app/src/withGlyph/AndroidManifest.xml`; the no-SDK flavor uses `app/src/withoutGlyph`.
