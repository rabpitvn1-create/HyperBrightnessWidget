package relay.android

import android.app.Activity
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import android.view.View
import android.widget.*
import relay.core.*
import java.io.*

private class LocalStore(context: Context) : Store {
    private val file = AtomicFile(File(context.filesDir, "relay-checkpoint-v1"))
    override fun load(): Run? = try { ObjectInputStream(file.openRead()).use { it.readObject() as Run } }
        catch (_: FileNotFoundException) { null }
    override fun save(run: Run) {
        val stream = file.startWrite()
        try {
            val output = ObjectOutputStream(stream)
            output.writeObject(run); output.flush(); file.finishWrite(stream)
        } catch (e: Exception) { file.failWrite(stream); throw e }
    }
}

class MainActivity : Activity() {
    private lateinit var engine: Engine
    private lateinit var body: LinearLayout
    private val mock = MockConnector()
    private val prefs by lazy { getSharedPreferences("models", MODE_PRIVATE) }
    private val roles = listOf("Soạn bản nháp", "Kiểm chứng và bổ sung nguồn", "Phản biện, lọc nhiễu", "Tổng hợp câu trả lời cuối")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = Engine(LocalStore(this), Routing(listOf(mock) + Provider.values().filter { it != Provider.MOCK }.map { ManualConnector(it, emptyList()) }))
        safe { render() }
    }
    private fun safe(action: () -> Unit) {
        try { action() } catch (e: Exception) { Toast.makeText(this, "Không thể thực hiện: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
    }
    private fun page() {
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 24, 28, 24) }
        setContentView(ScrollView(this).apply { addView(body) })
        label("Local AI Relay", 24f)
    }
    private fun label(text: String, size: Float = 16f) { body.addView(TextView(this).apply { this.text = text; textSize = size; setPadding(0, 10, 0, 10) }) }
    private fun input(hintText: String, value: String = "") = EditText(this).apply { hint = hintText; setText(value); body.addView(this) }
    private fun button(text: String, action: () -> Unit) { body.addView(Button(this).apply { this.text = text; setOnClickListener { safe(action) } }) }
    private fun copy(text: String) { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Relay", text)) }
    private fun remember(model: Model) {
        if (model.provider == Provider.MOCK) return
        val key = model.provider.name
        val values = prefs.getStringSet(key, emptySet()).orEmpty().toSet() + model.id
        check(prefs.edit().putStringSet(key, values).commit())
    }
    private fun picker(default: Provider = Provider.MOCK, defaultModel: String = ""): () -> Model {
        val providers = Provider.values()
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers.map { it.label })
        body.addView(spinner)
        val model = AutoCompleteTextView(this).apply { hint = "Nhập / chọn tên model"; threshold = 0; setSingleLine(true) }
        body.addView(model)
        fun populate(provider: Provider, initial: String = "") {
            val ids = if (provider == Provider.MOCK) mock.models().map { it.id } else prefs.getStringSet(provider.name, emptySet()).orEmpty().sorted()
            model.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ids))
            model.setText(initial.ifBlank { if (provider == Provider.MOCK) ids.first() else "" }, false)
        }
        spinner.setSelection(default.ordinal)
        populate(default, defaultModel)
        var selected = default
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val next = providers[position]; if (next != selected) { selected = next; populate(next) }
            }
        }
        model.setOnClickListener { model.showDropDown() }
        return {
            val p = providers[spinner.selectedItemPosition]
            Model(p, model.text.toString().trim()).also { if (p == Provider.MOCK) require(it in mock.models()) }
        }
    }
    private fun render() {
        page()
        val run = engine.current()
        if (run == null || run.phase == Phase.COMPLETED || run.phase == Phase.CANCELLED) {
            if (run != null) {
                label("Trạng thái: ${run.phase}")
                run.answers.lastOrNull()?.let { answer -> label("${answer.model.provider.label} / ${answer.model.id}\n${answer.content}"); button("Sao chép kết quả") { copy(answer.content) } }
                label("Bắt đầu lượt mới sẽ thay checkpoint hiện tại.")
            }
            label("Chọn nhà cung cấp và model riêng cho từng chặng. Với web, nhập tên model đúng như trên tài khoản; app không tự đổi model trên website.")
            val question = input("Câu hỏi")
            val choices = roles.mapIndexed { index, role ->
                label("Chặng ${index + 1}")
                val getModel = picker(Provider.MOCK, mock.models()[if (index == 3) 2 else index.coerceAtMost(1)].id)
                val instruction = input("Vai trò", role)
                getModel to instruction
            }
            fun start(auto: Boolean) {
                val steps = choices.map { (model, instruction) -> Step(model(), instruction.text.toString()) }
                if (auto) require(steps.all { it.model.provider == Provider.MOCK })
                steps.forEach { remember(it.model) }
                engine.start(question.text.toString(), steps)
                if (auto) repeat(steps.size) { engine.dispatch() }
                render()
            }
            button("Bắt đầu — duyệt từng chặng") { start(false) }
            button("Chạy tự động (chỉ Mock offline)") { start(true) }
            return
        }
        val step = requireNotNull(run.step)
        label("Chặng ${run.index + 1}/${run.steps.size} · ${run.phase}\n${step.model.provider.label} / ${step.model.id}")
        when (run.phase) {
            Phase.READY -> {
                val select = picker(step.model.provider, step.model.id)
                button("Đổi model / nhà cung cấp trước khi gửi") { val m = select(); remember(m); engine.changeRouteBeforeSending(m); render() }
                val prompt = input("Nội dung gửi", engine.prompt(run))
                button("Duyệt nội dung và tiếp tục") { engine.dispatch(prompt.text.toString()); render() }
            }
            Phase.WAITING_REPLY -> {
                if (step.model.provider == Provider.MOCK) button("Khôi phục Mock") { engine.recoverMock(); render() }
                else {
                    label("Chọn đúng model ${step.model.id} trên website. Chỉ sao chép phần dữ liệu muốn chia sẻ; không gửi trùng nếu đã gửi.")
                    button("Sao chép prompt đã duyệt") { copy(requireNotNull(run.pendingPrompt)) }
                    button("Mở website nhà cung cấp") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(requireNotNull(step.model.provider.url)))) }
                    val reply = input("Dán phản hồi hoàn chỉnh của model đã chọn")
                    val confirm = CheckBox(this).apply { text = "Tôi xác nhận đúng model và phản hồi đã hoàn chỉnh"; body.addView(this) }
                    button("Lưu phản hồi và chuyển chặng") { require(confirm.isChecked); engine.accept(Answer(requireNotNull(run.requestId), step.model, reply.text.toString())); render() }
                    button("Tạm dừng do hạn mức") { engine.pauseQuota(); render() }
                }
            }
            Phase.WAITING_QUOTA -> { label("Đang chờ. Không tự gửi lại hoặc xoay vòng tài khoản."); button("Tiếp tục thủ công") { engine.resumeQuota(); render() } }
            else -> Unit
        }
        run.answers.lastOrNull()?.let { label("Kết quả chặng trước:\n${it.content}") }
        button("Hủy workflow") { engine.cancel(); render() }
    }
}
