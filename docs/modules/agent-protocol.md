# Agent protocol: launcher ↔ NAS server (v2)

The launcher is a thin client. Everything that needs knowledge (search, suggestions, what is on the
NAS, requests through the *arr stack) lives on the NAS server. This file is the contract: the server
can be built against it without the launcher source. The launcher implements it in
`apps/launcher/.../feature/agent` (`AgentClient`, `AgentActionClient`, `AgentWebSocketDataSource`,
`DuinchPayloadRouter`, `AgentCards`).

## 1. Transport and auth

| Item | Value |
|---|---|
| Base URL | `http://<nas>:8788` (launcher setting `agent_url`, else UDP discovery) |
| Discovery | UDP broadcast `TV_AGENT_DISCOVER` to port `8789`; reply `{"port":8788}` from the server |
| Auth | `Authorization: Bearer <token>` on every request, including the websocket handshake |
| Encoding | UTF-8 JSON; field names are `snake_case` |
| Unknown fields | Both sides ignore them. Add fields freely; never change the meaning of one |

`401` on any endpoint means the token is wrong (the launcher shows "refused").

## 2. Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | `200` when up. The launcher shows online/offline from it |
| POST | `/v2/flow` | Search tasks and source results (see 10) |
| GET | `/v2/catalog/titles/{id}` | Title metadata and episode identities |
| POST | `/v2/catalog/episodes/{id}/playback` | Fresh media descriptor after explicit Play |
| POST | `/v2/ask` | The user asked something (voice or typed). Streams events |
| POST | `/v2/action` | The user pressed a card button that needs the server. Streams events |
| WS | `/ws/duinch` | Server pushes notifications, greetings and actions |
| POST | `/v2/watch/events` | The TV reports what its apps are playing (see 9) |
| GET | `/v2/watch/continue` | The "continue watching" list for Home (see 9) |

### 2.1 `POST /v2/ask`

Request body:

```json
{
  "request_id": "req-1730000000000",
  "session_id": "6f1c…",
  "utterance_id": "",
  "text": "tìm phim the matrix",
  "alternatives": [],
  "locale": "vi-VN",
  "input_type": "voice"
}
```

`input_type` is `voice`, `text` or `click`. `session_id` is stable for one open assistant panel, so the
server can keep short conversation context.

### 2.2 `POST /v2/action`

Request body:

```json
{
  "request_id": "act-1730000000000",
  "session_id": "card_action",
  "action_id": "media.request",
  "parameters": {"tmdb_id": 603, "media_type": "movie", "title": "The Matrix", "card_id": "tmdb:603"},
  "locale": "vi-VN"
}
```

`parameters` is copied from the card action. The launcher adds `card_id` when the card has an `id`.

### 2.3 Response stream (both endpoints)

`200` with a body of newline-delimited JSON (one event per line, flush after each line; any content
type). Each line is `{"type": <name>, "payload": {…}}`. Order is free except `accepted` first and
`completed` or `error` last.

| `type` | `payload` | Launcher behaviour |
|---|---|---|
| `accepted` | `request_id`, `session_id` | none |
| `progress` | `stage` (string) | none yet |
| `presentation` | see 3 | shows `speech_text` (Markdown) and the cards; may be sent several times |
| `client_action` | `step_id`, `action_type`, `parameters` | runs it on the TV (see 5) |
| `completed` | `request_id`, `status` (`succeeded`\|`failed`\|`cancelled`) | ends the request |
| `error` | `code`, `message` | shows an error. `code: "unauthorized"` shows "refused" |

Non-`200` status: the body text is shown as the error. The read timeout is 330 s, so a slow search can
stream `progress` lines to keep working.

## 3. `presentation`

```json
{
  "speech_text": "Có 2 kết quả. **The Matrix** đã có trên NAS.",
  "mood": "happy",
  "quick_chips": ["Tìm phim khác", "Phim mới tải"],
  "cards": [ … ],
  "client_actions": []
}
```

