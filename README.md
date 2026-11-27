# Local AI Relay — Mock Lab

Ứng dụng Android local-first để kiểm chứng kiến trúc điều phối đa AI. Hai module Gradle `core` (Kotlin JVM) và `app` (Android). Các workflow GitHub Actions hiện có được giữ nguyên.

## Sử dụng
Mở app, tạo hội thoại, chọn AUTO hoặc REVIEW_EACH_STEP, chọn tuyến và model (`fast`, `deep`) riêng từng chặng, sửa vai trò và xác nhận tuyến dự phòng Mock. Mặc định A → B → C → A; B2 là tài khoản giả lập thứ hai của B, D là nhà cung cấp giả lập thay thế. Bấm chạy hoặc duyệt từng chặng. Có thể xem nhiều hội thoại đã lưu, trạng thái, kết quả và giả lập quota, xác thực, lỗi mạng, lỗi tích hợp, crash sau khi nhận yêu cầu hoặc phản hồi chậm; chọn tuyến cần giả lập trước khi bấm chạy. Xác thực lại Mock rồi bấm khôi phục; nếu tất cả tuyến đang cooldown, UI tự đánh giá lại khi thời hạn đã qua. Nút hủy dừng runner và giải phóng connector.

Tất cả kết nối hiện là Mock offline, không có đăng nhập, API, web automation hoặc AI thật. Danh mục model giả lập không phản ánh model hiện có ở các hãng. Không xoay vòng tài khoản thật để né giới hạn. Không có luồng clipboard/trình duyệt ngoài. Lịch sử nhiều hội thoại, quota, checkpoint và ledger Mock lưu trong SQLite, mỗi workflow có một bản ghi JSON độc lập; không ghi đè hội thoại khác. DB private chưa mã hóa riêng; dữ liệu MVP cũ không được nhập vào schema mới.

## Build và kiểm thử
JDK 17, Gradle 8.10.2, Android SDK 35. Không kèm wrapper.

- `gradle :core:relayCheck` (bảy nhóm kiểm thử JVM; gồm sáu kịch bản bắt buộc)
- `gradle :app:assembleDebug` (APK ở `app/build/outputs/apk/debug/app-debug.apk`)

`relayCheck` là harness JVM dùng `check`, chạy không phụ thuộc Android; local có thể biên dịch bằng `kotlinc` với kotlinx-coroutines-core-jvm.jar. GitHub Actions gọi hai lệnh trên. Xem kết quả từng commit trong Actions; kiểm thử Android thực, SQLite trên thiết bị, model catalog thật và connector thật chưa được xác minh ở bước này. Xem [kiến trúc](docs/ARCHITECTURE.md).
