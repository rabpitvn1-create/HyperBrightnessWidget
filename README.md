# Local AI Relay

Trạm điều phối đa AI trên Android — bàn giao thiết kế v0.1.

Mục tiêu: nhập một yêu cầu, điều phối các AI theo kịch bản A → B → C → A, có chế độ tự động cho kết nối được phép và chế độ duyệt từng chặng; lưu lịch sử và tiến trình trên thiết bị, khôi phục được sau gián đoạn. Không yêu cầu máy chủ điều phối riêng. Các nhà cung cấp online vẫn xử lý dữ liệu trên máy chủ của họ.

## Tài liệu chính
- [Đặc tả kiến trúc, giao thức và tiêu chí nghiệm thu](docs/ARCHITECTURE.md)

## Trạng thái
Đây là bàn giao kiến trúc, chưa phải ứng dụng Android hoặc APK. Mã điều chỉnh độ sáng và cấu hình build/release cũ không được chuyển sang nhánh thiết kế này. Lịch sử Git vẫn được giữ nguyên.

Thứ tự thực hiện: Contract → Mock Connector → Engine + checkpoint → Quota & Routing → Connector thực tế → UI Android. Thử nghiệm sớm tính tương thích đăng nhập và vòng đời trình duyệt song song, không phụ thuộc vào kết quả để triển khai lõi.

Không xoay vòng tài khoản để vượt hạn mức, không vượt CAPTCHA, không tự động hóa kênh không được nhà cung cấp cho phép. Ưu tiên API chính thức miễn phí nếu có, BYOK, mô hình cục bộ và chuyển giao thủ công.
