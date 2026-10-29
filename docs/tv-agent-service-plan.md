# Kế hoạch TV Agent Service

Ngày khảo sát: 29/10/2026. Trạng thái: đề xuất triển khai, chưa thay đổi runtime.

## Mục tiêu và quyết định

Agent chạy trong Android Service thuộc APK launcher; UI gửi lệnh và quan sát trạng thái.
Không cấu hình hoặc không bật Home Sentinel/NAS vẫn tìm, xem phim qua API Internet.
Home Sentinel là adapter tùy chọn cho suy luận phức tạp, NAS và yêu cầu tải.
Plex/Jellyfin/Sonarr/Radarr tiếp tục nằm phía Home Sentinel, không port lên TV.
Mất Internet thì chỉ xem được metadata/history đã cache, không hứa phát video offline.

V1 dùng cùng process, local Binder, service `exported=false`; không mở HTTP server localhost,
không socket giữa UI và service, không AIDL hay APK thứ hai. Tách domain bằng interface
để có thể đổi transport sau này. Service sở hữu runtime; runtime không giữ Activity/View.

Agent ở đây là bộ điều phối phiên, capability, tác vụ và adapter. V1 không nhúng LLM,
không tự suy diễn rằng có thể hiểu mọi câu hỏi hoặc tìm kiếm toàn bộ web khi server tắt.
Tìm theo tên và lệnh media đơn giản chạy bằng quy tắc; câu phức tạp cần server hoặc hỏi rõ.

## Khảo sát repository

Số star/fork là xấp xỉ tại thời điểm trang GitHub được đọc, có thể là bản cache.
Độ phổ biến dùng để chọn tham khảo; không thay thế đánh giá code và thiết bị thực tế.

