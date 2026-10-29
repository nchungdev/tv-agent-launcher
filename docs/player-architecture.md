# Launcher Player and media details

Implementation date: 29/10/2026. Scope: foreground playback in the launcher window,
without a new Activity, using the Android platform `VideoView` / `MediaPlayer` engine.
The existing 5 MiB APK limit remains in force.

## References and decisions

| Reference | Observed information | Application here |
| --- | --- | --- |
| [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) | GitHub shows ~3.3k stars, ~561 forks, GPL-3.0; documentation describes detail → stream → player | Separate metadata/detail from starting playback; retain episode/source identity |
| [Nuvio Essential Mode](https://github.com/NuvioMedia/NuvioTV/blob/dev/docs/essential-mode.md) | Details retains Play, metadata and episode list; player exposes tracks/sources/episodes | Poster, synopsis, explicit primary action and shared episode component |
| [Nuvio focus issue](https://github.com/NuvioMedia/NuvioTV/issues/2505) | Documents restoring the selected item after returning from detail | Keep search rows by stable key and retain detail beneath the internal player |
| [Just Player](https://github.com/moneytoo/Player) | GitHub shows ~2.8k stars, 304 forks, Unlicense; uses Media3 | Track selection and recovery via external player; no code copied |
| [Stremio Android TV](https://blog.stremio.com/stremio-for-android-tv-now-in-google-play-store/) | Official 2022 introduction describes D-pad, seek and preferred tracks | Remote-first controls; this historical post is not evidence for every current release |
| [Android MediaPlayer lifecycle](https://developer.android.com/media/platform/mediaplayer/state-resources) | Valid operations depend on state; release resources when no longer used | Guard prepared/error state and release on dismissal, Home and Activity stop |

Counts above are approximate web observations. The visual follow-up inspected the actual
Nuvio `HeroSection.kt` and `EpisodesSection.kt` at
`d14832e1c477cca2b5613b893e590fd5fcab83ee`, and Jellyfin's details presenter/layout and official
TV screenshots at `a1a27fa9d8109953f15bbf929c966d2a6ea38151`.
References: [Nuvio hero](https://github.com/NuvioMedia/NuvioTV/blob/d14832e1c477cca2b5613b893e590fd5fcab83ee/app/src/main/java/com/nuvio/tv/ui/screens/detail/HeroSection.kt),
[Nuvio episodes](https://github.com/NuvioMedia/NuvioTV/blob/d14832e1c477cca2b5613b893e590fd5fcab83ee/app/src/main/java/com/nuvio/tv/ui/screens/detail/EpisodesSection.kt),
[Jellyfin detail screenshot](https://github.com/jellyfin/jellyfin-androidtv/blob/a1a27fa9d8109953f15bbf929c966d2a6ea38151/fastlane/metadata/android/en-US/images/tvScreenshots/3.png).

The visual implementation uses one white primary Play action, secondary controls, rounded
season pills, separated episode title/group rows and a visible remote focus border.
These are independent native Views, not copied Compose/Leanback implementations.
No Nuvio/Stremio/Jellyfin source code or artwork is included. Test posters are original
synthetic artwork generated only for the fixture; real posters come from the configured source.

Platform MediaPlayer avoids an additional APK dependency and decodes HLS/MP4 on the test TV
emulator. It does not establish support for every codec, HDR, DRM, external subtitles or
firmware. Media3 remains an alternative if measured format requirements exceed the platform
engine; changing it should retain identity, routing, preferences and history.

## Data and navigation

Search uses `/v2/flow`; the two `/api/media/search` response formats are not called or mixed
into this parser. `FlowSearchState` guards request/sequence; `FlowCards` retains kind, poster,
year, source and explicit actions. Synopsis stays on the media card/detail, not in chat prose.

```text
Search: All | Movie | Series | Other
  → source section → selected media
    → detail: poster, name, year/type/source, synopsis, explicit actions
      → catalog: seasons / 25-episode pages / Play or Resume
        → source preference → resolve episode → Launcher Player or app
          → Back: detail keeps its page and focus
          → Home: release playback and close detail
Watch Next: source + episode identity → same router → fresh resolve → resume
```

Unknown metadata is omitted. Web links remain links. Metadata-only results have no fabricated
Play/download action. Opening detail does not resolve a stream or start a video. Seasons appear as an inline
horizontal selector above the paged episode list; changing season filters that list in place.
Series use two columns when the content area is at least 720dp wide, otherwise one column.
Cards are 64dp high with a 16dp column gap, 12dp row gap and 20dp below season tabs.
Remote navigation follows reading order; resizing preserves the focused episode identity.
Movies use a larger hero and Play/Resume. A single movie version has no episode/season list;
multiple versions appear under Versions & sources with their original IDs/groups. Series use
a compact hero and inline seasons/episodes. The launcher uses the server kind, not item count,
to distinguish Movie from Series. Movie playback does not show an Episodes control.
The backend catalog currently provides name/year/kind/seasons/episodes; poster and synopsis
come from the selected search item. Cast, ratings and artwork beyond these are not invented.

`PlaybackIdentity` contains source/episode/title identity and display metadata, never a signed
URL as its key. Private preferences record source player choices and up to 20 local progress
entries. Launcher playback is available to Watch Next without Notification Access. Existing
media-session capture remains separate and still needs that permission.

The native player supports play/pause, seek/timeline, embedded track selection, a shared
catalog episode screen, explicit external-player fallback and manual retry. Progress is saved
on pause/close and every 10 seconds while the player exists; a one-second UI tick is removed
when the session closes. No playback service or permanent background polling is added.
Auto-next/countdown, advanced subtitle styling/delay and server episode-history sync remain
unimplemented. External players are not assumed to report precise position or ended state.

## Capability boundaries

A catalog episode can use Launcher Player after the backend returns a supported descriptor.
An app-only Plex/YouTube/etc. action cannot be converted into a stream by the launcher.
Choosing Launcher Player for such an action explains the missing stream and offers opening
its app; it preserves the stored preference. Additional source resolvers belong in the backend.

The new catalog history stays local until the server contract can preserve source/title/episode
identity, progress and tombstones. Existing `/v2/watch/events` remains unchanged; uploading
these entries through its title-based deduplication would conflate episodes.