- `speech_text`: Markdown subset (`**bold**`, `*italic*`, lists, headings, `[text](https://…)`). It is also
  read aloud when "voice replies" is on. Keep it short.
- `mood`: one of `happy`, `excited`, `thinking`, `love`, `surprised`, `silly`, `sad`, `wink`, `idle`.
- `quick_chips`: reserved. The launcher does not render them yet.
- `cards`: shown under the text, in order. A new `presentation` replaces the cards of the previous one.
- Legacy: `<div class="card" data-status data-title data-type data-year data-link><img src=…></div>`
  blocks inside `speech_text` still render as cards. New servers should use `cards`.

### 3.1 Card

```json
{
  "id": "tmdb:603",
  "title": "The Matrix",
  "year": "1999",
  "media_type": "movie",
  "poster_url": "http://nas:8788/img/603.jpg",
  "status": "requestable",
  "link": "plex://server/…",
  "actions": [
    {"id": "request", "label": "Thêm vào NAS", "type": "server",
     "action_id": "media.request", "confirm": "Thêm phim \"The Matrix\" vào NAS?",
     "parameters": {"tmdb_id": 603, "media_type": "movie", "title": "The Matrix"}},
    {"id": "open", "label": "Mở trong Plex", "type": "client",
     "action_type": "plex.open", "parameters": {"uri": "plex://server/…"}}
  ]
}
```

| Field | Required | Notes |
|---|---|---|
| `title` | yes | a card without it is dropped |
| `id` | no | stable id (`tmdb:603`, `tvdb:81189`); echoed back as `parameters.card_id` |
| `media_type` | no | `movie` or `series` |
| `year`, `poster_url`, `link` | no | `poster_url` must be reachable from the TV |
| `status` | no | badge, see below |
| `actions` | no | buttons, see below. Empty or missing: the launcher derives one from `status` |

`status` badges: `plex` or `available` = PLEX; `downloading` = ĐANG TẢI; `requested` = ĐÃ YÊU CẦU;
anything else = THƯ VIỆN (not on the NAS, can be requested).

Action without `actions` (legacy cards): `plex`/`available` opens `link` in Plex; `downloading` and
`requested` only show a status dialog; others ask to add the media through `media.request` with
`title`, `year`, `media_type`.

### 3.2 Card action

| Field | Notes |
|---|---|
| `label` | button text (required) |
| `type` | `server` (POST `/v2/action`) or `client` (runs on the TV). Default: `server` when `action_id` is set, else `client` |
| `action_id` | server actions: the id the server routes on |
| `action_type`, `parameters` | client actions: see 5 |
| `confirm` | optional; if set the launcher asks "Hủy / `label`" with this text first |

One action runs on OK. Two or more open a chooser with one button per action. No actions: status dialog.

Result of a server action: the server answers with the stream of 2.3. The launcher shows each
`presentation.speech_text` as a toast ("Đã gửi yêu cầu tới NAS." when it is empty). Progress after that (queued,
downloading, ready) must come as websocket notifications (4), because the dialog is already closed.

## 4. WebSocket `/ws/duinch`

Standard RFC 6455, text frames, the server must answer the `Authorization` header like HTTP. The
launcher reconnects with 4 s backoff doubling to 60 s and never sends frames except pongs. Frames over
32 KiB are skipped; frames of 64 KiB or more close the connection. Send a ping about every 60 s (the
read timeout is 75 s).

The connection is opened when the launcher Home is shown and `agent_url` is known, and stays up while
the launcher process lives. Every text frame is one JSON object with a `type`.

### 4.1 `notification`

Stored in the TV database (last 50 kept), counted in the unread badge on Home, listed in the
notifications sheet. Sending the same `id` again replaces it, so a request can move from "queued" to
"ready" in place.

```json
{
  "type": "notification",
  "notification": {
    "id": "req-603",
    "source": "overseerr",
    "title": "The Matrix",
    "message": "Đã tải xong, xem ngay trên Plex",
    "severity": "info",
    "timestamp": 1730000000000,
    "image_url": "http://nas:8788/img/603.jpg",
    "action": {"action_type": "plex.open", "parameters": {"uri": "plex://server/…"}}
  }
}
```

