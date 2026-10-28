# Plan tích hợp Home Sentinel vào TV Launcher

Ngày rà soát: 28/10/2026. Trạng thái: đề xuất, chưa triển khai.

## 1. Cơ sở và phạm vi

Đối chiếu working tree hiện tại của `../home-sentinel`, không chỉ commit gần nhất.
Backend đang có nhiều thay đổi chưa commit; phải chốt phiên bản giao thức trước khi code.

Nguồn đối chiếu:
- `../home-sentinel/docs/testing/implementation-status.md`
- `../home-sentinel/docs/request-flow.md`
- `../home-sentinel/docs/catalog-api.md`
- `../home-sentinel/internal/requestflow/contracts.go`
- `../home-sentinel/internal/catalog/types.go`
- `../home-sentinel/internal/playback/resolver.go`
- `docs/modules/agent-protocol.md` và code `feature/agent`, `feature/watching`.

Memory không trả kết quả cho home-sentinel; kết luận dưới đây dựa trên checkout.
Tài liệu request-flow có phần placeholder cũ; đọc cùng mục live update và code hiện tại.

| Tính năng backend | Hiện trạng launcher | Kế hoạch |
| --- | --- | --- |
| `/v2/flow`: task stream, kết quả từng nguồn | Đang dùng `/v2/ask` | P1 |
| Plex/Jellyfin/*arr/YouTube, PhimAPI, internet, TMDb | Card theo protocol cũ | P1–P2 |
| Lọc nguồn và `context.title` | Chưa gửi context của flow mới | P2 |
| Chi tiết phim, nhóm phát, danh sách tập | Chưa có catalog client/UI | P3 |
| Resolve tập thành descriptor HLS/MP4 | Chưa có đường nhận descriptor catalog | P4 |
| Watch history, Plex On Deck | Đã có reporter, local fallback và dock | P5: hoàn thiện |
| Hủy lời gọi provider theo request context | Client đã có cancel/generation | P1: kiểm chứng |
| AI engine/login trên server | Settings launcher có URL/token | P6: hướng dẫn cấu hình |
| Automation, camera presence/audio cải tiến | Backend sở hữu xử lý | P6: tích hợp khi cần |

Chưa coi compound request, đọc/tóm tắt URL, câu trả lời tổng hợp có dẫn nguồn,
progress từng tập, next episode và toàn bộ 180 acceptance case là đã hoàn thành.
Một manifest HLS trả 200 chưa chứng minh phát được trên TV thật.

## 2. Quyết định thiết kế

- Launcher giữ vai trò thin client: search, cache, provider credential và resolve ở server.
- Dùng side sheet/panel trong cửa sổ hiện tại cho search/detail/episode/player chooser.
  Không thêm Activity chỉ để hiển thị những màn hình này.
- Giữ `/v2/ask` cho chat/control và tương thích server cũ; không thay toàn bộ một lần.
- Ưu tiên player ngoài để giữ launcher dưới 700 KB; không nhúng Media3 vào lát cắt đầu.
- Không dùng WebView để đọc website phim hoặc coi web link là stream phát được.
- Không tự điều khiển thiết bị chỉ vì một kết quả tìm kiếm chứa lệnh hay URL.

Tham khảo đã tìm trước triển khai:
- [AndroidX Media3](https://github.com/androidx/media): lifecycle/player/session;
  chỉ cân nhắc dependency nếu sau này chấp thuận thay đổi ngân sách APK.
- [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv): tham khảo luồng TV;
  cần đọc code cụ thể và kiểm tra license trước khi dùng lại bất kỳ đoạn code nào.
- Trước mỗi lát cắt, ghi nhận stars/forks, maintenance và license của reference được chọn.
  Chưa ghi số stars/forks chưa xác minh vào tài liệu này.

## 3. Các task triển khai

### P0 — Chốt contract và khả năng tương thích

- [x] Lưu fixture từ schema hiện tại: flow input/event, catalog title/episode, descriptor/error.
- [x] Cập nhật `docs/modules/agent-protocol.md` cho API mới; ghi phiên bản backend đã đối chiếu.
- [x] Phân biệt envelope flow mới với `type/payload` của `/v2/ask`; không dùng chung parser mù.
- [x] Chốt cách phát hiện flow hỗ trợ. Trước mắt fallback khi 404/405 hoặc `legacy_route`.
  Không fallback khi 401/403, timeout hoặc lỗi provider; tránh gửi lặp yêu cầu có tác dụng phụ.
- [x] Chốt auth của media proxy, URL tương đối, hết hạn và khả năng player ngoài truy cập.
  Không chuyển bearer token quản trị server cho app player bên ngoài.

Nghiệm thu: fixture thực từ backend parse được; server cũ vẫn dùng được chat/control.

### P1 — Client flow và kết quả đến từng phần

- [x] Thêm client `/v2/flow` dùng URL/token và HTTP infrastructure hiện có.
- [x] Xử lý `plan_ready`, `task_started`, `task_result`, `response_completed`.
- [x] State theo `request_id`, `task_id`, `sequence`; bỏ event cũ và callback sau cancel.
- [x] Hiện nguồn đang tìm, kết quả nguồn đã xong; nguồn lỗi không xóa kết quả nguồn khác.
- [x] Phân biệt empty, not_configured, auth_required, unreachable, timeout, not_implemented.
- [x] Hiện câu hỏi khi `needs_input`; không suy diễn server đã hỗ trợ hội thoại đa lượt.
- [x] Đóng panel/gửi câu mới hủy socket; xác minh backend hủy context tương ứng.
- [x] Giới hạn kích thước NDJSON, số kết quả và ảnh; không tải ảnh trên main thread.

Nghiệm thu: kết quả nhanh hiện trước, lỗi một nguồn không làm mất nguồn khác;
request cũ không ghi đè request mới; mất kết nối không hiển thị thành công giả.

### P2 — UI tìm kiếm và chọn nguồn bằng remote

- [x] Render các loại item server đang trả: media_title, answer, web_link.
- [x] Nhóm theo nguồn, giữ item ID/source/action; không ép web_link thành card phim có nút Play.
- [x] Gửi `context.sources` khi người dùng chọn nguồn và `context.title` từ phim đã chọn.
- [x] Nguồn chưa cấu hình chỉ hiện giải thích khi được yêu cầu; không quảng bá là đang sẵn sàng.
- [x] Web link mở bằng ứng dụng phù hợp; thiếu ứng dụng thì báo rõ và giữ focus.
- [x] Giữ vị trí cuộn/focus khi task khác cập nhật; Back quay về input/kết quả đúng cấp.
- [x] Tách kết quả metadata từ TMDb khỏi hành động phát hoặc yêu cầu tải phim.

Nghiệm thu: dùng D-pad hoàn toàn, truy vấn phim và truy vấn kỹ thuật có UI đúng loại;
chọn riêng Plex không âm thầm mở rộng sang nguồn khác.

### P3 — Chi tiết phim và danh sách tập

- [x] Nhận action `catalog_detail`; gọi `GET /v2/catalog/titles/{id}` với ID được encode.
- [x] Hiện tên/năm, nhóm phát, season nếu provider có và label tập gốc.
- [x] Giữ episode ID server cấp; không tự tạo ID hoặc season từ số thứ tự.
- [x] Phân trang 25 tập, không tạo 500 view cùng lúc; hỗ trợ tập đặc biệt/trùng label khác nhóm.
- [x] Back phục hồi kết quả/focus; mở phim mới hủy request detail cũ.
- [x] Hiện lỗi title biến mất/provider offline và nút thử lại có giới hạn.

Nghiệm thu: fixture Naruto 500 tập thao tác mượt; tập đặc biệt và nhóm khác không lẫn ID.

### P4 — Resolve tập và mở player

- [x] Chỉ gọi `POST /v2/catalog/episodes/{id}/playback` sau khi người dùng chọn tập.
- [x] Resolve URL tương đối theo origin server đã cấu hình; kiểm tra scheme/origin/path cho proxy.
- [x] Chỉ nhận MIME/hành động hỗ trợ; không chạy intent/shell tùy ý từ nội dung nguồn.
- [x] Kiểm tra player đã cài và intent handler; chooser ưu tiên cấu hình rồi VLC/system.
  Thêm package visibility declaration tối thiểu nếu target SDK yêu cầu.
- [x] Không có player: chỉ dẫn cài player và cho thử lại; không báo mở thành công.
- [x] Lưu lựa chọn player; giữ trạng thái phim/tập khi trở lại launcher.
- [x] Chọn tập B hủy resolve A; response A đến muộn không được mở player.
- [x] URL hết hạn: resolve lại từ episode ID khi người dùng mở lại.
  Chỉ tự retry khi có tín hiệu lỗi đáng tin, không mở player lặp vô hạn.

Nghiệm thu: HLS phát được qua player thật với auth bật, không lộ token server;
kiểm tra MP4/range, không có player, link hết hạn và server restart.

### P5 — Watch Next theo đúng tập và resume

- [x] Rà soát nguồn server/local hiện tại: server trả rỗng đang bỏ qua local history.
  Chốt merge/dedup/tombstone trước khi sửa để không hồi sinh item người dùng đã xóa.
- [x] Thêm định danh source/title/episode vào contract history bằng thay đổi backend phối hợp.
  Khóa package + title hiện tại chưa đủ phân biệt tập và nhóm phát.
- [x] Dock lưu episode identity/action resolve mới, không lưu URL proxy đã hết hạn.
- [x] Kiểm chứng metadata MediaSession của player ngoài có thể liên kết với tập đã mở.
  Không suy ra progress chỉ từ thao tác bấm Play hoặc thời gian app ở foreground.
- [x] Resume position cần player hỗ trợ seek/callback hoặc API history tương ứng.
  Chưa có tín hiệu thì chỉ hứa mở lại tập, không tuyên bố resume chính xác.
- [x] Next episode cần thứ tự provider và tín hiệu ended đáng tin; đưa sau resume.
- [x] Thêm chẩn đoán: quyền listener, nguồn dữ liệu, lần refresh, lý do bị lọc, lỗi upload.
  Không ghi token/stream URL nhạy cảm vào log.

Nghiệm thu: xem dở → Home có đúng tập → mở lại bằng stream mới;
progress/ended chỉ được đánh dấu đạt sau kiểm chứng với player hỗ trợ trên thiết bị thật.

### P6 — Tích hợp vận hành và tính năng ngoài media

- [ ] Hiện trạng thái kết nối, lỗi token và đường dẫn cấu hình Sentinel trong Agent settings.
- [ ] Hướng người dùng tới dashboard để chọn engine/login; không mang API key/OAuth vào launcher.
- [ ] Rà soát greeting/notification/action websocket mới với router hiện có trước khi thêm UI.
- [ ] Automation tắt TV và vision/audio chạy phía backend; launcher chỉ nhận trạng thái/thông báo
  khi có event contract. Không thêm vòng polling hoặc editor automation đầy đủ lên TV.
- [ ] Nếu thêm camera/presence widget: phải có contract unknown/stale/health trước;
  hiện backend chưa hoàn tất health propagation, không hiển thị unknown thành phòng vắng.

## 4. Thứ tự giao hàng và kiểm thử

1. P0 + P1 + P2: tìm kiếm nhiều nguồn chạy được, giữ nguyên chat/control cũ.
2. P3 + P4: tìm phim → chọn tập → phát trên player ngoài.
3. P5: mở lại đúng tập, sau đó progress/resume và next episode theo khả năng đã xác minh.
4. P6: settings/notification trước; camera/automation UI sau khi backend có contract đủ.

Mỗi lát cắt: unit/fixture test cho parser và state transitions; UI test cho D-pad/focus/cancel.
Chạy build/lint/test hiện có và kiểm tra APK dưới 700 KB; không thêm polling nền thường trực.
E2E cần TV thật cùng server có auth: search, 500 tập, HLS/MP4, Home/Back, offline,
401/403, server cũ, response đến muộn và player thiếu/không hỗ trợ.
Fixture, điện thoại Android và manifest HLS 200 không thay thế nghiệm thu trên TV.

## 5. Dependency cần giải quyết ở Home Sentinel

- Phiên bản schema và cách phát hiện capability của flow/catalog.
- Chính sách cấp quyền media session cho player ngoài và vòng đời/revoke session.
- History theo episode, report progress/ended, resume và next-episode contract.
- Conversation context/clarification đầy đủ nếu muốn follow-up vượt `context.title/sources`.
- Camera health/unknown/stale contract trước khi đưa occupancy lên launcher.

Các task dependency thuộc backend phải được bàn giao riêng; plan này không tự sửa repo backend.
