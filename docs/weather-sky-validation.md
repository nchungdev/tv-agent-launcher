# Kiểm chứng nền trời và Home — 28/10/2026

## Thiết bị và phương pháp

- Android TV emulator `Television_1080p`, Google TV Android 16/API 36,
  1920×1080, density 320, ADB loopback 127.0.0.1:5555.
- Cài, chuyển Home, cấp listener, lấy mẫu và chụp ảnh bằng `scripts/tvctl`.
- Backup cấu hình, ảnh và log số đo nằm trong `local/weather-sky/`, được gitignore.
- Thử nghiệm trước đó trên Samsung SM-S926B Android 16, 2340×1080 ngang, density 450;
  sau yêu cầu của người dùng, kiểm chứng cuối và cài release trên TV emulator.

## Gate và QA

- `make check launcher`: Go style/vet/test, generate/contract drift, Android unit test,
  lint và release build pass. Lần build Android cuối cũng chạy lại unit/lint sau typography.
- 228 unit test, 0 failure. `git diff --check` pass.
- APK cuối 593392 bytes, dưới 716800 bytes.
- Instrumentation debug, scenario `weather`: 10 cảnh ngày/đêm cho clear/partly/cloudy/rain/unknown,
  Canvas 720p/1080p, đổi dữ liệu trong cùng phút, cảnh không đổi không rebuild/redraw: pass.
- Scenario Home rộng: ba ô nằm dưới đồng hồ; Xem tiếp ở cột phải; D-pad giữa clock và smarthome;
  giữ nội dung đang focus và ẩn khi hết dữ liệu sau khi rời focus: pass.
- Kiểm tra glyph: đáy PM lệch tối đa 1 pixel với chữ số, khoảng cách clock/date không quá 20dp,
  khoảng cách khối ngày tháng đến smarthome ít nhất 20dp: pass.
- Fixture Xem tiếp ở RAM, tách khỏi request thật và khôi phục nguồn sau QA; không ghi lịch sử xem.
- Harness gọi Home bằng ACTION_MAIN/CATEGORY_HOME và theo dõi Activity đã resume;
  tránh kiểm tra nhầm Activity trong task thường trên Google TV.

## Renderer

Phép đo riêng trên software Canvas 960×540, sau 30 lượt warm-up, 300 lượt vẽ cached:

| Thiết bị | CPU trung bình/lượt | Heap bytes theo runtime counter trong cửa sổ đo |
|---|---:|---:|
| TV emulator | 765982 ns (~0,77 ms) | 0 |
| Samsung, bản trước sửa typography | 960493 ns (~0,96 ms) | 0 |

Đây là phép đo đường vẽ cached, không phải frame GPU hay benchmark so sánh hai thiết bị.
Renderer không dùng bitmap cache; Path/Paint/shader được giữ lại.
Không tự lên lịch animation hoặc redraw; kiểm tra đứng yên 1,5 giây giữ nguyên draw/build counter.

## Snapshot toàn ứng dụng

| Mẫu | PSS (KiB) | RSS (KiB) | CPU từ ba mẫu top |
|---|---:|---:|---|
| Emulator trước cập nhật cuối | 70784 | 170260 | 16 / 30 / 71% |
| Release cuối, mẫu 1 | 38103 | 139820 | 20 / 3 / 6% |
| Release cuối, mẫu 2 | 44437 | 147140 | cùng cửa sổ CPU trên |

Các snapshot chịu ảnh hưởng GC, khởi động, agent, metrics, network và môi trường emulator.
Baseline được lấy trên checkout đang có công việc khác, không phải bản release chuẩn độc lập.
Không suy ra phần trăm cải thiện từ các mẫu này. Chưa có profile allocation/GC trước thay đổi
trong cùng điều kiện hoặc phép đo trên TV 1 GB thực; các mục tương ứng trong plan vẫn để mở.
720p/density được kiểm tra qua Canvas và mô hình bố trí, không thay cho kiểm thử TV 720p/4K thực.