`severity`: `info` (default), `warning`/`error`/`alert` (the bot looks surprised) or `suggestion` (the bot winks); other values behave like `info`. `source` should name the system (`overseerr`, `radarr`,
`sonarr`, `plex`, `nas`). `action` is optional and uses the client actions of 5. If Home is visible the
bot also says `message` (or `title`); otherwise a banner shows over the running app.

### 4.2 `greeting`

Anything with another `type` (or none) and a non-empty `text` is a greeting for the bot on Home:
`{"type":"greeting","text":"Chào buổi tối!","mood":"happy"}`. Shown as a banner when Home is not visible.

### 4.3 `action`

Run something on the TV without user input:
`{"type":"action","step_id":"s1","action_type":"plex.open","parameters":{…},"feedback_text":"Mở Plex nhé"}`.
`feedback_text` is optional and spoken by the bot.

## 5. Client actions

Used by `client_action` events, card actions of `type: "client"`, notification `action` and websocket `action`.

| `action_type` | `parameters` | Effect |
|---|---|---|
| `plex.open` | `uri` (deep link, optional) | opens Plex, at `uri` when given |
| `youtube.launch` | `video_id`, or `query` for a search | opens YouTube |
| `app.open`, `media.open`, `play` | `package_name` and/or `uri` | opens an app or a deep link |

Unknown `action_type` with neither `package_name` nor `uri` fails with `UNSUPPORTED_ACTION` (nothing is
shown). Never send shell commands; the set above is closed on purpose.

## 6. Server-side mapping to the *arr stack (recommended)

| User intent | Server does | Card `status` |
|---|---|---|
| "tìm phim X" | search Plex/Jellyfin library first, then Overseerr/Jellyseerr `GET /search` | `plex` if in library, `requested`/`downloading` if known to Seerr/Radarr/Sonarr, else `requestable` |
| `media.request` action | Overseerr `POST /request` (`mediaType`, `mediaId`); without Seerr: Radarr `POST /movie` or Sonarr `POST /series` with `addOptions.searchForMovie`/`searchForMissingEpisodes` | answer with `presentation`, then `notification` with the same `id` as it progresses |
| download finished | Radarr/Sonarr/Seerr webhook → `notification` with `action` `plex.open` | n/a |
| "gợi ý cho tôi" | recommendations from watch history or Seerr discover | cards with `status` as above |

Use a stable card `id` (`tmdb:<id>` / `tvdb:<id>`) and reuse it as the notification `id`; the server
should treat a repeated `media.request` for the same id as idempotent.

## 7. Errors

| Where | Condition | Launcher shows |
|---|---|---|
| any HTTP call | no token / no URL | "token missing" / "offline" |
| any HTTP call | `401` | "refused" |
| any HTTP call | other `>= 400` | the response body |
| stream | `error` event | `message` (code `unauthorized` = refused) |
| stream | connection drops | "Lỗi kết nối: …" for card actions, error text for asks |

## 8. Versioning

The path carries the major version (`/v2/…`). Additive changes (new event types, card fields, client
actions) need no version bump. The launcher ignores event types, card fields and websocket `type`s it does
not know, and the server should do the same for the launcher's requests.

## 9. Watch history

The TV launcher aggregates continue-watching items across Android TV's system provider
(`TvContract.WatchNextPrograms` with `READ_TV_LISTINGS`), local media sessions captured on device,
and the agent server (`GET /v2/watch/continue`). The server aggregates reported playback events
and external sources such as Plex On Deck to provide cross-device history.

### 9.1 `POST /v2/watch/events`

Sent when a session starts, pauses, stops or ends, and every 60 s while it plays. Answer `204`.

