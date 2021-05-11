package seb.temporal.test.flows.order

import io.temporal.activity.Activity
import io.temporal.activity.ActivityInterface
import io.temporal.client.WorkflowClient
import org.springframework.util.Assert
import seb.temporal.test.glue.KafkaConfig
import seb.temporal.test.glue.KafkaConfiguration
import seb.temporal.test.glue.KafkaResourceFactory
import seb.temporal.test.inbound.OrderReq
import seb.temporal.test.store.OrderStore
import seb.temporal.test.utils.info
import seb.temporal.test.utils.logger
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
    fun saveOrder(order: OrderReq): UUID

    /**
     * @param order state change event
     */
    fun publishOrder(order: OrderReq)

}


/**
 * Example of idempotent in-memory activities implementation
 */
class OrderActivityImpl(private val orderStore: OrderStore, private val kafkaResourceFactory: KafkaResourceFactory): OrderActivity {

    private val log by logger()

    val producer = kafkaResourceFactory.newProducer<String, String>(KafkaConfiguration.orderTopic)!! // force non null or fail

    override fun assertOrderValid(order: OrderReq) {
        Assert.isTrue(order.qty != BigDecimal.ZERO, "Quantity must be different than 0 (zero)")
    }

    override fun saveOrder(order: OrderReq): UUID {
        val ctx = Activity.getExecutionContext()
        log.info("${ctx.info()}::save order ${order.id}")
        return orderStore.save(order)
    }

    override fun publishOrder(order: OrderReq) {

    }
}

