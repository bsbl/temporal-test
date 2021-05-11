package seb.temporal.test.flows.group

import io.temporal.client.WorkflowClient
import io.temporal.worker.WorkerFactory


class GroupWorker(private val groupActivity: GroupActivity, private val workflowClient: WorkflowClient) {

    // Worker factory is used to create Workers that poll specific Task Queues.
    val factory = WorkerFactory.newInstance(workflowClient)
    val worker = factory.newWorker(GroupWorkflowImpl.queueName)

    // This Worker hosts both Workflow and Activity implementations.
    // Workflows are stateful so a type is needed to create instances.
    fun start() {
        worker.registerWorkflowImplementationTypes(GroupWorkflowImpl::class.java)
        // Activities are stateless and thread safe so a shared instance is used.
        worker.registerActivitiesImplementations(groupActivity)
        // Start listening to the Task Queue.
        factory.start()
    }

}