package seb.temporal.test.flows.order

import io.temporal.activity.Activity
import io.temporal.activity.ActivityInterface
import io.temporal.api.enums.v1.WorkflowIdReusePolicy
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowExecutionAlreadyStarted
import io.temporal.client.WorkflowOptions
import org.springframework.util.Assert
import seb.temporal.test.OrderModel
import seb.temporal.test.glue.KafkaConfiguration
import seb.temporal.test.glue.KafkaResourceFactory
import seb.temporal.test.inbound.OrderReq
import seb.temporal.test.store.OrderStore
import seb.temporal.test.utils.info
import seb.temporal.test.utils.logger
import seb.temporal.test.utils.send
import java.math.BigDecimal
import java.util.UUID


@ActivityInterface
interface OrderActivity {

    /**
     * Perform order validation
     */
    fun assertOrderValid(order: OrderReq)

    /**
     * Save an order into the order store.
     * @param order order to be store
     * @return order store ID of the provided order
     */
    fun saveOrder(orderReq: OrderReq, alreadySetOrderId: UUID)

}


/**
 * Example of idempotent in-memory activities implementation
 */
class OrderActivityImpl(private val orderStore: OrderStore,
                        private val kafkaResourceFactory: KafkaResourceFactory,
                        private val workflowClient: WorkflowClient): OrderActivity {

    private val log by logger()

    val producer = kafkaResourceFactory.newProducer<String, String>(KafkaConfiguration.eventOrderTopic)!! // force non null or fail

    override fun assertOrderValid(order: OrderReq) {
        Assert.isTrue(order.qty != BigDecimal.ZERO, "Quantity must be different than 0 (zero)")
    }

    override fun saveOrder(orderReq: OrderReq, orderId: UUID) {
        val ctx = Activity.getExecutionContext()
        log.info("${ctx.info()}::save order ${orderReq.id} -> $orderId")
        val order = OrderModel.Order(
                orderId,
                orderReq.id,
                orderReq.qty,
                orderReq.product)
        orderStore.save(order)
        producer.send(KafkaConfiguration.eventOrderTopic, orderId, order)
    }

}

