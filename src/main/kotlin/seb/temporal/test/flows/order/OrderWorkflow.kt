package seb.temporal.test.flows.order

import com.datastax.oss.driver.api.core.uuid.Uuids
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.QueryMethod
import io.temporal.workflow.SignalMethod
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import seb.temporal.test.inbound.OrderReq
import seb.temporal.test.utils.logger
import java.time.Duration
import java.util.UUID


@WorkflowInterface
interface OrderWorkflow {

    @WorkflowMethod(name = "OrderWorkflow-v1")
    fun acceptOrder(order: OrderReq)

    @SignalMethod(name = "OrderWorkflow-SetStatus-v1")
    fun setStatus(status: OrderStatus, message: String?)

    @QueryMethod
    fun getStatus(): OrderStatus

}

enum class OrderStatus(val terminated: Boolean) {
    /**
     * New order not yet sent to the market.
     */
    new(false),
    /**
     * Ready to be sent to the market (grouped).
     */
    ready(false),
    /**
     * Sent to the market
     */
    sent(false),
    /**
     * Something bad happened preventing the order
     * to be processed successfully on the market.
     */
    incomplete(true),
    /**
     * The order has been honored and been successfully
     * processed on the appropriate market.
     */
    complete(true)
}

class OrderWorkflowImpl() : OrderWorkflow {

    private val log by logger()

    val orderActivity: OrderActivity

    private var orderId: UUID? = null
    private var status = OrderStatus.new
    private var statusMessage: String? = null

    init {
        val retryoptions = RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofSeconds(1))
                .setMaximumInterval(Duration.ofSeconds(100))
                .setBackoffCoefficient(2.0)
                .setMaximumAttempts(500)
                .build()
        val options = ActivityOptions.newBuilder()
                // Timeout options specify when to automatically timeout Activities if the process is taking too long.
                .setStartToCloseTimeout(Duration.ofSeconds(5))
                // Optionally provide customized RetryOptions.
                // Temporal retries failures by default, this is simply an example.
                .setRetryOptions(retryoptions)
                .setTaskQueue(queueName)
                .build()

        this.orderActivity = Workflow.newActivityStub(
                OrderActivity::class.java, options)
    }

    override fun acceptOrder(order: OrderReq) {
        // Generate a TimeUUID in replay safe way:
        this.orderId = Workflow.sideEffect(UUID::class.java) {
            Uuids.timeBased()
        }
        orderActivity.assertOrderValid(order)
        orderActivity.saveOrder(order, this.orderId?: error("Should not be null"))
        this.status = OrderStatus.ready
        // wait for the status to be terminated or timeout
        while (true) {
            var status = this.status
            // after 30 days, timeout and force workflow instance to terminate
            Workflow.await(Duration.ofDays(30)) { this.status != status || this.status.terminated }
            if (this.status.terminated) {
                log.info("Order $orderId / ${order.id} isTerminated=${this.status}")
                return
            }
            log.info("Order workflow transition for order $orderId / ${order.id}: isTerminated=${status.terminated}")
        }
    }

    override fun setStatus(status: OrderStatus, message: String?) {
        this.status = status
    }

    override fun getStatus(): OrderStatus = this.status

    companion object {
        const val queueName = "order_q";
    }
}
