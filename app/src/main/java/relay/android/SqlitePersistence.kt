package relay.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import relay.core.contract.*
import relay.core.engine.*
import relay.core.mock.MockLedger
import relay.core.persistence.Persistence
import relay.core.quota.QuotaRecord

private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (k,v) -> put(k, v ?: JSONObject.NULL) } }
private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else getString(key)
private fun <T> JSONArray.mapItems(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
private fun ModelTarget.json() = obj("route" to routeId, "model" to modelId)
private fun JSONObject.target() = ModelTarget(getString("route"), getString("model"))
private fun ExecutionRequest.json() = obj("id" to requestId, "step" to stepId, "prompt" to prompt, "history" to recentHistory, "session" to sessionRef, "model" to modelId)
private fun JSONObject.request() = ExecutionRequest(getString("id"), getString("step"), getString("prompt"), getString("history"), optional("session"), getString("model"))
private fun ExecutionResult.Success.json() = obj("content" to content, "session" to sessionRef, "metadata" to JSONObject(metadata))
private fun JSONObject.answer(): ExecutionResult.Success {
    val meta = getJSONObject("metadata")
    return ExecutionResult.Success(getString("content"), optional("session"), meta.keys().asSequence().associateWith { meta.getString(it) })
}
private fun Workflow.json() = obj("id" to id, "question" to question, "mode" to mode.name, "phase" to phase.name,
    "steps" to JSONArray(steps.map { s -> obj("id" to s.id, "instruction" to s.instruction, "targets" to JSONArray(s.targets.map { it.json() })) }),
    "turns" to JSONArray(turns.map { t -> obj("step" to t.stepId, "target" to t.target.json(), "request" to t.requestId, "result" to t.result.json()) }),
    "pending" to pending?.let { obj("target" to it.target.json(), "request" to it.request.json()) },
    "approved" to approved, "retries" to retries, "reason" to reason)
private fun JSONObject.workflow(): Workflow = Workflow(getString("id"), getString("question"), Mode.valueOf(getString("mode")),
    getJSONArray("steps").mapItems { Step(it.getString("id"), it.getString("instruction"), it.getJSONArray("targets").mapItems { t -> t.target() }) },
    Phase.valueOf(getString("phase")), getJSONArray("turns").mapItems { Turn(it.getString("step"), it.getJSONObject("target").target(), it.getString("request"), it.getJSONObject("result").answer()) },
    optJSONObject("pending")?.let { Pending(it.getJSONObject("target").target(), it.getJSONObject("request").request()) },
    getBoolean("approved"), getInt("retries"), optional("reason"))

class SqlitePersistence(context: Context) : SQLiteOpenHelper(context.applicationContext, "relay-v2.db", null, 1), Persistence {
    override fun onCreate(db: SQLiteDatabase) { listOf("workflows", "quotas", "mock_ledger").forEach { db.execSQL("CREATE TABLE $it (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)") } }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Migration required") }
    private fun read(table: String, id: String): JSONObject? = readableDatabase.query(table, arrayOf("payload"), "id = ?", arrayOf(id), null, null, null).use {
        if (it.moveToFirst()) JSONObject(it.getString(0)) else null
    }
    private fun write(table: String, id: String, value: JSONObject) {
        val row = ContentValues().apply { put("id", id); put("payload", value.toString()) }
        check(writableDatabase.insertWithOnConflict(table, null, row, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    override fun get(id: String) = read("workflows", id)?.workflow()
    override fun list(): List<Workflow> = readableDatabase.query("workflows", arrayOf("payload"), null, null, null, null, "rowid DESC").use { c ->
        buildList { while (c.moveToNext()) add(JSONObject(c.getString(0)).workflow()) }
    }
    override fun save(workflow: Workflow) = write("workflows", workflow.id, workflow.json())
    override fun quota(routeId: String) = read("quotas", routeId)?.let { QuotaRecord(ConnectorStatus.valueOf(it.getString("status")), if (it.isNull("at")) null else it.getLong("at")) }
    override fun saveQuota(routeId: String, record: QuotaRecord) = write("quotas", routeId, obj("status" to record.status.name, "at" to record.availableAt))
    val ledger = object : MockLedger {
        override fun put(key: String, result: ExecutionResult.Success) = write("mock_ledger", key, result.json())
        override fun get(key: String) = read("mock_ledger", key)?.answer()
    }
}
