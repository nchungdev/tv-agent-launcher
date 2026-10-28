# Plan cập nhật Continue Watching

Trạng thái: đã hoàn thiện và kiểm thử.

## Mục tiêu

Bổ sung Watch Next từ Android TV Provider, kết hợp với server và lịch sử media session local.
Giữ giao diện dock hiện tại, ưu tiên dữ liệu đúng, cập nhật kịp thời và mở đúng nội dung.

## Hiện trạng

- `ContinueWatching` gộp ba nguồn: TV Provider (`TvWatchNextSource`), Agent Server (`ServerContinueSource`) và Media Sessions (`LocalWatchHistory`).
- Server rỗng hoặc lỗi độc lập không che khuất dữ liệu từ các nguồn khác.
- `WatchItem` có đầy đủ định danh nội dung (`contentId`, `deepLink`), nguồn (`WatchSource`), timestamp (`updatedAt`) và khóa gộp tin cậy.
- `WatchLauncher` ưu tiên deep link nội dung, kiểm tra scheme an toàn và fallback về app khi deep link thất bại.
- Notification Access phục vụ media session, độc lập với quyền đọc TV Provider (`READ_TV_LISTINGS`).
- Dock có thumbnail, tiến độ, menu nguồn và danh sách khi giữ OK, giữ action ổn định khi có focus.

## Tham khảo

LTvLauncher tại revision `a49697c4a72d511b3a18c879794bb50bdd7addb6`:

- [Native Android: query, observer, quyền và mở intent](https://github.com/leanbitlab-org/LtvLauncher/blob/a49697c4a72d511b3a18c879794bb50bdd7addb6/android/app/src/main/java/com/leanbitlab/ltvL/MainActivity.java#L1362)
- [Service: lifecycle, refresh và mở tiếp](https://github.com/leanbitlab-org/LtvLauncher/blob/a49697c4a72d511b3a18c879794bb50bdd7addb6/lib/providers/watch_next_service.dart)
- [Giao diện và bộ lọc](https://github.com/leanbitlab-org/LtvLauncher/blob/a49697c4a72d511b3a18c879794bb50bdd7addb6/lib/widgets/continue_watching_row.dart)
- [AOSP: kiểm soát quyền TV Provider](https://android.googlesource.com/platform/packages/providers/TvProvider/+/master/src/com/android/providers/tv/TvProvider.java#2032)

AOSP cho phép caller có `READ_TV_LISTINGS` đọc mục của app khác khi mục có `searchable=1`.
Firmware và việc app có xuất dữ liệu vẫn ảnh hưởng kết quả; không bảo đảm mọi app đều xuất hiện.
Tham khảo cơ chế và viết implementation phù hợp dự án; không sao chép nguyên code GPL của LTvLauncher.

## 1. Bổ sung nguồn Android TV Provider

- [x] Tạo `TvWatchNextSource`, query `TvContract.WatchNextPrograms` trên worker thread.
- [x] Đọc ID, package, tiêu đề/tập phim, poster/thumbnail, vị trí xem, thời lượng,
  thời điểm tương tác, loại Watch Next và `intentUri`.
- [x] Kiểm tra provider tồn tại; khai báo và xin `READ_TV_LISTINGS` khi phù hợp.
- [x] Phân biệt `ready`, `empty`, `permissionRequired`, `unsupported`, `error`.
- [x] Xử lý cursor null, cột thiếu, dữ liệu sai và quyền bị thu hồi mà không crash.
- [x] Giới hạn tài nguyên khi đọc; không cắt theo số lượng hiển thị trước bước lọc/gộp.

## 2. Gộp TV Provider, server và lịch sử local

- [x] Đọc từng nguồn độc lập; server rỗng hoặc lỗi không che kết quả của nguồn khác.
- [x] Bổ sung định danh nội dung, nguồn, timestamp và thông tin mở tiếp cho model.
- [x] Loại trùng bằng package + content ID/deep link khi có bằng chứng cùng nội dung.
  Không gộp hai tập phim chỉ vì cùng tiêu đề; không tự suy diễn ID giữa các nguồn.
- [x] Khi trùng, lấy tiến độ mới nhất có timestamp đáng tin; giữ deep link và poster tốt nhất.
  Timestamp thiếu phải có thứ tự xử lý xác định, không giả định là dữ liệu mới nhất.
- [x] Lọc mục ẩn, app bị ẩn và mục hoàn thành trước khi giới hạn số lượng.
- [x] Với TV Provider, chỉ đưa loại `CONTINUE` vào mục Xem tiếp trong đợt đầu.
- [x] Chuẩn hóa tiến độ hữu hạn trong khoảng 0..1; thời lượng không biết không bị loại
  chỉ vì thiếu duration. Chốt rõ ngưỡng tiến độ và thời gian lưu cho từng nguồn.
- [x] Lỗi tạm thời giữ snapshot tốt gần nhất của nguồn trong phiên.
  Nguồn trả rỗng thành công phải xóa snapshot cũ của chính nguồn đó.
- [x] Khi mất quyền, ngừng sử dụng dữ liệu cache của nguồn bị thu hồi quyền.

Không đổi giao thức server để phục vụ riêng việc gộp nếu chưa cần thiết.
Nếu phải thêm trường giao thức/config dùng chung, cập nhật nguồn contract và tài liệu trước.

## 3. Cập nhật theo lifecycle

- [x] Đăng ký `ContentObserver` khi Home hoạt động, debounce 500 ms.
- [x] Refresh khi quay lại Home và sau khi cấp quyền.
- [x] Hủy observer, callback và công việc liên quan khi dừng; không giữ Activity cũ.
- [x] Nếu đang tải, gom yêu cầu mới thành một lần tải tiếp; chặn kết quả cũ ghi đè.
- [x] Publish danh sách trước, tải poster sau; nguồn server chậm không chặn dữ liệu local/provider.
- [x] Tận dụng tick hiện có cho refresh dự phòng mỗi 5 phút khi Home đang hiện.
  Không thêm timer hay công việc nền thường trực.

## 4. Mở tiếp và điều khiển trên dock

- [x] Thử deep link đúng nội dung, thất bại mới mở app.
- [x] Hỗ trợ URI thường và `intent://`; giới hạn intent về package sở hữu nội dung.
  Không chuyển tiếp selector, component hoặc quyền URI ngoài phạm vi cần thiết.
- [x] Không đưa intent từ TV Provider vào nhánh executor làm mất thông tin mở tiếp.
- [x] Giữ nội dung/action hiện tại khi thumbnail đang có focus.
- [x] Giữ OK mở danh sách xem tiếp và thao tác ẩn.
- [x] Lưu trạng thái ẩn trong launcher; không xóa bản ghi thuộc app khác trong TV Provider.
- [x] Thêm trang trạng thái nguồn: thiếu quyền, không hỗ trợ, chưa có nội dung hoặc lỗi đọc.
  Dock rỗng không cần hiện thông báo lỗi thường trực.
- [x] Không log tiêu đề, deep link hay lịch sử xem trong chẩn đoán mặc định.

## 5. Kiểm thử và tài liệu

### Unit test

- [x] Server rỗng nhưng local/provider có dữ liệu; lỗi của từng nguồn độc lập.
- [x] Loại trùng, nhiều tập cùng tên, ID khác nhau, timestamp thiếu/sai hoặc bằng nhau.
- [x] Duration bằng 0, tiến độ âm/quá 100%, NaN/infinity và mục đã hoàn thành.
- [x] Lọc trước giới hạn, snapshot khi lỗi, xóa snapshot khi nguồn rỗng hoặc mất quyền.
- [x] Refresh dồn, yêu cầu refresh trong lúc đang tải và kết quả đến sai thứ tự.

### Instrumentation và thiết bị

- [ ] Thiếu quyền, cấp/từ chối/thu hồi quyền, provider không tồn tại và query lỗi.
- [ ] Observer cập nhật dồn; start/stop lặp lại không rò observer hoặc callback.
- [ ] Deep link hợp lệ, intent không mở được, app đã gỡ và fallback mở app.
- [ ] D-pad, giữ OK, cuộn danh sách, focus và action không thay đổi giữa lúc thao tác.
- [ ] Kiểm chứng trên Android TV thật với app có xuất Watch Next.
  Điện thoại Samsung chỉ xác minh giao diện, media session và nhánh fallback.
- [ ] Fixture kiểm thử không sửa lịch sử xem thật của người dùng.
- [x] Chạy test và assembleRelease, kiểm tra APK không vượt giới hạn dự án (~549 KB).
- [x] Sửa nhận định chỉ system launcher đọc được Watch Next trong `apps/launcher/AGENTS.md`
  và `docs/modules/agent-protocol.md`.

## Tiêu chí hoàn thành

- Có nội dung hợp lệ từ bất kỳ nguồn nào thì dock hiển thị được.
- Server rỗng không làm mất lịch sử local/provider.
- Mở đúng nội dung khi deep link hỗ trợ; mở app khi deep link thất bại.
- Quay lại Home cập nhật được tiến độ mới; observer hoạt động khi Home đang hiện.
- Một nguồn lỗi không làm mất toàn bộ danh sách; trạng thái nguồn giải thích được dock rỗng.
- Không query hoặc decode poster trên main thread, không thêm polling nền thường trực.
- Giao diện thời tiết, tài khoản và bố cục dock không bị thay đổi ngoài phạm vi Watch Next.

## Đợt sau

Tùy chọn kích thước thẻ, số lượng mục, ngưỡng hoàn thành và giao diện quản lý mục đã ẩn.
Config dùng chung nếu được thêm phải đi qua `packages/contract/contract.json` và `make generate`.
