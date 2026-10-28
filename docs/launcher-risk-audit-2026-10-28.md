# Launcher risk audit — 2026-10-28

## Scope and evidence

Reviewed the current working tree based on commit `c395c57`, including uncommitted changes.
Examined lifecycle ownership, background workers, networking/authentication, incoming payloads,
image decoding, persistence, updates and manifest exposure. This is a targeted source audit,
not a claim that every execution path or dependency is vulnerability-free.

Read-only runtime checks on MiTV_MOOR2 (`192.168.1.8`):

- Launcher PSS: 45,638 KB; RSS: 164,528 KB; 1 Activity, 132 Views, 0 WebViews.
- `/data`: 12 GB total, 3.6 GB used, 8 GB available.
- App external directory: 228 KB. Private app storage size was not accessible in this check.
- CPU accounting interval reported launcher 9.9%, media.swcodec 125%, logd 30%.
  These are historical accounting samples, not an isolated launcher benchmark.
- Installed package is not marked DEBUGGABLE; backup is disabled in the manifest.
- `make check` passed: Go vet/tests, contract drift check, release unit tests and lint.

No heap dump, controlled repeated-dialog test, malicious-server test, storage exhaustion,
or sustained performance benchmark was performed. Runtime APK and dirty source tree are
not proven byte-for-byte identical. No application code was changed during this audit.

P1 means fix soon; P2 means a concrete issue with a narrower trigger or lower impact.
All findings below are source-confirmed paths; their effects are not all reproduced on TV.

## P1 findings

### 1. Notification panel leaks its repository listener

`NotificationSheet.kt:78` installs an on-dismiss cleanup listener. `show()` then accesses
`dialogScope` at line 90; `CoroutineLifecycleExtensions.kt:28` installs another listener,
replacing the first. The repository singleton retains the listener added at line 108.
That closure captures the sheet/presenter and can retain the Activity after dismissal.
Repeated opening also leaves stale callbacks that can receive later notification updates.

Fix: give each dialog one cleanup owner that both cancels its scope and removes listeners.
Verify by repeatedly opening/closing notifications, then recreating Home; inspect retained
listeners and a heap dump after GC, not just total PSS.

### 2. Any LAN peer can replace pairing configuration while pairing is open

`LocalPairingServer.kt:37` binds a server on all interfaces. `/save` accepts URL and token
without a pairing nonce, client authentication or explicit TV-side approval. A LAN peer
can submit its own reachable server/token while the pairing screen is open. A successful
probe invokes `onPaired`, changing the configured Agent or Home Assistant connection.
Exposure is conditional on the pairing server being active; this is not an always-on API.

Fix: short-lived unpredictable pairing secret, TV confirmation of the selected endpoint,
expiration, origin/Host validation as defense in depth, and immediate shutdown on completion.

### 3. Pairing endpoint can exhaust memory or threads

`LocalPairingServer.kt:29` uses a cached thread pool; every accepted connection gets a task.
At line 90 a remote Content-Length becomes a CharArray allocation with no upper bound.
Header lines are also unbounded. OOM is not caught by `catch (Exception)`.
`stop()` closes only the listening socket, leaving accepted clients and queued callbacks.

Fix: cap header/body bytes before allocation, reject invalid lengths, bound simultaneous
clients, track/close accepted sockets, shut down workers and invalidate posted callbacks.
Test large declared lengths and slow clients in an isolated harness, not on the production TV.

### 4. WebSocket ignores HTTPS and does not authenticate the handshake response

`AgentWebSocketDataSource.kt:46-59` always opens a plain Socket and defaults to port 8788,
regardless of URL scheme. An HTTPS Agent URL therefore does not produce a TLS WebSocket;
with an explicit port the Bearer header is still written without TLS to that port.
The response check only tests a `101` status prefix, not Sec-WebSocket-Accept.
`DuinchPayloadRouter.handleActionPayload()` can execute incoming action messages.

Fix: use a WebSocket implementation that enforces ws/wss semantics, certificate/hostname
validation and protocol handshake validation. Reject unsupported schemes instead of downgrading.

## P2 findings

### 5. Automatic discovery can send the saved token to an unauthenticated LAN responder

`AgentClient.discover()` accepts the first UDP response, builds an HTTP URL from its source
address and stores it as `discoveredBaseUrl`. `ping()` then sends the existing Bearer token
to that address. Trigger: no explicitly saved URL or override file, with a token available.
`network_security.xml:3` permits cleartext globally. This also exposes tokens on ordinary
HTTP Agent/HA connections to a network observer able to observe that traffic.

Fix: discovery proposes an address; authenticate/pin the server before sending credentials.
Use TLS for remote connections and explicit LAN-only cleartext exceptions where required.

### 6. Pairing reports token validity from an unauthenticated health endpoint

`LocalPairingServer.verifyAgent()` treats HTTP 200 from `/health` as valid credentials.
The deployed Home Sentinel `/health` returns 200 without checking the Bearer token.
The same assumption in `AgentClient.ping()` can report ONLINE with the wrong token.

Fix: verify a read-only endpoint that actually requires authentication, and distinguish
server reachability from credential validity in the UI.

### 7. Response limits are missing or applied after allocation

