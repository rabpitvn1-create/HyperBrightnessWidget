# Local AI Relay — Architecture & Universal Connector Contract v0.1

Trạng thái: thiết kế đề xuất để triển khai; chưa kiểm chứng bằng mã nguồn hoặc thiết bị.

## 1. Phạm vi

- Android local-first: điều phối, lưu lịch sử, checkpoint, thiết lập kịch bản trên điện thoại.
- Workflow tùy biến A → B → C → A: nháp, kiểm chứng, phản biện, tổng hợp. Không coi đồng thuận AI là bằng chứng; giữ nguồn và mức độ chắc chắn.
- Hai chế độ: AUTO (chỉ với Connector được phép tự động hóa) và REVIEW_EACH_STEP (xem/sửa trước khi gửi).
- Tách lõi khỏi nhà cung cấp, giao diện web, API và mô hình offline.
- Quota Manager theo dõi hạn mức hợp lệ; chờ hồi hoặc chuyển nhà cung cấp được phép và đã được người dùng chấp thuận. Không dùng nhiều tài khoản để né giới hạn.
- Không cam kết tất cả nhà cung cấp đều hỗ trợ đăng nhập nhúng, miễn phí, streaming hoặc khôi phục phiên.

## 2. Ranh giới bốn lớp

1. **AI Orchestration Engine**: Workflow Engine, Context Engine, State Machine, Checkpoint Manager. Quản lý thứ tự chặng, phân vai, trạng thái, và ghép ngữ cảnh. Không thao tác trực tiếp với UI web, cookie, token hoặc HTTP.
2. **Quota & Routing Manager**: đánh giá trạng thái hiện tại, năng lực, quyền sử dụng, chính sách và lựa chọn tuyến hợp lệ; cô lập Connector hỏng. Không tự sửa workflow hoặc gửi lại yêu cầu đã có kết quả chưa rõ.
3. **Pluggable Connectors**: API chính thức/BYOK, model offline hoặc kênh web/chuyển giao do người dùng kiểm soát và được phép. Mỗi module chuẩn hóa trạng thái, lỗi và sự kiện; không tự định tuyến hay đổi danh tính.
4. **Local Memory & UI**: Room/SQLite cho lịch sử và checkpoint; giao diện Jetpack Compose; Android Keystore bảo vệ khóa mã hóa; lưu bí mật riêng và không đưa chúng vào lịch sử hoặc log. Persistence cung cấp giao diện cho lõi, không phụ thuộc Android trong domain.

Dependency direction: UI → Engine → Routing → Connector Contract. Provider implementations và persistence implementations được inject qua interface. Domain không import Android SDK.

## 3. Universal Connector Contract

Kotlin dưới đây là đặc tả API, chưa phải module có thể build. Flow thuộc kotlinx.coroutines; các kiểu dữ liệu cần được hoàn thiện trong bước triển khai.

```kotlin
interface AIConnector {
    suspend fun connect()
    fun getCapabilities(): ConnectorCapabilities
    suspend fun getStatus(): ConnectorStatus
    fun execute(request: ConnectorRequest): Flow<ConnectorEvent>
    suspend fun reconcile(requestId: String): ReconciliationResult
    suspend fun cancel(requestId: String)
    suspend fun disconnect()
}

data class ConnectorCapabilities(
    val supportsStreaming: Boolean,
    val supportsPersistentSession: Boolean,
    val supportsReconciliation: Boolean,
    val supportsIdempotency: Boolean,
    val supportsCancellation: Boolean,
    val supportsQuotaReporting: Boolean,
    val maxContextTokens: Int?
)

data class ConnectorRequest(
    val requestId: String,
    val workflowId: String,
    val stepId: String,
    val instruction: String,
    val prompt: String,
    val context: List<Message>,
    val sessionRef: String?,
    val timeoutMs: Long
)

data class ConnectorResponse(
    val requestId: String,
    val content: String,
    val sessionRef: String?,
    val providerRequestRef: String?,
    val completionStatus: CompletionStatus,
    val usage: UsageMetrics?
)

enum class CompletionStatus { COMPLETE, PARTIAL, UNKNOWN }
enum class ErrorCode {
    TRANSIENT, QUOTA_EXCEEDED, USER_ACTION_REQUIRED,
    ADAPTER_BROKEN, INPUT_REJECTED, OUTCOME_UNKNOWN, PERMANENT_FAILURE
}
```

