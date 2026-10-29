# Plan tích hợp Home Sentinel vào TV Launcher

Ngày rà soát: 29/10/2026. Trạng thái: có implementation ban đầu, chưa nghiệm thu đầy đủ.

## Tiến độ implementation trong working tree

- Đã có code: state flow nhiều nguồn, metadata, 4 tab/section, chọn nguồn tìm tiếp,
  media detail trước Play, season/phân trang tập, router/preference theo source,
  player tích hợp, history/resume local và mở lại qua Watch Next.
- Native test trên emulator TV đã chạy với fixture HTTP có auth: search/detail/focus,
  500 tập, EOF lỗi, decode/seek HLS/MP4, history/resume và Home release.
- Chưa nghiệm thu production Sentinel/TV thật; chưa đồng bộ history theo episode lên server,
  auto-next, DRM, phụ đề nâng cao hoặc toàn bộ matrix provider/permission.
- Các checkbox bên dưới vẫn là tiêu chí nghiệm thu toàn lát cắt, không tick đồng loạt
  theo việc code/build hoặc fixture đã đạt. Bằng chứng và giới hạn tại
  `docs/testing/launcher-media-validation.md`; kiến trúc tại `docs/player-architecture.md`.

## 1. Đích cuối và bằng chứng hiện có

- Search có đúng 4 tab **All, Movie, Series, Other**. Trong mỗi tab, mỗi source có
  một section chứa danh sách kết quả của nguồn đó.
- Phát trực tiếp bằng **Launcher Player**; Launcher settings cho chọn ưu tiên
  **Launcher Player / Ứng dụng tương ứng** riêng từng nguồn.
- Search, chi tiết/tập và Watch Next dùng chung định danh và đường mở phát.
- Xem dở → Home → mở lại đúng tập qua stream mới, khôi phục vị trí khi có dữ liệu đáng tin.
- Giữ chat/control và tương thích `/v2/ask`; không thêm Activity chỉ để hiển thị panel.

Review launcher `c395c57`: `make check launcher` thành công; báo cáo 249 unit test đạt;
APK 574.142 bytes. Chưa có bằng chứng nghiệm thu toàn luồng trên TV/server có auth.
Các checkbox của bản plan trước đã được thay bằng backlog dưới đây: có class/parser
không đồng nghĩa toàn bộ hành vi đã hoàn thành.

| Đã có trong code | Phần còn thiếu đã xác nhận |
| --- | --- |
| Flow client/parser, fallback legacy | State nhiều nguồn, metadata, sequence, EOF và source context |
| Catalog client và episode sheet | Chỉ hiện 25 tập đầu; chưa chuyển trang, hủy request và chặn resolve cũ |
| Mở player ngoài | Launcher Player, routing theo nguồn, capability và preference |
| Merge TV Provider/server/local, dock Watch Next | Identity catalog, resolve mới theo tập, resume và kiểm thử thiết bị |
| Agent settings URL/token | Trạng thái vận hành và hướng dẫn dashboard |

Không coi compound request, tóm tắt URL, hội thoại đa lượt, progress/ended,
next episode hoặc toàn bộ acceptance case của backend là đã hoàn thành.

## 2. Thứ tự làm và các quyết định tránh viết lại

| Thứ tự | Lát cắt | Phụ thuộc | Đầu ra dùng tiếp |
| --- | --- | --- | --- |
| S0 | Chốt UX, contract và engine | Không | Focus map, fixture, identity, capability, ngân sách |
| S1 | Model/state/request và routing chung | S0 | Nền chung cho mọi entry point |
| S2 | Một nguồn phát trực tiếp tới Watch Next local | S1 | Đường phát production đầu tiên, có lifecycle/resume |
| S3 | Search 4 tab và chi tiết/tập hoàn chỉnh | S1; nối player S2 | UI đích cuối, dùng routing đã có |
| S4 | Mở rộng nguồn và đồng bộ history | S2 + S3 | Nhiều nguồn, history server/local/provider nhất quán |
| S5 | Vận hành và nghiệm thu toàn luồng | Kiểm tra theo từng lát cắt, chốt sau S4 | Bằng chứng release và giới hạn hỗ trợ |

