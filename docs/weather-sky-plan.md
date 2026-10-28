# Kế hoạch nền trời theo phong cách Breezy Weather

Ngày lập: 28/10/2026. Trạng thái: đã triển khai và qua kiểm chứng chức năng trên TV emulator.

Điều chỉnh theo yêu cầu trong lúc triển khai:
- Vị trí TV được xác định một lần theo mạng và lưu lâu dài, không tự dò lại sau 24 giờ.
  Cài đặt launcher → Vị trí thời tiết cho phép cập nhật vị trí TV hoặc chọn thành phố.
- Khi đủ không gian (840dp ngang, 250dp giữa header và dock), đưa ba ô smarthome
  xuống dưới đồng hồ/lịch và Xem tiếp sang cột phải. Màn hình nhỏ giữ preview trong dock.
- Ba ô smarthome dùng icon vector mặc định, giá trị và tên xếp dọc; kích thước 88×78dp,
  nền nhẹ. Icon tùy chỉnh vẫn được giữ.


## 1. Mục tiêu và phạm vi

Làm lại nền trời của TV Launcher để mây tự nhiên, có chiều sâu và bố cục cân đối;
trạng thái cảnh cập nhật theo thời tiết và ngày/đêm. Ưu tiên tiết kiệm RAM, CPU
trên TV 1 GB RAM, đồng thời giữ chữ, widget và các nút dễ đọc.

- Tham khảo thiết kế và cách phối cảnh của Breezy Weather.
- Dùng Canvas native; bản đầu là hình tĩnh, không có vòng lặp animation.
- Header, widget thời tiết và nền trời dùng chung dữ liệu và lịch cập nhật.
- Thay đổi tập trung vào dữ liệu thời tiết, điều phối cập nhật và renderer nền trời.
- Không đổi chức năng tài khoản hoặc component manifest. Bố cục Xem tiếp và smarthome
  được bổ sung theo yêu cầu trực tiếp của người dùng trong lúc triển khai.
- Không đưa WebView, Lottie, video, blur hay engine đồ họa mới vào launcher.

## 2. Nguồn tham khảo

- Breezy Weather: https://github.com/breezy-weather/breezy-weather
- Module đồ họa: https://github.com/breezy-weather/breezy-weather/tree/main/ui-weather-view
- Giấy phép: https://github.com/breezy-weather/breezy-weather/blob/main/LICENSE
- Dữ liệu thời tiết: https://open-meteo.com/en/docs

### Task 2.1 — Khảo sát và chốt mẫu

- [x] Đọc renderer liên quan và chọn mẫu quang, ít mây, nhiều mây, mưa, ban đêm.
- [x] Ghi lại tỷ lệ thiên thể, đường bao mây, lớp sáng/tối và palette ngày/đêm.
- [x] Kiểm tra giấy phép từng file trước khi tái sử dụng code hoặc asset.
- [x] Ghi rõ nguồn và giữ thông tin giấy phép cần thiết nếu có phần được tái sử dụng.
- [ ] Chuẩn bị ảnh mẫu trên bố cục launcher thực tế trước khi nối dữ liệu.

Nghiệm thu: chốt được phong cách mây mềm, bất đối xứng, không còn viền răng cưa;
mẫu không che chữ hoặc ô điều khiển. Tham khảo hình thức không đồng nghĩa nhập cả thư viện.

## 3. Khảo sát code và baseline trước khi sửa

Các điểm cần kiểm tra lại trên checkout triển khai:

| Thành phần | Vai trò hiện tại / nội dung cần kiểm tra |
|---|---|
| `feature/system/data/Weather.kt` | API, cache, địa điểm, mã thời tiết và ngày/đêm |
| `feature/main/widget/SkyView.kt` | Gradient, hình thời tiết và điều kiện vẽ lại |
| `core/design/WeatherShapes.kt` | Hình học mây, thiên thể, mưa, tuyết và sương |
| `feature/main/coordinator/HomeHeaderCoordinator.kt` | Refresh từ header |
| `feature/main/widget/WidgetShelf.kt` | Refresh và hiển thị thời tiết trong widget |
| `feature/system/coordinator/HomeMetricsMonitor.kt` | Tick phút hiện có |
| `feature/main/ui/MainActivity.kt` | Lifecycle và tích hợp các thành phần |

