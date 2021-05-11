package seb.temporal.test.flows.exec

import io.temporal.activity.ActivityOptions
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import seb.temporal.test.inbound.OrderReq
import java.util.UUID


@WorkflowInterface
interface ExecutionWorkflow {
}

data class Group(val groupId: UUID, val orders: Set<OrderReq>)

class ExecutionWorkflowImpl() : ExecutionWorkflow {

    val executionActivity: ExecutionActivity

    init {
        this.executionActivity = Workflow.newActivityStub(
                ExecutionActivity::class.java,
                ActivityOptions.newBuilder()
                        .setTaskQueue(queueName)
                        .build())
    }

    companion object {
        const val queueName = "exec_q";
    }
}