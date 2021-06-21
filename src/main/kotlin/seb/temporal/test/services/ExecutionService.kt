package seb.temporal.test.services

import com.datastax.oss.driver.api.core.uuid.Uuids
import io.temporal.activity.ActivityOptions
import io.temporal.api.enums.v1.WorkflowIdReusePolicy
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowExecutionAlreadyStarted
import io.temporal.client.WorkflowOptions
import seb.temporal.test.ExecutionModel
import seb.temporal.test.flows.exec.ExecutionWorkflowStartObject
import seb.temporal.test.inbound.ProductReq
import seb.temporal.test.utils.logger
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Duration
import java.util.Optional
import java.util.Random
import java.util.UUID


interface ExecutionService {
    fun sendExecution(group: ExecutionModel.ExecReq)
}

/**
 * Simulate a synchronous service with random failure (ChaosUnit)
 */
class ExecutionServiceImpl(config: ChaosConfig, private val workflowClient: WorkflowClient) : ChaosUnit<ExecutionModel.FillExecMessage>(config.chaos["execution"]!!), ExecutionService {

    private val log by logger()

    private val random = Random()

    private val queue = "alloc_q"

    /**
     * Simulate an RPC call - this method generates the
     * execution messages received by execution gateway
     */
    override fun sendExecution(group: ExecutionModel.ExecReq) {

        // 1.decide whether it has to crash or not:
        crash("Send execution crash for: ${group.groupId}")

        // simulate execution messages coming from an
        // external system - this is purely to test
        // Temporal.io ability to deal with error and
        // to resume from where it failed previously

        // 2. start the group workflow if not exists:
        createGroupWorkflowInstance(
                group.groupId,
                // overall quantity to fill
                group.orders.fold(BigDecimal("0")) { acc, order -> order.qty.plus(acc) },
                group.orders.map { Pair(it.inboundId, it.orderId) }.toMap()
        )

        // 3. compute the overall total of orders
        val totalPerAssetClass = group.orders
                .groupBy { it.product }
                .mapValues { it.value.fold(BigDecimal("0")) { acc, order -> order.qty + acc } }
                .toMap()

        // 4. decide whether it's a partial or a total fill:
        totalPerAssetClass.entries
                .forEach {
                    // create one workflow per execId:
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hash = digest.digest(it.key.isin.toByteArray())
                    val hashHex = hash.joinToString("") { "%02x".format(it) }
                    val execReqId = "${group.groupId}:$hashHex"
                    //
                    val mockExecutions = buildExecution(execReqId, it.key, it.value, group.groupId)
                    super.offer(mockExecutions)
                }
    }

    private fun createGroupWorkflowInstance(groupId: UUID, qty: BigDecimal, orderIds: Map<String, UUID>) {
        val options = WorkflowOptions.newBuilder()
                .setTaskQueue(queue)
                .setWorkflowId(groupId.toString())
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .build()
        // Create the workflow client stub
        val workflow = workflowClient.newUntypedWorkflowStub("ExecutionWorkflow-v1", options)
        try {
            val we = workflow.start(ExecutionWorkflowStartObject(groupId, qty, orderIds))
            log.info("Workflow started: ${we.runId} for $groupId with $qty ($queue)")
        }
        catch (ex: WorkflowExecutionAlreadyStarted) {
            log.warn("Execution already started for this groupId: $groupId")
        }
    }

    private fun buildExecution(execReqId: String, productReq: ProductReq, qty: BigDecimal, groupId: UUID): List<ExecutionModel.FillExecMessage> {
        log.info("Create the list of executions: $qty")
        val filledId = Uuids.timeBased()
        // 1. sometimes we want to send an single exec to fill the whole group:
        if (random.nextInt(100) == 0) {
            return listOf(ExecutionModel.FillExecMessage(filledId, execReqId, groupId, productReq, qty))
        }
        // 2. simulate partial executions:
        val nbFill = (random.nextInt(5)+1)
        val nbPartialFill = BigDecimal.valueOf(nbFill.toLong())
        val bunchQty = qty.divide(nbPartialFill, 2, RoundingMode.DOWN)
        val execReqs = (2..nbFill).map {
            ExecutionModel.FillExecMessage(filledId, execReqId, groupId, productReq, bunchQty)
        }.toList()
        val remaining = qty - execReqs.fold(BigDecimal.ZERO) { acc, order -> acc + order.qty }
        return execReqs + ExecutionModel.FillExecMessage(filledId, execReqId, groupId, productReq, remaining)
    }

    /**
     * Invoked by the parent class based on a timer
     * to simulate messages from an external system
     */
    override fun release(element: ExecutionModel.FillExecMessage) {
        log.info("Release a new execution: $element")
        val workflow = workflowClient.newUntypedWorkflowStub(
                element.groupId.toString(),
                Optional.empty(),
                Optional.of("ExecutionWorkflow-v1"))
        workflow.signal("ExecutionWorkflow-NewFill-v1", element);
        log.info("New fill sent for ${element.groupId} with ${element.qty}")
    }

}