# Tham khảo UI smarthome cho TV Launcher

Khảo sát GitHub ngày 28/10/2026. Sao/fork là số tại thời điểm khảo sát, không phải bảo đảm chất lượng.

| Repo | Sao / fork | Bảo trì gần nhất | Giấy phép | Phần hữu ích |
|---|---:|---|---|---|
| [Mushroom](https://github.com/piitaya/lovelace-mushroom) | 5.231 / 445 | 20/10/2026 | Apache-2.0 | Thẻ entity dọc, phân cấp thông tin, icon theo trạng thái |
| [Bubble Card](https://github.com/Clooos/Bubble-Card) | 4.673 / 200 | 14/10/2026 | MIT | Thẻ gọn, popup chi tiết, nhóm thiết bị |
| [Home Assistant frontend](https://github.com/home-assistant/frontend) | 5.770 / 3.911 | 28/10/2026 | Apache-2.0 | Tile, trạng thái hoạt động, hành động, focus |
| [hass-config](https://github.com/matt8707/hass-config) | 5.207 / 668 | 13/08/2024 | MIT | Nhịp bố cục, sidebar thời gian, thông tin chi tiết trong popup |

`hass-config` công bố ngừng bảo trì từ 2024; chỉ tham khảo hình thức, không chọn làm nền tích hợp.
Không nhập code, asset hoặc runtime JavaScript của các repo này vào APK.

## Phát hiện từ mã và tài liệu

- [Mushroom Entity](https://github.com/piitaya/lovelace-mushroom/blob/main/docs/cards/entity.md)
  hỗ trợ bố cục vertical/horizontal, primary/secondary info, icon color khi active,
  tap/hold action. Có thể đặt giá trị làm thông tin chính và tên làm thông tin phụ.
- [Bubble Card](https://github.com/Clooos/Bubble-Card#pop-up) tách điều khiển chi tiết
  khỏi các thẻ tóm tắt. Phù hợp chuyển thành side sheet native thay vì nhét thêm nút lên Home.
- [HA Tile](https://github.com/home-assistant/frontend/blob/dev/src/panels/lovelace/cards/hui-tile-card.ts)
  tách tên/trạng thái/icon, tính màu theo trạng thái và cấu hình hành động riêng.
  [Focus style](https://github.com/home-assistant/frontend/blob/dev/src/panels/lovelace/cards/tile/tile-card-style.ts)
  dùng viền và shadow gắn với màu tile, không chỉ đổi kích thước.

## Hướng đề xuất cho TV

Đây là đề xuất thiết kế suy ra từ các nguồn trên, không phải tính năng đã triển khai toàn bộ.

1. Giữ ba tile native: icon 20dp → giá trị 18sp → tên 11sp, 88×78dp trên bố cục rộng.
   Số liệu cần nổi bật hơn nhãn; không kéo tile hết bề ngang cột.
2. Phân biệt active, off, unavailable và chưa kết nối. Màu trạng thái khác màu focus:
   focus luôn là viền rõ; tránh trường hợp thiết bị bật trông giống đang được chọn.
3. Sensor nhiệt độ mở thông tin; đèn/công tắc dùng OK cho hành động đã cấu hình.
   Điều hòa mở side sheet, không dùng nhiều mục tiêu focus li ti trong một tile.
4. Giữ OK để mở chi tiết/cấu hình; các bước tăng giảm nhiệt độ nằm trong side sheet.
   Không mang double tap hoặc thao tác vuốt của dashboard cảm ứng sang remote.
5. Khi thiếu dữ liệu, hiển thị trạng thái rõ và thời điểm cập nhật nếu có;
   không giả trạng thái tắt hoặc suy đoán bật từ số liệu rỗng.
6. Giữ nền nhẹ, icon vector nhất quán; chỉ đổi nội dung/màu khi trạng thái thay đổi.
   Không thêm animation thường trực, WebView, blur hoặc polling mới cho trang Home.

Bản hiện tại đã làm bước 1 và chuẩn hóa icon mặc định; giữ icon tùy chỉnh.
Các bước về ngữ nghĩa trạng thái và side sheet nên triển khai riêng cùng dữ liệu Home Assistant.
