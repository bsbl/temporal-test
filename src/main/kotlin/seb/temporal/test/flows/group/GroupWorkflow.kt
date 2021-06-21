package seb.temporal.test.flows.group

import com.datastax.oss.driver.api.core.uuid.Uuids
import io.temporal.activity.ActivityOptions
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import seb.temporal.test.GroupModel
import seb.temporal.test.flows.order.OrderStatus
import seb.temporal.test.utils.logger
import java.time.Duration
import java.util.UUID


@WorkflowInterface
interface GroupWorkflow {

    /**
     * This workflow is cron scheduled.
     */
    @WorkflowMethod(name = "GroupWorkflow-v1")
    fun releaseGroup()

}


class GroupWorkflowImpl() : GroupWorkflow {

    private val continueAsNewStub: GroupWorkflow
    private val log by logger()
    private val groupActivity: GroupActivity

    /**
     * Ensure that the group is properly released
     */
    private var groupId: UUID? = null

    /**
     * Used to continue as new
     */
    private var executionCount = 0

    private val MAX_EXECUTION_BEFORE_CONTINUE_AS_NEW = 100

    init {
        this.groupActivity = Workflow.newActivityStub(
                GroupActivity::class.java,
                ActivityOptions.newBuilder()
                        .setTaskQueue(queueName)
                        .setStartToCloseTimeout(Duration.ofSeconds(20))
                        .build())
        this.continueAsNewStub = Workflow.newContinueAsNewStub(GroupWorkflow::class.java)
    }

    override fun releaseGroup() {
        if (executionCount >= MAX_EXECUTION_BEFORE_CONTINUE_AS_NEW) {
            log.info("Continue as new workflow instance $executionCount")
            executionCount = 0
            continueAsNewStub.releaseGroup()
        }
        log.info("Release current group")
        if (groupId == null) {
            groupId = Uuids.timeBased()
        }
        if (groupActivity.group(groupId!!)) {
            val orders = groupActivity.releaseGroup(groupId!!)
            groupActivity.notifyOrders(groupId!!, OrderStatus.sent, orders)
            groupActivity.logEvent(groupId!!, GroupModel.Group(groupId!!, orders))
        }
        // indicates to the next tick that the previous execution was
        // successful and that the group of orders has been released:
        groupId = null
        executionCount++
    }

    companion object {
        const val queueName = "group_q";
        const val workflowIdGroupRelease = "group"
    }
}