### Task 3.1 — Ghi baseline

- [x] Đọc trạng thái git; giữ nguyên thay đổi đang có từ công việc khác.
- [x] Xác định mọi nơi gọi fetch, lịch tick và nơi giữ bản dữ liệu thời tiết riêng.
- [x] Kiểm tra guard `lastMinute`: dữ liệu mới trong cùng phút có bị bỏ qua không.
- [x] Chụp ảnh các trạng thái nền và bố cục hiện tại để so sánh.
- [ ] Đo CPU khi Home đứng yên, PSS, allocation và thời gian cập nhật một cảnh.
- [x] Ghi thiết bị, Android, độ phân giải, density, cấu hình và điều kiện đo.

## 4. Dữ liệu và trạng thái cảnh

### Task 4.1 — Hoàn thiện dữ liệu thời tiết

- [x] Giữ các dữ liệu đang dùng: mã thời tiết, nhiệt độ, địa điểm và thời điểm tải.
- [x] Phân biệt thời điểm quan trắc với thời điểm ứng dụng tải dữ liệu.
- [x] Bổ sung sunrise/sunset, múi giờ địa điểm và độ phủ mây khi thiết kế cần.
- [x] Dùng thông tin ngày/đêm của địa điểm; không chỉ dựa vào giờ cố định trên TV.
- [x] Có fallback khi thiếu sunrise/sunset, đổi ngày hoặc dữ liệu ngoài khoảng dự báo.
- [x] Cache gắn với địa điểm; không dùng cache thành phố cũ sau khi đổi cấu hình.
- [x] Đọc tương thích cache cũ; trường mới thiếu không làm ứng dụng lỗi.
- [x] Mã thời tiết không nhận diện được phải thành trạng thái không xác định.

### Task 4.2 — Tách mô hình cảnh

- [x] Tạo mô hình nhỏ, ví dụ `WeatherSceneState`, độc lập với View.
- [x] Chứa loại thời tiết, ngày/đêm, nhóm độ phủ mây và mức độ mới của dữ liệu.
- [x] Chỉ giữ dữ liệu ảnh hưởng đến hình vẽ; không sao chép toàn bộ response API.
- [x] Tách việc so sánh màu, hình học và bounds để biết chính xác phần cần cập nhật.

| Trạng thái | Hình ảnh mục tiêu |
|---|---|
| Quang | Thiên thể rõ, rất ít hoặc không có mây |
| Ít mây | Một vài cụm mây; có thể che một phần thiên thể |
| Nhiều mây | Các cụm lớn hơn, xếp lớp, giảm độ nổi của thiên thể |
| Mưa | Mây tối và nét mưa tiết chế |
| Giông | Mây dày, chi tiết sét tĩnh; không nhấp nháy liên tục |
| Tuyết | Màu lạnh, mây sáng và chi tiết tuyết |
| Sương | Các dải sương mềm, giảm tương phản cảnh nền |
| Không xác định | Gradient trung tính, không giả định trời quang |

## 5. Một luồng điều phối cập nhật chung

### Task 5.1 — Tạo coordinator thời tiết

- [x] Tạo `HomeWeatherCoordinator` hoặc thành phần tương đương, trách nhiệm nhỏ và rõ.
- [x] Header, widget và nền trời nhận cùng một bản dữ liệu từ coordinator.
- [x] Chuyển các thao tác refresh thủ công về cùng đường xử lý.
- [x] Gộp yêu cầu trùng; chỉ một request đang chạy cho cùng địa điểm.
- [x] Network chạy trên I/O; cập nhật View trên main thread.
- [x] Khi đổi địa điểm, bỏ qua kết quả request cũ trả về muộn.
- [x] Gỡ các lịch tải trùng sau khi chuyển đủ caller, không duy trì song song hai cơ chế.
- [x] Nền trời vẫn cập nhật khi widget thời tiết bị tắt.

## 6. Lịch cập nhật, cache và lỗi mạng

### Task 6.1 — Chính sách cập nhật

