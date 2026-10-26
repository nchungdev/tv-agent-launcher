# Developer Onboarding Guide

Welcome to the `tv-agent-launcher` codebase! This guide helps you set up your development environment, build the project, run tests, and deploy the launcher to an Android TV.

---

## 1. Prerequisites & Environment Setup

Ensure the following tools are installed:

- **Java Development Kit (JDK)**: JDK 17 or 21 (recommended: Temurin 21 or Android Studio bundled JDK).
- **Android SDK**:
  - `compileSdkVersion`: **34** (Android 14)
  - `minSdkVersion`: **26** (Android 8.0 Oreo)
  - `targetSdkVersion`: **34**
  - Android Build Tools: `34.0.0`
- **Go**: Version `1.22+` (only needed if building or modifying `apps/tvctl`).
- **Operating System**: macOS, Linux, or Windows (WSL2 / PowerShell).

Verify your setup:
```bash
java -version
javac -version
echo $ANDROID_HOME
```

---

## 2. Project Directory Structure

- `apps/launcher`: The core Android TV Launcher application (Kotlin, Android SDK).
- `apps/tvctl`: CLI tool written in Go for network ADB pairing, installation, and TV management.
- `packages/`: Shared data contracts (`packages/contract`), themes, and debloat lists.
- `docs/`: System documentation, architecture guides, and technical references.

---

## 3. Essential Gradle Commands

All Gradle commands should be executed from `apps/launcher/`:

```bash
cd apps/launcher
```

### A. Run Unit Tests (Fast Verification)
The project includes 196+ JVM unit tests covering MVI state holders, repositories, URL parsers, and contracts:
```bash
./gradlew testReleaseUnitTest
```
*Expected output*: `BUILD SUCCESSFUL` within ~5–7 seconds.

### B. Build Debug APK
```bash
./gradlew assembleDebug
```
Output: `apps/launcher/app/build/outputs/apk/debug/app-debug.apk`

### C. Build Production Release APK
```bash
./gradlew assembleRelease
```
Output: `apps/launcher/app/build/outputs/apk/release/tv-launcher.apk`

### D. Verify CI Size Gate
Ensure the release APK does not exceed the 700 KB ceiling:
```bash
stat -f "%z bytes" app/build/outputs/apk/release/tv-launcher.apk
test $(stat -f %z app/build/outputs/apk/release/tv-launcher.apk) -lt 716800 && echo "PASS"
```

---

## 4. Code Quality & Formatting Checks

Before opening a pull request or submitting code, ensure compliance with the repository rules:

1. **File Length**: Maximum **300 lines** per file.
2. **Line Length**: Maximum **100 characters** per line.
3. **No `typealias`**: Always use explicit data classes or interfaces.
4. **100% Coroutines**: Zero `Thread`, `Handler`, or `AsyncTask`.

Quick automated scan from project root:
```bash
# Check line length
git grep -n '^.\{101,\}$' 'apps/launcher/app/src/main/java/**/*.kt'

# Check for typealias
git grep "typealias " apps/launcher/
```

---

## 5. Deploying to an Android TV

1. **Enable Developer Options on TV**:
   - Go to `Settings -> Device Preferences -> About -> Build`.
   - Tap `Build` 7 times until developer mode is unlocked.
   - Go to `Settings -> Device Preferences -> Developer options` and enable **Network debugging** (or USB debugging).

2. **Connect via `tvctl` or ADB**:
   ```bash
   export TV_HOST=192.168.1.50
   adb connect 192.168.1.50:5555
   ```
   Accept the authorization prompt on the TV screen.

3. **Install Launcher**:
   ```bash
   make install
   # Or using tvctl directly:
   scripts/tvctl install
   ```

4. **Verify Real-Time Logs**:
   ```bash
   adb logcat -s "TvHome" "DuinchAgent" "HassClient"
   ```