### Dữ liệu bổ sung và bất biến

- Message: role, content, origin, messageId; nguồn và nội dung từ AI là dữ liệu không đáng tin cậy, không được trao quyền thực thi hành động. Context gồm câu hỏi gốc, kết quả các chặng, lịch sử phù hợp; không tự tiết lộ bí mật.
- ConnectorStatus: READY, WAITING_QUOTA, USER_ACTION_REQUIRED, DISABLED, UNKNOWN; thời gian thử lại nếu nguồn đáng tin có cung cấp, lý do và thời điểm quan sát. UNKNOWN không đồng nghĩa READY.
- ConnectorEvent: Started, Delta, Completed(response), Failed(error). Một yêu cầu có tối đa một sự kiện kết thúc; nếu stream bị gián đoạn trước sự kiện kết thúc, lưu partial và đánh giá OUTCOME_UNKNOWN nếu đã gửi hoặc không chắc chắn.
- Error: code, safeMessage, retryAt (nullable), deliveryState (NOT_SENT, ACCEPTED, UNKNOWN), reason; INPUT_REJECTED phân biệt quá dài, sai định dạng và từ chối chính sách. Chỉ rút gọn khi thực sự quá giới hạn ngữ cảnh; không sửa để né từ chối.
- ReconciliationResult: Completed(response), InProgress, DefinitelyNotSent, Unknown, Unsupported. Không suy diễn DefinitelyNotSent từ timeout hoặc không thấy kết quả.
- UsageMetrics: số token/hạn mức nếu biết; null nếu không đo được. Không giả lập số liệu nhà cung cấp.
- requestId ổn định với cùng yêu cầu logic và payload, attemptId riêng cho mỗi lần thử (lưu trong checkpoint). Nếu thay đổi payload, tạo requestId mới sau khi xử lý trạng thái yêu cầu cũ. Idempotency là năng lực riêng với reconciliation và chỉ có giá trị trong phạm vi/ thời hạn được nhà cung cấp bảo đảm. Không tự động gửi lại khi không chứng minh được an toàn.
- sessionRef là dữ liệu mờ, gắn với connectorId + connectionIdentityId; không dùng chéo danh tính/nhà cung cấp. Lưu tham chiếu ngoài vòng đời Connector. Không chứa token/cookie thô.
- Stateful: chỉ gửi delta khi xác nhận phiên có đúng lịch sử cần thiết. Stateless hoặc phiên mới: nhận context từ Local Memory. Nếu phiên cũ thiếu hoặc lệch lịch sử, dùng context được tái tạo và không giả định phiên đã đồng bộ.
- connect/disconnect giải phóng tài nguyên; disconnect không đồng nghĩa đăng xuất, hủy hoặc xóa phiên. Nếu không hỗ trợ reconcile/cancel, trả Unsupported hoặc báo giới hạn rõ ràng. Hủy cục bộ không chứng minh nhà cung cấp đã dừng.
- Connector không tự thử lại nếu yêu cầu có thể đã được gửi. Engine là chủ sở hữu chính sách retry, cancellation và routing.

## 4. State Machine và Checkpoint

Trạng thái workflow: READY, RUNNING, WAITING_APPROVAL, PAUSED_FOR_USER, WAITING_QUOTA, RECONCILING, RESUMING, COMPLETED, FAILED, CANCELLED.