| Sự kiện | Hành vi |
|---|---|
| Home xuất hiện | Hiện cache ngay; tải nếu thiếu hoặc quá 30 phút |
| Home đang hiển thị | Mỗi phút kiểm tra hạn tải và trạng thái thời gian |
| Dữ liệu đến hạn | Tải bản mới; không gọi API ở mọi tick phút |
| Refresh thủ công | Tải ngay hoặc dùng request đang chạy |
| Đổi địa điểm | Chuyển sang dữ liệu phù hợp, vô hiệu kết quả cũ |
| Mạng trở lại | Thử cập nhật nếu cần, có chống gọi dồn |
| Home xuống nền | Dừng lịch và listener; hủy tác vụ thuộc phiên Home |
| Home quay lại | Tính trạng thái hiện tại, không chạy bù các tick đã bỏ lỡ |

- [x] Tái sử dụng tick phút hiện có hoặc thay thế bằng một lịch duy nhất.
- [x] `start()`/`stop()` gọi nhiều lần vẫn không tạo job hay listener trùng.
- [x] Dùng thời gian monotonic cho retry; thời gian thực cho ngày/đêm và tuổi dữ liệu lưu đĩa.
- [x] Xử lý đổi giờ, múi giờ và ngày; timestamp bất thường không giữ cache mới mãi.
- [x] Không thêm background worker, alarm hoặc service chỉ để cập nhật nền Home.

### Task 6.2 — Cache và retry

| Tuổi dữ liệu | Chính sách |
|---|---|
| Dưới 30 phút | Dùng cache, không tự tải lại |
| Từ 30 phút đến 3 giờ | Giữ cảnh gần nhất trong khi thử cập nhật |
| Quá 3 giờ hoặc chưa có dữ liệu | Nền trung tính; tiếp tục cập nhật theo lịch |

- [x] Retry khi lỗi theo các khoảng 5, 15, 30 phút; tối đa mỗi 30 phút.
- [x] Thành công đặt lại backoff; thao tác mạng liên tiếp không tạo retry dồn.
- [x] Giữ timeout và đóng connection/stream ở mọi đường thoát.
- [x] Hủy coroutine phải đi kèm xử lý connection đang chặn nếu transport yêu cầu.
- [x] Chỉ ghi cache khi dữ liệu hợp lệ và đúng địa điểm đang được yêu cầu.

## 7. Renderer mây và thiên thể

### Task 7.1 — Hình học

- [x] Thay hình mây nhiều múi bằng đường Bézier trơn.
- [x] Tạo một số ít mẫu cố định: khối lớn nhỏ bất đối xứng, đáy thoải.
- [x] Thêm lớp nền và lớp sáng nhẹ để tạo chiều sâu.
- [x] Đổi màu theo ngày/đêm và thời tiết; không dùng cùng màu xám cho mọi cảnh.
- [x] Mặt trời có đĩa sáng và quầng nhẹ; mặt trăng có dáng rõ ràng.
- [x] Thứ tự vẽ cho phép mây che thiên thể tự nhiên.
- [x] Không tạo hình ngẫu nhiên mới mỗi phút khiến cảnh thay đổi đột ngột.

### Task 7.2 — Bố trí thích ứng

- [x] Lấy bounds thật của header, đồng hồ, widget và dock sau layout.
- [x] Dùng vùng trống và khoảng cách an toàn để chọn vị trí, không chỉ tọa độ phần trăm.
- [x] Tính cả focus phóng lớn, tooltip, system insets và thay đổi kích thước.
- [x] Giảm số lượng hoặc kích thước mây khi chật; không thu nhỏ widget để nhường nền.
- [x] Có bố cục khi xuất hiện hoặc biến mất nội dung Xem tiếp.
- [x] Tránh thiên thể bị UI che một phần hoặc mây làm chìm chữ.

## 8. Khi nào dựng lại và vẽ lại

### Task 8.1 — Cập nhật có điều kiện

| Thay đổi | Công việc |
|---|---|
| Dữ liệu mới, cảnh không đổi | Cập nhật thông tin, không dựng lại hình |
| Loại thời tiết/độ phủ mây đổi | Dựng lại hình bị ảnh hưởng rồi invalidate |
| Ngày/đêm hoặc palette đổi | Cập nhật màu; chỉ dựng lại hình nếu cần |
| Bounds/kích thước đổi | Dựng lại hình học theo vùng mới |
| Tick phút, cảnh không đổi | Không chủ động invalidate hoặc tạo lại tài nguyên |
| Hệ thống yêu cầu redraw | Vẽ từ tài nguyên đã cache |

