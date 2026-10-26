# TV Launcher

Android TV launcher with a remote-driven home screen, a Vietnamese AI Agent and a
native Home Assistant control panel. This repository is
[nchungdev/tv-agent-launcher](https://github.com/nchungdev/tv-agent-launcher).

- **Launcher:** app dock/grid, widgets, remote shortcuts and a clock screensaver.
- **AI Agent:** voice or text requests sent to a separately configured server.
  See [Agent setup](services/agent/README.md) for configuration and limitations.
- **Home Assistant:** the TV connects directly using its own Hass URL and token.
  See the [Smart Home Module Documentation](docs/modules/smarthome.md).
- **Camera:** optional stream, vision and camera control service. See
  [Camera setup](docs/guides/camera-streaming.md).
- **tvctl:** network ADB tooling for launcher installation, themes and guarded
  debloating. See [tvctl Guide](docs/guides/tvctl-and-remote.md).

## 📖 Architecture & Developer Documentation

For a comprehensive walkthrough of the system architecture, MVI state management, and module breakdown, visit the **[Documentation Hub](docs/README.md)**:

- 🏗️ **[Architecture Overview](docs/architecture/overview.md)**: Feature-First vertical slicing & low-RAM design.
- 🔄 **[MVI State Management](docs/architecture/state-management-mvi.md)**: Unidirectional Data Flow with Kotlin Coroutines & Flow.
- 📏 **[Coding Standards & Constraints](docs/architecture/coding-standards.md)**: Strict limits (<300 lines/file, <=100 chars/line, 0 typealias, <700KB APK).
- 🚀 **[Developer Onboarding Guide](docs/guides/developer-onboarding.md)**: Toolchains, build commands, and 196+ unit tests.

The former Hub, Gateway and Smart Home backends have been retired. The optional
backends now live in `services/agent` and `services/camera`.

## Quick start

1. Enable USB/network debugging in the TV's Developer options.
2. Clone this repository and read [AGENTS.md](AGENTS.md) and
   [troubleshooting](docs/guides/troubleshooting.md).
3. Use the Go version declared in `apps/tvctl/go.mod`, JDK 21 and Android SDK 34:

   ```bash
   git clone https://github.com/nchungdev/tv-agent-launcher.git
   cd tv-agent-launcher
   export TV_HOST=192.168.1.50
   scripts/tvctl probe
   make install
   ```

Accept the debugging prompt on the TV. Installation verifies the Home handover.
Debloating is a separate optional action: read the matching list under
`packages/debloat-lists` and preserve the Home, Wi-Fi and input services.

For guided setup and remote management, see [tvctl Guide](docs/guides/tvctl-and-remote.md)
with the TV model and its IP address. Restore changes with
`scripts/tvctl restore packages/debloat-lists/<your-tv>/debloat.txt`.
Apply a theme with `scripts/tvctl theme packages/themes/<theme>`.
Windows users can use `scripts\tvctl.ps1`.

Build the launcher with `make launcher`. The release artifact is located at
`apps/launcher/app/build/outputs/apk/release/tv-launcher.apk`. If a compatible asset
is published, use [this repository's releases](https://github.com/nchungdev/tv-agent-launcher/releases).
The in-app updater also checks this repository; it does not install upstream releases.

Close debugging when remote control no longer requires ADB. Check the physical
remote after installing: injected keys do not prove accessibility filtering works.

## Layout and development

- `apps/launcher`: Kotlin/Java Android UI with native custom views and an optional
  rich-answer WebView; package identity remains `com.gathin.tvhome`.
- `apps/tvctl`: Go CLI and network ADB client.
- `packages`: shared contract, debloat lists and themes.
- `services`: independent Agent and Camera backends.
- `local`: ignored private configuration, research and device verification files.

`make check` runs the launcher and tvctl checks. Backend tests run separately;
see each service's documentation. Historical images in `docs/media` show the
upstream UI and may differ from the current launcher.

## Attribution & Credits

This project acknowledges and credits previous upstream open-source work:
- Derived from [kienmai160598/freeTV](https://github.com/kienmai160598/freeTV) (initial `tvctl` Go CLI tooling, Vietnamese localization, and Material 3 design foundation).
- Originally derived from [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (CleanYourTV ecosystem).

Original copyright and MIT terms remain in [LICENSE](LICENSE).
Google Sans Flex uses the SIL Open Font License; Material Color Utilities uses Apache 2.0. Bundled libraries retain their respective license notices.
