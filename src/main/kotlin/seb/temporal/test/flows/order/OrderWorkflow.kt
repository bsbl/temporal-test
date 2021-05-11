package seb.temporal.test.flows.order

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import seb.temporal.test.inbound.OrderReq
import java.time.Duration
import java.util.UUID


@WorkflowInterface
interface OrderWorkflow {

    @WorkflowMethod
    fun acceptOrder(order: OrderReq): UUID

}


class OrderWorkflowImpl() : OrderWorkflow {

    val orderActivity: OrderActivity

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

    override fun acceptOrder(order: OrderReq): UUID {
        orderActivity.assertOrderValid(order)
        return orderActivity.saveOrder(order)
    }

    companion object {
        const val queueName = "order_q";
    }
}