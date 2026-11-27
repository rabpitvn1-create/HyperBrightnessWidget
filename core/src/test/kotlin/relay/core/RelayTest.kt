package relay.core

import java.io.*

private class Memory : Store {
    private var run: Run? = null
    override fun load() = run
    override fun save(run: Run) { this.run = run }
}
fun main() {
    val store = Memory()
    val mock = MockConnector()
    val routing = Routing(listOf(mock, ManualConnector(Provider.CHATGPT, emptyList()), ManualConnector(Provider.GEMINI, emptyList())))
    fun engine() = Engine(store, routing)
    val a = mock.models()[0]; val b = mock.models()[1]; val c = mock.models()[2]
    var e = engine()
    e.start("test", listOf(a,b,c,a).map { Step(it,"role") })
    repeat(4) { e.dispatch() }
    check(e.current()!!.phase == Phase.COMPLETED)
    check(e.current()!!.answers.map { it.model } == listOf(a,b,c,a))
    check(e.current()!!.answers.last().content.contains("Original question"))
    println("PASS 4 steps / model identity / context")
    val g = Model(Provider.GEMINI,"custom-gemini"); val h = Model(Provider.CHATGPT,"custom-chatgpt")
    e.start("manual", listOf(Step(g,"draft"),Step(h,"review")))
    val pending = e.dispatch("edited prompt")
    val bytes = ByteArrayOutputStream().also { buffer -> ObjectOutputStream(buffer).use { it.writeObject(pending) } }.toByteArray()
    val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as Run }
    check(restored == pending); store.save(restored)
    e = engine(); check(e.current() == pending)
    check(runCatching { e.dispatch() }.isFailure)
    e.pauseQuota(); e = engine(); check(e.current()!!.phase == Phase.WAITING_QUOTA)
    check(e.resumeQuota().requestId == pending.requestId)
    check(runCatching { e.accept(Answer(pending.requestId!!,h,"wrong")) }.isFailure)
    val answer = Answer(pending.requestId!!,g,"real manual reply")
    e.accept(answer)
    check(runCatching { e.accept(answer) }.isFailure)
    check(e.prompt(e.current()!!).contains("real manual reply"))
    e.changeRouteBeforeSending(a); e.dispatch()
    check(e.current()!!.phase == Phase.COMPLETED)
    println("PASS restart / quota pause / no duplicate / model mismatch / routing")
    e.start("cancel",listOf(Step(h,"draft"))); e.dispatch(); e.cancel()
    check(runCatching { e.dispatch() }.isFailure)
    println("PASS cancellation")
    e.start("mock recovery",listOf(Step(a,"draft")))
    val ready = e.current()!!
    store.save(ready.copy(phase=Phase.WAITING_REPLY,requestId="crash-id",pendingPrompt=e.prompt(ready)))
    e = engine(); check(e.recoverMock().answers.single().requestId == "crash-id")
    println("PASS mock crash recovery")
}
