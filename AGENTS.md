# Repository instructions

## Android toolchain

On this workstation, use the Homebrew-managed JDK and Android SDK:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

The SDK includes Android platforms 35, 36, and 37. Do not conclude that the SDK
or a phone is unavailable before checking these paths and running `adb devices -l`.

## Build and phone installation

For every task that changes Android app code, resources, manifests, or build
configuration, do not consider the task complete until the updated app has been
built and installed on the connected phone:

```sh
./gradlew :app:testWithoutGlyphDebugUnitTest :app:installWithoutGlyphDebug
adb shell pm path com.madebygps.dotfasting
```

Use the `withoutGlyph` variant by default. Use `withGlyph` only when the task
requires it and the proprietary Glyph SDK AAR is present. If no authorized phone
is connected, report installation as blocked instead of silently skipping it.