| Repo | Tín hiệu cộng đồng / license | Học và áp dụng |
| --- | --- | --- |
| [CloudStream](https://github.com/recloudstream/cloudstream) | ~11.1k star / 1.2k fork; GPL-3.0 | MainAPI tách search, load detail, loadLinks; dùng cách chia trách nhiệm cho provider |
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) | ~2k / 622; GPL-3.0 | StreamingService tách search extractor khỏi stream extractor; lỗi nguồn được cô lập |
| [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv) | ~4.6k / 985; GPL-2.0 | Tham khảo client TV, trạng thái kết nối, media navigation; không đưa Jellyfin trực tiếp vào V1 |
| [Android architecture samples](https://github.com/android/architecture-samples) | ~45.9k / 11.9k; Apache-2.0 | Repository boundary, coroutine, state và dependency injection bằng constructor |
| [AndroidX Media](https://github.com/androidx/media) | ~3.1k / 1k; Apache-2.0 | Mốc tham khảo playback và media session nếu player nền tảng không đủ |
| [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) | GPL-3.0; Kotlin/Compose/Media3 | Tham khảo luồng detail → chọn nguồn → phát, tiếp nối nghiên cứu player hiện có |

Đã đọc trực tiếp contract
[CloudStream MainAPI](https://github.com/recloudstream/cloudstream/blob/master/library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt)
và [NewPipe StreamingService](https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/main/java/org/schabi/newpipe/extractor/StreamingService.java).
Đây là các tham khảo sát bài toán hơn việc đưa framework agent tổng quát lên TV.
Không nhúng toàn bộ app, extension engine hay sao chép code GPL vào launcher MIT trong plan này.
Adapter Kotlin sẽ viết theo API/hợp đồng của dự án; thư viện mới cần đánh giá license và size.

Trang releases Jellyfin hiển thị v0.19.10 là latest. Trang releases CloudStream/NewPipe có
kết quả đọc không đủ rõ để khẳng định ngày phát hành mới nhất. Trước khi dùng dependency
phải pin revision/version và kiểm tra release, CI, issue liên quan tại thời điểm triển khai.
Nghiên cứu này là đọc repo/contract, chưa build hay benchmark các repo tham khảo.

## Hiện trạng đã đối chiếu code

- `AgentRequestRunner`: UI đang sở hữu executor, session, cancel và callback;
  gọi `/v2/flow`, fallback legacy `/v2/ask`. Không chỉ có `/v2/ask`.
- `AgentEventHub`: singleton coroutine tạo `AgentWebSocketDataSource`, nhận pushed events.
- `AgentWebSocketDataSource`: tự handshake/frame bằng raw Socket, default port 8788;
  chưa có TLS socket theo scheme, handshake mới kiểm tra status 101.
- `CatalogClient`: detail và resolve playback đều cần Agent URL/token.
- Backend `internal/mediasearch/phimapi.go`: search `/v1/api/tim-kiem`.
- Backend `internal/catalog`: detail `/phim/{slug}`, stable IDs và fresh resolve.
- Backend playback có proxy/session/HLS; URL qua proxy NAS không dùng được khi NAS tắt.
- Player hiện dùng VideoView/MediaPlayer; episode identity và Watch Next đã có.
- Build minSdk 26, targetSdk 34; tài liệu quy định release APK dưới 5 MiB.

## Ranh giới đề xuất

```text
Launcher / Agent panel / Detail / Watch Next
                 |
          AgentServiceClient
                 | local Binder + observable state
           TvAgentService
                 |
           AgentRuntime
            /         \
 InternetMedia      HomeSentinelAdapter
 PhimApiProvider    HTTP flow/catalog + WebSocket events
       |                    |
 Public API/CDN       Home Sentinel → NAS/*arr
```

Media identity và nguồn xử lý độc lập: `sourceId/titleId/episodeId` định danh nội dung;
`executionRoute` là direct hoặc sentinel. Không dùng stream URL làm ID.
Không tự đổi route sau khi đã gửi hành động có tác dụng phụ.

Contract đề xuất:

- `AgentCommand`: search, loadTitle, listEpisodes, resolvePlayback, ask, cancel.
- `AgentSnapshot`: sessionId, requestId, capabilities, results, task state, connection state.
- `AgentEffect`: hành động UI cần consume một lần, có effectId để chống thực thi lại.
- `MediaProvider`: search(query, page), getTitle(id), resolvePlayback(episodeId).
- `HomeSentinelGateway`: flow/ask/catalog, events và trạng thái xác thực/kết nối.
- `AgentTaskStore`: snapshot giới hạn dung lượng, local history; không lưu URL ký làm identity.

State dùng StateFlow; progress/events không phụ thuộc một callback singleton.
Request có ID, sequence và cancel; UI bind lại nhận snapshot, không gửi lại request tự động.
Tác vụ: accepted/running/succeeded/failed/cancelled/unknown. Process chết giữa lệnh ghi
phải thành unknown và reconcile, không replay. Service giữ policy và task execution;
UI chỉ mở app/player khi lifecycle cho phép, không tự bật Activity từ background.

## Routing và capability

| Điều kiện | Hành vi |
| --- | --- |
| Không cấu hình Sentinel | Không mở socket, không discovery/poll vô hạn; API phim chạy trực tiếp |
| Sentinel offline, Internet còn | Direct search/detail/resolve tiếp tục; NAS/request tải unavailable |
| Sentinel ready | Thêm nguồn NAS và agent nâng cao; direct API vẫn không phải chờ server |
| Chỉ socket lỗi | Phân biệt events disconnected với HTTP/capability; không kết luận NAS đã tắt |
| Internet lỗi | Cache có nhãn stale, retry do người dùng; không giả kết quả rỗng thành thành công |
| Provider lỗi/schema đổi | Báo lỗi đúng nguồn, nguồn khác vẫn trả kết quả |

Một request chỉ có một chủ sở hữu cho mỗi provider; không gọi PhimAPI cả direct và server
rồi nhân đôi kết quả. Giữ lựa chọn nguồn explicit; gộp chỉ khi identity đủ chắc chắn.
V1 direct-first cho PhimAPI; Sentinel xử lý phần NAS và yêu cầu nâng cao.

## Vòng đời Android và socket

[Bound service](https://developer.android.com/develop/background-work/services)
chỉ tồn tại khi còn client bind nếu không được start riêng. Service không phải thread riêng
và cùng process vẫn chết cùng launcher; IO phải ở dispatcher riêng, task có SupervisorJob.

V1 bảo đảm agent khi Home, panel, detail hoặc player đang dùng service. Bind/unbind có
reference ownership rõ ràng; mở nhiều UI chỉ có một runtime/socket. Mất mạng hủy kết nối
cũ; reconnect exponential backoff + jitter, không retry auth thất bại liên tục.

Yêu cầu nhận push khi đang xem ứng dụng khác là cổng kiểm chứng riêng trước phát hành:
đo hành vi existing accessibility binding trên TV nhưng không xem đó là bảo đảm.
Nếu cần foreground service, phải xác định đúng loại, notification và điều kiện start theo
[quy định Android](https://developer.android.com/develop/background-work/services/fgs/service-types).
Không gán mediaPlayback chỉ để giữ socket; không hứa always-on bằng START_STICKY.
Nếu nền không được hỗ trợ thì công bố trạng thái paused, khôi phục khi quay lại Home.
WorkManager không dùng làm kết nối WebSocket liên tục.

Transport phải xử lý wss/TLS, ping/pong, fragmentation, close và cancellation.
Khảo sát client WebSocket trưởng thành như OkHttp, đo APK R8 trước khi chọn dependency;
không mặc định thêm vì giới hạn size. Nếu vượt 5 MiB, báo số đo và đánh giá packaging; không giữ raw socket thiếu protocol chỉ để đạt size.

## Các bước triển khai và điều kiện hoàn tất

1. **P0 — Chốt contract, baseline và spike vòng đời.**
   Ghi baseline APK/RAM/thread/socket; kiểm tra API PhimAPI trực tiếp từ TV, khả năng CDN,
   header/redirect/subtitle cần thiết. Thử bind → ra app khác → standby → resume → kill.
   Đầu ra: route/capability contract, số đo và chính sách foreground/background khả thi.

2. **P1 — Đưa runtime vào service, giữ nguyên backend behavior.**
   Tách session/task từ AgentRequestRunner; chuyển AgentEventHub/socket/router vào runtime;
   UI dùng AgentServiceClient. Giữ nguyên endpoint/contract và component manifest hiện có.
   Hoàn tất khi Home/panel bind lại không nhân socket, không nhân task hoặc mất kết quả.

3. **P2 — PhimAPI direct end-to-end.**
   Viết provider search/detail/episodes/fresh resolve bằng Kotlin. Port semantics từ backend,
   fixture parity cho IDs/order/errors; không nhúng Go server. Stream descriptor hỗ trợ URL,
   headers cần thiết và loại media; direct URL không tham chiếu proxy NAS.
   Metadata TTL/cache giới hạn; fresh resolve mỗi lần Play/Resume; embed-only không giả Play.
   Hoàn tất khi NAS tắt vẫn search → detail → chọn tập → phát → resume được trên TV.

4. **P3 — Capability và Home Sentinel tùy chọn.**
   Kết nối flow/catalog/socket qua gateway; NAS/*arr giữ ở server; local rules cho media đơn giản.
   Offline/401/timeout không chặn direct flow. Chặn duplicate action và explicit unknown.
   Cấu hình Internet source tách khỏi Agent URL/token; không cần token NAS để gọi public API.

5. **P4 — Nối player/history/UI và kiểm chứng phát hành.**
   Detail, episode pagination, playback resolver, source preference và Watch Next đi qua service.
   Player vẫn thuộc playback layer; service resolve và điều phối, không giữ View/MediaPlayer UI.
   Giữ phong cách hiện tại, DPAD/focus và panel inline; không thêm TTS/focus theft tự động.
   Hoàn tất ma trận dưới, build/check/size pass và báo rõ bằng chứng trên TV.

Mỗi bước là commit/PR nhỏ với điều kiện hoàn tất riêng. Không nâng player sang Media3,
không tách APK/process và không thêm nguồn thứ hai trong cùng đợt chuyển kiến trúc.
Nếu P0 chứng minh player hiện tại không đáp ứng direct stream thì bổ sung hạng mục riêng.

## Ma trận kiểm chứng bắt buộc

| Tình huống | Kết quả phải quan sát được |
| --- | --- |
| Chưa từng cấu hình server | Search/detail/episode/play direct hoạt động, không đòi token |
| NAS thực sự tắt, Internet còn | Full direct flow, không timeout NAS trước khi hiện phim |
| NAS bật rồi tắt giữa phiên | UI còn dùng được, socket backoff, task ghi không replay |
| 401 / lỗi HTTP / chỉ WS hỏng | Trạng thái riêng biệt; direct API không bị khóa |
| Home ↔ panel ↔ app khác | Không duplicate socket/listener; focus và kết quả được giữ |
| Force-stop/process kill/reboot | Bind mới khôi phục state hợp lệ; không tự phát/gửi lệnh cũ |
| API rỗng / JSON lỗi / response quá lớn | Empty/error phân biệt; response limit và timeout |
| URL hết hạn / redirect / embed-only | Fresh resolve, hành động phù hợp, retry có giới hạn |
| Watch Next / Resume | Stable episode ID, đúng tập/position, resolve không qua NAS |
| Standby/resume và mạng đổi | Kết nối cũ đóng; state resync; không loop ăn CPU |

Unit/contract tests cho routing, parity parser, cancellation, stale request và dedup.
Instrumentation cho binding lifecycle và UI reattach; physical remote + screenshot cho TV.
Chạy `make check launcher` và size gate theo repo. Ghi p50/p95 search/cold start, PSS,
CPU idle, socket/thread count trước/sau; chốt ngân sách từ baseline P0.
Không xem test giả lập hoặc build pass là bằng chứng CDN/player/remote thật đã hoạt động.

## Ngoài phạm vi V1

LLM/STT luôn chạy trên TV, web crawler tổng quát, tải phim nền, extension APK/script động,
server Plex/*arr trên TV, sync history xuyên thiết bị và bảo đảm socket 24/7 trên mọi firmware.
Nguồn Internet đầu tiên đề xuất là PhimAPI vì backend đã có contract và fixture;
tính khả dụng trực tiếp trên TV còn phải xác nhận ở P0.