READY → RUNNING sau khi lưu checkpoint đầu tiên.
RUNNING → WAITING_APPROVAL trước mỗi lần gửi trong chế độ duyệt; chấp thuận thì lưu payload chính xác rồi thực thi.
RUNNING → COMPLETED chỉ khi chặng cuối có kết quả COMPLETE và đã commit vào Local Memory.
RUNNING → RUNNING nếu còn chặng và kết quả COMPLETE đã lưu nguyên tử.
RUNNING → PAUSED_FOR_USER khi xác thực, CAPTCHA, từ chối chia sẻ dữ liệu hoặc cần xác nhận gửi lại.
RUNNING → WAITING_QUOTA khi không còn tuyến hợp lệ; không bận chờ, lưu retryAt nếu biết.
RUNNING → RECONCILING nếu không rõ kết quả yêu cầu đã gửi.
RECONCILING → RUNNING nếu kết quả hoàn chỉnh được đối soát và lưu hoặc đã xác minh chưa gửi; → PAUSED_FOR_USER nếu không thể đối soát an toàn; InProgress tiếp tục chờ có giới hạn.
WAITING_APPROVAL / PAUSED_FOR_USER / WAITING_QUOTA → RESUMING khi người dùng hoặc điều kiện hợp lệ cho phép.
RESUMING → RECONCILING nếu có yêu cầu chưa rõ kết quả; nếu không, đánh giá lại tuyến rồi RUNNING hoặc trạng thái chờ tương ứng.
Bất kỳ trạng thái chưa kết thúc → CANCELLED theo yêu cầu người dùng; → FAILED nếu lỗi không thể khôi phục. Không tự gửi lại sau cancellation.

Checkpoint lưu workflowId, stepIndex, stepId, requestId, attemptId, connectorId, connectionIdentityId, sessionRef, providerRequestRef nếu có, trạng thái, deliveryState, payload đã duyệt, các kết quả complete/partial, lý do dừng và version.

- Lưu ý định gửi trước side effect; nếu process chết giữa gửi và ghi nhận, trạng thái mặc định là chưa rõ và cần đối soát.
- Commit kết quả hoàn chỉnh và chuyển stepIndex trong cùng giao dịch; unique key workflowId + stepId chống xử lý hai lần.
- Chỉ một runner được giữ lease/claim của workflow; khi khôi phục lease phải đối soát yêu cầu đang dang dở trước mọi lần gửi.
- Checkpoint không quyết định nhà cung cấp tiếp theo. Routing kiểm tra trạng thái mới tại lúc resume, không tin quota snapshot cũ. Khi reconcile phải dùng đúng kết nối ban đầu.
- Không chuyển tiếp PARTIAL/UNKNOWN như kết quả cuối. Không tự gửi lại sang nhà cung cấp khác khi yêu cầu cũ còn chưa rõ.
- Lưu lịch sử cục bộ có thể bị người dùng xóa; phiên bên nhà cung cấp quản lý độc lập.

## 5. Error Taxonomy & Routing

| Error | Hành vi |
| --- | --- |
| TRANSIENT | Retry giới hạn, backoff có jitter chỉ khi chưa gửi hoặc idempotency thực sự được bảo đảm; nếu không chuyển RECONCILING. |
| QUOTA_EXCEEDED | Ghi trạng thái quota, chờ hoặc chọn tuyến được phép theo chính sách và sự chấp thuận; nếu delivery chưa rõ, đối soát trước. |
| USER_ACTION_REQUIRED | Dừng tại checkpoint; người dùng tự xác thực, không vượt CAPTCHA hoặc cơ chế hạn chế. |
| ADAPTER_BROKEN | Cô lập Connector; chuyển tuyến nếu hợp lệ và yêu cầu trước đã được xử lý an toàn. |
| INPUT_REJECTED | Kiểm tra nguyên nhân; rút gọn ngữ cảnh nếu quá dài, không né chính sách; không retry mù. |
| OUTCOME_UNKNOWN | Đối soát yêu cầu cũ; nếu không được hỗ trợ, giữ trạng thái chờ quyết định rõ ràng của người dùng. |
| PERMANENT_FAILURE | Không retry tự động; báo nguyên nhân, giữ checkpoint. |