- [x] Dữ liệu mới trong cùng phút vẫn cập nhật ngay, không chờ tick tiếp theo.
- [x] Không để guard theo phút bỏ qua thay đổi dữ liệu, ngày hoặc múi giờ.
- [x] Chỉ cập nhật phần gradient khi palette thực sự thay đổi.

## 9. Điều kiện bắt buộc về RAM và CPU

### Task 9.1 — Giới hạn tài nguyên

- [x] Dùng lại `Path`, `Paint`, shader, mảng và đối tượng tạm có thể tái sử dụng.
- [x] Không tạo các đối tượng này trong `onDraw()` hoặc cấp phát lặp lại do cảnh đứng yên.
- [x] Giới hạn rõ số mây, số lớp và độ phức tạp đường vẽ.
- [x] Tránh phép union `Path` lặp lại; ưu tiên contour dựng sẵn khi có thể.
- [x] Ưu tiên gradient/vector; nếu cần bitmap, chỉ giữ cache cảnh hiện tại với kích thước giới hạn.
- [x] Không cache nhiều bitmap toàn màn hình, không tăng độ phân giải cache theo màn hình 4K.
- [x] Mục tiêu cache bitmap của renderer không quá 1 MiB; kiểm chứng chất lượng ảnh trước khi chốt.
- [x] Không dùng bitmap decode, network hoặc disk I/O trên main thread.
- [x] Không giữ Activity trong tác vụ sống lâu hơn lifecycle của Home.
- [x] Dừng lịch/listener khi xuống nền; tái sử dụng tài nguyên khi pause ngắn để tránh churn.
- [x] Dọn tài nguyên khi View/host bị hủy, không giữ lại renderer không còn được sử dụng.

### Task 9.2 — Đo và quyết định

- [ ] So sánh trước/sau trên cùng thiết bị, cấu hình và điều kiện, sau giai đoạn warm-up.
- [ ] Đo CPU đứng yên, PSS, allocation, GC và thời gian cập nhật cảnh; lấy nhiều mẫu.
- [x] Tách chi phí renderer khỏi network, metrics và các tác vụ khác của launcher.
- [x] Không đặt cam kết phần trăm cải thiện khi chưa có baseline.
- [x] Nếu một hiệu ứng làm tăng chi phí đáng kể, giản lược hoặc bỏ trước khi nghiệm thu.

Mục tiêu: khi cảnh không đổi, renderer không tự tạo lượt vẽ hoặc cấp phát lặp lại.
Không cam kết toàn bộ ứng dụng có CPU bằng 0, vì Home còn các tác vụ khác và redraw từ hệ thống.

## 10. Kiểm thử và kiểm chứng

### Task 10.1 — Test hành vi

- [x] Ánh xạ đủ trạng thái thời tiết, mã không biết và dữ liệu thiếu.
- [x] Cache mới/cũ/quá hạn, cache phiên bản trước, địa điểm thay đổi.
- [x] Một request khi nhiều nơi refresh; không áp dụng kết quả địa điểm cũ.
- [x] Retry/backoff, mạng phục hồi và thành công đặt lại backoff.
- [x] Sunrise/sunset, qua nửa đêm, đổi múi giờ, thiếu dữ liệu thiên văn.
- [x] Resume nhiều lần, pause khi request đang chạy, resume sau thời gian dài.
- [x] Dữ liệu đổi trong cùng phút; bounds đổi dù thời tiết không đổi.
- [x] Cảnh không đổi không dựng lại hình hay lên lịch redraw liên tục.
- [x] Dùng clock và nguồn dữ liệu giả để test lịch mà không chờ thời gian thực.

### Task 10.2 — Kiểm tra hình ảnh và tích hợp

- [x] Chụp ngày/đêm với quang, ít mây, nhiều mây, mưa và dữ liệu không xác định.
- [ ] Kiểm tra điện thoại ngang, TV 720p/1080p và các density khác nhau.
- [x] Kiểm tra có/không Xem tiếp, focus widget, focus dock và tooltip.
- [x] Xác nhận chữ dễ đọc, thiên thể không bị cắt, không có hình phủ sai vị trí.
- [x] Chạy `make check launcher`, kiểm tra contract drift và `git diff --check`.
- [x] Kiểm tra APK cuối nhỏ hơn 716800 bytes.
- [ ] Hoàn thành báo cáo RAM/CPU so với baseline và nêu rõ thiết bị đã thử.

