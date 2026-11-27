package relay.core.contract

data class ConnectorCapabilities(val supportsStreaming: Boolean, val maintainsSession: Boolean,
    val maxContextChars: Int, val requiresUserInteraction: Boolean, val supportsReconciliation: Boolean)
enum class ConnectorStatus { AVAILABLE, COOLDOWN, AUTH_EXPIRED, ISOLATED }
enum class ErrorCode { RETRYABLE_TRANSIENT, QUOTA_EXCEEDED, AUTH_OR_CHALLENGE_REQUIRED, PROVIDER_BROKEN, INPUT_REJECTED }
enum class Delivery { NOT_SENT, UNKNOWN }
data class ExecutionRequest(val requestId: String, val stepId: String, val prompt: String,
    val recentHistory: String, val sessionRef: String?, val modelId: String)
sealed interface ExecutionResult {
    data class Success(val content: String, val sessionRef: String?, val metadata: Map<String, String> = emptyMap()) : ExecutionResult
    data class Failure(val code: ErrorCode, val retryAfterMs: Long? = null,
        val delivery: Delivery = Delivery.UNKNOWN) : ExecutionResult
}
sealed interface ReconciliationResult {
    data class Completed(val result: ExecutionResult.Success) : ReconciliationResult
    object NotSent : ReconciliationResult
    object InProgress : ReconciliationResult
    object Unknown : ReconciliationResult
    object Unsupported : ReconciliationResult
}
interface AiConnector {
    suspend fun connect()
    fun getCapabilities(): ConnectorCapabilities
    suspend fun getStatus(): ConnectorStatus
    suspend fun execute(request: ExecutionRequest): ExecutionResult
    suspend fun cancel(requestId: String)
    suspend fun disconnect()
    suspend fun reconcile(requestId: String): ReconciliationResult = ReconciliationResult.Unsupported
}
data class ModelTarget(val routeId: String, val modelId: String)
data class Route(val id: String, val provider: String, val account: String, val models: List<String>, val create: () -> AiConnector)
