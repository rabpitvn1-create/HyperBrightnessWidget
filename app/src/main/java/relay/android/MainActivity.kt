package relay.android

import android.app.Activity
import android.os.Bundle
import android.widget.*
import kotlinx.coroutines.*
import relay.core.contract.*
import relay.core.engine.*
import relay.core.mock.*
import relay.core.quota.QuotaRoutingManager

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: SqlitePersistence
    private lateinit var backend: MockBackend
    private lateinit var routing: QuotaRoutingManager
    private lateinit var engine: WorkflowEngine
    private lateinit var body: LinearLayout
    private var selected: String? = null
    private var runner: Job? = null
    private var statusView: TextView? = null
    private val ids = listOf("A", "B", "B2", "C", "D")
    private val roles = listOf("Soạn bản nháp", "Kiểm chứng", "Phản biện", "Tổng hợp")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SqlitePersistence(this)
        backend = MockBackend(store.ledger)
        routing = QuotaRoutingManager(store, ids.map { id -> Route(id, if (id == "B2") "B" else id, "mock-$id", listOf("fast", "deep")) { ConfigurableMockConnector(id, backend) } })
        engine = WorkflowEngine(store, routing)
        selected = savedInstanceState?.getString("selected")?.takeIf { store.get(it) != null }
        render()
        scope.launch {
            while (isActive) {
                val id = selected
                if (id != null && statusView != null) {
                    val w = engine.get(id); statusView?.text = status(w)
                    if (runner?.isActive != true && w.phase == Phase.WAITING_QUOTA && routing.select(w.steps[w.index].targets) != null) runSelected()
                }
                delay(250)
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("selected", selected); super.onSaveInstanceState(outState) }
    override fun onDestroy() { scope.coroutineContext[Job]?.invokeOnCompletion { store.close() }; scope.cancel(); super.onDestroy() }
    private fun page() {
        statusView = null
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        setContentView(ScrollView(this).apply {
            addView(body)
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 30) { val i = insets.getInsets(android.view.WindowInsets.Type.systemBars()); view.setPadding(i.left, i.top, i.right, i.bottom) }
                insets
            }
        })
        label("Local AI Relay · Mock Lab", 23f)
        label("Toàn bộ connector là mô phỏng offline. Không đăng nhập hay gửi dữ liệu cho AI thật.")
    }
    private fun label(value: String, size: Float = 16f) = TextView(this).apply { text = value; textSize = size; setPadding(0, 8, 0, 8); body.addView(this) }
    private fun input(hintText: String, value: String = "") = EditText(this).apply { hint = hintText; setText(value); body.addView(this) }
    private fun button(value: String, action: () -> Unit) { body.addView(Button(this).apply { text = value; setOnClickListener { try { action() } catch (e: Exception) { notify(e.message ?: "Lỗi") } } }) }
    private fun notify(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    private fun spinner(values: List<String>, initial: Int = 0): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values); setSelection(initial); body.addView(this)
    }
    private fun render() { if (isDestroyed || !scope.isActive) return; page(); val id = selected; if (id == null) home() else detail(engine.get(id)) }
    private fun home() {
        label("Hội thoại đã lưu")
        engine.list().forEach { w -> button("${w.question.take(45)} · ${w.phase}") { selected = w.id; render() } }
        label("Tạo hội thoại mới", 20f)
        val question = input("Nhập câu hỏi")
        val mode = spinner(Mode.values().map { it.name })
        val defaults = listOf("A", "B", "C", "A")
        val fields = roles.mapIndexed { i, role ->
            label("Chặng ${i+1}")
            Triple(spinner(ids, ids.indexOf(defaults[i])), spinner(listOf("fast", "deep"), 1), input("Vai trò", role))
        }
        val fallback = CheckBox(this).apply { text = "Cho phép tuyến dự phòng Mock B2 / D"; isChecked = true; body.addView(this) }
        button("Tạo workflow") {
            val steps = fields.mapIndexed { i, (route, model, instruction) ->
                val primary = ids[route.selectedItemPosition]; val modelId = model.selectedItem.toString()
                val allowed = if (fallback.isChecked) (listOf(primary) + (if (primary == "B") listOf("B2", "D") else listOf("D"))).distinct() else listOf(primary)
                Step("step-${i+1}", instruction.text.toString(), allowed.map { ModelTarget(it, modelId) })
            }
            selected = engine.create(question.text.toString(), Mode.values()[mode.selectedItemPosition], steps).id; render()
        }
    }
    private fun status(w: Workflow): String = buildString {
        append("${w.phase} · ${w.index}/${w.steps.size}\n"); w.reason?.let { append(it).append('\n') }
        w.steps.forEachIndexed { i, s ->
            val turn = w.turns.getOrNull(i)
            append("${i+1}. ${s.instruction}: ${turn?.target?.routeId ?: w.pending?.takeIf { it.request.stepId == s.id }?.target?.routeId ?: s.targets.first().routeId}/${turn?.target?.modelId ?: w.pending?.takeIf { it.request.stepId == s.id }?.target?.modelId ?: s.targets.first().modelId}")
            append(if (turn != null) " ✓\n" else "\n")
        }
        append("\nTrạng thái Mock:\n")
        ids.forEach { id -> val q = routing.status(id); append("$id: ${q.status}"); if (q.status == ConnectorStatus.AUTH_EXPIRED) append(" (PAUSED_FOR_USER)"); q.availableAt?.let { append(" (${((it-System.currentTimeMillis()).coerceAtLeast(0)+999)/1000}s)") }; append('\n') }
    }
    private fun runSelected(approve: Boolean = false) {
        if (runner?.isActive == true) return
        val id = selected ?: return
        runner = scope.launch {
            try { engine.run(id, approve) }
            catch (_: SimulatedCrash) { notify("Đã mô phỏng mất tiến trình. Bấm khôi phục để đối soát.") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { notify(e.message ?: "Lỗi workflow") }
            finally { render() }
        }
    }
    private fun detail(w: Workflow) {
        button("Danh sách hội thoại") { if (runner?.isActive == true) notify("Hãy chờ hoặc hủy workflow đang chạy") else { selected = null; render() } }
        label(w.question, 20f); label(w.mode.name)
        statusView = label(status(w))
        if (!w.terminal) {
            button(if (w.phase == Phase.WAITING_REVIEW) "Duyệt chặng" else "Chạy / khôi phục checkpoint") { runSelected(engine.get(w.id).phase == Phase.WAITING_REVIEW) }
            button("Hủy workflow") { scope.launch { engine.cancel(w.id); render() } }
            val route = spinner(ids, ids.indexOf(w.steps[w.index].targets.first().routeId).coerceAtLeast(0))
            fun simulate(scenario: Scenario) { backend.enqueue(route.selectedItem.toString(), scenario); notify("Đã xếp sự cố cho lần gọi Mock tiếp theo") }
            fun fail(code: ErrorCode, retry: Long? = null) = simulate(Scenario.Fail(ExecutionResult.Failure(code, retry, Delivery.NOT_SENT)))
            label("Giả lập sự cố cho tuyến đã chọn")
            button("Hết quota (30 giây)") { fail(ErrorCode.QUOTA_EXCEEDED, 30000) }
            button("Yêu cầu xác thực") { fail(ErrorCode.AUTH_OR_CHALLENGE_REQUIRED) }
            button("Lỗi mạng tạm thời") { fail(ErrorCode.RETRYABLE_TRANSIENT) }
            button("Lỗi tích hợp") { fail(ErrorCode.PROVIDER_BROKEN) }
            button("Mất tiến trình sau khi nhận yêu cầu") { simulate(Scenario.CrashAfterAccept) }
            button("Phản hồi chậm (10 giây)") { simulate(Scenario.Reply("Slow mock result", 10000)) }
            button("Xác thực lại Mock đã chọn") { routing.authenticated(route.selectedItem.toString()); render() }
        }
        w.turns.forEach { t -> label("${t.stepId} · ${t.target.routeId}/${t.target.modelId}\n${t.result.content}") }
    }
}
