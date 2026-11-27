package relay.core.persistence

import relay.core.engine.Workflow
import relay.core.quota.QuotaRecord

interface Persistence {
    fun get(id: String): Workflow?
    fun list(): List<Workflow>
    fun save(workflow: Workflow)
    fun quota(routeId: String): QuotaRecord?
    fun saveQuota(routeId: String, record: QuotaRecord)
}
class MemoryPersistence : Persistence {
    private val workflows = linkedMapOf<String, Workflow>()
    private val quotas = mutableMapOf<String, QuotaRecord>()
    @Synchronized override fun get(id: String) = workflows[id]
    @Synchronized override fun list() = workflows.values.toList()
    @Synchronized override fun save(workflow: Workflow) { workflows[workflow.id] = workflow }
    @Synchronized override fun quota(routeId: String) = quotas[routeId]
    @Synchronized override fun saveQuota(routeId: String, record: QuotaRecord) { quotas[routeId] = record }
}