## 11. Thứ tự triển khai

1. Khảo sát code, đo baseline và chốt ảnh mẫu Breezy Weather.
2. Hoàn thiện dữ liệu, cache, mô hình cảnh và test ánh xạ.
3. Gom coordinator, lịch refresh, lifecycle và test điều phối.
4. Làm renderer tĩnh với cache tài nguyên có giới hạn.
5. Bố trí theo vùng trống, nối cập nhật dữ liệu với renderer.
6. Kiểm chứng screenshot, RAM/CPU, sửa regression và chạy gate cuối.

Mỗi bước giữ APK chạy được; không gộp refactor không liên quan. Thành phần mới dưới 300 dòng,
dòng source không quá 100 ký tự. Nếu phát sinh config dùng chung, sửa contract và regenerate.

## 12. Định nghĩa hoàn thành

- [x] Mây tự nhiên theo mẫu đã chốt, màu ngày/đêm rõ và phù hợp thời tiết.
- [x] Cảnh cập nhật theo dữ liệu, thời gian và bố cục mà không cần khởi động lại launcher.
- [x] Header, widget và nền trời nhất quán; không phụ thuộc widget thời tiết đang bật.
- [x] Không có lịch/request trùng, vòng lặp animation hoặc renderer chạy nền không cần thiết.
- [x] Không có regression bố cục, tương phản hoặc thao tác remote trong các kịch bản đã kiểm tra.
- [ ] Có số đo trước/sau, ảnh kiểm chứng và toàn bộ gate yêu cầu đã pass.

Animation mây trôi hoặc hiệu ứng liên tục chỉ được xét thành task riêng sau khi bản tĩnh đạt chuẩn.

## 13. Kết quả triển khai 28/10/2026

- Functional gate: `make check launcher`, 228 unit test, lint, contract drift và `git diff --check` pass.
- APK release: 593392 bytes, dưới ngưỡng 716800 bytes.
- TV emulator Android 16, 1920×1080, density 320: cài và chuyển Home bằng `scripts/tvctl`.
- QA native: 10 cảnh ngày/đêm, Canvas 720p/1080p, dữ liệu đổi trong cùng phút,
  không rebuild/redraw khi cảnh không đổi; toàn bộ pass.
- QA Home rộng: smarthome dưới clock, Xem tiếp cột phải, ẩn khi không có dữ liệu,
  giữ mục khi đang focus, D-pad lên/xuống, đáy chữ PM và khoảng cách; toàn bộ pass.
- Renderer dùng gradient và Path, bitmap cache = 0. Phép đo software Canvas 960×540,
  30 lượt warm-up + 300 lượt đo trên emulator: 765982 ns/frame CPU, 0 bytes heap
  cấp phát theo runtime counter trong cửa sổ đo. Đây không phải số frame GPU hay cam kết trên TV 1 GB.
- Samsung SM-S926B Android 16, 2340×1080 ngang, density 450: bản trước chỉnh typography
  đã qua QA các cảnh và phép đo tương tự 960493 ns/frame, 0 bytes; sau đó chuyển thử nghiệm
  sang TV emulator theo yêu cầu. Không coi đây là so sánh tốc độ hai thiết bị.
- Ảnh và backup local: `local/weather-sky/` (gitignored). Instrumentation `weather`/`hub`
  dùng fixture trong RAM, không ghi lịch sử xem hoặc thay vị trí đã lưu.
- Snapshot tài nguyên emulator trước/sau ghi trong `weather-sky-validation.md`.

Các mục chưa đánh dấu giữ mở có chủ đích: không dựng prototype riêng trước khi nối dữ liệu;
không có bộ profile allocation/GC trước thay đổi với cùng điều kiện, nên không kết luận
mức cải thiện toàn ứng dụng. 720p và các density khác đã kiểm tra bằng Canvas/mô hình bố trí,
chưa coi là nghiệm thu trên TV 720p/4K và TV 1 GB thực.
