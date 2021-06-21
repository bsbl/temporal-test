package seb.temporal.test.flows.exec

import com.fasterxml.jackson.annotation.JsonProperty
import io.temporal.activity.ActivityInterface
import io.temporal.client.WorkflowClient
import seb.temporal.test.ExecutionModel
import seb.temporal.test.flows.order.OrderStatus
import seb.temporal.test.inbound.ProductReq
import seb.temporal.test.utils.logger
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

@ActivityInterface
interface ExecutionActivity {
    /**
     * Allocate filled quantities to different orders (and hence specific account ids).
     */
    fun allocation(fillExecMessage: ExecutionModel.FillExecMessage): Allocations

    /**
     * Register transactions (debit/credit) in the accounting system (ledger).
     */
    fun booking(allocs: Allocations)

    fun allocationCompleted(groupId: UUID, orderIds: Map<String, UUID>)
}

data class Allocations(
        @JsonProperty("fillId")    val fillId:    UUID,
        @JsonProperty("execReqId") val execReqId: String,
        @JsonProperty("groupId")   val groupId:   UUID,
        @JsonProperty("qty")       val qty:       BigDecimal,
        @JsonProperty("allocs")    val allocs:    List<Allocation>
)

data class Allocation(@JsonProperty("product")   val product: ProductReq,
                      @JsonProperty("qty")       val qty: BigDecimal,
                      @JsonProperty("accountId") val accountId: String
)

class ExecutionActivityImpl(private val workflowClient: WorkflowClient): ExecutionActivity {

    private val log by logger()

    override fun allocation(fillExecMessage: ExecutionModel.FillExecMessage): Allocations {
        // Allocation phase: this phase is not implemented since it does not bring new things into the picture
        log.info("Allocation: $fillExecMessage")
        //TODO SBL would need to fetch the orders details by querying the order workflow
        //         to retrieve the accountIds and expected quantities
        return Allocations(
               fillExecMessage.fillId, fillExecMessage.execReqId, fillExecMessage.groupId,
                fillExecMessage.qty, emptyList()
        )
    }

    override fun booking(allocs: Allocations) {
        log.info("Allocations to be sent for booking: $allocs")
        //TODO SBL other workflows could be started here for booking
    }

    override fun allocationCompleted(groupId: UUID, orderIds: Map<String, UUID>) {
        orderIds.forEach {
            changeStatusQuietly(groupId, orderIds.entries.first().key, orderIds.entries.first().value)
        }
    }

    private fun changeStatusQuietly(groupId: UUID, inboundId: String, orderId: UUID) {
        try {
            val workflow = workflowClient.newUntypedWorkflowStub(
                    inboundId.toString(),
                    Optional.empty(),
                    Optional.of(""))
            workflow.signal("OrderWorkflow-SetStatus-v1", OrderStatus.complete, "");
            log.info("New signal sent (OrderWorkflow-SetStatus-v1) for $groupId, $inboundId, $orderId to ${OrderStatus.complete}")
        }
        catch (ex: IllegalStateException) {
            log.error("An exception was thrown while changing the status of an order - check whether the workflow is already in terminated state: $groupId, $inboundId, $orderId")
        }
    }

}

