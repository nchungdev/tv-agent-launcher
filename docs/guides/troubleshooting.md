# Troubleshooting Guide

This guide covers common issues and resolutions when deploying, connecting to, and operating `tv-agent-launcher` across diverse Android TV hardware platforms.

---

## 1. ADB Connection Issues

### "No route to host" on macOS
- **Cause**: macOS Local Network Privacy blocks the standard Google `adb` binary from communicating over local IP sockets.
- **Fix**: Use `scripts/tvctl` instead of standard ADB. `tvctl` is written in Go and bypasses this restriction.

### No "Wireless Debugging" toggle in Developer Options
- **Xiaomi TV / Mi Box**: Enabling **USB debugging** alone opens TCP port `5555` over Wi-Fi/Ethernet.
- **Google TV (Chromecast, Sony, TCL)**:
  1. Pair once using TLS:
     ```bash
     adb pair <ip>:<port> <code>
     ```
     (Find the pairing port via `dns-sd -B _adb-tls-pairing._tcp`).
  2. Connect and enable TCP mode:
     ```bash
     adb connect <ip>:<port>
     adb tcpip 5555
     ```

### Device on another VLAN or Subnet
- Add a temporary firewall pinhole permitting TCP port `5555` from your development machine to the TV's IP address.

---

## 2. Installation & Default Home Handover

### `INSTALL_FAILED_VERIFICATION_FAILURE`
- **Cause**: Google Play Protect rejects APKs targeting outdated Android SDKs.
- **Fix**: Ensure your build targets SDK 34 (`targetSdkVersion 34`).

### Home screen doesn't switch after install
- Check active Home category handlers:
  ```bash
  adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  ```
- If the stock OEM launcher re-asserts priority, use `scripts/tvctl debloat` with the appropriate device list from `packages/debloat-lists/`.

### Google TV Setup Wizard crash-loop (`setupwraith`)
> [!CAUTION]
> On Google TV, `com.google.android.tungsten.setupwraith` registers a fallback Home activity (priority 1).
> If the stock launcher is disabled, `setupwraith` can cause a `system_server` crash-loop.
> Always disable the entire package:
> ```bash
> adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
> ```
> To escape a crash loop:
> ```bash
> adb shell pm enable --user 0 com.google.android.apps.tv.launcherx
> ```

---

## 3. Remote Control & Input Issues

### Remote Assistant Button does not trigger Duinch
- Ensure [`InputKeyService`](file:///Users/lap16792/Personal/tv-agent/tv-agent-launcher/apps/launcher/app/src/main/java/com/gathin/tvhome/service/InputKeyService.kt) is enabled as an Accessibility Service:
  ```bash
  adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/.service.InputKeyService
  ```

### No On-Screen Keyboard on Xiaomi TV after debloat
- Re-select the Google Latin IME:
  ```bash
  adb shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
  ```