Routing chỉ chọn trong danh sách tuyến được phép cho workflow, xét năng lực, quyền chia sẻ dữ liệu, hạn mức mới nhất và yêu cầu continuity. Việc đổi nhà cung cấp có thể làm thay đổi chất lượng, chi phí và bộ nhớ phía server, nên cần chính sách fallback đã được chấp thuận. Hạn mức không đọc được phải được đánh dấu unknown.

## 6. Android và bảo mật

- Kotlin, Coroutines/Flow, Compose, Room, Android Keystore. Lõi JVM độc lập Android; inject clock, storage, routing và Connector để test.
- Lazy loading: ưu tiên chỉ một Connector hoạt động tại một thời điểm; sessionRef lưu tách rời. Không đảm bảo tiến trình chạy khi tắt màn hình.
- Dùng cơ chế Android nền phù hợp với loại tác vụ và chính sách hiện hành; lưu checkpoint để sống sót khi hệ điều hành dừng tiến trình. Không giả định Foreground Service bảo đảm chạy vô hạn.
- GeckoView hoặc extension chỉ là hướng nghiên cứu khả năng tương thích; không giả định đăng nhập Google trong trình duyệt nhúng được chấp nhận. Ưu tiên luồng xác thực được hỗ trợ. Không lưu mật khẩu thô.
- Adapter từ xa nếu được phép: cấu hình khai báo có schema/version, chữ ký xác thực, origin allowlist, rollback; không thực thi JavaScript hoặc code tải tùy ý từ xa.
- Người dùng xem được nội dung chuyển giao giữa nhà cung cấp và lựa chọn loại trừ dữ liệu nhạy cảm; không gửi cookie, credentials, session token hoặc toàn bộ lịch sử mặc định.
- Không thực hiện hành động bên ngoài chỉ vì đầu ra AI yêu cầu. Câu dẫn do người dùng/ứng dụng thiết lập, phản hồi AI được phân tách khỏi chỉ thị có thẩm quyền.

## 7. Roadmap & nghiệm thu

1. Contract: triển khai kiểu dữ liệu và interface; biên dịch JVM, kiểm thử bất biến, không phụ thuộc Android.
2. Mock Connector: giả lập streaming, success, quota, auth, timeout trước/sau gửi, partial, đối soát unsupported, hủy và sessionRef.
3. Engine + Checkpoint: chạy A → B → C → A; duyệt từng chặng; dừng/khôi phục đúng vị trí; xử lý lỗi không làm mất dữ liệu.
4. Quota & Routing: retry có giới hạn, trạng thái realtime khi resume, fallback hợp lệ, không gửi trùng hoặc vượt hạn mức.
5. Real Connectors: thẩm định điều khoản và khả năng kỹ thuật mỗi hãng; ưu tiên API được hỗ trợ / model offline / chuyển giao thủ công. Thử nghiệm đăng nhập, session, streaming, RAM trên Android thực.
6. Android UI: lịch sử, trạng thái từng chặng, chỉnh câu dẫn, thao tác resume/cancel và thông báo rõ về chia sẻ dữ liệu.

Acceptance tests tối thiểu: full four-step workflow, manual approval, stateless context, stateful session A quay lại, partial không được chuyển tiếp, crash trước/sau send, crash sau complete trước advance, no duplicate after resume, reconciliation unknown/unsupported, quota stale, retry budget exhausted, auth pause, adapter isolation, user cancel, hai runner cạnh tranh, provider identity mismatch, input rejection, redaction. Test bằng fake clock và storage giả lập; bổ sung test process death với Room và đo RAM/pin trên thiết bị thật.

Không phát hành APK trước khi các tiêu chí liên quan được kiểm thử. Các quyết định công nghệ nêu trên là đề xuất, chưa được đo benchmark hoặc xác nhận tương thích thực tế.