S0 nghiên cứu trước khi chọn engine hoặc viết player UI production. Spike engine là phép đo
nhỏ trên nhánh thử nghiệm, không phải một player tạm phải giao hàng rồi thay lại.
S1 chỉ thêm model/chức năng đã được S2/S3 tiêu thụ; không dựng framework tổng quát.
S3 search state/UI không phụ thuộc việc player hỗ trợ hết codec; có thể làm khi nền S1 ổn định.
Đường găng là S0 → S1 → S2 → S4; không đợi hoàn tất camera/automation hoặc mọi nguồn.

- Không hoàn thiện một chooser player ngoài dùng riêng rồi thay bằng Launcher Player.
  Bọc đường mở app hiện có thành một lựa chọn của routing chung ngay từ S1.
- Không sửa flat card list để giao hàng rồi xây lại tab/source section.
  Model cuối cùng là request → task/source → item; tab chỉ là phép chiếu của model đó.
- Không thêm source/episode ID muộn ở Watch Next. Giữ identity xuyên suốt ngay từ contract S0.
- Không xây player xong mới thêm history. S2 ghi progress local từ engine và mở lại đúng tập;
  S4 bổ sung đồng bộ server và merge, không thay identity.
- Không triển khai ba network client chỉ vì backend có ba dạng response.
  Chọn `/v2/flow` cho search trong lượt này; `/api/media/search` là contract đối chiếu,
  chỉ thêm adapter/network khi một tính năng thật sự cần endpoint đó.
- Không thay toàn bộ HTTP infrastructure. Tái sử dụng transport phù hợp, bổ sung hủy request,
  giới hạn tài nguyên và state còn thiếu theo trách nhiệm cụ thể.
- Không chỉnh weather, account, dock layout hay các module khác ngoài điểm tích hợp cần thiết.

## 3. S0 — Chốt thiết kế và dependency trước implementation

### 3.1 Nghiên cứu player và UX