`AgentClient.kt:318-334` reads unlimited error bodies and NDJSON lines. FlowClient reads
whole JSON/error bodies; its 65,536-character check occurs after `readLine()` has allocated
the line. `AgentRequestRunner.kt:82-93` appends Flow content/questions without the 32,768
character limit used by `askLegacy()`. A large or endless response can consume RAM and
make NativeMarkdown/card rebuilding expensive. CatalogClient also uses unbounded readText.

Fix: bound bytes while reading, total response length, event/card counts and request duration;
disconnect when a cap is exceeded. Apply the same budget to Flow and legacy paths.

### 8. Blocking persistence and image work occur on the UI thread

`AgentEventHub.kt:37` routes events on Dispatchers.Main. Notification routing synchronously
calls repository `add()`, which performs SQLite insert and pruning in `upsert()`.
PlaybackTracker registers callbacks on the main Handler and calls ArtworkStore, which can
scale/compress/write JPEGs and list/delete files there. WallpaperPlayer's constructor calls
`showFirst()` and synchronously decodes wallpapers, including trying multiple bad files.

Fix: move persistence, artwork compression and initial wallpaper decode to bounded workers;
return only final UI state to Main. Verify with a trace and StrictMode in a development build.

### 9. Image requests retain detached views and duplicate in-flight work

`ImageLoader.kt:27-58` uses a process-wide scope whose jobs capture targetView. Detaching a
view does not cancel its request. Rebinding/refreshing can start multiple downloads for the
same URL before the cache is populated; a 4 MB cache does not cap in-flight buffers/bitmaps.
The tag guard prevents stale painting, but does not release the View/Activity during the job.
Read timeouts are per read, not a total transfer deadline, so slow responses can prolong it.

Fix: cancel requests on detach/rebind, weakly reference targets, deduplicate by URL and size,
and bound concurrent transfers and total elapsed time. This is conditional retention, not
proof of a permanent leak in every image request.

### 10. Background queues and socket cancellation can accumulate work

WatchReporter uses a process-wide newSingleThreadExecutor with an unbounded task queue.
Each report serializes history then performs a network request; frequent session changes
while NAS is unavailable can enqueue stale events faster than they drain.
WebSocket flow cancellation only closes its Socket in finally; a blocking read may wait
up to 75 seconds before reaching that cleanup. stop/start can overlap old/new collectors.

Fix: coalesce watch events per item, bound pending work, and close sockets immediately from
the cancellation owner. Verify rapid reconnect and server-unavailable scenarios.

### 11. Secret encryption fallback is derived from public device properties

`AgentHomeConfig.getDeviceBoundKey()` derives AES material from BOARD, FINGERPRINT and the
package name with a predictable salt. If Android Keystore fails, encryption falls back to
this key. It provides no independent secret against someone who obtains that preferences
file plus device information. It does not by itself bypass Android's private storage sandbox.

Fix: fail explicitly when Keystore is unavailable, or use a randomly generated protected
fallback key with a documented threat model. Do not silently label this device-derived mode
as equivalent to a hardware-backed key.

### 12. Storage has count limits but incomplete byte/dimension budgets

ArtworkStore prunes to 24 files but constrains only bitmap width to 480; extreme aspect ratios
can retain huge height and costly encoding. It writes directly to final files, so process
termination can leave partial artwork. LocalWatchHistory caps at 20 entries, notifications
at 50, but individual metadata strings/parameters have no independent persisted byte budget.
These are conditional size/corruption risks, not evidence that TV storage is currently leaking.

Fix: cap both image dimensions/pixels, bytes per record and total storage, and use temporary
files plus atomic rename. Test failure during write and very large metadata.

### 13. Remote web actions accept arbitrary URI schemes

AgentActionExecutor's web.open/url.open branch constructs ACTION_VIEW directly from received
parameters without restricting scheme or destination. WebSocket action messages reach this
executor without a local user click. A compromised/mispaired endpoint can launch exported
deep links and interrupt the active app. This is not evidence of arbitrary shell execution.

Fix: allow only http/https for web actions; permit app deep links only through explicit,
validated action types/package targets. Require user confirmation for unsolicited actions.

## Existing protection and separate pending fix

- RemoteGestures now uses weak references and lifecycle cancellation in the local tree.
  That earlier fix was not newly deployed or heap-verified by this audit.
- ImageLoader limits each download to 5 MB, sizes decoded images and caps memory cache at 4 MB.
- Notifications cap at 50 entries; watch history at 20; artwork at 24 files.
- Config parsing has a byte limit. Release metadata is bounded; APK install streams data,
  verifies declared size and SHA-256, and abandons failed installer sessions.
- Token storage uses AES-GCM with Android Keystore on the normal path; backup is disabled.
- Accessibility, notification and dream services require their platform binding permissions.
- AgentOverlay releases speech, TTS and request workers on panel close; DuinchView removes
  animation callbacks on detach. No WebView JavaScript bridge exists in the current renderer.

## Suggested execution order

1. Repair notification dismissal ownership and add a lifecycle regression test.
2. Protect/bound the pairing endpoint and remove the health/token false positive.
3. Fix WebSocket TLS/identity and credential handling during discovery.
4. Add response/event/storage budgets and bounded queues.
5. Move disk/image work off Main; profile repeated panel and app transitions on the TV.

Audit only: no application changes, deployment, credential extraction, destructive storage
tests or unrelated service changes were performed in this pass.
