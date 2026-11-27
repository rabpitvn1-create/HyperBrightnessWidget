package relay.core.mock

import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import relay.core.contract.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

sealed interface Scenario {
    data class Reply(val text: String = "Mock response", val delayMs: Long = 30) : Scenario
    data class Fail(val error: ExecutionResult.Failure) : Scenario
    object CrashAfterAccept : Scenario
}
class SimulatedCrash : CancellationException("Simulated process loss after acceptance")
interface MockLedger {
    fun put(key: String, result: ExecutionResult.Success)
    fun get(key: String): ExecutionResult.Success?
}
class MockBackend(private val ledger: MockLedger? = null) {
    val events = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val scripts = ConcurrentHashMap<String, ConcurrentLinkedQueue<Scenario>>()
    private val completed = ConcurrentHashMap<String, ExecutionResult.Success>()
    fun enqueue(id: String, scenario: Scenario) { scripts.computeIfAbsent(id) { ConcurrentLinkedQueue() }.add(scenario) }
    fun next(id: String): Scenario = scripts[id]?.poll() ?: Scenario.Reply()
    fun complete(id: String, requestId: String, result: ExecutionResult.Success) { val key = "$id/$requestId"; if (ledger != null) ledger.put(key, result) else completed[key] = result }
    fun lookup(id: String, requestId: String) = ledger?.get("$id/$requestId") ?: completed["$id/$requestId"]
}
class ConfigurableMockConnector(private val id: String, private val backend: MockBackend) : AiConnector {
    private var connected = false
    override suspend fun connect() { check(!connected); connected = true; backend.events.add("$id:connect") }
    override fun getCapabilities() = ConnectorCapabilities(false, true, 20000, false, true)
    override suspend fun getStatus() = ConnectorStatus.AVAILABLE
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        check(connected); backend.events.add("$id:execute:${request.requestId}")
        backend.events.add("$id:session:${request.sessionRef}")
        val scenario = backend.next(id)
        if (scenario is Scenario.Fail) return scenario.error
        if (scenario is Scenario.Reply) delay(scenario.delayMs)
        val text = if (scenario is Scenario.Reply) scenario.text else "Recovered mock response"
        val answer = ExecutionResult.Success(text, request.sessionRef ?: "session-${request.requestId}", mapOf("model" to request.modelId, "history" to request.recentHistory))
        backend.complete(id, request.requestId, answer)
        if (scenario == Scenario.CrashAfterAccept) throw SimulatedCrash()
        return answer
    }
    override suspend fun reconcile(requestId: String): ReconciliationResult {
        check(connected); backend.events.add("$id:reconcile:$requestId")
        return backend.lookup(id, requestId)?.let { ReconciliationResult.Completed(it) } ?: ReconciliationResult.Unknown
    }
    override suspend fun cancel(requestId: String) { backend.events.add("$id:cancel:$requestId") }
    override suspend fun disconnect() { connected = false; backend.events.add("$id:disconnect") }
}
