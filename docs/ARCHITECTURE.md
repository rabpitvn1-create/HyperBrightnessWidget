# Local AI Relay — triển khai lõi Mock

## Ranh giới module và package

`core` là Kotlin JVM, kotlinx-coroutines-core; `app` dùng Android native Views, kotlinx-coroutines-android và SQLite/JSON. Core không import Android.

1. `relay.core.contract`: AiConnector có connect, getCapabilities, getStatus, execute, cancel, disconnect và reconcile mặc định Unsupported. Các thao tác I/O là suspend; capabilities là hàm đồng bộ. ExecutionRequest có requestId, stepId, prompt, recentHistory, sessionRef, modelId. ExecutionResult là Success(content, sessionRef, metadata) hoặc Failure(code, retryAfterMs, delivery). Capabilities mô tả streaming, session, giới hạn ký tự, tương tác, reconciliation. ModelTarget ràng buộc tuyến + model, Route ràng buộc provider/account và factory. Không lưu secret trong contract.
2. `relay.core.mock`: ConfigurableMockConnector và MockBackend nhận kịch bản theo route (Reply có độ trễ, Fail, CrashAfterAccept). MockLedger lưu kết quả đã được nhận để đối soát; Android lưu ledger vào SQLite; test dùng ledger bộ nhớ. Mock không hỗ trợ streaming hoặc AI thật.
3. `relay.core.engine`: WorkflowEngine điều phối tuần tự, ContextEngine ghép câu hỏi/vai trò và phần cuối lịch sử trong giới hạn ký tự, StateMachine quản lý phase/bất biến, CheckpointManager ghi trước/sau gửi. Mode AUTO hoặc REVIEW_EACH_STEP cho mọi connector. Mỗi stage có danh sách ModelTarget theo thứ tự ưu tiên đã được chấp thuận. Chỉ một runner trên một instance Engine; connect/disconnect theo lượt, cancel sử dụng coroutine cancellation và NonCancellable cleanup.
4. `relay.core.quota` và `relay.core.persistence`: QuotaRoutingManager chọn tuyến hợp lệ theo trạng thái hiện tại; Persistence là port lưu workflow và quota. MemoryPersistence dùng cho kiểm thử. SqlitePersistence Android dùng JSON trong bảng workflows, quotas và mock_ledger; mỗi workflow snapshot có ID riêng, lần cập nhật kết quả và tiến độ trong một row atomic. Không dùng Java object serialization. Các package lõi trao đổi qua kiểu dữ liệu và interface, không phải bốn Gradle module riêng.

## State machine và phục hồi

IDLE → RUNNING_STEP hoặc WAITING_REVIEW; khi đã duyệt, lưu yêu cầu đầy đủ trước execute. Success ghi turn/sessionRef/requestId và tăng index cùng một lần save; chặng cuối → COMPLETED. Chặng tiếp theo trong review cần duyệt mới. SessionRef chỉ tái dùng khi ModelTarget trùng và connector duy trì phiên; các connector stateless nhận ngữ cảnh từ local. `recentHistory` là cắt phần cuối theo ký tự, chưa tóm tắt ngữ nghĩa. Prompt gốc quá dài → INPUT_REJECTED. Các phản hồi là dữ liệu tham khảo không đáng tin, không phải lệnh thực thi.

Phase gồm IDLE, RUNNING_STEP, WAITING_REVIEW, PAUSED_FOR_USER, WAITING_QUOTA, COMPLETED, FAILED, CANCELLED. Checkpoint pending chứa chính xác target/request. Nếu khôi phục có pending, đối soát đúng connector trước routing; Completed ghi nhận một lần, NotSent cho phép yêu cầu mới, Unknown/InProgress/Unsupported dừng PAUSED_FOR_USER (không gửi lại). Khi auth chưa giải quyết, giữ pause. Nếu kết quả chưa rõ, giữ pending ngay cả khi báo quota/lỗi mạng. Không cam kết exactly-once với connector không có cơ chế đối soát. Khi hủy, lưu CANCELLED và gọi cancel/disconnect với runner hiện tại; hủy cục bộ không chứng minh server đã hủy.

## Lỗi và định tuyến

| Mã chuẩn | Hành vi khi xác định chưa gửi |
| --- | --- |
| RETRYABLE_TRANSIENT | Retry tối đa hai lần, backoff tăng dần |
| QUOTA_EXCEEDED | COOLDOWN, retryAfterMs nếu có; chọn tuyến khác được phép hoặc WAITING_QUOTA |
| AUTH_OR_CHALLENGE_REQUIRED | AUTH_EXPIRED, PAUSED_FOR_USER; không tự bỏ qua, yêu cầu xác thực |
| PROVIDER_BROKEN | ISOLATED, chọn tuyến khác hoặc FAILED |
| INPUT_REJECTED | FAILED, báo đầu vào bị từ chối |

Delivery.UNKNOWN là thuộc tính độc lập, không phải mã lỗi thứ sáu. Quota được lưu theo route; khi thời điểm hồi phục đã qua, trạng thái trở về AVAILABLE và connector được kiểm tra getStatus trước khi gửi. Không có retryAfterMs thì giữ cooldown không đoán thời điểm. Route ISOLATED không tự hồi. App Mock có nút xác thực lại. Nếu mọi tuyến đang cooldown, UI tự thử khôi phục khi có tuyến hợp lệ; không tự chạy nền. Dự phòng chỉ nằm trong danh sách của từng chặng. Không tự xoay tài khoản thật để vượt quota; chưa tích hợp các hãng thật.

## Kiểm thử và giới hạn

`RelayTest.kt` chạy bảy nhóm qua `relayCheck`: vòng 4 chặng + lazy loading/session; quota fallback/context/cooldown expiry; auth pause/resume; crash reconcile không gửi lặp; cancel/disconnect; nhiều hội thoại; review + isolation + retry + unknown safety. Kiểm thử JVM dùng MemoryPersistence, không phải kiểm thử SQLite/Android. UI dùng SQLite private, chưa mã hóa riêng, không có migration từ file MVP cũ; mock ledger là mô phỏng server, không phải dữ liệu từ provider thật. Không có API, GeckoView, streaming, xử lý nền hay kiểm thử thiết bị thật trong phạm vi này.
