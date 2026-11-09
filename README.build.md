# Build Rhine Terminal

This is a native Java Android live wallpaper. The app has no third-party runtime dependencies.

## Requirements

- JDK 17
- Android SDK platform 35 and Build Tools 35.0.0
- Gradle 8.13 (the included standard Gradle wrapper downloads and verifies it)

Android Gradle Plugin is pinned to 8.9.2. The application targets API 35 and supports API 26 and newer.

## Build

Set `JAVA_HOME` to your JDK 17 directory and `ANDROID_HOME` to your Android SDK directory. Then run:

```sh
export GRADLE_USER_HOME="$PWD/.tools/gradle-home"
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest lintDebug
```

On the development Mac, these paths are available:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
export GRADLE_USER_HOME="$PWD/.tools/gradle-home"
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest lintDebug
```

Outputs:

- App: `app/build/outputs/apk/debug/app-debug.apk`
- Instrumentation tests: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Lint report: `app/build/reports/lint-results-debug.html`

The debug APK uses the Android debug signing key and can be installed directly on a development device. Release builds require your own signing configuration before distribution.

## GitHub releases

Every GitHub version release must include the compiled, tested APK as a downloadable asset.

1. Build from the commit being released. Confirm the APK package version matches the `v<versionName>` tag. Use the established signing key so existing installations can upgrade.
2. Create a draft GitHub Release targeting that commit. Attach the APK as `RhineTerminal-<versionName>-debug.apk` for the current debug distribution, and include its SHA-256 in the release notes.
3. Confirm the uploaded asset matches the local checksum, then publish. Verify the published asset and the tag after publication.

## Device tests

With a connected Android device or emulator, install both debug APKs and run:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.rhine.terminal.test/dev.rhine.terminal.TerminalInstrumentation
```

Run instrumentation before applying the wallpaper: the test framework force-stops the target package when starting and finishing, and repeated stops can make Samsung restore its default wallpaper. After all tests, apply Rhine Terminal with the system picker. Do not run instrumentation afterward unless you plan to apply again.

For capture checks while the device is locked, use `adb shell am instrument -w -e skipPreview true dev.rhine.terminal.test/dev.rhine.terminal.TerminalInstrumentation`. Full preview lifecycle checks require the user to unlock normally.

Use the app's wallpaper preview to inspect the animation and apply it through Android's wallpaper picker.

## Source archive

Include `gradlew`, `gradlew.bat`, `gradle/wrapper/`, the root build files, `app/build.gradle`, and `app/src/`. Exclude `.tools/`, `.gradle/`, `build/`, and `local.properties`. These caches and machine-specific paths are unnecessary for rebuilding.

## Offline previews (0.7.1)

Generate the self-contained previews from the Android shaders and bundled assets. The archive also extracts the native mesh and timeline using JDK 17.

```sh
python3 tools/archive-desktop/build.py
python3 tools/carbon-preview/build.py
```

Open either preview in Chrome and choose an orientation. The archive preview offers light and graphite specimen wallpapers and a timeline for the lock scene. The carbon preview adds sample telemetry to the graphite wallpaper. See [export and verification tools](tools/archive-desktop/README.md) for wallpaper PNGs. Android previews and lock wallpaper share the opt-in 主题音效 setting. The archive timeline is visual only.

The accelerometer fallback can be checked with a deterministic synthetic trace using JDK 17.

```sh
javac -d build/tap-test app/src/main/java/dev/rhine/terminal/SoftTapDetector.java tools/tap-detector/SoftTapDetectorTest.java
java -cp build/tap-test SoftTapDetectorTest
```

Generate the six vector wallpapers with the standard-library SVG tool.

```sh
python3 tools/wallpaper-svg/build.py --output outputs/wallpapers-v0.5.1/svg
```

## Historical desktop diagnostic (0.2.0)

Use JDK 17 and Python 3 to generate a self-contained WebGL 2 preview from the actual Java mesh and shader strings. The desktop UI is a diagnostic composition, not the Android lock screen.

```sh
python3 tools/desktop/build.py --output build/desktop-preview.html
```

Open the HTML with Chrome. For automated desktop checks, make Node.js Playwright and Chrome available; `PLAYWRIGHT_MODULE` can point to an installed module and `CHROME_BIN` to an installed Chrome binary:

```sh
node tools/desktop/test.cjs build/desktop-preview.html build/desktop-validation
```

The test reports timer-query measurements for both queued stress and single-draw synchronized samples. The latter includes a synchronous pixel readback to distinguish completed work from submitted commands. Browser timings do not prove Android display presentation. On the tablet after authorized installation, compare `RhineGL` submitted FPS/CPU percentiles with system compositor frame timestamps. Both normal and warning modes must be checked at the actual lock-screen refresh rate.

The optional device-credential failure trigger is OFF until explicit system device-admin authorization. Its manifest declares only `watch-login`. Keep native authentication intact, never script a PIN entry, and verify Samsung callback delivery and wallpaper visibility with the user's involvement. No fingerprint failure events are claimed.

## Home preview (0.3.0)

Build the standalone Home preview with JDK 17 and Python 3, then open it in Chrome.

```sh
python3 tools/home-desktop/build.py --output build/home-preview.html
node tools/home-desktop/test.cjs build/home-preview.html build/home-validation
```

The same mesh and shaders run in Android `HomeWallpaperService`. Open Rhine Terminal, select 主屏预览 to inspect the manual unfolding effect, then select 设置主屏壁纸 and choose 主屏幕 in the system picker. The old lock-screen action uses the original service component. For hardware validation measure compositor timing at the actual launcher refresh rate, verify the visible edge widths in each orientation, and compare the manually reproduced unlock sequence with the wallpaper entrance. The desktop test does not access Android battery or thermal state.
