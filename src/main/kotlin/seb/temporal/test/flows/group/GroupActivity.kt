package seb.temporal.test.flows.group

import io.temporal.activity.Activity
import io.temporal.activity.ActivityInterface
import io.temporal.client.WorkflowClient
import org.apache.kafka.clients.producer.KafkaProducer
import seb.temporal.test.ExecutionModel
import seb.temporal.test.GroupModel
import seb.temporal.test.OrderModel
import seb.temporal.test.flows.order.OrderStatus
import seb.temporal.test.glue.KafkaConfiguration
import seb.temporal.test.glue.KafkaResourceFactory
import seb.temporal.test.services.ExecutionService
import seb.temporal.test.store.OrderStore
import seb.temporal.test.utils.info
import seb.temporal.test.utils.logger
import seb.temporal.test.utils.send
import java.util.Optional
import java.util.UUID

@ActivityInterface
interface GroupActivity {

    /**
     * Group all pending orders into a new group
     * @return true if the group can be released (has pending orders) false otherwise
     */
    fun group(groupId: UUID): Boolean

    /**
     * Release a group of orders and send it to the market
     * @param groupId id of the group to release
     * @return execution id
     */
    fun releaseGroup(groupId: UUID): Set<OrderModel.Order>

    fun notifyOrders(groupId: UUID, status: OrderStatus, orders: Set<OrderModel.Order>)

    fun logEvent(groupId: UUID, group: GroupModel.Group)
}


class GroupActivityImpl(private val store: OrderStore,
                        kafkaResourceFactory: KafkaResourceFactory,
                        private val execService: ExecutionService,
                        private val workflowClient: WorkflowClient): GroupActivity {

    private val log by logger()

    // force non null / required as per configuration
    private val producer: KafkaProducer<String, String> = kafkaResourceFactory.newProducer(KafkaConfiguration.eventGroupTopic)!!

    override fun group(groupId: UUID): Boolean {
        val ctx = Activity.getExecutionContext()
        log.info("${ctx.info()}::grouping orders $groupId")
        val orders = store.groupPendingOrders(groupId)
        log.info("${ctx.info()}::group $groupId contains ${orders.size} orders")
        return orders.isNotEmpty()
    }

    override fun releaseGroup(groupId: UUID): Set<OrderModel.Order> {
        val ctx = Activity.getExecutionContext()
        log.info("${ctx.info()}::release group $groupId")
        val orderIds = store.groupPendingOrders(groupId)
        log.info("${ctx.info()}::Start an execution workflow for group $groupId with orders $orderIds")
        val orders = store.get(orderIds)
        val group = GroupModel.Group(groupId, orders)
        // this is where the critical section starts
        // call the execution service in rpc style
        // this is volontary RPC to demonstrate how
        // Temporal is able to deal to failures and
        // replay within a workflow:
        execService.sendExecution(ExecutionModel.ExecReq(
                group.groupId, group.orders))
        return group.orders
    }

    override fun notifyOrders(groupId: UUID, status: OrderStatus, orders: Set<OrderModel.Order>) {
        log.info("Change order status: $groupId to $status")
        orders.forEach { order ->
                val workflow = workflowClient.newUntypedWorkflowStub(
                        order.inboundId.toString(),
                        Optional.empty(),
                        Optional.of(""))
                workflow.signal("OrderWorkflow-SetStatus-v1", status, "");
                log.info("New signal sent (OrderWorkflow-SetStatus-v1) for $groupId, $order to $status")
        }
    }

    override fun logEvent(groupId: UUID, group: GroupModel.Group) {
        log.info("Publish into Kafka")
        // idempotent thanks to the groupId
        val future = producer.send(KafkaConfiguration.eventGroupTopic, groupId, group)
        log.info("Waiting (until retries finish) for ack(s) with groupId: $groupId")
        val record = future.get()
        log.info("Message published successfully for key $groupId, record=$record")
    }

}
