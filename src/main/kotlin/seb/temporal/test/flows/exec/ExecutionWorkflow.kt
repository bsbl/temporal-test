package seb.temporal.test.flows.exec

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.temporal.activity.ActivityOptions
import io.temporal.workflow.QueryMethod
import io.temporal.workflow.SignalMethod
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import io.temporal.workflow.WorkflowQueue
import org.springframework.util.Assert
import seb.temporal.test.ExecutionModel
import seb.temporal.test.utils.logger
import java.math.BigDecimal
import java.time.Duration
import java.util.Deque
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue


@WorkflowInterface
interface ExecutionWorkflow {

    /**
     * Create a new workflow instance to process
     * all fill messaged related to this market/group
     * or orders until it's filled (unless cancelled)
     */
    @WorkflowMethod(name = "ExecutionWorkflow-v1")
    fun new(startObject: ExecutionWorkflowStartObject)

    /**
     * Fill message received.
     */
    @SignalMethod(name = "ExecutionWorkflow-NewFill-v1")
    fun newFill(fillExecMessage: ExecutionModel.FillExecMessage)

    @QueryMethod
    fun orders(): Map<String, UUID>
}

data class ExecutionWorkflowStartObject @JsonCreator constructor(
        @JsonProperty("groupId")  val groupId: UUID,
        @JsonProperty("qty")      val qty: BigDecimal,
        @JsonProperty("orderIds") val orderIds: Map<String, UUID>
)

/**
 * The assumption here is that the fill responses can span across several days or
 * even weeks.
 * Hence, few things have to be considered carefully:
 * - Workflow history: cannot exceed 50K events - it is required to invoke ContinueAsNew
 *   to reset the history (create a new workflow instance different runId)
 * - In case of a failure, history is transferred from the server to the new worker
 *   This can consume a lot of bandwidth so it might be required to use external
 *   storage and to convey keys only within the workflow instance.
 * -
 *
 *
 */
class ExecutionWorkflowImpl: ExecutionWorkflow {

    val log by logger()

    val executionActivity: ExecutionActivity

    private var qty: BigDecimal? = null
    private var groupId: UUID? = null
    private var orderIds: Map<String, UUID>? = null
    private var filled: BigDecimal = BigDecimal.ZERO
    private val filledQueue: WorkflowQueue<ExecutionModel.FillExecMessage>

    init {
        this.executionActivity = Workflow.newActivityStub(
                ExecutionActivity::class.java,
                ActivityOptions.newBuilder()
                        .setTaskQueue(queueName)
                        .setStartToCloseTimeout(Duration.ofSeconds(10))
                        .build())
        filledQueue = Workflow.newQueue(Int.MAX_VALUE)
    }

    companion object {
        const val queueName = "alloc_q";
    }

    override fun new(startObject: ExecutionWorkflowStartObject) {
        log.info("New execution: $startObject")
        this.qty = startObject.qty
        this.groupId = startObject.groupId
        this.orderIds = startObject.orderIds
        // await for all orders to be filled
        // activity methods cannot be called from signals - it does not work
        // so the approach is to rely on a message queue (see io.temporal.samples.hello.HelloSignal)
        while (true) {
            Workflow.await { null != filledQueue.peek() || this.qty!! <= filled }
            if (this.qty!! <= this.filled) {
                log.info("Group is fully filled: $groupId (${this.qty} <= $filled)")
                executionActivity.allocationCompleted(groupId!!, orderIds!!)
                return
            }
            var fillExecMessage = filledQueue.poll()
            while (fillExecMessage != null) {
                log.info("Start of: Processing fill message: $groupId, ${fillExecMessage.qty}, $filled")
                val allocs = executionActivity.allocation(fillExecMessage)
                // send allocations for booking
                executionActivity.booking(allocs)
                this.filled = this.filled + fillExecMessage.qty
                log.info("End of: Processing fill message: $groupId, ${fillExecMessage.qty}, $filled")
                fillExecMessage = filledQueue.poll()
            }
        }
    }

    override fun newFill(fillExecMessage: ExecutionModel.FillExecMessage) {
        log.info("Enqueue new fill message: ${fillExecMessage.groupId} -> ${fillExecMessage.product}:${fillExecMessage.qty}")
        this.filledQueue.offer(fillExecMessage)
    }

    override fun orders(): Map<String, UUID> = this.orderIds ?: emptyMap()

}