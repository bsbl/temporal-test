package seb.temporal.test.flows.group

import com.datastax.oss.driver.api.core.uuid.Uuids
import io.temporal.activity.ActivityOptions
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import seb.temporal.test.utils.logger
import java.time.Duration


@WorkflowInterface
interface GroupWorkflow {

    /**
     * This workflow is cron scheduled.
     */
    @WorkflowMethod
    fun releaseGroup()

}


class GroupWorkflowImpl() : GroupWorkflow {

    private val log by logger()
    private val groupActivity: GroupActivity

    init {
        this.groupActivity = Workflow.newActivityStub(
                GroupActivity::class.java,
                ActivityOptions.newBuilder()
                        .setTaskQueue(queueName)
                        .setStartToCloseTimeout(Duration.ofSeconds(20))
                        .build())
    }

    override fun releaseGroup() {
        log.info("Release current group")
        val groupId = Uuids.timeBased()
        if (groupActivity.group(groupId)) {
            groupActivity.releaseGroup(groupId)
        }
    }

    companion object {
        const val queueName = "group_q";
        const val groupWorkflowRelease = "group"
    }
}