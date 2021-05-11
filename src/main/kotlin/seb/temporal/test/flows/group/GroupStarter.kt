package seb.temporal.test.flows.group

import io.temporal.api.enums.v1.WorkflowIdReusePolicy
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowExecutionAlreadyStarted
import io.temporal.client.WorkflowOptions
import seb.temporal.test.utils.logger
import java.time.Duration


class GroupStarter(private val workflowClient: WorkflowClient) {

    private val log by logger()

    fun start() {
        // cron based execution: https://github.com/temporalio/samples-java/blob/079316d43d23bad3b785a3b1fa38f0c572321a28/src/main/java/io/temporal/samples/hello/HelloCron.java
        val instance = workflowClient.newWorkflowStub(GroupWorkflow::class.java,
                WorkflowOptions.newBuilder()
                        .setCronSchedule("* * * * *") // run every minute
                        .setTaskQueue(GroupWorkflowImpl.queueName)
                        .setWorkflowId(GroupWorkflowImpl.groupWorkflowRelease)
                        // we want our workflow to run forever - this example would stop the cron after 3 minutes:
                        // .setWorkflowExecutionTimeout(Duration.ofMinutes(3))
                        // stop the run after 30 seconds
                        .setWorkflowRunTimeout(Duration.ofSeconds(30))
                        .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE)
                        .build())
        try {
            // Start asynchronously
            val execution = WorkflowClient.start(instance::releaseGroup)
            log.info("Workflow started: $execution")
        }
        catch (e: WorkflowExecutionAlreadyStarted) {
            log.info("Workflow already running: ${e.execution}")
        }
    }

}