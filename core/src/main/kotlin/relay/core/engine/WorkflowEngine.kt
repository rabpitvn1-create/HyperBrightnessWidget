package relay.core.engine

import kotlinx.coroutines.*
import relay.core.contract.*
import relay.core.persistence.Persistence
import relay.core.quota.QuotaRoutingManager
import java.util.UUID

class WorkflowEngine(private val store: Persistence, private val routing: QuotaRoutingManager,
    private val context: ContextEngine = ContextEngine()) {
    private val checkpoints = CheckpointManager(store)
    private val guard = Any()
    private val active = mutableMapOf<String, Job>()
    fun create(question: String, mode: Mode, steps: List<Step>): Workflow {
        require(question.isNotBlank() && steps.isNotEmpty() && steps.map { it.id }.distinct().size == steps.size)
        steps.forEach { require(it.instruction.isNotBlank() && it.targets.isNotEmpty()); it.targets.forEach(routing::route) }
        return checkpoints.save(Workflow(UUID.randomUUID().toString(), question, mode, steps.toList()))
    }
    fun get(id: String) = checkpoints.read(id)
    fun list() = store.list()
    private fun update(id: String, change: (Workflow) -> Workflow): Workflow = synchronized(guard) {
        val w = get(id); if (w.terminal) w else checkpoints.save(change(w))
    }
    private fun move(id: String, phase: Phase, reason: String? = null) = update(id) { StateMachine.move(it, phase, reason) }
    private suspend fun <T> use(id: String, target: ModelTarget, requestId: String?, block: suspend (AiConnector) -> T): T {
        val connector = routing.route(target).create()
        try { connector.connect(); return block(connector) }
        finally { withContext(NonCancellable) {
            try { if (get(id).phase == Phase.CANCELLED && requestId != null) connector.cancel(requestId) }
            finally { connector.disconnect() }
        } }
    }
    suspend fun cancel(id: String) {
        val job = synchronized(guard) {
            val w = get(id)
            if (!w.terminal) checkpoints.save(StateMachine.move(w, Phase.CANCELLED))
            active[id]
        }
        job?.cancelAndJoin()
    }
    suspend fun run(id: String, approve: Boolean = false): Workflow = coroutineScope {
        val job = currentCoroutineContext()[Job]!!
        synchronized(guard) { check(active.isEmpty()) { "Another workflow is already running" }; active[id] = job }
        try { drive(id, approve) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { move(id, Phase.PAUSED_FOR_USER, "Connector or storage error; check pending request: ${e.javaClass.simpleName}") }
        finally { synchronized(guard) { active.remove(id) } }
    }
    private suspend fun drive(id: String, approve: Boolean): Workflow {
        var w = get(id)
        if (w.terminal) return w
        val authRoute = w.pending?.target?.routeId ?: w.reason?.takeIf { it.startsWith("Authenticate ") }?.removePrefix("Authenticate ")
        if (authRoute != null && routing.status(authRoute).status == ConnectorStatus.AUTH_EXPIRED)
            return move(id, Phase.PAUSED_FOR_USER, "Authenticate $authRoute")
        if (w.pending != null) {
            val p = requireNotNull(w.pending)
            val reconciled = use(id, p.target, p.request.requestId) { connector ->
                if (connector.getCapabilities().supportsReconciliation) connector.reconcile(p.request.requestId) else ReconciliationResult.Unsupported
            }
            w = when (reconciled) {
                is ReconciliationResult.Completed -> synchronized(guard) { val current = get(id); if (current.terminal) current else checkpoints.after(current, reconciled.result) }
                ReconciliationResult.NotSent -> update(id) { it.copy(pending = null) }
                else -> return move(id, Phase.PAUSED_FOR_USER, "Previous outcome not established; no resend")
            }
        }
        if (approve && w.phase == Phase.WAITING_REVIEW) w = update(id) { it.copy(approved = true) }
        while (!w.terminal) {
            currentCoroutineContext().ensureActive()
            val step = w.steps[w.index]
            if (w.mode == Mode.REVIEW_EACH_STEP && !w.approved) return move(id, Phase.WAITING_REVIEW)
            val target = routing.select(step.targets) ?: return when {
                step.targets.any { routing.status(it.routeId).status == ConnectorStatus.AUTH_EXPIRED } -> move(id, Phase.PAUSED_FOR_USER, "Authentication required")
                step.targets.any { routing.status(it.routeId).status == ConnectorStatus.COOLDOWN } -> move(id, Phase.WAITING_QUOTA, "No authorized route available until cooldown")
                else -> move(id, Phase.FAILED, "No healthy authorized route")
            }
            val requestId = UUID.randomUUID().toString()
            val result = use(id, target, requestId) { connector ->
                val status = connector.getStatus()
                if (status != ConnectorStatus.AVAILABLE) {
                    routing.observe(target.routeId, status)
                    val code = when (status) {
                        ConnectorStatus.COOLDOWN -> ErrorCode.QUOTA_EXCEEDED
                        ConnectorStatus.AUTH_EXPIRED -> ErrorCode.AUTH_OR_CHALLENGE_REQUIRED
                        else -> ErrorCode.PROVIDER_BROKEN
                    }
                    ExecutionResult.Failure(code, delivery = Delivery.NOT_SENT)
                } else {
                    val request = try { context.request(w, target, requestId, connector.getCapabilities()) }
                        catch (_: IllegalArgumentException) { return@use ExecutionResult.Failure(ErrorCode.INPUT_REJECTED, delivery = Delivery.NOT_SENT) }
                    synchronized(guard) { val current = get(id); if (!current.terminal) checkpoints.before(current, target, request) }
                    currentCoroutineContext().ensureActive()
                    if (get(id).terminal) throw CancellationException("Cancelled")
                    connector.execute(request)
                }
            }
            if (get(id).terminal) return get(id)
            when (result) {
                is ExecutionResult.Success -> { w = synchronized(guard) { val current = get(id); if (current.terminal) current else checkpoints.after(current, result) } }
                is ExecutionResult.Failure -> {
                    routing.failure(target.routeId, result)
                    if (result.delivery != Delivery.NOT_SENT) return move(id, Phase.PAUSED_FOR_USER, "Outcome uncertain; reconcile before retry")
                    w = update(id) { it.copy(pending = null) }
                    when (result.code) {
                        ErrorCode.AUTH_OR_CHALLENGE_REQUIRED -> return move(id, Phase.PAUSED_FOR_USER, "Authenticate ${target.routeId}")
                        ErrorCode.INPUT_REJECTED -> return move(id, Phase.FAILED, "Input rejected")
                        ErrorCode.RETRYABLE_TRANSIENT -> {
                            if (w.retries >= 2) return move(id, Phase.FAILED, "Retry budget exhausted")
                            w = update(id) { it.copy(retries = it.retries + 1) }; delay(100L * w.retries)
                        }
                        ErrorCode.QUOTA_EXCEEDED -> if (routing.status(target.routeId).status == ConnectorStatus.AVAILABLE) return move(id, Phase.WAITING_QUOTA, "Retry after quota refresh")
                        ErrorCode.PROVIDER_BROKEN -> Unit
                    }
                }
            }
        }
        return w
    }
}
