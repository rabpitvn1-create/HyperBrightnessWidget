package relay.core

import kotlinx.coroutines.*
import relay.core.contract.*
import relay.core.engine.*
import relay.core.mock.*
import relay.core.persistence.*
import relay.core.quota.*

private class Fixture {
    val store = MemoryPersistence()
    val backend = MockBackend()
    var time = 1000L
    val routes = listOf("A", "B", "C", "D").map { id -> Route(id, id, "mock-$id", listOf("fast", "deep")) { ConfigurableMockConnector(id, backend) } }
    val routing = QuotaRoutingManager(store, routes) { time }
    fun engine() = WorkflowEngine(store, routing)
    fun step(id: String, vararg targets: String) = Step(id, "role-$id", targets.map { ModelTarget(it, "deep") })
    fun fail(id: String, code: ErrorCode, retry: Long? = null, delivery: Delivery = Delivery.NOT_SENT) = backend.enqueue(id, Scenario.Fail(ExecutionResult.Failure(code, retry, delivery)))
}
fun main() = runBlocking {
    run {
        val f = Fixture(); val e = f.engine()
        val w = e.create("question", Mode.AUTO, listOf(f.step("1", "A"), f.step("2", "B"), f.step("3", "C"), f.step("4", "A")))
        val done = e.run(w.id); check(done.phase == Phase.COMPLETED)
        check(done.turns.map { it.target.routeId } == listOf("A", "B", "C", "A"))
        check(done.turns[0].result.sessionRef == done.turns[3].result.sessionRef)
        check(f.backend.events.filterNot { ":session:" in it }.map { it.substringBeforeLast(":").takeIf { _ -> ":execute:" in it } ?: it } ==
            listOf("A", "B", "C", "A").flatMap { listOf("$it:connect", "$it:execute", "$it:disconnect") })
        check(f.backend.events.contains("A:session:${done.turns[0].result.sessionRef}"))
        println("PASS 1: four stages, session and lazy loading")
    }
    run {
        val f = Fixture(); val e = f.engine(); f.fail("B", ErrorCode.QUOTA_EXCEEDED, 5000)
        val w = e.create("q", Mode.AUTO, listOf(f.step("1", "A"), f.step("2", "B", "D"), f.step("3", "C"), f.step("4", "A")))
        val done = e.run(w.id); check(done.phase == Phase.COMPLETED && done.turns[1].target.routeId == "D")
        check(f.routing.status("B") == QuotaRecord(ConnectorStatus.COOLDOWN, 6000))
        check(done.turns[1].result.metadata.getValue("history").contains(done.turns[0].result.content))
        f.time = 6000; check(f.routing.status("B").status == ConnectorStatus.AVAILABLE)
        println("PASS 2: quota isolation, authorized fallback, context and expiry")
    }
    run {
        val f = Fixture(); f.fail("B", ErrorCode.AUTH_OR_CHALLENGE_REQUIRED)
        val e = f.engine(); val w = e.create("q", Mode.AUTO, listOf(f.step("1", "A"), f.step("2", "B", "D")))
        val paused = e.run(w.id); check(paused.phase == Phase.PAUSED_FOR_USER && paused.index == 1 && paused.pending == null)
        val restored = f.engine(); check(restored.run(w.id).phase == Phase.PAUSED_FOR_USER)
        f.routing.authenticated("B"); val done = restored.run(w.id)
        check(done.phase == Phase.COMPLETED && done.turns[1].target.routeId == "B")
        println("PASS 3: auth pause, checkpoint and resume same stage")
    }
    run {
        val f = Fixture(); f.backend.enqueue("A", Scenario.CrashAfterAccept)
        val e = f.engine(); val w = e.create("q", Mode.AUTO, listOf(f.step("1", "A")))
        check(runCatching { e.run(w.id) }.exceptionOrNull() is SimulatedCrash)
        val pending = f.store.get(w.id)!!.pending!!
        val done = f.engine().run(w.id); check(done.phase == Phase.COMPLETED && done.turns.single().requestId == pending.request.requestId)
        check(f.backend.events.count { ":execute:" in it } == 1 && f.backend.events.contains("A:reconcile:${pending.request.requestId}"))
        println("PASS 4: crash reconciliation without duplicate send")
    }
    run {
        val f = Fixture(); val e = f.engine(); f.backend.enqueue("A", Scenario.Reply("slow", 10000))
        val w = e.create("q", Mode.AUTO, listOf(f.step("1", "A")))
        val job = launch { e.run(w.id) }
        withTimeout(2000) { while (f.store.get(w.id)!!.pending == null) delay(5) }
        val pending = f.store.get(w.id)!!.pending!!; e.cancel(w.id); job.join()
        check(e.get(w.id).phase == Phase.CANCELLED && f.backend.events.contains("A:cancel:${pending.request.requestId}"))
        check(f.backend.events.last() == "A:disconnect")
        println("PASS 5: cancel and disconnect")
    }
    run {
        val f = Fixture(); val e = f.engine()
        val first = e.create("one", Mode.AUTO, listOf(f.step("1", "A"))); e.run(first.id)
        val second = e.create("two", Mode.AUTO, listOf(f.step("1", "B"))); e.run(second.id)
        val restored = f.engine(); check(restored.list().size == 2 && restored.get(first.id).question == "one" && restored.get(second.id).question == "two")
        check(restored.get(first.id).turns.single().target.routeId == "A" && restored.get(second.id).turns.single().target.routeId == "B")
        println("PASS 6: separate conversations and histories")
    }
    run {
        val f = Fixture(); val e = f.engine(); f.fail("A", ErrorCode.PROVIDER_BROKEN); f.fail("D", ErrorCode.RETRYABLE_TRANSIENT)
        val w = e.create("q", Mode.REVIEW_EACH_STEP, listOf(f.step("1", "A", "D"), f.step("2", "C")))
        check(e.run(w.id).phase == Phase.WAITING_REVIEW && f.backend.events.isEmpty())
        check(e.run(w.id, true).phase == Phase.WAITING_REVIEW && e.get(w.id).index == 1)
        check(f.routing.status("A").status == ConnectorStatus.ISOLATED && e.run(w.id, true).phase == Phase.COMPLETED)
        val unknown = e.create("q2", Mode.AUTO, listOf(f.step("1", "B", "D"))); f.fail("B", ErrorCode.RETRYABLE_TRANSIENT, delivery = Delivery.UNKNOWN)
        check(e.run(unknown.id).phase == Phase.PAUSED_FOR_USER); val count = f.backend.events.count { ":execute:" in it }
        check(e.run(unknown.id).phase == Phase.PAUSED_FOR_USER && f.backend.events.count { ":execute:" in it } == count)
        println("PASS 7: review, broken isolation, transient retry, unknown delivery safety")
    }
    Unit
}
