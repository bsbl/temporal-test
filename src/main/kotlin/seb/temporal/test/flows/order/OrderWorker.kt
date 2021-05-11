package seb.temporal.test.flows.order

import io.temporal.client.WorkflowClient
import io.temporal.worker.WorkerFactory


class OrderWorker(private val orderActivity: OrderActivity, private val workflowClient: WorkflowClient) {

    // Worker factory is used to create Workers that poll specific Task Queues.
    val factory = WorkerFactory.newInstance(workflowClient)
    val worker = factory.newWorker(OrderWorkflowImpl.queueName)

    // This Worker hosts both Workflow and Activity implementations.
    // Workflows are stateful so a type is needed to create instances.
    fun start() {
        worker.registerWorkflowImplementationTypes(OrderWorkflowImpl::class.java)
        // Activities are stateless and thread safe so a shared instance is used.
        worker.registerActivitiesImplementations(orderActivity)
        // Start listening to the Task Queue.
        factory.start()
    }

}