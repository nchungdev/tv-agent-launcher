# Launcher media validation

Date: 29/10/2026. Device: Television_1080p emulator, Android TV API 36,
1920×1080 at density 320. No physical-TV or production-Sentinel E2E claim.

## Final verified build

- `make check launcher`: passed, 259 unit tests, zero failures.
- Release APK: 597,958 bytes, below the unchanged 716,800-byte CI limit.
- Release installed on the TV emulator through `scripts/tvctl`; Home resolves to launcher,
  and package flags contain no DEBUGGABLE flag.
- Native media scenario passed after the Movie/Series visual redesign, including source
  focus, Movie version visibility, Series grid/resize/D-pad checks, season filtering,
  all 500 episodes, truncated flow,
  cancelled resolve, HLS/MP4 seek/resume and Home release.

One initial check run hit the existing asynchronous `WatchFeedTest` assertion
(`independentSourcesDoNotBlockOrHideEachOther`); the full rerun passed without changes
to WatchFeed or its test. This remains a test reliability issue to investigate separately.

## Automated checks

- Unit tests cover request/sequence rejection, independent source results, retained metadata,
  explicit actions, four content types, cross-source identity and playback URL validation.
- Native `media` scenario uses actual HTTP, platform decoder, media session, video surface,
  inline season filtering, pagination and local history. It restores Agent configuration and playback/search
  preferences in `finally`; fixture media never becomes permanent user watch history.
- Fixture server requires a test-only bearer for API calls and exposes a separate media
  capability path without that bearer. It serves MP4 HTTP Range and HLS segments.
- Native Series checks verify two-column sizing, season spacing, D-pad movement, narrow
  single-column fallback and focused episode identity across resize.
- Native Movie checks verify that one version has no season/episode list and multiple
  versions show a separate Versions & sources section; Series keeps season filtering.
- Successful native run checked search/source focus, detail navigation, 500 episodes,
  truncated-flow failure, HLS/MP4 decode, seek with advancing playback, local progress,
  fresh resolve through Watch Next, late cancelled resolve rejection and release on Home.

Fixtures are authored from the checked backend schemas, not captured production responses:
`requestflow/contracts.go`, `catalog/types.go`, `playback/sessions.go` and catalog handlers
at checkout HEAD `f088227` with uncommitted media-search changes. They do not prove backend
provider reachability, authorization policy or every source's capability.

## Reproduce

From repository root, with Python 3 and FFmpeg available. Optional Pillow generates the
original synthetic posters; without it, the FFmpeg test-pattern frame is used:

```sh
python3 apps/launcher/test-fixtures/media_server.py --generate
```

In another terminal, start Television_1080p and build the debug target:

```sh
android emulator start Television_1080p
JAVA_HOME=$(/usr/libexec/java_home -v 21) apps/launcher/gradlew -p apps/launcher \
  -PtvTestBuildType=debug assembleDebug assembleDebugAndroidTest
export TV_HOST=127.0.0.1 TV_PORT=5555
scripts/tvctl install --stock none --now-playing \
  apps/launcher/app/build/outputs/apk/debug/tv-launcher-debug.apk
scripts/tvctl push \
  apps/launcher/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  /data/local/tmp/media-qa.apk
scripts/tvctl shell 'pm install -r -t /data/local/tmp/media-qa.apk'
scripts/tvctl shell 'am instrument -w -e scenario media \
  com.gathin.tvhome.test/com.gathin.tvhome.AgentRenderInstrumentation'
```

This scenario targets the emulator host alias `10.0.2.2:8765`. Do not run it on the owner's
physical TV. The server binds only host loopback; stop it after testing. Test screenshots
are in the app's external `player-qa` folder. Generated media and pulled screenshots are
under gitignored `local/player-qa`.

Restore the release APK with `scripts/tvctl install --stock none --now-playing` after QA.
If stock Home still owns the emulator, follow the normal tvctl Home handover instead of
assuming `--stock none` can disable it.

## Still required

- Real Sentinel/provider E2E with auth, URL expiry/restart and measured cancellation.
- Physical TV 1 GB profiling: codec/HDR/audio compatibility, PSS/CPU/frame time and cold start.
- Embedded subtitle/audio matrix, DRM, external subtitle delivery and all firmware variants.
- Player-app fallback with the actual source app installed; no precise external resume claim.
- Episode-aware server history, upload retry/tombstones and auto-next contract.
- Full TV Provider permission/observer instrumentation from the Continue Watching plan.
