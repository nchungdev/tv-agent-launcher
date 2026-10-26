# TV Control Tool (`tvctl`) & Remote Management

`tvctl` is a dedicated Go-based CLI and ADB network controller located in `apps/tvctl/`. It provides safe, automated installation, theme deployment, and guarded debloating for Android TV devices.

---

## 1. Why `tvctl` Instead of Standard ADB?

- **Network-First**: Built-in discovery and connection retries optimized for TV sleep/wake cycles.
- **Cross-Platform**: Compiles into a single static binary for macOS (Apple Silicon / Intel), Linux, and Windows.
- **Bypasses macOS Local Network Restrictions**: Unlike the standard Google `adb` binary which is occasionally blocked by macOS local network privacy popups ("No route to host"), `tvctl` uses pure Go network sockets.
- **Guarded Debloating**: Verifies essential packages (Wi-Fi, Bluetooth, input methods, Home handler) before applying debloat lists.

---

## 2. Common `tvctl` Commands

Run `tvctl` via the provided helper script:

```bash
# On macOS / Linux:
scripts/tvctl <command> [args]

# On Windows (PowerShell):
scripts\tvctl.ps1 <command> [args]
```

| Command | Purpose |
|---|---|
| `scripts/tvctl probe` | Connects to `$TV_HOST` and inspects model, OS version, resolution, and RAM. |
| `scripts/tvctl install` | Builds and installs `tv-launcher.apk`, setting it as the default Home handler. |
| `scripts/tvctl theme <theme>` | Applies a visual theme preset from `packages/themes/`. |
| `scripts/tvctl debloat <list>` | Disables safe OEM bloatware apps listed in `packages/debloat-lists/`. |
| `scripts/tvctl restore <list>` | Re-enables previously disabled OEM packages. |
| `scripts/tvctl log` | Streams filtered Android TV system and launcher logs. |

---

## 3. Remote Management over Tailscale

When managing an Android TV located in a different house or network:

> [!WARNING]
> **Never forward port 5555 on a home router**. Plaintext ADB over the public internet exposes root/shell control to scanners and automated botnets.

### Recommended Secure Approach:
1. **Install Tailscale on the TV**:
   - Install the official Tailscale app via Google Play Store or sideload the APK.
   - Log in and enable the device in your Tailscale tailnet.
2. **Access via 100.x.y.z IP**:
   - Set `TV_HOST` to the TV's Tailscale IP address:
     ```bash
     export TV_HOST=100.85.12.34
     scripts/tvctl probe
     ```
   - All ADB traffic is automatically encapsulated in end-to-end encrypted WireGuard tunnels.
