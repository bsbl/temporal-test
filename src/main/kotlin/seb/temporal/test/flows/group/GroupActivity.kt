package seb.temporal.test.flows.group

import io.temporal.activity.Activity
import io.temporal.activity.ActivityInterface
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import seb.temporal.test.glue.KafkaConfiguration
import seb.temporal.test.glue.KafkaResourceFactory
import seb.temporal.test.flows.exec.Group
import seb.temporal.test.store.OrderStore
import seb.temporal.test.utils.Json
import seb.temporal.test.utils.info
import seb.temporal.test.utils.logger
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
    fun releaseGroup(groupId: UUID)

}


class GroupActivityImpl(private val store: OrderStore,
                        private val kafkaResourceFactory: KafkaResourceFactory): GroupActivity {

    private val log by logger()

    // force non null / required as per configuration
    private val producer: KafkaProducer<String, String> = kafkaResourceFactory.newProducer(KafkaConfiguration.executionGwTopic)!!

    override fun group(groupId: UUID): Boolean {
        val ctx = Activity.getExecutionContext()
        log.info("${ctx.info()}::grouping orders $groupId")
        val orders = store.groupPendingOrders(groupId)
        log.info("${ctx.info()}::group $groupId contains ${orders.size} orders")
        return orders.isNotEmpty()
    }

    override fun releaseGroup(groupId: UUID) {
        val ctx = Activity.getExecutionContext()
        log.info("${ctx.info()}::release group $groupId")
        val orderIds = store.groupPendingOrders(groupId)
        log.info("${ctx.info()}::Start an execution workflow for group ${groupId} with orders $orderIds")
        val orders = store.get(orderIds)
        val group = Group(groupId, orders)
        log.info("Publish into Kafka")
        // idempotent thanks to the groupId
        val future = producer.send(
                ProducerRecord(KafkaConfiguration.executionGwTopic,
                    groupId.toString(),
                        Json.objectMapper.writeValueAsString(group)))
        log.info("Waiting (until retries finish) for ack(s) with groupId: $groupId")
        val record = future.get()
        log.info("Message published successfully for key $groupId, record=$record")
    }

}