Nguồn ban đầu:
- [Nuvio TV](https://github.com/NuvioMedia/NuvioTV).
- [Stremio Android TV](https://blog.stremio.com/stremio-for-android-tv-now-in-google-play-store/).
- [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv).
- [Just Player](https://github.com/moneytoo/Player).
- [AndroidX Media3](https://github.com/androidx/media).

- [ ] Ghi revision/version, stars/forks, ngày bảo trì và license của reference đã đọc.
  Phân biệt app TV chính thức với fork/mobile; tách screenshot/quan sát thật khỏi đề xuất.
  Không sao chép code/asset khi chưa kiểm tra tương thích license.
- [ ] Chốt mockup và focus map cho search → detail/season/tập → player → Home/Watch Next.
  Giữ identity và state khi Back; không xây hai episode picker riêng cho detail và player.
- [ ] Khảo sát controls: tên phim/tập, timeline, thời gian còn lại, play/pause, seek,
  buffering/retry, audio/phụ đề, danh sách tập và tập kế tiếp.
- [ ] Chốt D-pad/OK/Back/media keys, seek khi giữ phím, ẩn/hiện controls và phục hồi focus.
  Lát cắt đầu có playback, seek, audio/subtitle track, lỗi/retry và resume;
  countdown/auto-next, đổi nguồn/quality nâng cao và subtitle styling làm sau nền này.

### 3.2 Engine và ngân sách

- [ ] So sánh engine Android TV phù hợp, đo APK/RAM/cold start/decoder lifecycle bằng
  HLS/MP4 mẫu trên emulator TV; không chọn engine chỉ vì app tham khảo đang dùng nó.
- [ ] Ghi quyết định engine, surface/window/lifecycle và tập codec/DRM được hỗ trợ ban đầu.
  Đích là video và controls trong launcher; không thay bằng companion app như đích giao hàng.
- [ ] Đo ảnh hưởng so với giới hạn 5 MiB và TV 1 GB trước khi thêm dependency production.
  Nếu engine không thể nằm trong ngân sách, trình số đo và đề xuất thay đổi giới hạn để chốt;
  không tự miễn trừ CI hoặc cam kết giữ nguyên kích thước khi chưa đo.
- [ ] Giữ nguyên manifest component đã lưu trên thiết bị. Không thêm Activity cho panel;
  quyết định host playback dựa trên lifecycle thực tế và kiến trúc đã chốt.

### 3.3 Contract với Home Sentinel

Đối chiếu checkout `../home-sentinel` ngày 29/10/2026: HEAD `f088227`, có thay đổi
chưa commit ở media search contract/service/skill. Đây chưa phải revision đã phát hành.

| Endpoint | Envelope hiện tại | Quyết định |
| --- | --- | --- |
| `POST /v2/flow` | NDJSON `plan_ready`, `task_started`, `task_result`, `response_completed` | Search chính |
| `POST /v2/ask` | Stream `type/payload` | Chat/control và fallback legacy |
| `GET /api/media/search?q=...` | NDJSON `search_started`, `source_capability`, `source_results`, `search_completed` | Tham chiếu; chưa thêm client riêng |
| `GET /api/media/search?q=...&format=json` | `{ok, result: {..., sources, content_types}}` | Tham chiếu; adapter khi có consumer |

`MediaSearchEvent.Snapshot` có `json:"-"`, không xuất ra NDJSON. Không đợi snapshot hoặc
`content_types` trong flow. Skill mới giữ `entry.Kind`, không còn ép mọi item thành `media_title`.

- [ ] Chốt revision/schema và lưu fixture có provenance từ backend cho endpoint sẽ sử dụng:
  flow, legacy, catalog detail/episode, playback descriptor và error.
  Ghi contract envelope media search để tránh trộn parser; không code client chưa dùng.
- [ ] Chốt identity source/title/episode/group và nghĩa của item ID theo namespace nguồn.
  Giữ ID server cấp; không suy ra nguồn từ tên phim/package hoặc season từ số thứ tự.
  Với item provider/local không biết source catalog, giữ identity gốc và đường mở app hiện có.
- [ ] Chốt capability phát: resolve stream, app action/package, MIME, audio/subtitle,
  DRM/headers nếu có; metadata/web-only không trở thành nguồn phát chỉ vì có URL.
- [ ] Chốt playback session/history: identity, position/duration, observedAt, progress/ended,
  idempotency, ẩn/xóa và thứ tự tập. Không giả định schema backend hiện tại đã hỗ trợ đủ.
- [ ] Chốt auth media proxy, URL tương đối, scope/origin/path, expiry/revoke và khả năng
  truy cập từ engine/player ngoài. Không chuyển bearer quản trị cho app bên ngoài.
- [ ] Ghi dependency chưa có và phân định phần launcher có thể làm local trước.
  Backend thay đổi bằng task phối hợp riêng; plan này không tự sửa repo backend.

Đầu ra S0: mockup/focus map, quyết định engine/ngân sách và contract/fixture đã chốt.
Không cần mọi provider sẵn sàng: chỉ cần một nguồn HLS/MP4 có auth để mở đường S2.

## 4. S1 — Nền chung dùng ngay cho search và playback

- [ ] Model giữ source/item/title/episode ID, kind gốc, poster/year/season/episode,
  task ID và action type/source_id/item_id/url. Tách identity khỏi URL resolve có hạn dùng.
- [ ] Tạo state request theo request ID/generation, task/source và sequence; bỏ event cũ/lặp,
  nguồn lỗi không xóa nguồn khác. Không dedupe hai nguồn bằng title hoặc ID không cùng namespace.
- [ ] Request owner hủy theo panel/phiên: hủy socket/IO và chặn callback muộn trước mọi side effect.
  Chọn tập B hủy resolve A; đóng panel hoặc Home/Back áp dụng policy lifecycle đã chốt.
- [ ] Giới hạn NDJSON trước khi cấp phát dòng quá lớn, tổng answer, số item và ảnh;
  không query/decode/network trên main thread. EOF thiếu terminal phải báo lỗi, kết thúc loading.
- [ ] Fallback flow chỉ khi 404/405 hoặc `legacy_route`; không fallback khi 401/403,
  timeout/provider error. Không retry tự động yêu cầu có tác dụng phụ.
- [ ] Một bộ định tuyến playback nhận identity + capability từ mọi entry point.
  Resolve chỉ sau hành động Play; kiểm tra URL/MIME/origin/path, không chạy intent/shell tùy ý.
- [ ] Playback preference theo source ID ổn định, lưu local; mặc định chưa chọn thì hỏi lần đầu.
  Hai lựa chọn Launcher Player / Ứng dụng tương ứng; đổi setting áp dụng lần mở tiếp theo.
  Nguồn tạm offline không làm mất preference. Config chung nếu thêm phải qua contract/generate.
- [ ] Bọc opener app hiện có: content deep link, package visibility/handler và lỗi thiếu app.
  Fallback sang đường còn lại cần người dùng chọn, không tự đổi preference hoặc lặp mở player.
  Item không biết source không bị gán nhầm preference của nguồn khác.
- [ ] Kiểm thử state/cancel/EOF và routing table: source khác nhau, unknown capability,
  first choice, restart, app bị gỡ, stream không hỗ trợ và response đến muộn.

Đầu ra S1: cùng một model/routing được S2/S3 sử dụng, không đổi UI sang bố cục tạm.

## 5. S2 — Lát cắt phát trực tiếp đầu tiên, có resume local

- [ ] Nối một nguồn catalog thật: identity tập → resolve → Launcher Player → Home → Watch Next.
  Dùng host/controls đúng thiết kế S0; dùng detail/episode component có thể mở rộng ở S3,
  không tạo màn demo production hoặc episode picker thứ hai.
- [ ] HLS và MP4 có auth theo contract, play/pause, seek, chọn audio/subtitle track;
  state loading/playing/paused/buffering/ended/error/cancelled và retry có giới hạn.
- [ ] Audio focus, media keys/session, Home/Back, pause/release decoder và khôi phục màn trước;
  không giữ Activity cũ hoặc decode/play khi launcher trở về trạng thái không cho phép playback.
- [ ] Ghi history local bằng identity S0 từ tín hiệu engine; lưu position/duration khi có thật,
  pause/seek/stop và ended. Không suy diễn từ click Play hay thời gian app ở foreground.
  Không thêm polling nền thường trực; tiến độ UI chỉ cập nhật trong phiên player đang hoạt động.
- [ ] Watch Next local dùng routing chung, resolve URL mới từ episode ID và resume sau khi
  engine sẵn sàng; không lưu signed stream URL làm định danh/action lâu dài.
- [ ] Settings → Playback hiện nguồn/lựa chọn; kiểm tra mở cùng tập bằng app hoặc launcher.
  Stream/DRM chưa hỗ trợ phải giải thích và cho chọn app, không coi web page là media.
- [ ] Test vòng xem dở → Home → mở lại, URL hết hạn, mất mạng, đổi tập khi đang resolve,
  app chưa cài, retry, giải phóng tài nguyên và preference sau restart.

Nghiệm thu S2: một nguồn phát thật và mở lại đúng tập/vị trí bằng Launcher Player;
đường mở app vẫn hoạt động qua cùng router. Fixture/HTTP 200 không thay thế playback thật.

## 6. S3 — Search và catalog UI đích cuối

### 6.1 Bốn tab, section theo source

- [ ] All chứa mọi loại; Movie chứa `movie`; Series chứa `show/series/season/episode`;
  Other chứa `video/web_link/answer`, loại chưa biết và `media_title` cũ chưa xác định loại.
  Không có tab Videos riêng và không dùng source làm tab.
- [ ] Mỗi tab có một section cho mỗi source: heading nguồn + list item.
  Update đúng task/source rồi tính lại tab; giữ section nguồn khác, tab đang chọn,
  scroll và focus theo identity. Thứ tự nguồn theo plan, nguồn mới thêm cuối ổn định.
- [ ] Giữ poster/year/season/episode và action; không gán mọi card là available/PLEX.
  TMDb metadata và bài viết không tự sinh nút Play hoặc yêu cầu tải.
- [ ] Hiện source đang tìm, empty, not_configured, auth_required, unreachable, timeout,
  not_implemented/cancelled; error source không xóa kết quả khác.
- [ ] Route rõ `open_url`, `open_app`, `catalog_detail` và playback đã chốt;
  action lạ không tự biến thành web.open, action disabled không được chạy.
  Chỉ dùng capability/availability mà endpoint thực sự cung cấp, không suy diễn trường bị thiếu.
- [ ] Gửi `context.sources` từ lựa chọn nguồn và `context.title` từ phim đã chọn.
  `needs_input` hiện câu hỏi; không giả định backend có hội thoại đa lượt hoàn chỉnh.
- [ ] Giữ chat/answer/control legacy; web link mở bằng app phù hợp, thiếu handler báo rõ.

Nếu sau này cần media JSON: đọc `result.content_types[].sections[]`, gộp videos/other
vào Other, fallback `sources[].items[]`; chỉ chọn một biểu diễn, không render trùng.
Không triển khai nhánh này trước khi có consumer đã được xác định.

### 6.2 Detail và danh sách tập

- [ ] Bấm media phải mở detail trước: poster, tên, metadata có thật, synopsis, source,
  hành động Play/Resume/app theo capability rồi mới đến season/tập. Metadata-only cũng có
  detail; không tự phát hoặc resolve khi chỉ mở thông tin. Season và episode hiển thị
  ngay trong detail: chọn season lọc list tập tại chỗ, không cần màn chọn riêng.
  Tham khảo Nuvio/Stremio/Jellyfin.
- [ ] Movie dùng hero lớn và Play/Resume; chỉ hiện phiên bản/nguồn khi có nhiều bản,
  không hiển thị season hoặc đánh số tập. Series dùng hero gọn, season và tập ngay trong detail.
  Dùng kind server cấp; không suy ra Movie/Series từ số lượng stream. Player Movie không có
  nút Episodes. Có test riêng cho Movie một bản, nhiều phiên bản và Series.
- [ ] ID URL-encoded, tên/năm, season/group/label gốc; tôn trọng playable/capability.
- [ ] Danh sách 500 tập truy cập được đầy đủ bằng phân trang 25 hoặc list tái sử dụng view;
  chọn một cách phù hợp UI S0, không xây cả hai. Không dùng `take(25)` làm phân trang.
- [ ] Episode component dùng chung ở detail/player; phân biệt tập đặc biệt và label trùng
  theo group/episode ID. Back khôi phục tab/source/item/scroll đang chọn.
- [ ] Mở title mới hủy detail cũ; chọn tập đi thẳng routing S1, không mở player bằng nhánh riêng.
  Lỗi title biến mất/provider offline có retry giới hạn; giữ state khi player trở lại.
- [ ] UI test D-pad/hold/scroll/Back/focus với 500 tập và nhiều nguồn trả khác thời điểm;
  test metadata cũ/mới, missing fields, loại chưa biết, duplicate/out-of-order event và cancel.

Nghiệm thu S3: search → detail → mọi tập → player/app theo preference,
Back về đúng vị trí; kết quả nguồn sau không ghi đè nguồn trước.

## 7. S4 — Nhiều nguồn và history đồng bộ

- [ ] Mở rộng capability/provider theo contract, không thêm một player/router riêng cho mỗi nguồn.
  TV Provider/local không có catalog identity tiếp tục dùng deep link/app an toàn hiện có.
- [ ] Đồng bộ history server từ identity/progress S2; xử lý offline/upload retry có giới hạn,
  idempotency và tombstone để không hồi sinh nội dung đã ẩn/xóa.
- [ ] Gộp server/local/TV Provider theo identity có bằng chứng, không title-only;
  nguồn rỗng thành công xóa snapshot của nguồn đó, lỗi giữ snapshot tốt,
  thu hồi quyền xóa cache nguồn bị mất quyền. Lọc trước giới hạn hiển thị.
- [ ] Home start/stop hủy observer/callback/request; debounce, coalesce và refresh bằng tick sẵn có.
  Publish item trước poster; nguồn server chậm không chặn local/provider.
- [ ] Giữ action item đang focus; hold OK có danh sách/ẩn/source status và refresh reason,
  permission state, upload error. Không log token, stream URL hoặc lịch sử xem mặc định.
- [ ] Player ngoài chỉ có resume/progress nếu metadata/callback liên kết được đúng tập.
  Không có bằng chứng thì chỉ ghi nhận mở lại tập, không tuyên bố resume chính xác.
- [ ] Sau resume ổn định mới thêm auto-next/countdown theo thứ tự provider và ended thật;
  đổi nguồn/quality nâng cao, subtitle styling/delay làm trên cùng player/session.
- [ ] Test permission cấp/từ chối/thu hồi, provider thiếu/query lỗi, observer dồn,
  start/stop, deep link/fallback/app bị gỡ và fixture không sửa lịch sử người dùng.

Chi tiết merge/provider tiếp tục tham chiếu `docs/continue-watching-plan.md`;
identity và player routing mới lấy theo plan này, không phát triển hai đường catalog song song.

## 8. S5 — Vận hành và điều kiện hoàn thành

- [ ] Agent settings hiện online/token error và link dashboard cấu hình engine/login;
  không đưa API key/OAuth provider vào launcher. Phần này có thể làm sau S1.
- [ ] Kiểm tra greeting/notification/action websocket với router hiện có, không thêm UI trùng.
- [ ] Mỗi lát cắt chạy unit/fixture + UI test phù hợp, `make check launcher` và kiểm tra APK
  theo ngân sách đã chốt. Đo RAM/CPU/frame time, cold start và release decoder trên TV 1 GB.
- [ ] Cài và điều khiển emulator TV bằng `scripts/tvctl`, chụp/đọc screenshot để xác minh
  tab/section, controls/focus, setting theo nguồn và Home/Watch Next.
- [ ] E2E trên TV thật cùng server có auth: nhiều nguồn, 500 tập, HLS/MP4/range,
  track/seek/resume, Home/Back, offline, 401/403, server cũ/restart, URL hết hạn,
  response muộn và fallback. Emulator/fixture chỉ chứng minh phần đã chạy trên chúng.
- [ ] Ghi trạng thái riêng: code có, test tự động đạt, emulator đạt, TV thật đạt;
  đánh dấu hoàn thành chỉ khi có bằng chứng tương ứng, không tick toàn bộ nhóm theo build.

Ngoài đường găng: automation/camera/presence/audio ở backend; chỉ thêm UI sau contract
unknown/stale/health đầy đủ. Không hiển thị unknown thành phòng vắng, không thêm editor
hoặc vòng polling automation lên TV. Compound request/conversation nâng cao là task riêng.

## 9. Tài liệu contract và nguồn đối chiếu

- Launcher: `docs/modules/agent-protocol.md`, `docs/continue-watching-plan.md`.
- Backend: `docs/request-flow.md`, `docs/catalog-api.md`,
  `docs/testing/implementation-status.md`.
- Backend code: `internal/requestflow/contracts.go`, `internal/skills/search.go`,
  `internal/contracts/media_search.go`, `internal/contracts/media_search_events.go`,
  `internal/mediasearch/content_types.go`, `internal/catalog/types.go`,
  `internal/playback/resolver.go`, `cmd/sentinel/handlers_media_search.go`,
  `web/scripts/01_search_flow.js`.

Cập nhật tài liệu giao thức cùng lát cắt thay đổi contract. Các nguồn này mô tả code tại
thời điểm đối chiếu; fixture/revision được chốt ở S0 mới là cơ sở triển khai và nghiệm thu.
