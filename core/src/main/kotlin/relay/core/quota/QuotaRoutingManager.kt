package relay.core.quota

import relay.core.contract.*
import relay.core.persistence.Persistence

data class QuotaRecord(val status: ConnectorStatus, val availableAt: Long? = null)
class QuotaRoutingManager(private val store: Persistence, val routes: List<Route>,
    private val now: () -> Long = System::currentTimeMillis) {
    init { require(routes.map { it.id }.distinct().size == routes.size) }
    fun route(target: ModelTarget): Route = routes.single { it.id == target.routeId }.also { require(target.modelId in it.models) }
    @Synchronized fun status(id: String): QuotaRecord {
        val record = store.quota(id) ?: QuotaRecord(ConnectorStatus.AVAILABLE)
        if (record.status == ConnectorStatus.COOLDOWN && record.availableAt?.let { it <= now() } == true) {
            return QuotaRecord(ConnectorStatus.AVAILABLE).also { store.saveQuota(id, it) }
        }
        return record
    }
    fun select(targets: List<ModelTarget>): ModelTarget? = targets.firstOrNull { route(it); status(it.routeId).status == ConnectorStatus.AVAILABLE }
    @Synchronized fun observe(id: String, status: ConnectorStatus) {
        if (status != ConnectorStatus.AVAILABLE) store.saveQuota(id, QuotaRecord(status))
    }
    @Synchronized fun failure(id: String, error: ExecutionResult.Failure) {
        val record = when (error.code) {
            ErrorCode.QUOTA_EXCEEDED -> QuotaRecord(ConnectorStatus.COOLDOWN, error.retryAfterMs?.coerceIn(0, Long.MAX_VALUE - now())?.let { now() + it })
            ErrorCode.AUTH_OR_CHALLENGE_REQUIRED -> QuotaRecord(ConnectorStatus.AUTH_EXPIRED)
            ErrorCode.PROVIDER_BROKEN -> QuotaRecord(ConnectorStatus.ISOLATED)
            else -> return
        }
        store.saveQuota(id, record)
    }
    // Call only after the connection has actually been reauthenticated (mock in this MVP).
    @Synchronized fun authenticated(id: String) { if (status(id).status == ConnectorStatus.AUTH_EXPIRED) store.saveQuota(id, QuotaRecord(ConnectorStatus.AVAILABLE)) }
}
