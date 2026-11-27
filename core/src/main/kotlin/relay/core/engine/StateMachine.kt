package relay.core.engine

import relay.core.contract.*

enum class Phase { IDLE, RUNNING_STEP, WAITING_REVIEW, PAUSED_FOR_USER, WAITING_QUOTA, COMPLETED, FAILED, CANCELLED }
enum class Mode { AUTO, REVIEW_EACH_STEP }
data class Step(val id: String, val instruction: String, val targets: List<ModelTarget>)
data class Turn(val stepId: String, val target: ModelTarget, val requestId: String, val result: ExecutionResult.Success)
data class Pending(val target: ModelTarget, val request: ExecutionRequest)
data class Workflow(val id: String, val question: String, val mode: Mode, val steps: List<Step>,
    val phase: Phase = Phase.IDLE, val turns: List<Turn> = emptyList(), val pending: Pending? = null,
    val approved: Boolean = false, val retries: Int = 0, val reason: String? = null) {
    val index: Int get() = turns.size
    val terminal: Boolean get() = phase in setOf(Phase.COMPLETED, Phase.FAILED, Phase.CANCELLED)
}
object StateMachine {
    fun move(w: Workflow, phase: Phase, reason: String? = null): Workflow {
        require(!w.terminal || w.phase == phase) { "Terminal workflow" }
        require(phase != Phase.COMPLETED || w.index == w.steps.size && w.pending == null)
        require(phase != Phase.WAITING_REVIEW || w.pending == null)
        return w.copy(phase = phase, reason = reason)
    }
}
