# Coding Standards & Engineering Constraints

To ensure long-term maintainability, high performance on low-RAM hardware, and consistency across autonomous contributions, all code inside `tv-agent-launcher` must strictly adhere to the following standards.

---

## 1. File & Line Length Limits

- **File Length Limit**:
  - Every source file should remain under **300 lines of code**.
  - If a file approaches or exceeds 300 lines, it must be decomposed into smaller, specialized helper classes, coordinators, or sub-components.
- **Line Length Limit**:
  - Hard limit of **100 characters per line**.
  - Wrap long expressions, method parameters, and chained calls across multiple lines.
  - Automated check: Verified via `scripts/check-style`.

---

## 2. Kotlin Language Guidelines

### A. Zero `typealias`
- **Rule**: Do not declare or use `typealias` in this repository.
- **Reason**: `typealias` obscures true domain types, weakens IDE navigation, and bypasses strong typing guarantees.
- **Enforcement**: Always declare explicit domain data classes, interfaces, or inline value classes.

### B. 100% Kotlin Coroutines & Flow
- **Rule**: Legacy concurrency models (`java.lang.Thread`, `android.os.Handler`, `AsyncTask`, `Timer`, `Runnable`) are prohibited in business logic.
- **Enforcement**:
  - Use `kotlinx.coroutines.CoroutineScope`, `launch`, `async`, and `withContext`.
  - Use `StateFlow` for state observation and `SharedFlow` for one-shot events.
  - Respect Android lifecycle via lifecycle-aware extensions ([`CoroutineLifecycleExtensions.kt`](file:///Users/lap16792/Personal/tv-agent/tv-agent-launcher/apps/launcher/app/src/main/java/com/gathin/tvhome/core/util/CoroutineLifecycleExtensions.kt)).
  - Always handle cancellations properly without swallowing `CancellationException`.

### C. Naming & Conventions
- Class names must be descriptive and follow PascalCase: `HassClient`, `SmartHomeRepository`, `HomeGridCoordinator`.
- Use **`Hass`** prefix for Home Assistant clients/tools (e.g., `HassClient`, `HassControls`), while keeping general domain layers named after **`SmartHome`** (`SmartHomeDevice`, `SmartHomeSheet`).

---

## 3. Memory & Resource Constraints (1 GB Android TV)

1. **Zero Allocations in Render Paths**:
   - `onDraw()` in custom views must never allocate new objects (`Paint`, `Rect`, `Path`, `Shader`).
   - Pre-allocate all graphic primitives during initialization or bounds change (`onSizeChanged`).

2. **Image Loading**:
   - Never pull external heavy libraries like Glide or Coil into the APK.
   - Use the built-in [`ImageLoader`](file:///Users/lap16792/Personal/tv-agent/tv-agent-launcher/apps/launcher/app/src/main/java/com/gathin/tvhome/core/util/ImageLoader.kt) which uses downsampling, low-RAM hardware bitmap configurations, and a bounded in-memory cache.

3. **In-Window Floating Sheets**:
   - Avoid creating new Activities for modals, settings, or tools.
   - Implement them as lightweight in-window dialogs extending [`SideSheet`](file:///Users/lap16792/Personal/tv-agent/tv-agent-launcher/apps/launcher/app/src/main/java/com/gathin/tvhome/core/view/SideSheet.kt) or [`CenterModal`](file:///Users/lap16792/Personal/tv-agent/tv-agent-launcher/apps/launcher/app/src/main/java/com/gathin/tvhome/feature/settings/ui/CenterModal.kt).

---

## 4. Release Size & CI Gate

- **CI Release Size Gate**:
  - The release APK (`tv-launcher.apk`) must remain strictly under **716,800 bytes (~700 KB)**.
  - Current footprint: **~561 KB** (575,221 bytes).
  - Any pull request or commit that causes the APK to exceed this ceiling will fail CI verification.
- Verification command:
  ```bash
  test $(stat -f %z apps/launcher/app/build/outputs/apk/release/tv-launcher.apk) -lt 716800
  ```

---

## 5. Git Commit Protocol

- Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:
  - `feat(<scope>): ...`
  - `fix(<scope>): ...`
  - `refactor(<scope>): ...`
  - `chore(<scope>): ...`
  - `docs(<scope>): ...`
- **Strict Rule**: Never use `git commit --amend` to combine or overwrite previous commits unless explicitly requested. Always create new, discrete commits to ensure an auditable history.