```json
{"events": [{
  "package_name": "com.google.android.youtube.tv",
  "app_label": "YouTube",
  "title": "Big Buck Bunny 60fps 4K",
  "subtitle": "Blender",
  "media_id": "aqz-KE-bpKQ",
  "position_ms": 3985,
  "duration_ms": 634000,
  "state": "playing",
  "artwork_url": "https://i.ytimg.com/vi/aqz-KE-bpKQ/hqdefault.jpg",
  "at": 1793091600000
}]}
```

| Field | Notes |
|---|---|
| `state` | `playing`, `paused`, `stopped` (the session went away) |
| `media_id`, `subtitle`, `artwork_url`, `duration_ms` | optional; whatever the app published |
| `at` | the TV's clock, epoch milliseconds |

The same `package_name` + `title` is one entry: the newest event replaces the older one.

### 9.2 `GET /v2/watch/continue`

```json
{"items": [{
  "id": "plex:12345",
  "title": "The Expanse",
  "subtitle": "S2 · E5 · Home",
  "app_label": "Plex",
  "package_name": "com.plexapp.android",
  "progress": 0.42,
  "poster_url": "http://<nas>:32400/library/metadata/12345/thumb?X-Plex-Token=…",
  "action": {"action_type": "plex.open", "parameters": {"uri": "plex://server/play?key=12345"}}
}]}
```

Newest first, at most 8. Entries between 3 % and 95 % watched (or without a duration) are kept for
14 days. `action` is a client action (see 5); the launcher falls back to opening `package_name`.


## 10. Flow search and catalog playback

Launcher sends `POST /v2/flow` with `{text, context: {title, sources}}` using the configured
Agent bearer. Context fields are optional. The response is NDJSON with top-level `version`,
`request_id`, `sequence`, `type` and the event-specific `plan`, `task` or `result`.
Events are `plan_ready`, `task_started`, `task_result`, `response_completed`.
The client rejects duplicate/out-of-order events and unexpected request IDs. EOF before a
terminal event is an error. Only 404/405 or JSON `status: legacy_route` invokes `/v2/ask`.

Results preserve source/task/item identity, original kind, poster, year, season/episode and
explicit actions. Search projects those results into All/Movie/Series/Other, each grouped by
source. No fallback Play/download action is inferred for metadata or unknown actions.
Opening media shows detail before resolving playback; web links remain links.

`GET /v2/catalog/titles/{encoded id}` returns `{title: {id, source_id, name, kind, year,
seasons, episodes}}`. Episode fields are `id`, `title_id`, `label`, `group`, `order`,
`season_id`, `playable`. IDs are opaque. The client pages 25 episodes and can select a season.
Poster/synopsis can come from the selected search result; absent metadata is omitted.

Only explicit Play calls `POST /v2/catalog/episodes/{encoded id}/playback` with `{}`.
The response has `episode` and `playback: {url, mime_type, title, preferred_handler,
preferred_players, expires_at}`. Resolve relative URLs against the configured server;
accept HTTP(S) without embedded credentials. Media proxy capability URLs must be usable
without passing the administrative Agent bearer to another application. Stream URLs are
not persisted as the identity of a catalog history entry.

Per-source player preference routes to Launcher Player or an external application.
App-only actions without a resolvable episode stream remain app actions; choosing the native
player cannot synthesize a stream. Unsupported playback offers an explicit alternate route
without changing the saved preference. Local native progress is available to Watch Next
without Notification Access and resolves a fresh URL when reopened.

`/api/media/search` has a different event envelope (`source_results`, etc.); its JSON form
wraps `content_types` inside `result`. Launcher does not call this endpoint in the current
implementation. `Snapshot` in the Go media event is internal (`json:"-"`), not a wire field.
The schemas above were compared with checkout `f088227` plus uncommitted search changes;
production-provider compatibility still requires a pinned backend/server E2E run.

New catalog progress is local only. Section 9 remains the legacy external-media reporting
contract; its package+title deduplication is not sufficient for catalog episode sync.
See `docs/player-architecture.md` and `docs/testing/launcher-media-validation.md`.
