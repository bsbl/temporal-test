package seb.temporal.test.glue

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import seb.temporal.test.flows.group.GroupActivity
import seb.temporal.test.flows.group.GroupActivityImpl
import seb.temporal.test.flows.group.GroupStarter
import seb.temporal.test.flows.group.GroupWorker
import seb.temporal.test.inbound.OrderInitiator
import seb.temporal.test.inbound.OrderInitiatorImpl
import seb.temporal.test.flows.order.OrderActivity
import seb.temporal.test.flows.order.OrderActivityImpl
import seb.temporal.test.flows.order.OrderWorker
import seb.temporal.test.store.OrderStore

@Configuration
class TemporalConfiguration {

    @Bean
    fun workflowClient(): WorkflowClient {
        val service = WorkflowServiceStubs.newInstance();
        // WorkflowClient can be used to start, signal, query, cancel, and terminate Workflows.
        val client = WorkflowClient.newInstance(service);
        return client
    }

    //////////////////////////// ORDER FLOW ////////////////////////////

    @Bean
    fun orderActivities(orderStore: OrderStore, kafkaResourceFactory: KafkaResourceFactory) =
            OrderActivityImpl(orderStore, kafkaResourceFactory)

    @Bean(initMethod = "start")
    fun orderWorker(orderActivity: OrderActivity, orderWorkflowClient: WorkflowClient): OrderWorker =
            OrderWorker(orderActivity, orderWorkflowClient)

    @Bean(initMethod = "start")
    fun orderInitiator(orderWorkflowClient: WorkflowClient,
                       kafkaResourceFactory: KafkaResourceFactory): OrderInitiator =
            OrderInitiatorImpl(orderWorkflowClient, kafkaResourceFactory)


    //////////////////////////// GROUP FLOW ////////////////////////////

    @Bean
    fun groupActivities(orderStore: OrderStore, kafkaResourceFactory: KafkaResourceFactory): GroupActivity =
            GroupActivityImpl(orderStore, kafkaResourceFactory)

    @Bean(initMethod = "start")
    fun groupWorker(groupActivity: GroupActivity, workflowClient: WorkflowClient): GroupWorker =
            GroupWorker(groupActivity, workflowClient)

    @Bean(initMethod = "start")
    fun groupStarter(workflowClient: WorkflowClient): GroupStarter =
            GroupStarter(workflowClient)


    //////////////////////////// EXEC FLOW ////////////////////////////


}

/*
@Bean("orderWorkflowFactory")
fun orderWorkflowFactory(@Autowired @Qualifier("orderWorkflowClient") client: WorkflowClient):
        WorkflowStubFactory<OrderWorkflow> = WorkflowStubFactory(client, OrderWorkflow::class.java,
        WorkflowOptions.newBuilder()
                .setTaskQueue(OrderWorkflowImpl.queueName)
                // A WorkflowId prevents this it from having duplicate instances, remove it to duplicate.
                .setWorkflowId("order-workflow")
                .build())


@Bean("groupWorkflowFactory")
fun groupWorkflowFactory(@Autowired @Qualifier("groupWorkflowClient") client: WorkflowClient):
        WorkflowStubFactory<GroupWorkflow> = WorkflowStubFactory(client, GroupWorkflow::class.java,
        WorkflowOptions.newBuilder()
                .setTaskQueue(GroupWorkflowImpl.queueName)
                // A WorkflowId prevents this it from having duplicate instances, remove it to duplicate.
                .setWorkflowId("group-workflow")
                .build())


}

class WorkflowStubFactory<T>(private val client: WorkflowClient, private val clazz: Class<T>, private val defaultOptions: WorkflowOptions) {
fun new(): T = client.newWorkflowStub(clazz, defaultOptions)
fun new(options: WorkflowOptions): T = client.newWorkflowStub(clazz, options)
}
*/
/*
        WorkflowServiceStubs service = WorkflowServiceStubs.newInstance();
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(Shared.MONEY_TRANSFER_TASK_QUEUE)
                // A WorkflowId prevents this it from having duplicate instances, remove it to duplicate.
                .setWorkflowId("money-transfer-workflow")
                .build();
        // WorkflowClient can be used to start, signal, query, cancel, and terminate Workflows.
        WorkflowClient client = WorkflowClient.newInstance(service);
        // WorkflowStubs enable calls to methods as if the Workflow object is local, but actually perform an RPC.
        MoneyTransferWorkflow workflow = client.newWorkflowStub(MoneyTransferWorkflow.class, options);
        String referenceId = UUID.randomUUID().toString();
        String fromAccount = "001-001";
        String toAccount = "002-002";
        double amount = 18.74;
        // Asynchronous execution. This process will exit after making this call.
        WorkflowExecution we = WorkflowClient.start(workflow::transfer, fromAccount, toAccount, referenceId, amount);
        System.out.printf("\nTransfer of $%f from account %s to account %s is processing\n", amount, fromAccount, toAccount);
        System.out.printf("\nWorkflowID: %s RunID: %s", we.getWorkflowId(), we.getRunId());
        System.exit(0);

 */

