package relay.core.engine

import relay.core.persistence.Persistence
import relay.core.contract.*

class CheckpointManager(private val store: Persistence) {
    fun read(id: String): Workflow = requireNotNull(store.get(id))
    fun save(w: Workflow): Workflow = w.also { store.save(it) }
    fun before(w: Workflow, target: ModelTarget, request: ExecutionRequest): Workflow =
        save(StateMachine.move(w.copy(pending = Pending(target, request)), Phase.RUNNING_STEP))
    fun after(w: Workflow, result: ExecutionResult.Success): Workflow {
        val p = requireNotNull(w.pending)
        val next = w.copy(turns = w.turns + Turn(p.request.stepId, p.target, p.request.requestId, result),
            pending = null, approved = false, retries = 0)
        return save(StateMachine.move(next, if (next.index == next.steps.size) Phase.COMPLETED else Phase.IDLE))
    }
}
